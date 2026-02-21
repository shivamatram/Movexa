package com.example.movexa.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Utility class for time formatting used across the app.
 * Provides relative time strings ("5 min ago"), formatted dates, etc.
 */
object TimeUtils {

    /**
     * Returns a human-readable relative time string.
     *
     * Examples:
     * - "Just now" (< 1 minute)
     * - "5 min ago"
     * - "2 hr ago"
     * - "3 days ago"
     * - "Jan 15" (> 7 days)
     */
    fun getRelativeTimeString(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                "$minutes min ago"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "$hours hr ago"
            }
            diff < TimeUnit.DAYS.toMillis(7) -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                "$days days ago"
            }
            else -> {
                val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    /**
     * Format a timestamp to "Today", "Yesterday", or a date string.
     */
    fun getDateLabel(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val todayStart = getStartOfDay(now)
        val yesterdayStart = todayStart - TimeUnit.DAYS.toMillis(1)

        return when {
            timestamp >= todayStart -> "Today"
            timestamp >= yesterdayStart -> "Yesterday"
            else -> {
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    /**
     * Format timestamp to time only (e.g., "2:30 PM").
     */
    fun getTimeString(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Format timestamp to full date-time string.
     */
    fun getFullDateTimeString(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Get the start of the current day in milliseconds.
     */
    private fun getStartOfDay(timestamp: Long): Long {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dayString = sdf.format(Date(timestamp))
        return sdf.parse(dayString)?.time ?: timestamp
    }

    /**
     * Format last updated timestamp for dashboard headers.
     * Returns "Last updated 2:30 PM" or "Last updated Jan 15, 2:30 PM"
     */
    fun getLastUpdatedString(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val todayStart = getStartOfDay(now)

        return if (timestamp >= todayStart) {
            getTimeString(timestamp)
        } else {
            val sdf = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
