package com.opencode.client.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.opencode.client.MainActivity
import com.opencode.client.R
import com.opencode.client.notify.Notifier

/**
 * Optional foreground service (dataSync) that keeps the OpenCode event stream attached while an
 * agent task runs and the user has left the app.
 *
 * Scope honesty: this extends how long work stays observable on modern Android; it is NOT a way
 * to guarantee execution after process death or force-stops. It stops itself the moment no
 * session is busy.
 */
class AgentForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(
                        Notifier.ID_KEEPALIVE,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(Notifier.ID_KEEPALIVE, notification)
                }
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Notifier.CHANNEL_AGENT)
            .setSmallIcon(R.drawable.ic_stat_opencode)
            .setContentTitle("OpenCode is working")
            .setContentText("Your agent is running a task. Tap to return.")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.opencode.client.STOP"

        /** Starts/stops the service to match the busy state. Safe to call from any process state. */
        fun setRunning(context: Context, running: Boolean) {
            val intent = Intent(context, AgentForegroundService::class.java)
            try {
                if (running) {
                    context.startForegroundService(intent)
                } else {
                    intent.action = ACTION_STOP
                    context.startService(intent)
                }
            } catch (_: IllegalStateException) {
                // Starting services from background is restricted; the stream simply continues
                // in-process and completion notifications fire only while we stay alive.
            }
        }
    }
}
