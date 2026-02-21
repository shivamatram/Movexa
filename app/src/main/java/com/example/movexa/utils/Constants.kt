package com.example.movexa.utils

/**
 * Application-wide constants.
 * Centralized string keys, request codes, and identifiers.
 */
object Constants {

    // ─── Bundle Keys ────────────────────────────────────────────
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_ROLE = "user_role"
    const val KEY_VEHICLE_ID = "vehicle_id"
    const val KEY_TRIP_ID = "trip_id"
    const val KEY_REPORT_ID = "report_id"
    const val KEY_NOTIFICATION_ID = "notification_id"

    // ─── Request Codes ──────────────────────────────────────────
    const val REQUEST_LOCATION_PERMISSION = 1001
    const val REQUEST_CAMERA_PERMISSION = 1002
    const val REQUEST_STORAGE_PERMISSION = 1003
    const val REQUEST_IMAGE_CAPTURE = 2001
    const val REQUEST_IMAGE_PICK = 2002
    const val REQUEST_DOCUMENT_PICK = 2003

    // ─── Notification Channels ──────────────────────────────────
    const val CHANNEL_GENERAL = "movexa_general"
    const val CHANNEL_TRIPS = "movexa_trips"
    const val CHANNEL_ALERTS = "movexa_alerts"
    const val CHANNEL_MAINTENANCE = "movexa_maintenance"

    // ─── Deep Link Prefixes ─────────────────────────────────────
    const val DEEP_LINK_SCHEME = "movexa"
    const val DEEP_LINK_HOST = "app"

    // ─── Regex Patterns ─────────────────────────────────────────
    val EMAIL_PATTERN = Regex(
        "[a-zA-Z0-9+._%\\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+"
    )
    val PHONE_PATTERN = Regex("^[+]?[0-9]{10,15}$")
    val PASSWORD_PATTERN = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$")
    val NAME_PATTERN = Regex("^[a-zA-Z\\s]{2,100}$")
    val LICENSE_PLATE_PATTERN = Regex("^[A-Z0-9\\-\\s]{3,15}$")
}
