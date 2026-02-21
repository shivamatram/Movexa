package com.example.movexa.data.model.enums

/**
 * Types of trip events logged during a trip lifecycle.
 */
enum class TripEventType(val displayName: String) {
    CREATED("Trip Created"),
    DRIVER_ASSIGNED("Driver Assigned"),
    STARTED("Trip Started"),
    CHECKPOINT_REACHED("Checkpoint Reached"),
    DEVIATION_DETECTED("Route Deviation"),
    FUEL_STOP("Fuel Stop"),
    REST_STOP("Rest Stop"),
    DELAY_REPORTED("Delay Reported"),
    INCIDENT_REPORTED("Incident Reported"),
    COMPLETED("Trip Completed"),
    CANCELLED("Trip Cancelled"),
    NOTE_ADDED("Note Added");

    companion object {
        fun fromString(value: String?): TripEventType {
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: NOTE_ADDED
        }
    }
}
