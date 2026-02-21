package com.example.movexa.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Utility class for date and time formatting throughout Movexa.
 * All date operations should use this for consistency.
 */
object DateUtils {

    private val locale: Locale = Locale.getDefault()

    // ─── Formatting ─────────────────────────────────────────────

    /**
     * Format a timestamp to a display-friendly date string.
     * Example: "21 Feb 2026"
     */
    fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("dd MMM yyyy", locale).format(Date(timestamp))
    }

    /**
     * Format a timestamp to time string.
     * Example: "02:30 PM"
     */
    fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("hh:mm a", locale).format(Date(timestamp))
    }

    /**
     * Format a timestamp to full datetime string.
     * Example: "21 Feb 2026, 02:30 PM"
     */
    fun formatDateTime(timestamp: Long): String {
        return SimpleDateFormat("dd MMM yyyy, hh:mm a", locale).format(Date(timestamp))
    }

    /**
     * Format a timestamp to ISO 8601 format.
     */
    fun formatISO8601(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", locale)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestamp))
    }

    /**
     * Format a timestamp with a custom pattern.
     */
    fun format(timestamp: Long, pattern: String): String {
        return SimpleDateFormat(pattern, locale).format(Date(timestamp))
    }

    // ─── Parsing ────────────────────────────────────────────────

    /**
     * Parse a date string to timestamp.
     */
    fun parse(dateString: String, pattern: String): Long? {
        return try {
            SimpleDateFormat(pattern, locale).parse(dateString)?.time
        } catch (e: Exception) {
            null
        }
    }

    // ─── Relative Time ──────────────────────────────────────────

    /**
     * Get a human-readable relative time string.
     */
    fun getRelativeTime(timestamp: Long): String {
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
                "$days day${if (days > 1) "s" else ""} ago"
            }
            diff < TimeUnit.DAYS.toMillis(30) -> {
                val weeks = TimeUnit.MILLISECONDS.toDays(diff) / 7
                "$weeks week${if (weeks > 1) "s" else ""} ago"
            }
            else -> formatDate(timestamp)
        }
    }

    // ─── Utilities ──────────────────────────────────────────────

    /**
     * Check if a timestamp is today.
     */
    fun isToday(timestamp: Long): Boolean {
        val todayCal = Calendar.getInstance()
        val dateCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return todayCal.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
                todayCal.get(Calendar.DAY_OF_YEAR) == dateCal.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Check if a timestamp is yesterday.
     */
    fun isYesterday(timestamp: Long): Boolean {
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val dateCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return yesterdayCal.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
                yesterdayCal.get(Calendar.DAY_OF_YEAR) == dateCal.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Get the start of today (00:00:00) as timestamp.
     */
    fun getStartOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Format duration in milliseconds to a readable string.
     * Example: "2h 30m" or "45m"
     */
    fun formatDuration(durationMs: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }
}
