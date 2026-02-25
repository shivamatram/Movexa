package com.example.movexa.ui.dashboard.admin

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ActivityLog
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.DashboardSummary
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
import com.example.movexa.ui.dashboard.common.StatCardData
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel for the Admin Dashboard screen.
 *
 * Computes dashboard stats from actual Firestore collections
 * (vehicles, trips, drivers, alerts) rather than pre-aggregated documents.
 * Also observes real-time alerts and activity feeds.
 */
class AdminDashboardViewModel : BaseViewModel() {

    // ── Repositories ────────────────────────────────────────────

    private val vehicleRepository: VehicleRepository = VehicleRepositoryImpl()
    private val tripRepository: TripRepository = TripRepositoryImpl()
    private val driverRepository: DriverRepository = DriverRepositoryImpl()
    private val alertRepository: AlertRepository = AlertRepositoryImpl()
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepositoryImpl()

    // ── State Flows ─────────────────────────────────────────────

    private val _dashboardSummary = MutableStateFlow<ResultState<DashboardSummary?>>(ResultState.Loading)
    val dashboardSummary: StateFlow<ResultState<DashboardSummary?>> = _dashboardSummary.asStateFlow()

    private val _activeAlerts = MutableStateFlow<ResultState<List<Alert>>>(ResultState.Loading)
    val activeAlerts: StateFlow<ResultState<List<Alert>>> = _activeAlerts.asStateFlow()

    private val _recentActivity = MutableStateFlow<ResultState<List<ActivityLog>>>(ResultState.Loading)
    val recentActivity: StateFlow<ResultState<List<ActivityLog>>> = _recentActivity.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    /** Pre-computed stat cards from DashboardStatsCalculator. */
    private val _statCards = MutableStateFlow<List<StatCardData>>(emptyList())
    val statCards: StateFlow<List<StatCardData>> = _statCards.asStateFlow()

    /** Pre-computed fleet status data for the FleetStatusWidget. */
    private val _fleetStatus = MutableStateFlow<FleetStatusData?>(null)
    val fleetStatus: StateFlow<FleetStatusData?> = _fleetStatus.asStateFlow()

    private var currentCompanyId: String? = null

    // ── Initialization ──────────────────────────────────────────

    fun loadDashboard() {
        viewModelScope.launch {
            val sessionManager = SessionManager.getInstance()
            val companyId = sessionManager.getCachedCompanyId() ?: return@launch

            currentCompanyId = companyId

            val user = sessionManager.currentUser.value
            _userName.value = user?.displayName ?: "Admin"

            computeLiveStats(companyId)
            observeActiveAlerts(companyId)
            observeRecentActivity(companyId)
        }
    }

    fun refreshDashboard() {
        val companyId = currentCompanyId ?: return
        _dashboardSummary.value = ResultState.Loading
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
     * actual Firestore collections and builds a [DashboardSummary].
     */
    private suspend fun computeLiveStats(companyId: String) {
        _dashboardSummary.value = ResultState.Loading

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
            val totalVehicles = vehicles.size
            val activeVehicles = vehicles.count { it.status == VehicleStatus.ON_TRIP }
            val inMaintenance = vehicles.count { it.status == VehicleStatus.SERVICE }
            val inactiveVehicles = vehicles.count { it.status == VehicleStatus.INACTIVE }

            // ── Trip stats ──────────────────────────
            val activeTrips = trips.count { it.status.isActive() && !it.status.isTerminal }
            val pendingTrips = trips.count { it.status == TripStatus.CREATED }
            val completedToday = trips.count {
                it.status == TripStatus.COMPLETED && it.endTime >= todayStart
            }
            val cancelledToday = trips.count {
                it.status == TripStatus.CANCELLED && it.updatedAt >= todayStart
            }

            // Average trip distance (completed trips only)
            val completedTrips = trips.filter { it.status == TripStatus.COMPLETED }
            val avgDistance = if (completedTrips.isNotEmpty()) {
                completedTrips.map { it.distance }.average()
            } else 0.0

            // Today's total distance (completed today)
            val todayDistance = trips
                .filter { it.status == TripStatus.COMPLETED && it.endTime >= todayStart }
                .sumOf { it.distance }

            // ── Driver stats ────────────────────────
            val totalDrivers = drivers.size
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
            val offDutyDrivers = totalDrivers - availableDrivers - onTripDrivers

            // ── Fleet utilization ───────────────────
            val utilization = if (totalVehicles > 0) {
                ((activeVehicles.toDouble() + inMaintenance) / totalVehicles) * 100
            } else 0.0

            // Build DashboardSummary from computed data
            val summary = DashboardSummary(
                companyId = companyId,
                totalVehicles = totalVehicles,
                activeVehicles = activeVehicles,
                inactiveVehicles = inactiveVehicles,
                inMaintenanceVehicles = inMaintenance,
                totalDrivers = totalDrivers,
                availableDrivers = availableDrivers,
                onTripDrivers = onTripDrivers,
                offDutyDrivers = offDutyDrivers.coerceAtLeast(0),
                activeTrips = activeTrips,
                completedTripsToday = completedToday,
                pendingTrips = pendingTrips,
                cancelledTripsToday = cancelledToday,
                todayRevenue = todayDistance * 15.0, // ₹15/km estimate
                weekRevenue = 0.0,
                monthRevenue = 0.0,
                activeAlerts = activeAlertsList.size,
                criticalAlerts = criticalAlertsList.size,
                averageTripDistance = avgDistance,
                fleetUtilizationPercent = utilization,
                lastUpdated = System.currentTimeMillis()
            )

            _dashboardSummary.value = ResultState.Success(summary)

            // Also compute display models via calculator
            _statCards.value = DashboardStatsCalculator.buildAdminStatCards(summary)
            _fleetStatus.value = DashboardStatsCalculator.buildAdminFleetStatus(summary)

        } catch (e: Exception) {
            _dashboardSummary.value = ResultState.Error(
                message = e.message ?: "Failed to load dashboard data",
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

    fun hasAnyError(): Boolean {
        return _dashboardSummary.value is ResultState.Error ||
                _activeAlerts.value is ResultState.Error ||
                _recentActivity.value is ResultState.Error
    }

    fun isAllLoading(): Boolean {
        return _dashboardSummary.value is ResultState.Loading &&
                _activeAlerts.value is ResultState.Loading &&
                _recentActivity.value is ResultState.Loading
    }
}
