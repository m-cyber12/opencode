package com.opencode.client

import android.app.Application
import android.content.Context
import android.os.Bundle
import com.opencode.client.core.secure.CredentialStore
import com.opencode.client.core.secure.EncryptedCredentialStore
import com.opencode.client.data.repo.ConfigRepository
import com.opencode.client.data.repo.FileRepository
import com.opencode.client.data.repo.ProjectRepository
import com.opencode.client.data.repo.SessionRepository
import com.opencode.client.data.settings.SettingsRepository
import com.opencode.client.engine.GatewayController
import com.opencode.client.engine.ServerController
import com.opencode.client.notify.Notifier
import com.opencode.client.service.AgentForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Manual dependency graph - explicit, testable, no framework magic. */
class AppContainer(private val context: Context) {

    val credentialStore: CredentialStore by lazy {
        try {
            EncryptedCredentialStore(context)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Secure storage unavailable - refusing to store credentials in plaintext.", e
            )
        }
    }

    val settings: SettingsRepository by lazy { SettingsRepository(context).also { it.start() } }

    val serverController: ServerController by lazy {
        ServerController(
            credentialProvider = { profile -> credentialStore.get(profile.id) },
            bearerProvider = { profile ->
                if (profile.kind == com.opencode.client.data.settings.ServerKind.CLOUD)
                    credentialStore.get(GatewayController.TOKEN_KEY) else null
            }
        )
    }

    val gateway: GatewayController by lazy {
        GatewayController(settings, credentialStore, serverController)
    }

    val sessionRepo: SessionRepository by lazy { SessionRepository(serverController) }
    val projectRepo: ProjectRepository by lazy { ProjectRepository(serverController) }
    val fileRepo: FileRepository by lazy { FileRepository(serverController) }
    val configRepo: ConfigRepository by lazy { ConfigRepository(serverController) }

    val notifier: Notifier by lazy { Notifier(context) }
}

object LocalGraph {
    @Volatile
    lateinit var instance: AppContainer
        private set

    fun init(context: Context): AppContainer {
        if (!::instance.isInitialized) {
            synchronized(this) {
                if (!::instance.isInitialized) instance = AppContainer(context.applicationContext)
            }
        }
        return instance
    }
}

class OpencodeApp : Application() {

    private var resumedActivities = 0

    val isForeground: Boolean get() = resumedActivities > 0

    override fun onCreate() {
        super.onCreate()
        val container = LocalGraph.init(this)
        container.settings.start()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                resumedActivities++
            }

            override fun onActivityPaused(activity: android.app.Activity) {
                resumedActivities = (resumedActivities - 1).coerceAtLeast(0)
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })

        // Notify about finished agent work only while the process is alive AND app backgrounded;
        // Android does not guarantee long-lived streams in background, and we never claim otherwise.
        CoroutineScope(Dispatchers.Default).launch {
            var previousBusy = emptySet<String>()
            container.serverController.busySessions.collect { busy ->
                val finished = previousBusy - busy

                // Optional foreground service keeps the live stream attached while work runs
                // and the user leaves the app. Correct Android mechanism, not an infinite-
                // background promise: it stops the moment nothing is running.
                if (container.settings.value.keepAliveServiceEnabled) {
                    AgentForegroundService.setRunning(this@OpencodeApp, busy.isNotEmpty())
                }

                if (finished.isNotEmpty() && !isForeground &&
                    container.settings.value.notificationsEnabled
                ) {
                    val title = container.settings.value.lastSessionId
                    container.notifier.notifyAgentFinished(finished.size, title)
                }
                previousBusy = busy
            }
        }
    }
}
