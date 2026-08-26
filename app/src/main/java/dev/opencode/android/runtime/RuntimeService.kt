package dev.opencode.android.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.opencode.android.OpenCodeApp
import dev.opencode.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground service hosting the local OpenCode runtime.
 *
 * Why a foreground service (spec §20): agent turns may run for minutes. If the
 * app were only an activity, Android could kill it mid-generation, orphaning
 * PRoot tracees or corrupting workspace state. The service pins one runtime
 * process and enforces duplicate-start prevention + graceful shutdown.
 */
class RuntimeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ensureJob: Job? = null
    private var requestedProjectId: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }
        intent?.getStringExtra(EXTRA_PROJECT_ID)?.let { requestedProjectId = it }
        kickStart()
        return START_STICKY
    }

    private fun kickStart() {
        val c = OpenCodeApp.get().container
        if (ensureJob?.isActive == true) return // duplicate-start prevention
        ensureJob = scope.launch {
            val settings = c.settingsStore.current()
            val configJson = c.buildConfigContentJson()
            val authJson = c.secureCredentials.buildAuthContentJson()
            val projectId = requestedProjectId ?: c.projectRepository.list().firstOrNull()?.id
            c.logs.append("service", "ensureStarted(project=$projectId)")
            c.runtimeManager.ensureStarted { port ->
                c.launcher.defaultInputs(
                    port = port,
                    projectId = projectId,
                    authContentJson = authJson,
                    configContentJson = configJson,
                    disableSeccomp = false,
                    debugLogs = settings.runtimeDebugLogs,
                )
            }
            c.runtimeManager.resetRestartCounter()
        }
    }

    private fun stopSelfSafely() {
        ensureJob?.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        // Graceful shutdown without zombie tracees; bounded wait.
        ensureJob?.cancel()
        runBlocking {
            launch { OpenCodeApp.get().container.runtimeManager.stop("app shutdown") }
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.runtime_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.runtime_notification_title))
            .setContentText(getString(R.string.runtime_notification_text))
            .setOngoing(true)
            .build()

    companion object {
        const val CHANNEL_ID = "opencode_runtime"
        const val NOTIF_ID = 42
        const val ACTION_STOP = "dev.opencode.android.runtime.STOP"
        const val EXTRA_PROJECT_ID = "project_id"

        fun start(context: Context, projectId: String?) {
            val i = Intent(context, RuntimeService::class.java).apply {
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RuntimeService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}
