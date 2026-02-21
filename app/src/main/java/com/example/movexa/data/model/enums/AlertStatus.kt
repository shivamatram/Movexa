package com.example.movexa.data.model.enums

/**
 * Status of an alert through its lifecycle.
 */
enum class AlertStatus(val displayName: String) {
    ACTIVE("Active"),
    ACKNOWLEDGED("Acknowledged"),
    RESOLVED("Resolved"),
    DISMISSED("Dismissed");

    companion object {
        fun fromString(value: String?): AlertStatus {
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: ACTIVE
        }
    }

    fun isOpen(): Boolean = this == ACTIVE || this == ACKNOWLEDGED
}
