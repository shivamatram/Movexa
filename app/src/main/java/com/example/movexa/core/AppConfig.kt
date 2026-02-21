package com.example.movexa.core

/**
 * Application-wide configuration constants.
 * Centralized location for build configuration and environment settings.
 */
object AppConfig {

    // ─── App Info ───────────────────────────────────────────────
    const val APP_NAME = "Movexa"
    const val APP_TAGLINE = "Smart • Safe • Optimized"
    const val APP_DESCRIPTION = "Fleet Management System"

    // ─── Timeouts ───────────────────────────────────────────────
    const val SPLASH_LOGO_DELAY_MS = 1500L
    const val SPLASH_BRAND_DELAY_MS = 2500L
    const val NETWORK_TIMEOUT_MS = 30_000L
    const val SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000L // 24 hours

    // ─── Pagination ─────────────────────────────────────────────
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 100

    // ─── Validation ─────────────────────────────────────────────
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 64
    const val MAX_NAME_LENGTH = 100
    const val MAX_EMAIL_LENGTH = 254
    const val PHONE_MIN_LENGTH = 10
    const val PHONE_MAX_LENGTH = 15

    // ─── File Upload ────────────────────────────────────────────
    const val MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024L  // 5 MB
    const val MAX_DOCUMENT_SIZE_BYTES = 10 * 1024 * 1024L  // 10 MB
    const val IMAGE_COMPRESSION_QUALITY = 80
    val ALLOWED_IMAGE_TYPES = listOf("image/jpeg", "image/png", "image/webp")
    val ALLOWED_DOCUMENT_TYPES = listOf("application/pdf", "image/jpeg", "image/png")

    // ─── Location ───────────────────────────────────────────────
    const val LOCATION_UPDATE_INTERVAL_MS = 10_000L
    const val LOCATION_FASTEST_INTERVAL_MS = 5_000L
    const val GEOFENCE_RADIUS_METERS = 100f

    // ─── Animation ──────────────────────────────────────────────
    const val ANIMATION_DURATION_SHORT = 200L
    const val ANIMATION_DURATION_MEDIUM = 400L
    const val ANIMATION_DURATION_LONG = 600L
    const val ANIMATION_DURATION_SPLASH = 500L

    // ─── Date/Time Formats ──────────────────────────────────────
    const val DATE_FORMAT_DISPLAY = "dd MMM yyyy"
    const val DATE_FORMAT_API = "yyyy-MM-dd"
    const val TIME_FORMAT_12H = "hh:mm a"
    const val TIME_FORMAT_24H = "HH:mm"
    const val DATETIME_FORMAT_DISPLAY = "dd MMM yyyy, hh:mm a"
    const val DATETIME_FORMAT_API = "yyyy-MM-dd'T'HH:mm:ss'Z'"
}
