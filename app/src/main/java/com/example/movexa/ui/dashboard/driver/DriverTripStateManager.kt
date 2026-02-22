package com.example.movexa.ui.dashboard.driver

import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.enums.TripStatus

/**
 * Manages trip state transitions for the driver home screen.
 *
 * ═══════════════════════════════════════════════════════════════
 * STATE MACHINE
 * ═══════════════════════════════════════════════════════════════
 *
 *   CREATED     → (no driver action — admin assigns)
 *   ASSIGNED    → ACCEPTED  ("Accept Trip")
 *   ACCEPTED    → STARTED   ("Start Trip")
 *   STARTED     → COMPLETED ("Mark Delivered")
 *
 * ═══════════════════════════════════════════════════════════════
 * RESPONSIBILITIES
 * ═══════════════════════════════════════════════════════════════
 *
 * 1. Determine the primary action button text for a given status
 * 2. Determine the action button icon for a given status
 * 3. Validate whether a transition is allowed
 * 4. Provide descriptive text for each trip state
 * 5. Double-tap prevention via action locking
 */
class DriverTripStateManager {

    // ─── Action Lock ────────────────────────────────────────────
    @Volatile
    private var isActionInProgress = false

    /**
     * Try to acquire the action lock.
     * Returns true if lock acquired, false if already locked.
     * Prevents double-tap / concurrent state transitions.
     */
    @Synchronized
    fun tryAcquireActionLock(): Boolean {
        if (isActionInProgress) return false
        isActionInProgress = true
        return true
    }

    /**
     * Release the action lock after operation completes.
     */
    @Synchronized
    fun releaseActionLock() {
        isActionInProgress = false
    }

    /**
     * Whether an action is currently being processed.
     */
    val isLocked: Boolean
        get() = isActionInProgress

    // ═══════════════════════════════════════════════════════════
    //  ACTION BUTTON CONFIG
    // ═══════════════════════════════════════════════════════════

    /**
     * Data class describing a primary action button.
     */
    data class ActionConfig(
        val labelResName: String,
        val iconResName: String,
        val action: TripAction,
        val isEnabled: Boolean = true,
        val isDestructive: Boolean = false,
        val requiresConfirmation: Boolean = false
    )

    /**
     * Enum of possible driver actions on a trip.
     */
    enum class TripAction {
        ACCEPT,
        VIEW_DETAILS,
        START,
        OPEN_NAVIGATION,
        MARK_DELIVERED,
        VIEW_SUMMARY,
        NONE
    }

    /**
     * Get the appropriate action configuration for the given trip status.
     *
     * Returns null if no action is available (e.g., CREATED — only admin can assign).
     */
    fun getActionConfig(status: TripStatus): ActionConfig? {
        return when (status) {
            TripStatus.CREATED -> null // Driver cannot act on CREATED trips

            TripStatus.ASSIGNED -> ActionConfig(
                labelResName = "driver_action_accept",
                iconResName = "ic_check_circle",
                action = TripAction.ACCEPT,
                requiresConfirmation = true
            )

            TripStatus.ACCEPTED -> ActionConfig(
                labelResName = "driver_action_start",
                iconResName = "ic_play_arrow",
                action = TripAction.START,
                requiresConfirmation = true
            )

            TripStatus.STARTED -> ActionConfig(
                labelResName = "driver_action_complete",
                iconResName = "ic_check_circle",
                action = TripAction.MARK_DELIVERED,
                requiresConfirmation = true
            )

            TripStatus.COMPLETED -> ActionConfig(
                labelResName = "driver_action_summary",
                iconResName = "ic_assignment",
                action = TripAction.VIEW_SUMMARY
            )

            TripStatus.REJECTED_BY_DRIVER -> null
            TripStatus.CANCELLED -> null
        }
    }

    /**
     * Validate a transition and return the next status, or null if invalid.
     */
    fun getNextStatus(currentStatus: TripStatus, action: TripAction): TripStatus? {
        return when (action) {
            TripAction.ACCEPT -> {
                if (currentStatus.canTransitionTo(TripStatus.ACCEPTED))
                    TripStatus.ACCEPTED else null
            }
            TripAction.START -> {
                if (currentStatus.canTransitionTo(TripStatus.STARTED))
                    TripStatus.STARTED else null
            }
            TripAction.MARK_DELIVERED -> {
                if (currentStatus.canTransitionTo(TripStatus.COMPLETED))
                    TripStatus.COMPLETED else null
            }
            else -> null
        }
    }

    /**
     * Get the status description text for the driver home card.
     */
    fun getStatusDescription(status: TripStatus): String {
        return when (status) {
            TripStatus.CREATED -> "Trip has been created. Awaiting assignment."
            TripStatus.ASSIGNED -> "A new trip has been assigned to you. Please accept."
            TripStatus.ACCEPTED -> "Trip accepted. You can start when ready."
            TripStatus.STARTED -> "Trip is in progress. Drive safely!"
            TripStatus.COMPLETED -> "Trip completed successfully."
            TripStatus.REJECTED_BY_DRIVER -> "Trip was rejected."
            TripStatus.CANCELLED -> "Trip was cancelled."
        }
    }

    /**
     * Get a priority level for the action (used for button styling).
     *
     * 0 = info (neutral), 1 = primary, 2 = urgent (needs immediate attention)
     */
    fun getActionPriority(status: TripStatus): Int {
        return when (status) {
            TripStatus.ASSIGNED -> 2   // Needs immediate attention
            TripStatus.ACCEPTED -> 1   // Ready to start
            TripStatus.STARTED -> 1    // In progress
            TripStatus.COMPLETED -> 0  // Info only
            else -> 0
        }
    }

    /**
     * Whether the trip status means the driver should see an ETA indicator.
     */
    fun shouldShowEta(status: TripStatus): Boolean {
        return status == TripStatus.STARTED
    }

    /**
     * Whether the trip card should pulse (attention animation).
     */
    fun shouldPulse(status: TripStatus): Boolean {
        return status == TripStatus.ASSIGNED
    }

    /**
     * Whether the driver can reject/decline this trip.
     */
    fun canReject(status: TripStatus): Boolean {
        return status == TripStatus.ASSIGNED
    }
}
