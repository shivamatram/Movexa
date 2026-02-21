package com.example.movexa.data.model.enums

/**
 * Status of a vehicle in the fleet.
 *
 * Lifecycle: AVAILABLE → ON_TRIP → AVAILABLE
 *            AVAILABLE → SERVICE → AVAILABLE
 *            Any → INACTIVE (decommissioned/retired)
 */
enum class VehicleStatus(val displayName: String) {
    AVAILABLE("Available"),
    ON_TRIP("On Trip"),
    SERVICE("In Service"),
    INACTIVE("Inactive");

    companion object {
        fun fromString(value: String?): VehicleStatus {
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: INACTIVE
        }
    }

    fun isOperational(): Boolean = this == AVAILABLE || this == ON_TRIP
    fun canAssignTrip(): Boolean = this == AVAILABLE
    fun canScheduleService(): Boolean = this == AVAILABLE || this == INACTIVE
}
