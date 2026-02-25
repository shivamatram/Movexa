package com.example.movexa.ui.dashboard.common

import com.example.movexa.R
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.DashboardSummary
import com.example.movexa.data.model.OperationsSummary
import com.example.movexa.data.model.ServiceRecord
import com.example.movexa.data.model.Repair
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.DriverSummary
import com.example.movexa.data.model.enums.AlertPriority
import com.example.movexa.data.model.enums.AlertStatus
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.data.model.enums.VerificationStatus
import com.example.movexa.theme.AppColors
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════════
//  DASHBOARD STATS CALCULATOR
// ═══════════════════════════════════════════════════════════════════════════════
//
//  Pure computation class — takes raw Firestore data and produces dashboard
//  display models. No side effects, no I/O, fully deterministic and testable.
//
//  Used by all role-based ViewModels to transform repository data into
//  stat cards, fleet status widgets, and section content.
// ═══════════════════════════════════════════════════════════════════════════════

object DashboardStatsCalculator {

    private val currencyFormatter: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    // ─── Admin Stats ────────────────────────────────────────────────────────

    /**
     * Build admin stat cards from [DashboardSummary].
     *
     * Returns 5 cards: Total Vehicles, Active Trips, Available Drivers,
     * Today Revenue, Active Alerts.
     */
    fun buildAdminStatCards(summary: DashboardSummary): List<StatCardData> {
        return listOf(
            StatCardData(
                id = "total_vehicles",
                iconRes = R.drawable.ic_dashboard_vehicle,
                label = "Total Vehicles",
                value = summary.totalVehicles.toString(),
                subtitle = "${summary.activeVehicles} active",
                iconTint = AppColors.PRIMARY,
                trend = null,
                navigateAction = 0
            ),
            StatCardData(
                id = "active_trips",
                iconRes = R.drawable.ic_trending_up,
                label = "Active Trips",
                value = summary.activeTrips.toString(),
                subtitle = "${summary.pendingTrips} pending",
                iconTint = AppColors.SECONDARY,
                trend = null,
                navigateAction = 0
            ),
            StatCardData(
                id = "available_drivers",
                iconRes = R.drawable.ic_dashboard_driver,
                label = "Available Drivers",
                value = summary.availableDrivers.toString(),
                subtitle = "${summary.totalDrivers} total",
                iconTint = AppColors.SUCCESS,
                trend = null,
                navigateAction = 0
            ),
            StatCardData(
                id = "today_revenue",
                iconRes = R.drawable.ic_dashboard_revenue,
                label = "Today Revenue",
                value = formatCurrency(summary.todayRevenue),
                subtitle = "Month: ${formatCurrency(summary.monthRevenue)}",
                iconTint = AppColors.WARNING,
                trend = null,
                navigateAction = 0
            ),
            StatCardData(
                id = "active_alerts",
                iconRes = R.drawable.ic_warning,
                label = "Active Alerts",
                value = summary.activeAlerts.toString(),
                subtitle = if (summary.criticalAlerts > 0)
                    "${summary.criticalAlerts} critical" else "All clear",
                iconTint = if (summary.criticalAlerts > 0) AppColors.ERROR else AppColors.SUCCESS,
                trend = null,
                navigateAction = 0
            )
        )
    }

    /**
     * Build admin fleet status breakdown from [DashboardSummary].
     */
    fun buildAdminFleetStatus(summary: DashboardSummary): FleetStatusData {
        return FleetStatusData.fromSummary(summary)
    }

    // ─── Manager Stats ──────────────────────────────────────────────────────

    /**
     * Build manager stat cards from [OperationsSummary].
     *
     * Returns 5 cards: Active Trips, Available Vehicles, Available Drivers,
     * Pending Verifications (derived), Active Alerts.
     */
    fun buildManagerStatCards(summary: OperationsSummary): List<StatCardData> {
        return listOf(
            StatCardData(
                id = "active_trips",
                iconRes = R.drawable.ic_trending_up,
                label = "Active Trips",
                value = summary.activeTrips.toString(),
                subtitle = "${summary.pendingTrips} pending",
                iconTint = AppColors.SECONDARY,
                navigateAction = 0
            ),
            StatCardData(
                id = "available_vehicles",
                iconRes = R.drawable.ic_dashboard_vehicle,
                label = "Available Vehicles",
                value = (summary.assignedVehicles - summary.activeVehicles
                        - summary.inMaintenanceVehicles).coerceAtLeast(0).toString(),
                subtitle = "${summary.assignedVehicles} total",
                iconTint = AppColors.PRIMARY,
                navigateAction = 0
            ),
            StatCardData(
                id = "available_drivers",
                iconRes = R.drawable.ic_dashboard_driver,
                label = "Available Drivers",
                value = summary.availableDrivers.toString(),
                subtitle = "${summary.onTripDrivers} on trip",
                iconTint = AppColors.SUCCESS,
                navigateAction = 0
            ),
            StatCardData(
                id = "completed_today",
                iconRes = R.drawable.ic_check_circle,
                label = "Completed Today",
                value = summary.completedTripsToday.toString(),
                subtitle = if (summary.delayedTrips > 0) "${summary.delayedTrips} delayed" else "",
                iconTint = AppColors.INFO,
                navigateAction = 0
            ),
            StatCardData(
                id = "active_alerts",
                iconRes = R.drawable.ic_warning,
                label = "Active Alerts",
                value = summary.openAlerts.toString(),
                subtitle = if (summary.criticalAlerts > 0)
                    "${summary.criticalAlerts} critical" else "All clear",
                iconTint = if (summary.criticalAlerts > 0) AppColors.ERROR else AppColors.SUCCESS,
                navigateAction = 0
            )
        )
    }

    /**
     * Build manager pending actions from [OperationsSummary].
     */
    fun buildManagerPendingActions(summary: OperationsSummary): List<PendingAction> {
        return listOf(
            PendingAction(
                id = "unassigned_trips",
                category = PendingCategory.UNASSIGNED_TRIPS,
                title = "Unassigned Trips",
                count = summary.pendingTrips,
                iconRes = R.drawable.ic_assignment
            ),
            PendingAction(
                id = "fuel_approvals",
                category = PendingCategory.FUEL_APPROVAL,
                title = "Fuel Approvals",
                count = summary.pendingFuelApprovals,
                iconRes = R.drawable.ic_dashboard_revenue
            ),
            PendingAction(
                id = "maintenance_requests",
                category = PendingCategory.VEHICLE_SERVICE,
                title = "Maintenance Requests",
                count = summary.pendingMaintenanceRequests,
                iconRes = R.drawable.ic_build
            ),
            PendingAction(
                id = "leave_requests",
                category = PendingCategory.LEAVE_REQUEST,
                title = "Leave Requests",
                count = summary.pendingLeaveRequests,
                iconRes = R.drawable.ic_pending
            )
        )
    }

    /**
     * Build manager fleet status from [OperationsSummary].
     */
    fun buildManagerFleetStatus(summary: OperationsSummary): FleetStatusData {
        return FleetStatusData.fromOperations(summary)
    }

    // ─── Mechanic Stats ─────────────────────────────────────────────────────

    /**
     * Calculate mechanic dashboard stats from raw service/repair data.
     */
    fun buildMechanicStats(
        pendingServices: List<ServiceRecord>,
        activeRepairs: List<Repair>,
        completedToday: List<ServiceRecord>,
        urgentAlerts: List<Alert>
    ): MechanicStats {
        val pendingCost = pendingServices.sumOf { it.cost } +
                activeRepairs.sumOf { it.cost }

        return MechanicStats(
            vehiclesDueForService = pendingServices.size,
            vehiclesUnderRepair = activeRepairs.size,
            completedToday = completedToday.size,
            urgentAlerts = urgentAlerts.size,
            totalPendingCost = pendingCost
        )
    }

    /**
     * Build mechanic stat cards.
     */
    fun buildMechanicStatCards(stats: MechanicStats): List<StatCardData> {
        return listOf(
            StatCardData(
                id = "due_for_service",
                iconRes = R.drawable.ic_build,
                label = "Due for Service",
                value = stats.vehiclesDueForService.toString(),
                subtitle = "",
                iconTint = AppColors.WARNING
            ),
            StatCardData(
                id = "under_repair",
                iconRes = R.drawable.ic_build,
                label = "Under Repair",
                value = stats.vehiclesUnderRepair.toString(),
                subtitle = "",
                iconTint = AppColors.SECONDARY
            ),
            StatCardData(
                id = "completed_today",
                iconRes = R.drawable.ic_check_circle,
                label = "Completed Today",
                value = stats.completedToday.toString(),
                subtitle = "",
                iconTint = AppColors.SUCCESS
            ),
            StatCardData(
                id = "urgent_alerts",
                iconRes = R.drawable.ic_warning,
                label = "Urgent Alerts",
                value = stats.urgentAlerts.toString(),
                subtitle = "",
                iconTint = AppColors.ERROR
            )
        )
    }

    /**
     * Build sorted maintenance task queue from services and repairs.
     */
    fun buildMaintenanceQueue(
        pendingServices: List<ServiceRecord>,
        vehicles: Map<String, Vehicle>
    ): List<MaintenanceTask> {
        return pendingServices.map { service ->
            val vehicle = vehicles[service.vehicleId]
            val vehicleNum = vehicle?.number ?: service.vehicleId
            val currentOdo = vehicle?.lastOdometer ?: 0L
            val isOverdue = service.isOverdue(currentOdo)
            val remainingKm = service.remainingKmToNextService(currentOdo)

            val urgency = when {
                isOverdue -> MaintenanceUrgency.OVERDUE
                remainingKm in 1..500 -> MaintenanceUrgency.CRITICAL
                remainingKm in 501..2000 -> MaintenanceUrgency.DUE_SOON
                else -> MaintenanceUrgency.SCHEDULED
            }

            MaintenanceTask(
                id = service.serviceId,
                vehicleNumber = vehicleNum,
                vehicleId = service.vehicleId,
                serviceType = service.serviceType.displayName,
                urgency = urgency,
                description = service.description.ifBlank {
                    "${service.serviceType.displayName} service"
                },
                dueInfo = when {
                    isOverdue -> "Overdue by ${-remainingKm} km"
                    remainingKm > 0 -> "${remainingKm} km remaining"
                    else -> "Scheduled"
                },
                estimatedCost = service.cost
            )
        }.sortedByDescending { it.urgency.level }
    }

    // ─── Driver Stats ───────────────────────────────────────────────────────

    /**
     * Build driver today summary from trips and performance data.
     */
    fun buildDriverTodaySummary(
        todayTrips: List<Trip>,
        driverSummary: DriverSummary?,
        driverAlerts: List<Alert>
    ): DriverTodaySummary {
        val completed = todayTrips.filter { it.status == TripStatus.COMPLETED }
        val totalDistance = completed.sumOf { it.distance }
        val totalDuration = completed.sumOf { it.duration }

        return DriverTodaySummary(
            completedTrips = completed.size,
            totalDistanceKm = totalDistance,
            totalDurationMinutes = totalDuration / 60_000,
            alertCount = driverAlerts.size,
            currentScore = driverSummary?.score ?: 0,
            scoreGrade = driverSummary?.grade ?: "—"
        )
    }

    // ─── Utility ────────────────────────────────────────────────────────────

    fun formatCurrency(amount: Double): String {
        return try {
            currencyFormatter.format(amount)
        } catch (_: Exception) {
            "₹${amount.toLong()}"
        }
    }

    fun formatDistance(km: Double): String {
        return if (km >= 100) {
            "%.0f km".format(km)
        } else {
            "%.1f km".format(km)
        }
    }

    fun formatPercent(value: Double): String {
        return "%.0f%%".format(value)
    }

    /**
     * Get the start-of-day timestamp for filtering "today" data.
     */
    fun todayStartTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
