package com.example.movexa.data.model.enums

/**
 * Types of activity log entries for audit trail.
 */
enum class ActivityLogType(val displayName: String) {
    // Auth events
    USER_LOGIN("User Login"),
    USER_LOGOUT("User Logout"),
    USER_REGISTERED("User Registered"),
    PASSWORD_RESET("Password Reset"),

    // Vehicle events
    VEHICLE_ADDED("Vehicle Added"),
    VEHICLE_UPDATED("Vehicle Updated"),
    VEHICLE_REMOVED("Vehicle Removed"),
    VEHICLE_ASSIGNED("Vehicle Assigned"),

    // Trip events
    TRIP_CREATED("Trip Created"),
    TRIP_ASSIGNED("Trip Assigned"),
    TRIP_STARTED("Trip Started"),
    TRIP_COMPLETED("Trip Completed"),
    TRIP_CANCELLED("Trip Cancelled"),

    // Maintenance events
    SERVICE_SCHEDULED("Service Scheduled"),
    SERVICE_COMPLETED("Service Completed"),
    REPAIR_LOGGED("Repair Logged"),
    PART_REPLACED("Part Replaced"),

    // Fuel events
    FUEL_LOGGED("Fuel Logged"),

    // Alert events
    ALERT_CREATED("Alert Created"),
    ALERT_RESOLVED("Alert Resolved"),

    // Admin events
    USER_VERIFIED("User Verified"),
    USER_BLOCKED("User Blocked"),
    USER_UNBLOCKED("User Unblocked"),
    SETTINGS_CHANGED("Settings Changed"),

    // System events
    SYSTEM("System Event");

    companion object {
        fun fromString(value: String?): ActivityLogType {
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: SYSTEM
        }
    }
}
