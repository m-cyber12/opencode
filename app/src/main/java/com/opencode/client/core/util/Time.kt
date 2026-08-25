package com.opencode.client.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Time {

    fun now(): Long = System.currentTimeMillis()

    /** "14:32" style time. */
    fun clock(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    /** "Aug 24, 14:32". */
    fun shortDateTime(ms: Long): String =
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ms))

    /** Bucket label for session grouping. */
    fun dayBucket(epochMs: Long): DayBucket {
        val cal = java.util.Calendar.getInstance()
        val startOfToday = cal.apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return when {
            epochMs >= startOfToday -> DayBucket.TODAY
            epochMs >= startOfToday - 86_400_000L -> DayBucket.YESTERDAY
            epochMs >= startOfToday - 6 * 86_400_000L -> DayBucket.THIS_WEEK
            else -> DayBucket.OLDER
        }
    }

    fun duration(ms: Long): String = when {
        ms < 1_000 -> "${ms}ms"
        ms < 60_000 -> String.format(Locale.US, "%.1fs", ms / 1000.0)
        else -> String.format(Locale.US, "%dm %02ds", ms / 60_000, (ms % 60_000) / 1000)
    }
}

enum class DayBucket(val label: String) {
    TODAY("Today"), YESTERDAY("Yesterday"), THIS_WEEK("This week"), OLDER("Earlier")
}
