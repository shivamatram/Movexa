package com.example.movexa.data.model.enums

/**
 * Type classification for vehicle service/maintenance records.
 */
enum class ServiceType(val displayName: String) {
    OIL_CHANGE("Oil Change"),
    TIRE_ROTATION("Tire Rotation"),
    BRAKE_INSPECTION("Brake Inspection"),
    ENGINE_TUNE("Engine Tune-up"),
    TRANSMISSION("Transmission Service"),
    BATTERY_CHECK("Battery Check"),
    AIR_FILTER("Air Filter Replacement"),
    COOLANT_FLUSH("Coolant Flush"),
    FULL_SERVICE("Full Service"),
    PERIODIC_MAINTENANCE("Periodic Maintenance"),
    INSURANCE_RENEWAL("Insurance Renewal"),
    FITNESS_CHECK("Fitness Check"),
    EMISSION_TEST("Emission Test"),
    OTHER("Other");

    companion object {
        fun fromString(value: String?): ServiceType {
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: OTHER
        }
    }
}
