package com.opencode.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.opencode.client.LocalGraph
import com.opencode.client.core.Outcome
import com.opencode.client.data.settings.ServerKind
import com.opencode.client.domain.ConnectionState
import com.opencode.client.engine.GatewayController
import com.opencode.client.ui.screens.activity.ActivityScreen
import com.opencode.client.ui.screens.connect.ConnectScreen
import com.opencode.client.ui.screens.diff.DiffScreen
import com.opencode.client.ui.screens.files.FilesScreen
import com.opencode.client.ui.screens.files.FileViewerScreen
import com.opencode.client.ui.screens.onboarding.OnboardingScreen
import com.opencode.client.ui.screens.projects.ProjectsScreen
import com.opencode.client.ui.screens.settings.SettingsScreen
import com.opencode.client.ui.screens.workspaces.WorkspacesScreen
import com.opencode.client.ui.screens.workspace.WorkspaceScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"
    const val WORKSPACES = "workspaces"          // zero-setup home (cloud)
    const val PROJECTS = "projects"              // developer home (self-hosted servers)
    const val CONNECT = "connect"                // developer: add/edit a manual server
    const val WORKSPACE = "workspace/{sessionId}"
    const val FILES = "files"
    const val FILE_VIEWER = "fileviewer/{path}"
    const val DIFF = "diff/{sessionId}"
    const val SETTINGS = "settings"
    const val ACTIVITY = "activity/{sessionId}"

    fun workspace(sessionId: String) = "workspace/$sessionId"
    fun fileViewer(path: String) = "fileviewer/${android.net.Uri.encode(path)}"
    fun diff(sessionId: String) = "diff/$sessionId"
    fun activity(sessionId: String?) = "activity/${sessionId ?: "none"}"
}

@Composable
fun OpencodeNavHost(
    nav: NavHostController,
    deepLinkSessionId: String? = null,
) {
    val container = LocalGraph.instance
    val settings by container.settings.settings.collectAsState()
    val connection by container.serverController.connectionState.collectAsState()
    val auth by container.gateway.authState.collectAsState()

    NavHost(navController = nav, startDestination = Routes.ONBOARDING) {

        // ---------------------------------------------------------------- first run / sign-in
        composable(Routes.ONBOARDING) {
            // Returning cloud user with a live token skips straight to workspaces.
            LaunchedEffect(auth, settings.onboarded) {
                if (auth is GatewayController.AuthState.SignedIn && settings.onboarded &&
                    container.gateway.isConfigured && settings.activeServerId != null &&
                    connection is ConnectionState.Disconnected
                ) {
                    resumeLastOrWorkspaces(container, nav)
                }
            }
            OnboardingScreen(
                container = container,
                onSignedIn = { nav.navigate(Routes.WORKSPACES) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
                onDemo = { nav.navigate(Routes.workspace("new")) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }
            )
        }

        // ---------------------------------------------------------------- zero-setup home
        composable(Routes.WORKSPACES) {
            WorkspacesScreen(
                container = container,
                onOpenWorkspace = {
                    openAfterConnect(container, nav)
                },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onDeveloperConnect = { nav.navigate(Routes.CONNECT) }
            )
        }

        // ---------------------------------------------------------------- developer flows
        composable(Routes.CONNECT) {
            ConnectScreen(
                container = container,
                onConnected = { nav.navigate(Routes.PROJECTS) { popUpTo(Routes.CONNECT) { inclusive = true } } }
            )
            LaunchedEffect(settings.onboarded, settings.activeServerId) {
                if (settings.onboarded && connection is ConnectionState.Disconnected) {
                    val profile = settings.servers.firstOrNull { it.id == settings.activeServerId }
                        ?.takeIf { it.kind == ServerKind.SELF_HOSTED || it.kind == ServerKind.DEMO }
                        ?: return@LaunchedEffect
                    when (container.serverController.connect(profile)) {
                        is Outcome.Ok -> {
                            container.serverController.setProject(settings.lastProjectPath)
                            nav.navigate(Routes.workspace(settings.lastSessionId ?: "new")) {
                                popUpTo(Routes.CONNECT) { inclusive = true }
                            }
                        }
                        is Outcome.Err -> Unit
                    }
                }
            }
        }

        composable(Routes.PROJECTS) {
            ProjectsScreen(
                container = container,
                onOpenProject = { project ->
                    container.serverController.setProject(project.worktree)
                    val last = container.settings.value.lastSessionId
                    nav.navigate(Routes.workspace(last ?: "new"))
                },
                onSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }

        // ---------------------------------------------------------------- the agent workspace
        composable(
            Routes.WORKSPACE,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()

            if (sessionId.isBlank() || sessionId == "new") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                LaunchedEffect(Unit) {
                    var created: String? = null
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        when (val res = container.sessionRepo.createSession(null)) {
                            is Outcome.Ok -> created = res.value.id
                            is Outcome.Err -> Unit
                        }
                    }
                    val id = created ?: return@LaunchedEffect
                    persistLastSession(container, id)
                    nav.navigate(Routes.workspace(id)) {
                        popUpTo(Routes.workspace("new")) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else {
                LaunchedEffect(sessionId) { persistLastSession(container, sessionId) }

                WorkspaceScreen(
                    sessionId = sessionId,
                    container = container,
                    onBack = { /* drawer-first surface */ },
                    onOpenFiles = { nav.navigate(Routes.FILES) },
                    onOpenDiff = { nav.navigate(Routes.diff(sessionId)) },
                    onOpenActivity = { nav.navigate(Routes.activity(sessionId.ifBlank { null })) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onSessionSelected = { id ->
                        if (id != sessionId && id.isNotBlank()) {
                            nav.navigate(Routes.workspace(id)) { launchSingleTop = true }
                        }
                    },
                    onNewSession = {
                        nav.navigate(Routes.workspace("new")) { launchSingleTop = true }
                    }
                )

                // Notification deep-link: land directly in the referenced session once connected.
                LaunchedEffect(deepLinkSessionId, connection) {
                    if (deepLinkSessionId != null && connection is ConnectionState.Connected) {
                        nav.navigate(Routes.workspace(deepLinkSessionId)) { launchSingleTop = true }
                    }
                }
            }
        }

        composable(Routes.FILES) {
            FilesScreen(container = container, onBack = { nav.popBackStack() }, onOpenFile = { path -> nav.navigate(Routes.fileViewer(path)) })
        }

        composable(
            Routes.FILE_VIEWER,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { entry ->
            FileViewerScreen(
                path = entry.arguments?.getString("path").orEmpty(),
                container = container,
                onBack = { nav.popBackStack() }
            )
        }

        composable(
            Routes.DIFF,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { entry ->
            DiffScreen(
                sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                container = container,
                onBack = { nav.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, onBack = { nav.popBackStack() })
        }

        composable(
            Routes.ACTIVITY,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId")
            ActivityScreen(
                container = container,
                sessionId = sessionId?.takeIf { it != "none" && it.isNotBlank() },
                onBack = { nav.popBackStack() }
            )
        }
    }
}

private suspend fun resumeLastOrWorkspaces(
    container: com.opencode.client.AppContainer,
    nav: NavHostController,
) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Reconnect to the active cloud profile (workspace endpoint).
        val s = container.settings.value
        val profile = s.servers.firstOrNull { it.id == s.activeServerId }
        if (profile != null && profile.kind == ServerKind.CLOUD) {
            container.serverController.connect(profile)
        }
    }
    val lastSession = container.settings.value.lastSessionId
    if (lastSession != null &&
        container.serverController.connectionState.value is ConnectionState.Connected
    ) {
        nav.navigate(Routes.workspace(lastSession)) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
    } else {
        nav.navigate(Routes.WORKSPACES) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
    }
}

/** Waits (bounded) for the workspace connection to settle, then opens the session surface. */
fun openAfterConnect(
    container: com.opencode.client.AppContainer,
    nav: NavHostController,
) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        kotlinx.coroutines.withTimeoutOrNull(20_000) {
            container.serverController.connectionState.first { state ->
                state is ConnectionState.Connected ||
                    state is ConnectionState.Failed ||
                    state is ConnectionState.Disconnected
            }
        }
        nav.navigate(Routes.workspace("new"))
    }
}

private fun persistLastSession(
    container: com.opencode.client.AppContainer,
    sessionId: String,
) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        container.settings.setLastLocation(
            projectPath = container.serverController.activeDirectory,
            sessionId = sessionId
        )
    }
}
