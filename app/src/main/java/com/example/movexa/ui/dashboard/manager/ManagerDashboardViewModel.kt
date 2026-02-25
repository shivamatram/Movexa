package com.example.movexa.ui.dashboard.manager

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ActivityLog
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.OperationsSummary
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.enums.AlertPriority
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.data.model.enums.VerificationStatus
import com.example.movexa.data.repository.contracts.ActivityLogRepository
import com.example.movexa.data.repository.contracts.AlertRepository
import com.example.movexa.data.repository.contracts.DriverRepository
import com.example.movexa.data.repository.contracts.TripRepository
import com.example.movexa.data.repository.contracts.VehicleRepository
import com.example.movexa.data.repository.impl.ActivityLogRepositoryImpl
import com.example.movexa.data.repository.impl.AlertRepositoryImpl
import com.example.movexa.data.repository.impl.DriverRepositoryImpl
import com.example.movexa.data.repository.impl.TripRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import com.example.movexa.ui.dashboard.common.DashboardStatsCalculator
import com.example.movexa.ui.dashboard.common.FleetStatusData
import com.example.movexa.ui.dashboard.common.PendingAction
import com.example.movexa.ui.dashboard.common.StatCardData
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel for the Manager Dashboard screen.
 *
 * Computes operational stats from actual Firestore collections
 * (vehicles, trips, drivers, alerts) rather than pre-aggregated documents.
 * Also observes real-time alerts and activity feeds.
 */
class ManagerDashboardViewModel : BaseViewModel() {

    // ── Repositories ────────────────────────────────────────────

    private val vehicleRepository: VehicleRepository = VehicleRepositoryImpl()
    private val tripRepository: TripRepository = TripRepositoryImpl()
    private val driverRepository: DriverRepository = DriverRepositoryImpl()
    private val alertRepository: AlertRepository = AlertRepositoryImpl()
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepositoryImpl()

    // ── State Flows ─────────────────────────────────────────────

    private val _operationsSummary = MutableStateFlow<ResultState<OperationsSummary?>>(ResultState.Loading)
    val operationsSummary: StateFlow<ResultState<OperationsSummary?>> = _operationsSummary.asStateFlow()

    private val _activeAlerts = MutableStateFlow<ResultState<List<Alert>>>(ResultState.Loading)
    val activeAlerts: StateFlow<ResultState<List<Alert>>> = _activeAlerts.asStateFlow()

    private val _recentActivity = MutableStateFlow<ResultState<List<ActivityLog>>>(ResultState.Loading)
    val recentActivity: StateFlow<ResultState<List<ActivityLog>>> = _recentActivity.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    /** Pre-computed stat cards from DashboardStatsCalculator. */
    private val _statCards = MutableStateFlow<List<StatCardData>>(emptyList())
    val statCards: StateFlow<List<StatCardData>> = _statCards.asStateFlow()

    /** Pre-computed fleet status for FleetStatusWidget. */
    private val _fleetStatus = MutableStateFlow<FleetStatusData?>(null)
    val fleetStatus: StateFlow<FleetStatusData?> = _fleetStatus.asStateFlow()

    /** Pre-computed pending actions from DashboardStatsCalculator. */
    private val _pendingActions = MutableStateFlow<List<PendingAction>>(emptyList())
    val pendingActions: StateFlow<List<PendingAction>> = _pendingActions.asStateFlow()

    private var currentCompanyId: String? = null

    // ── Initialization ──────────────────────────────────────────

    fun loadDashboard() {
        viewModelScope.launch {
            val sessionManager = SessionManager.getInstance()
            val companyId = sessionManager.getCachedCompanyId() ?: return@launch

            currentCompanyId = companyId

            val user = sessionManager.currentUser.value
            _userName.value = user?.displayName ?: "Manager"

            computeLiveStats(companyId)
            observeActiveAlerts(companyId)
            observeRecentActivity(companyId)
        }
    }

    fun refreshDashboard() {
        val companyId = currentCompanyId ?: return
        _operationsSummary.value = ResultState.Loading
        _activeAlerts.value = ResultState.Loading
        _recentActivity.value = ResultState.Loading

        viewModelScope.launch {
            computeLiveStats(companyId)
        }
        observeActiveAlerts(companyId)
        observeRecentActivity(companyId)
    }

    // ── Compute Stats From Real Collections ─────────────────────

    /**
     * Fetches vehicles, trips, drivers, and alerts in parallel from
     * actual Firestore collections and builds an [OperationsSummary].
     */
    private suspend fun computeLiveStats(companyId: String) {
        _operationsSummary.value = ResultState.Loading

        try {
            // Parallel fetch from all collections
            val vehiclesDeferred = viewModelScope.async {
                vehicleRepository.getVehiclesByCompany(companyId)
            }
            val tripsDeferred = viewModelScope.async {
                tripRepository.getTripsByCompany(companyId)
            }
            val driversDeferred = viewModelScope.async {
                driverRepository.getDriversByCompany(companyId)
            }
            val alertsDeferred = viewModelScope.async {
                alertRepository.getActiveAlerts(companyId)
            }
            val criticalDeferred = viewModelScope.async {
                alertRepository.getCriticalAlerts(companyId)
            }

            // Await results — extract lists, default to empty on error
            val vehicles = extractList(vehiclesDeferred.await())
            val trips = extractList(tripsDeferred.await())
            val drivers = extractList(driversDeferred.await())
            val activeAlertsList = extractList(alertsDeferred.await())
            val criticalAlertsList = extractList(criticalDeferred.await())

            // Today's start timestamp (midnight)
            val todayStart = todayStartTimestamp()

            // ── Vehicle stats ───────────────────────
            val assignedVehicles = vehicles.size
            val activeVehicles = vehicles.count { it.status == VehicleStatus.ON_TRIP }
            val idleVehicles = vehicles.count { it.status == VehicleStatus.AVAILABLE }
            val inMaintenance = vehicles.count { it.status == VehicleStatus.SERVICE }

            // ── Trip stats ──────────────────────────
            val activeTrips = trips.count { it.status.isActive() && !it.status.isTerminal }
            val pendingTrips = trips.count { it.status == TripStatus.CREATED }
            val completedToday = trips.count {
                it.status == TripStatus.COMPLETED && it.endTime >= todayStart
            }
            val delayedTrips = 0 // Requires estimated vs actual comparison

            // Today's total distance
            val todayDistance = trips
                .filter { it.status == TripStatus.COMPLETED && it.endTime >= todayStart }
                .sumOf { it.distance }

            // ── Driver stats ────────────────────────
            val assignedDrivers = drivers.size
            val onTripDriverIds = trips
                .filter { it.status.isOngoing() }
                .map { it.driverId }
                .toSet()
            val availableDrivers = drivers.count {
                !it.blocked &&
                it.verificationStatus == VerificationStatus.APPROVED &&
                it.driverId !in onTripDriverIds
            }
            val onTripDrivers = onTripDriverIds.size

            // ── Efficiency metrics ──────────────────
            val completedTrips = trips.filter { it.status == TripStatus.COMPLETED }
            val teamEfficiency = if (assignedDrivers > 0) {
                (completedTrips.size.toDouble() / assignedDrivers.coerceAtLeast(1)) * 10.0
            } else 0.0
            val onTimePercent = if (completedTrips.isNotEmpty()) {
                // All completed trips count as on-time (no delay tracking yet)
                100.0
            } else 0.0

            // Build OperationsSummary from computed data
            val summary = OperationsSummary(
                companyId = companyId,
                managerId = SessionManager.getInstance().getCachedUserId() ?: "",
                assignedVehicles = assignedVehicles,
                activeVehicles = activeVehicles,
                idleVehicles = idleVehicles,
                inMaintenanceVehicles = inMaintenance,
                assignedDrivers = assignedDrivers,
                availableDrivers = availableDrivers,
                onTripDrivers = onTripDrivers,
                activeTrips = activeTrips,
                pendingTrips = pendingTrips,
                completedTripsToday = completedToday,
                delayedTrips = delayedTrips,
                pendingApprovals = pendingTrips, // Unassigned trips need approval
                pendingFuelApprovals = 0,
                pendingLeaveRequests = 0,
                pendingMaintenanceRequests = inMaintenance,
                openAlerts = activeAlertsList.size,
                criticalAlerts = criticalAlertsList.size,
                todayDistance = todayDistance,
                todayFuelCost = 0.0,
                teamEfficiencyPercent = teamEfficiency.coerceAtMost(100.0),
                onTimeDeliveryPercent = onTimePercent,
                lastUpdated = System.currentTimeMillis()
            )

            _operationsSummary.value = ResultState.Success(summary)

            // Also compute display models via calculator
            _statCards.value = DashboardStatsCalculator.buildManagerStatCards(summary)
            _fleetStatus.value = DashboardStatsCalculator.buildManagerFleetStatus(summary)
            _pendingActions.value = DashboardStatsCalculator.buildManagerPendingActions(summary)

        } catch (e: Exception) {
            _operationsSummary.value = ResultState.Error(
                message = e.message ?: "Failed to load operations data",
                exception = e
            )
        }
    }

    // ── Real-Time Observers ─────────────────────────────────────

    private fun observeActiveAlerts(companyId: String) {
        viewModelScope.launch {
            alertRepository.observeActiveAlerts(companyId)
                .catch { e ->
                    _activeAlerts.value = ResultState.Error(
                        message = e.message ?: "Failed to load alerts",
                        exception = e
                    )
                }
                .collect { result ->
                    _activeAlerts.value = result
                }
        }
    }

    private fun observeRecentActivity(companyId: String) {
        viewModelScope.launch {
            activityLogRepository.observeRecentLogs(companyId)
                .catch { e ->
                    _recentActivity.value = ResultState.Error(
                        message = e.message ?: "Failed to load activity",
                        exception = e
                    )
                }
                .collect { result ->
                    _recentActivity.value = result
                }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun <T> extractList(result: ResultState<List<T>>): List<T> {
        return (result as? ResultState.Success)?.data ?: emptyList()
    }

    private fun todayStartTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getTotalPendingActions(): Int {
        val summary = (_operationsSummary.value as? ResultState.Success)?.data
        return summary?.totalPendingActions ?: 0
    }

    fun hasAnyError(): Boolean {
        return _operationsSummary.value is ResultState.Error ||
                _activeAlerts.value is ResultState.Error ||
                _recentActivity.value is ResultState.Error
    }

    fun isAllLoading(): Boolean {
        return _operationsSummary.value is ResultState.Loading &&
                _activeAlerts.value is ResultState.Loading &&
                _recentActivity.value is ResultState.Loading
    }
}
