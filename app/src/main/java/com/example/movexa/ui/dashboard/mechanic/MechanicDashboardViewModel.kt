package com.example.movexa.ui.dashboard.mechanic

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ActivityLog
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.Repair
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.ServiceRecord
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.AlertPriority
import com.example.movexa.data.repository.contracts.ActivityLogRepository
import com.example.movexa.data.repository.contracts.AlertRepository
import com.example.movexa.data.repository.contracts.RepairRepository
import com.example.movexa.data.repository.contracts.ServiceRepository
import com.example.movexa.data.repository.contracts.VehicleRepository
import com.example.movexa.data.repository.impl.ActivityLogRepositoryImpl
import com.example.movexa.data.repository.impl.AlertRepositoryImpl
import com.example.movexa.data.repository.impl.RepairRepositoryImpl
import com.example.movexa.data.repository.impl.ServiceRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import com.example.movexa.ui.dashboard.common.DashboardStatsCalculator
import com.example.movexa.ui.dashboard.common.MaintenanceTask
import com.example.movexa.ui.dashboard.common.MechanicStats
import com.example.movexa.ui.dashboard.common.StatCardData
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════════
//  MECHANIC DASHBOARD VIEWMODEL
// ═══════════════════════════════════════════════════════════════════════════════
//
//  Full implementation — reads from multiple repositories:
//  - ServiceRepository    → pending services, completed today
//  - RepairRepository     → active repairs (cost tracking)
//  - AlertRepository      → urgent maintenance alerts
//  - VehicleRepository    → vehicle details for maintenance queue labels
//  - ActivityLogRepository → recent activity feed
//
//  All data is transformed through DashboardStatsCalculator to produce
//  display-ready models (stat cards, maintenance queue, cost summary).
//
//  Data flow:
//  1. loadDashboard() → parallel fetch from all repositories
//  2. DashboardStatsCalculator transforms raw data → UI models
//  3. Fragment observes StateFlows and renders
// ═══════════════════════════════════════════════════════════════════════════════

class MechanicDashboardViewModel : BaseViewModel() {

    // ── Repositories ────────────────────────────────────────────

    private val serviceRepository: ServiceRepository = ServiceRepositoryImpl()
    private val repairRepository: RepairRepository = RepairRepositoryImpl()
    private val alertRepository: AlertRepository = AlertRepositoryImpl()
    private val vehicleRepository: VehicleRepository = VehicleRepositoryImpl()
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepositoryImpl()

    // ── State Flows ─────────────────────────────────────────────

    /** Error message when dashboard fails to load. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** User display name for welcome header. */
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    /** Pre-computed mechanic stat cards (4 cards). */
    private val _statCards = MutableStateFlow<List<StatCardData>>(emptyList())
    val statCards: StateFlow<List<StatCardData>> = _statCards.asStateFlow()

    /** Pre-computed mechanic stats with cost data. */
    private val _mechanicStats = MutableStateFlow(MechanicStats())
    val mechanicStats: StateFlow<MechanicStats> = _mechanicStats.asStateFlow()

    /** Sorted maintenance task queue. */
    private val _maintenanceQueue = MutableStateFlow<List<MaintenanceTask>>(emptyList())
    val maintenanceQueue: StateFlow<List<MaintenanceTask>> = _maintenanceQueue.asStateFlow()

    /** Active alerts (maintenance-related). */
    private val _activeAlerts = MutableStateFlow<ResultState<List<Alert>>>(ResultState.Loading)
    val activeAlerts: StateFlow<ResultState<List<Alert>>> = _activeAlerts.asStateFlow()

    /** Recent activity logs. */
    private val _recentActivity = MutableStateFlow<ResultState<List<ActivityLog>>>(ResultState.Loading)
    val recentActivity: StateFlow<ResultState<List<ActivityLog>>> = _recentActivity.asStateFlow()

    /** Last updated timestamp. */
    private val _lastUpdated = MutableStateFlow(0L)
    val lastUpdated: StateFlow<Long> = _lastUpdated.asStateFlow()

    private var currentCompanyId: String? = null

    // ── Initialization ──────────────────────────────────────────

    /**
     * Start loading all dashboard data. Called from fragment's initViews().
     */
    fun loadDashboard() {
        viewModelScope.launch {
            val sessionManager = SessionManager.getInstance()
            val companyId = sessionManager.getCachedCompanyId() ?: return@launch

            currentCompanyId = companyId

            val user = sessionManager.currentUser.value
            _userName.value = user?.displayName ?: "Mechanic"

            // Load dashboard data
            fetchDashboardData(companyId)

            // Start real-time listeners for alerts and activity
            observeActiveAlerts(companyId)
            observeRecentActivity(companyId)
        }
    }

    /**
     * Refresh all dashboard data (triggered by swipe-to-refresh or retry).
     */
    fun refreshDashboard() {
        val companyId = currentCompanyId ?: return
        setLoading(true)
        _error.value = null
        _activeAlerts.value = ResultState.Loading
        _recentActivity.value = ResultState.Loading

        viewModelScope.launch {
            fetchDashboardData(companyId)
            observeActiveAlerts(companyId)
            observeRecentActivity(companyId)
        }
    }

    // ── Data Fetching ───────────────────────────────────────────

    /**
     * Parallel fetch of all mechanic dashboard data:
     * - Pending services
     * - Active repairs (by company)
     * - Completed services today
     * - Urgent alerts
     * - Vehicles (for queue labels)
     */
    private suspend fun fetchDashboardData(companyId: String) {
        setLoading(true)
        _error.value = null

        try {
            // Launch all fetches in parallel
            val pendingServicesDeferred = viewModelScope.async {
                serviceRepository.getPendingServices(companyId)
            }
            val repairsByCompanyDeferred = viewModelScope.async {
                repairRepository.getRepairsByCompany(companyId)
            }
            val completedTodayDeferred = viewModelScope.async {
                val todayStart = DashboardStatsCalculator.todayStartTimestamp()
                val now = System.currentTimeMillis()
                serviceRepository.getServicesByDateRange(companyId, todayStart, now)
            }
            val urgentAlertsDeferred = viewModelScope.async {
                alertRepository.getActiveAlerts(companyId)
            }
            val vehiclesDeferred = viewModelScope.async {
                vehicleRepository.getVehiclesByCompany(companyId)
            }

            // Await all results
            val pendingServicesResult = pendingServicesDeferred.await()
            val repairsResult = repairsByCompanyDeferred.await()
            val completedTodayResult = completedTodayDeferred.await()
            val alertsResult = urgentAlertsDeferred.await()
            val vehiclesResult = vehiclesDeferred.await()

            // Extract data (default to empty on error)
            val pendingServices = extractData(pendingServicesResult) ?: emptyList()
            val activeRepairs = (extractData(repairsResult) ?: emptyList())
                .filter { it.repairDone.isBlank() } // Only active (not yet done) repairs
            val completedToday = (extractData(completedTodayResult) ?: emptyList())
                .filter { it.completed }
            val urgentAlerts = (extractData(alertsResult) ?: emptyList())
                .filter { it.priority == AlertPriority.HIGH || it.priority == AlertPriority.CRITICAL }
            val vehicles = (extractData(vehiclesResult) ?: emptyList())
                .associateBy { it.vehicleId }

            // Build stats via calculator
            val stats = DashboardStatsCalculator.buildMechanicStats(
                pendingServices = pendingServices,
                activeRepairs = activeRepairs,
                completedToday = completedToday,
                urgentAlerts = urgentAlerts
            )
            _mechanicStats.value = stats
            _statCards.value = DashboardStatsCalculator.buildMechanicStatCards(stats)

            // Build maintenance queue
            _maintenanceQueue.value = DashboardStatsCalculator.buildMaintenanceQueue(
                pendingServices = pendingServices,
                vehicles = vehicles
            )

            _lastUpdated.value = System.currentTimeMillis()
            setLoading(false)

        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to load mechanic dashboard"
            setLoading(false)
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

    // ── Utility ─────────────────────────────────────────────────

    /**
     * Safely extract data from a ResultState, returning null on error.
     */
    private fun <T> extractData(result: ResultState<T>): T? {
        return when (result) {
            is ResultState.Success -> result.data
            else -> null
        }
    }

    /**
     * Check if there's a critical error that prevents showing any data.
     */
    fun hasCriticalError(): Boolean {
        return _error.value != null && _statCards.value.isEmpty()
    }
}
