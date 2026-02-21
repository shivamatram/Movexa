package com.example.movexa.data.model.enums

/**
 * Alert priority levels for fleet notifications and warnings.
 */
enum class AlertPriority(val displayName: String, val level: Int) {
    LOW("Low", 0),
    MEDIUM("Medium", 1),
    HIGH("High", 2),
    CRITICAL("Critical", 3);

    companion object {
        fun fromString(value: String?): AlertPriority {
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: MEDIUM
        }

        fun fromLevel(level: Int): AlertPriority {
            return entries.firstOrNull { it.level == level } ?: MEDIUM
        }
    }

    fun isUrgent(): Boolean = this == HIGH || this == CRITICAL
}
