package com.example.movexa.data.model.enums

/**
 * Status of a trip through its lifecycle.
 *
 * Lifecycle: CREATED → ASSIGNED → STARTED → COMPLETED
 *            CREATED → CANCELLED
 *            ASSIGNED → CANCELLED
 */
enum class TripStatus(val displayName: String, val isTerminal: Boolean) {
    CREATED("Created", false),
    ASSIGNED("Assigned", false),
    STARTED("Started", false),
    COMPLETED("Completed", true),
    CANCELLED("Cancelled", true);

    companion object {
        fun fromString(value: String?): TripStatus {
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: CREATED
        }

        /** Active statuses for queries (non-terminal). */
        fun activeStatuses(): List<TripStatus> = entries.filter { !it.isTerminal }
    }

    fun canTransitionTo(next: TripStatus): Boolean {
        return when (this) {
            CREATED -> next == ASSIGNED || next == CANCELLED
            ASSIGNED -> next == STARTED || next == CANCELLED
            STARTED -> next == COMPLETED
            COMPLETED, CANCELLED -> false
        }
    }

    fun isActive(): Boolean = !isTerminal
    fun isInProgress(): Boolean = this == STARTED
}
