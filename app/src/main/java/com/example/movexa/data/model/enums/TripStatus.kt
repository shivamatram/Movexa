package com.example.movexa.data.model.enums

/**
 * Status of a trip through its lifecycle.
 *
 * Full lifecycle:
 *   CREATED → ASSIGNED → ACCEPTED → STARTED → COMPLETED
 *
 * Rejection path:
 *   ASSIGNED → REJECTED_BY_DRIVER → (trip returns to CREATED)
 *
 * Cancellation:
 *   CREATED / ASSIGNED / ACCEPTED → CANCELLED
 */
enum class TripStatus(val displayName: String, val isTerminal: Boolean) {
    CREATED("Created", false),
    ASSIGNED("Assigned", false),
    ACCEPTED("Accepted", false),
    REJECTED_BY_DRIVER("Rejected by Driver", true),
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

        /** Ongoing statuses (accepted or started). */
        fun ongoingStatuses(): List<TripStatus> = listOf(ACCEPTED, STARTED)

        /** Terminal/completed statuses. */
        fun terminalStatuses(): List<TripStatus> = entries.filter { it.isTerminal }
    }

    fun canTransitionTo(next: TripStatus): Boolean {
        return when (this) {
            CREATED -> next == ASSIGNED || next == CANCELLED
            ASSIGNED -> next == ACCEPTED || next == REJECTED_BY_DRIVER || next == CANCELLED
            ACCEPTED -> next == STARTED || next == CANCELLED
            STARTED -> next == COMPLETED
            REJECTED_BY_DRIVER, COMPLETED, CANCELLED -> false
        }
    }

    fun isActive(): Boolean = !isTerminal
    fun isInProgress(): Boolean = this == STARTED
    fun isOngoing(): Boolean = this == ACCEPTED || this == STARTED
    fun isAwaitingDriver(): Boolean = this == ASSIGNED
    fun isUnassigned(): Boolean = this == CREATED
}
