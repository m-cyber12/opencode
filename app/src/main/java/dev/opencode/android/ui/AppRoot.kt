package dev.opencode.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.navigation.NavHost
import androidx.compose.navigation.composable
import androidx.compose.navigation.rememberNavController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.viewModel
import dev.opencode.android.ui.chat.ChatScreen
import dev.opencode.android.ui.chat.ChatViewModel
import dev.opencode.android.ui.diagnostics.DiagnosticsScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.IconButton
import dev.opencode.android.ui.projects.ProjectsScreen
import dev.opencode.android.ui.projects.ProjectsViewModel
import dev.opencode.android.ui.projects.WelcomeScreen
import dev.opencode.android.ui.settings.SettingsScreen
import dev.opencode.android.ui.settings.SettingsViewModel
import dev.opencode.android.runtime.RuntimePhase
import dev.opencode.android.runtime.RuntimeState
import dev.opencode.android.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(container: AppContainer) {
    val nav = rememberNavController()
    var drawerOpen by remember { mutableStateOf(false) }
    val runtimeState = container.runtimeManager.state.collectAsStateWithLifecycle().value

    ModalNavigationDrawer(
        drawerState = rememberDrawerState(DrawerValue.Closed),
        drawerContent = {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp)) {
                Text("OpenCode", style = MaterialTheme.typography.titleLarge)
                NavigationDrawerItem(
                    label = { Text("Projects") },
                    selected = false,
                    onClick = { drawerOpen = false; nav.navigate("projects") },
                )
                NavigationDrawerItem(
                    label = { Text("Diagnostics") },
                    selected = false,
                    onClick = { drawerOpen = false; nav.navigate("diagnostics") },
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { drawerOpen = false; nav.navigate("settings") },
                )
            }
        },
        content = {
            NavHost(nav, "welcome") {
                composable("welcome") {
                    WelcomeScreen(
                        runtimeState = runtimeState,
                        onReady = { nav.navigate("projects") },
                        onCreateProject = { nav.navigate("projects") },
                    )
                }
                composable("projects") {
                    val vm: ProjectsViewModel = viewModel(factory = container.projectsViewModelFactory())
                    ProjectsScreen(
                        vm = vm,
                        onOpenProject = { id -> nav.navigate("chat/$id") },
                        onOpenSettings = { drawerOpen = true; nav.navigate("settings") },
                        onOpenDiagnostics = { drawerOpen = true; nav.navigate("diagnostics") },
                    )
                }
                composable(
                    route = "chat/{projectId}",
                    arguments = listOf(androidx.navigation.navArgument("projectId") { type = androidx.navigation.NavType.StringType }),
                ) { backStackEntry ->
                    val projectId = backStackEntry.getString() ?: ""
                    val vm: ChatViewModel = viewModel(factory = container.chatViewModelFactory(projectId))
                    ChatScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() },
                        onOpenSettings = { drawerOpen = true },
                    )
                }
                composable("settings") {
                    val vm: SettingsViewModel = viewModel(factory = container.settingsViewModelFactory())
                    SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
                }
                composable("diagnostics") {
                    DiagnosticsScreen(
                        runtimeState = runtimeState,
                        logs = container.logs,
                        onCopyLogs = { /* clipboard copy */ },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        },
    )
}