package com.example.movexa.ui.dashboard.common

import com.example.movexa.data.model.ActivityLog
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.DashboardSummary
import com.example.movexa.data.model.OperationsSummary
import com.example.movexa.data.model.ServiceRecord
import com.example.movexa.data.model.Repair
import com.example.movexa.data.model.enums.AlertPriority
import com.example.movexa.data.model.enums.VehicleStatus

// ═══════════════════════════════════════════════════════════════════════════════
//  DASHBOARD UI STATE — Unified state models for all role dashboards
// ═══════════════════════════════════════════════════════════════════════════════
//
//  Each dashboard screen has a single sealed UiState that drives the entire UI.
//  Fragment observes ONE StateFlow<DashboardScreenState> and renders accordingly.
//
//  Sub-states allow individual sections to load/fail independently while the
//  overall screen remains usable (partial data is better than full error).
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Top-level screen state for any dashboard.
 */
sealed class DashboardScreenState {
    /** Initial loading — show shimmer placeholders. */
    data object Loading : DashboardScreenState()

    /** At least partial data available — show content. */
    data class Content(
        val sections: Map<DashboardSection, SectionState> = emptyMap()
    ) : DashboardScreenState()

    /** Complete failure — show full-screen error. */
    data class Error(
        val message: String,
        val isOffline: Boolean = false
    ) : DashboardScreenState()
}

/**
 * Identifies each section on a dashboard for independent state tracking.
 */
enum class DashboardSection {
    STATS_GRID,
    FLEET_STATUS,
    QUICK_ALERTS,
    RECENT_ACTIVITY,
    PENDING_ACTIONS,
    ACTIVE_TRIP,
    TODAY_SUMMARY,
    MAINTENANCE_QUEUE,
    QUICK_ACTIONS
}

/**
 * State of an individual dashboard section.
 */
sealed class SectionState {
    data object Loading : SectionState()
    data object Empty : SectionState()
    data class Loaded(val data: Any) : SectionState()
    data class Failed(val message: String) : SectionState()
}

// ─── Stat Card Model ────────────────────────────────────────────────────────

/**
 * Generic stat card data — role-agnostic.
 *
 * @param id Unique identifier for click handling and DiffUtil
 * @param iconRes Drawable resource for the card icon
 * @param label Display label (e.g. "Total Vehicles")
 * @param value Display value (e.g. "42")
 * @param subtitle Optional secondary text (e.g. "5 active")
 * @param iconTint Color int for the icon background tint
 * @param trend Optional trend data for mini indicator
 * @param navigateAction Navigation action ID when tapped (0 = no nav)
 */
data class StatCardData(
    val id: String,
    val iconRes: Int,
    val label: String,
    val value: String,
    val subtitle: String = "",
    val iconTint: Int = 0,
    val trend: TrendData? = null,
    val navigateAction: Int = 0
)

/**
 * Trend indicator for stat cards — shows directional change.
 */
data class TrendData(
    val direction: TrendDirection,
    val percentage: Double = 0.0,
    val label: String = ""
)

enum class TrendDirection {
    UP, DOWN, STABLE
}

// ─── Fleet Status Model ─────────────────────────────────────────────────────

/**
 * Fleet breakdown for the segmented status widget.
 */
data class FleetStatusData(
    val moving: Int = 0,
    val idle: Int = 0,
    val inService: Int = 0,
    val offline: Int = 0,
    val total: Int = 0
) {
    val movingPercent: Float get() = safePercent(moving)
    val idlePercent: Float get() = safePercent(idle)
    val inServicePercent: Float get() = safePercent(inService)
    val offlinePercent: Float get() = safePercent(offline)

    private fun safePercent(value: Int): Float =
        if (total > 0) (value.toFloat() / total) * 100f else 0f

    companion object {
        fun fromSummary(summary: DashboardSummary): FleetStatusData {
            val moving = summary.activeVehicles
            val inService = summary.inMaintenanceVehicles
            val total = summary.totalVehicles
            val offline = summary.inactiveVehicles
            val idle = total - moving - inService - offline
            return FleetStatusData(
                moving = moving,
                idle = idle.coerceAtLeast(0),
                inService = inService,
                offline = offline,
                total = total
            )
        }

        fun fromOperations(summary: OperationsSummary): FleetStatusData {
            val moving = summary.activeVehicles
            val inService = summary.inMaintenanceVehicles
            val total = summary.assignedVehicles
            val idle = summary.idleVehicles
            val offline = total - moving - inService - idle
            return FleetStatusData(
                moving = moving,
                idle = idle,
                inService = inService,
                offline = offline.coerceAtLeast(0),
                total = total
            )
        }
    }
}

// ─── Alert Card Model ───────────────────────────────────────────────────────

/**
 * Simplified alert data for dashboard display cards.
 */
data class DashboardAlertItem(
    val alertId: String,
    val type: String,
    val title: String,
    val message: String,
    val vehicleInfo: String,
    val driverInfo: String,
    val priority: AlertPriority,
    val timestamp: Long,
    val timeAgo: String
) {
    companion object {
        fun fromAlert(alert: Alert, vehicleName: String = "", driverName: String = ""): DashboardAlertItem {
            return DashboardAlertItem(
                alertId = alert.alertId,
                type = alert.type.displayName,
                title = alert.title,
                message = alert.message,
                vehicleInfo = vehicleName.ifBlank { alert.vehicleId ?: "—" },
                driverInfo = driverName.ifBlank { alert.driverId ?: "—" },
                priority = alert.priority,
                timestamp = alert.timestamp,
                timeAgo = ""
            )
        }
    }
}

// ─── Activity Item Model ────────────────────────────────────────────────────

/**
 * Simplified activity log data for dashboard display.
 */
data class DashboardActivityItem(
    val logId: String,
    val type: String,
    val message: String,
    val timestamp: Long,
    val timeAgo: String,
    val referenceId: String,
    val referenceType: String,
    val iconType: String
) {
    companion object {
        fun fromLog(log: ActivityLog): DashboardActivityItem {
            return DashboardActivityItem(
                logId = log.logId,
                type = log.type.displayName,
                message = log.message,
                timestamp = log.timestamp,
                timeAgo = "",
                referenceId = log.referenceId,
                referenceType = log.referenceType,
                iconType = log.type.name
            )
        }
    }
}

// ─── Pending Action Model ───────────────────────────────────────────────────

/**
 * Pending action item for manager dashboard.
 */
data class PendingAction(
    val id: String,
    val category: PendingCategory,
    val title: String,
    val count: Int,
    val iconRes: Int
)

enum class PendingCategory {
    DRIVER_VERIFICATION,
    VEHICLE_SERVICE,
    UNASSIGNED_TRIPS,
    FUEL_APPROVAL,
    LEAVE_REQUEST,
    GENERAL
}

// ─── Mechanic Dashboard Models ──────────────────────────────────────────────

/**
 * Maintenance task item for mechanic dashboard queue.
 */
data class MaintenanceTask(
    val id: String,
    val vehicleNumber: String,
    val vehicleId: String,
    val serviceType: String,
    val urgency: MaintenanceUrgency,
    val description: String,
    val dueInfo: String,
    val estimatedCost: Double = 0.0
)

enum class MaintenanceUrgency(val level: Int, val label: String) {
    CRITICAL(3, "Critical"),
    OVERDUE(2, "Overdue"),
    DUE_SOON(1, "Due Soon"),
    SCHEDULED(0, "Scheduled")
}

/**
 * Mechanic dashboard stats.
 */
data class MechanicStats(
    val vehiclesDueForService: Int = 0,
    val vehiclesUnderRepair: Int = 0,
    val completedToday: Int = 0,
    val urgentAlerts: Int = 0,
    val totalPendingCost: Double = 0.0
)

// ─── Driver Dashboard Models ────────────────────────────────────────────────

/**
 * Today's driving summary for driver dashboard.
 */
data class DriverTodaySummary(
    val completedTrips: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalDurationMinutes: Long = 0,
    val alertCount: Int = 0,
    val currentScore: Int = 0,
    val scoreGrade: String = "—"
)

/**
 * Quick action button for driver dashboard.
 */
data class QuickAction(
    val id: String,
    val label: String,
    val iconRes: Int,
    val enabled: Boolean = true,
    val navigateAction: Int = 0
)
