package com.example.movexa.data.model.enums

/**
 * Type classification for vehicles in the fleet.
 */
enum class VehicleType(val displayName: String) {
    SEDAN("Sedan"),
    SUV("SUV"),
    HATCHBACK("Hatchback"),
    TRUCK("Truck"),
    VAN("Van"),
    BUS("Bus"),
    MINI_BUS("Mini Bus"),
    TEMPO("Tempo"),
    THREE_WHEELER("Three Wheeler"),
    TWO_WHEELER("Two Wheeler"),
    OTHER("Other");

    companion object {
        fun fromString(value: String?): VehicleType {
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: OTHER
        }
    }
}
