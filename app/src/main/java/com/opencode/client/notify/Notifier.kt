package com.opencode.client.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.opencode.client.MainActivity
import com.opencode.client.R

/**
 * Foreground-process notifications for completed agent turns.
 *
 * Honest scope: streams only run while the app process is alive. When the user swipes the app
 * away, Android may reclaim the process and no further notifications are produced - this is a
 * documented platform constraint, not a silent promise.
 */
class Notifier(private val context: Context) {

    companion object {
        const val CHANNEL_AGENT = "agent_activity"
        private const val ID_FINISHED = 41
        const val ID_KEEPALIVE = 42
        const val EXTRA_SESSION_ID = "com.opencode.client.SESSION_ID"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_AGENT,
                "Agent activity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when OpenCode finishes working on a session"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifyAgentFinished(finishedCount: Int, sessionTitle: String?) {
        if (!canNotify()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = "OpenCode finished"
        val text = when {
            !sessionTitle.isNullOrBlank() -> "“${sessionTitle.take(48)}” is done."
            finishedCount == 1 -> "The agent finished its current task."
            else -> "$finishedCount sessions finished their work."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_AGENT)
            .setSmallIcon(R.drawable.ic_stat_opencode)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_FINISHED, notification)
        } catch (_: SecurityException) {
            // Permission revoked mid-flight; ignore.
        }
    }

    /** “Permission required” nudge — the agent is paused until the user answers. */
    fun notifyPermissionNeeded(sessionId: String?, title: String) {
        if (!canNotify()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            sessionId?.let { putExtra(EXTRA_SESSION_ID, it) }
        }
        val pending = PendingIntent.getActivity(
            context, sessionId?.hashCode() ?: 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_AGENT)
            .setSmallIcon(R.drawable.ic_stat_opencode)
            .setContentTitle("Permission required")
            .setContentText(title.take(120))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(43, notification)
        } catch (_: SecurityException) {
        }
    }
}
