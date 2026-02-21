package com.example.movexa.ui.dashboard.manager

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.enums.AlertPriority
import com.example.movexa.data.model.enums.AlertStatus
import com.example.movexa.data.model.enums.AlertType
import com.example.movexa.data.repository.contracts.AlertRepository
import com.example.movexa.data.repository.contracts.DriverRepository
import com.example.movexa.data.repository.contracts.VehicleRepository
import com.example.movexa.data.repository.impl.AlertRepositoryImpl
import com.example.movexa.data.repository.impl.DriverRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel for the Manager Alerts screen.
 *
 * Responsibilities:
 * ─────────────────────────────────────────────────────────────
 * - Real-time observation of alerts via Firestore snapshots
 * - Client-side filtering by tab (Active / Resolved / All)
 * - Client-side filtering by priority (Critical, High) and type chips
 * - Full-text search across title, message, vehicle, driver
 * - Alert lifecycle management: acknowledge, resolve, dismiss
 * - Batch operations: resolve all active, delete resolved
 * - Vehicle number and driver name resolution with caching
 * - Statistics computation (active count, critical count, today count)
 * - Undo support via transient state tracking
 *
 * Data Flow:
 * ─────────────────────────────────────────────────────────────
 * Firestore alerts collection (real-time)
 *   → _allAlerts (raw unfiltered list)
 *   → applyFilters() (tab + chip + search)
 *   → _filteredAlerts (displayed in RecyclerView)
 *   → stats computed from _allAlerts
 *
 * Architecture follows project pattern:
 * - Extends [BaseViewModel]
 * - Repositories instantiated directly (no DI)
 * - Uses [SessionManager.getCachedUserId] for company scope
 * - MutableStateFlow for all reactive state
 */
class ManagerAlertsViewModel : BaseViewModel() {

    // ═══════════════════════════════════════════════════════════
    //  Dependencies
    // ═══════════════════════════════════════════════════════════

    private val alertRepository: AlertRepository = AlertRepositoryImpl()
    private val vehicleRepository: VehicleRepository = VehicleRepositoryImpl()
    private val driverRepository: DriverRepository = DriverRepositoryImpl()

    // ═══════════════════════════════════════════════════════════
    //  State Flows — Alert Data
    // ═══════════════════════════════════════════════════════════

    /** Raw alert list from Firestore (all company alerts, unfiltered). */
    private val _allAlerts = MutableStateFlow<ResultState<List<Alert>>>(ResultState.Loading)

    /** Filtered alert list displayed in the RecyclerView. */
    private val _filteredAlerts = MutableStateFlow<ResultState<List<Alert>>>(ResultState.Loading)
    val filteredAlerts: StateFlow<ResultState<List<Alert>>> = _filteredAlerts.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  State Flows — Statistics
    // ═══════════════════════════════════════════════════════════

    /** Count of active (ACTIVE + ACKNOWLEDGED) alerts. */
    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    /** Count of critical-priority active alerts. */
    private val _criticalCount = MutableStateFlow(0)
    val criticalCount: StateFlow<Int> = _criticalCount.asStateFlow()

    /** Count of alerts generated today. */
    private val _todayCount = MutableStateFlow(0)
    val todayCount: StateFlow<Int> = _todayCount.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  State Flows — UI Controls
    // ═══════════════════════════════════════════════════════════

    /** Currently selected tab index: 0=Active, 1=Resolved, 2=All. */
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    /** Currently selected filter chip. Null = All. */
    private val _selectedFilter = MutableStateFlow<AlertFilter>(AlertFilter.ALL)
    val selectedFilter: StateFlow<AlertFilter> = _selectedFilter.asStateFlow()

    /** Current search query text. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Whether the search bar is currently visible. */
    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    /** Single-shot operation result (resolve, dismiss, acknowledge). */
    private val _operationResult = MutableStateFlow<ResultState<String>>(ResultState.Idle)
    val operationResult: StateFlow<ResultState<String>> = _operationResult.asStateFlow()

    /** Whether a refresh is in progress. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  Name Caches
    // ═══════════════════════════════════════════════════════════

    /** Cached vehicle number by vehicleId for display. */
    private val vehicleNameCache = mutableMapOf<String, String>()

    /** Cached driver name/license by driverId for display. */
    private val driverNameCache = mutableMapOf<String, String>()

    /** Company ID for this session. */
    private var currentCompanyId: String? = null

    /** Job for the active realtime observation. */
    private var observationJob: Job? = null

    // ═══════════════════════════════════════════════════════════
    //  Filter Enum
    // ═══════════════════════════════════════════════════════════

    /**
     * Filter options for the chip group.
     */
    enum class AlertFilter {
        ALL,
        CRITICAL,
        HIGH,
        OVERSPEED,
        HARSH_BRAKING,
        LONG_IDLE,
        ACCIDENT;

        /**
         * Match an alert against this filter.
         */
        fun matches(alert: Alert): Boolean {
            return when (this) {
                ALL -> true
                CRITICAL -> alert.priority == AlertPriority.CRITICAL
                HIGH -> alert.priority == AlertPriority.HIGH
                OVERSPEED -> alert.type == AlertType.OVER_SPEED
                HARSH_BRAKING -> alert.type == AlertType.HARSH_BRAKING
                LONG_IDLE -> alert.type == AlertType.LONG_IDLE
                ACCIDENT -> alert.type == AlertType.ACCIDENT_SUSPECTED
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Initialization
    // ═══════════════════════════════════════════════════════════

    /**
     * Start observing company alerts.
     * Call from fragment's [initViews] or [observeData].
     */
    fun loadAlerts() {
        viewModelScope.launch {
            val companyId = SessionManager.getInstance().getCachedUserId()
            if (companyId.isNullOrBlank()) {
                val error = ResultState.Error("No company ID found. Please log in again.")
                _allAlerts.value = error
                _filteredAlerts.value = error
                return@launch
            }
            currentCompanyId = companyId
            observeCompanyAlerts(companyId)
        }
    }

    /**
     * Refresh alert data (pull-to-refresh).
     */
    fun refreshAlerts() {
        val companyId = currentCompanyId ?: return
        _isRefreshing.value = true
        _allAlerts.value = ResultState.Loading

        // Re-trigger observation
        observeCompanyAlerts(companyId)
    }

    // ═══════════════════════════════════════════════════════════
    //  Real-Time Observation
    // ═══════════════════════════════════════════════════════════

    /**
     * Observe all company alerts in real-time.
     *
     * Uses [AlertRepository.observeActiveAlerts] for active tab,
     * and fetches all alerts for the "All" and "Resolved" tabs.
     * Both results are merged into [_allAlerts].
     */
    private fun observeCompanyAlerts(companyId: String) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            alertRepository.observeActiveAlerts(companyId)
                .catch { e ->
                    val error = ResultState.Error(
                        message = e.message ?: "Failed to load alerts",
                        exception = e
                    )
                    _allAlerts.value = error
                    _filteredAlerts.value = error
                    _isRefreshing.value = false
                }
                .collect { result ->
                    when (result) {
                        is ResultState.Success -> {
                            // Active alerts from realtime listener
                            val activeAlerts = result.data

                            // Also load resolved/dismissed alerts for "All" and "Resolved" tabs
                            loadAllAlertsInBackground(companyId, activeAlerts)
                        }
                        is ResultState.Error -> {
                            _allAlerts.value = result
                            _filteredAlerts.value = result
                            _isRefreshing.value = false
                        }
                        is ResultState.Loading -> {
                            _allAlerts.value = ResultState.Loading
                            _filteredAlerts.value = ResultState.Loading
                        }
                        else -> {}
                    }
                }
        }
    }

    /**
     * Load all company alerts (including resolved) to supplement the
     * real-time active alerts stream.
     *
     * Merges active alerts (real-time) with resolved/dismissed alerts
     * (one-time fetch) into [_allAlerts].
     */
    private fun loadAllAlertsInBackground(
        companyId: String,
        activeAlerts: List<Alert>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allResult = alertRepository.getAlertsByCompany(companyId)
                val allAlerts = if (allResult is ResultState.Success) {
                    // Merge: active alerts from stream take precedence
                    val activeIds = activeAlerts.map { it.alertId }.toSet()
                    val resolvedAlerts = allResult.data.filter { it.alertId !in activeIds }
                    activeAlerts + resolvedAlerts
                } else {
                    activeAlerts
                }

                // Sort by timestamp descending
                val sorted = allAlerts.sortedByDescending { it.timestamp }
                _allAlerts.value = ResultState.Success(sorted)

                // Update stats
                computeStatistics(sorted)

                // Resolve names
                resolveNames(sorted)

                // Apply current filters
                applyFilters()

                _isRefreshing.value = false
            } catch (e: Exception) {
                // Fall back to just active alerts
                _allAlerts.value = ResultState.Success(activeAlerts)
                computeStatistics(activeAlerts)
                applyFilters()
                _isRefreshing.value = false
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Statistics
    // ═══════════════════════════════════════════════════════════

    /**
     * Compute alert statistics from the full alert list.
     */
    private fun computeStatistics(alerts: List<Alert>) {
        // Active count (ACTIVE + ACKNOWLEDGED)
        _activeCount.value = alerts.count { it.status.isOpen() }

        // Critical count (CRITICAL priority + still open)
        _criticalCount.value = alerts.count {
            it.priority == AlertPriority.CRITICAL && it.status.isOpen()
        }

        // Today count (timestamp within today)
        val todayStart = getTodayStartTimestamp()
        _todayCount.value = alerts.count { it.timestamp >= todayStart }
    }

    /**
     * Get the timestamp for the start of today (midnight).
     */
    private fun getTodayStartTimestamp(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    // ═══════════════════════════════════════════════════════════
    //  Filtering
    // ═══════════════════════════════════════════════════════════

    /**
     * Set the currently selected tab.
     * 0 = Active, 1 = Resolved, 2 = All
     */
    fun setSelectedTab(tabIndex: Int) {
        if (_selectedTab.value != tabIndex) {
            _selectedTab.value = tabIndex
            applyFilters()
        }
    }

    /**
     * Set the alert filter from chip selection.
     */
    fun setFilter(filter: AlertFilter) {
        if (_selectedFilter.value != filter) {
            _selectedFilter.value = filter
            applyFilters()
        }
    }

    /**
     * Set the search query text.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    /**
     * Toggle search bar visibility.
     */
    fun toggleSearch() {
        val newState = !_isSearchVisible.value
        _isSearchVisible.value = newState
        if (!newState) {
            // Clear search when hiding
            _searchQuery.value = ""
            applyFilters()
        }
    }

    /**
     * Apply all active filters (tab + chip + search) to produce [_filteredAlerts].
     *
     * Filter pipeline:
     * 1. Tab filter (Active/Resolved/All)
     * 2. Chip filter (priority or type)
     * 3. Search query (title, message, vehicle, driver)
     */
    private fun applyFilters() {
        val currentState = _allAlerts.value
        if (currentState !is ResultState.Success) {
            _filteredAlerts.value = currentState
            return
        }

        val alerts = currentState.data
        val tab = _selectedTab.value
        val filter = _selectedFilter.value
        val query = _searchQuery.value.trim().lowercase()

        // Step 1: Tab filter
        val tabFiltered = when (tab) {
            0 -> alerts.filter { it.status.isOpen() } // Active
            1 -> alerts.filter { !it.status.isOpen() } // Resolved/Dismissed
            else -> alerts // All
        }

        // Step 2: Chip filter
        val chipFiltered = tabFiltered.filter { filter.matches(it) }

        // Step 3: Search filter
        val searchFiltered = if (query.isBlank()) {
            chipFiltered
        } else {
            chipFiltered.filter { alert ->
                alert.title.lowercase().contains(query) ||
                alert.message.lowercase().contains(query) ||
                alert.type.displayName.lowercase().contains(query) ||
                alert.priority.displayName.lowercase().contains(query) ||
                alert.status.displayName.lowercase().contains(query) ||
                (alert.vehicleId?.let { vehicleNameCache[it]?.lowercase()?.contains(query) } == true) ||
                (alert.driverId?.let { driverNameCache[it]?.lowercase()?.contains(query) } == true)
            }
        }

        _filteredAlerts.value = ResultState.Success(searchFiltered)
    }

    // ═══════════════════════════════════════════════════════════
    //  Alert Actions
    // ═══════════════════════════════════════════════════════════

    /**
     * Acknowledge an alert (change status from ACTIVE to ACKNOWLEDGED).
     */
    fun acknowledgeAlert(alertId: String) {
        _operationResult.value = ResultState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = alertRepository.acknowledgeAlert(alertId)) {
                    is ResultState.Success -> {
                        _operationResult.value = ResultState.Success("Alert acknowledged")
                        // Real-time listener will auto-update the list
                    }
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            result.message, result.exception
                        )
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to acknowledge alert", e
                )
            }
        }
    }

    /**
     * Resolve an alert.
     *
     * @param alertId The alert to resolve.
     */
    fun resolveAlert(alertId: String) {
        _operationResult.value = ResultState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = SessionManager.getInstance().getCachedUserId() ?: "manager"
                when (val result = alertRepository.resolveAlert(alertId, userId)) {
                    is ResultState.Success -> {
                        _operationResult.value = ResultState.Success("Alert resolved")
                    }
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            result.message, result.exception
                        )
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to resolve alert", e
                )
            }
        }
    }

    /**
     * Dismiss an alert (low-priority action).
     */
    fun dismissAlert(alertId: String) {
        _operationResult.value = ResultState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = alertRepository.dismissAlert(alertId)) {
                    is ResultState.Success -> {
                        _operationResult.value = ResultState.Success("Alert dismissed")
                    }
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            result.message, result.exception
                        )
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to dismiss alert", e
                )
            }
        }
    }

    /**
     * Resolve all currently active alerts in batch.
     */
    fun resolveAllActive() {
        val currentState = _allAlerts.value
        if (currentState !is ResultState.Success) return

        val activeAlertIds = currentState.data
            .filter { it.status.isOpen() }
            .map { it.alertId }

        if (activeAlertIds.isEmpty()) {
            _operationResult.value = ResultState.Error("No active alerts to resolve")
            return
        }

        _operationResult.value = ResultState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = SessionManager.getInstance().getCachedUserId() ?: "manager"
                when (val result = alertRepository.resolveMultipleAlerts(activeAlertIds, userId)) {
                    is ResultState.Success -> {
                        _operationResult.value = ResultState.Success(
                            "${activeAlertIds.size} alerts resolved"
                        )
                    }
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            result.message, result.exception
                        )
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to resolve alerts", e
                )
            }
        }
    }

    /**
     * Delete all resolved alerts for the company.
     */
    fun deleteResolvedAlerts() {
        val companyId = currentCompanyId ?: return

        _operationResult.value = ResultState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = alertRepository.deleteResolvedAlerts(companyId)) {
                    is ResultState.Success -> {
                        _operationResult.value = ResultState.Success("Resolved alerts deleted")
                        refreshAlerts()
                    }
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            result.message, result.exception
                        )
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to delete resolved alerts", e
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Name Resolution
    // ═══════════════════════════════════════════════════════════

    /**
     * Resolve vehicle numbers and driver names for display.
     * Results are cached to minimize Firestore reads.
     */
    private fun resolveNames(alerts: List<Alert>) {
        viewModelScope.launch(Dispatchers.IO) {
            var needsRefilter = false

            for (alert in alerts) {
                // Resolve vehicle number
                val vId = alert.vehicleId
                if (!vId.isNullOrBlank() && !vehicleNameCache.containsKey(vId)) {
                    try {
                        val result = vehicleRepository.getVehicleById(vId)
                        if (result is ResultState.Success && result.data != null) {
                            vehicleNameCache[vId] = result.data.number
                            needsRefilter = true
                        }
                    } catch (_: Exception) {}
                }

                // Resolve driver name
                val dId = alert.driverId
                if (!dId.isNullOrBlank() && !driverNameCache.containsKey(dId)) {
                    try {
                        val result = driverRepository.getDriverById(dId)
                        if (result is ResultState.Success && result.data != null) {
                            val driver = result.data
                            driverNameCache[dId] = driver.licenseNumber.ifBlank {
                                "Driver ${driver.driverId.take(6)}"
                            }
                            needsRefilter = true
                        }
                    } catch (_: Exception) {}
                }
            }

            // Re-apply filters so search can match resolved names
            if (needsRefilter && _searchQuery.value.isNotBlank()) {
                applyFilters()
            }
        }
    }

    /**
     * Get the cached vehicle number for display.
     *
     * @param vehicleId The vehicle ID to look up.
     * @return The vehicle number, or null if not cached.
     */
    fun getVehicleNumber(vehicleId: String): String? {
        return vehicleNameCache[vehicleId]
    }

    /**
     * Get the cached driver name for display.
     *
     * @param driverId The driver ID to look up.
     * @return The driver name/license, or null if not cached.
     */
    fun getDriverName(driverId: String): String? {
        return driverNameCache[driverId]
    }

    // ═══════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════

    /**
     * Clear the operation result after consumption by the fragment.
     */
    fun clearOperationResult() {
        _operationResult.value = ResultState.Idle
    }

    /**
     * Get alert by ID from the current cached data.
     */
    fun getAlertById(alertId: String): Alert? {
        val currentState = _allAlerts.value
        if (currentState !is ResultState.Success) return null
        return currentState.data.firstOrNull { it.alertId == alertId }
    }

    /**
     * Get the total count of all alerts currently loaded.
     */
    fun getTotalAlertCount(): Int {
        val currentState = _allAlerts.value
        return if (currentState is ResultState.Success) currentState.data.size else 0
    }

    /**
     * Get the count of filtered alerts currently displayed.
     */
    fun getFilteredAlertCount(): Int {
        val currentState = _filteredAlerts.value
        return if (currentState is ResultState.Success) currentState.data.size else 0
    }
}
