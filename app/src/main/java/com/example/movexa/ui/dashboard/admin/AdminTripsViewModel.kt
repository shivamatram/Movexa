package com.example.movexa.ui.dashboard.admin

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.data.repository.AdminTripsRepository
import com.example.movexa.data.repository.contracts.DriverRepository
import com.example.movexa.data.repository.contracts.VehicleRepository
import com.example.movexa.data.repository.impl.DriverRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel for the Admin Trips Operations screen.
 *
 * Manages:
 * - Real-time observation of ongoing trips (Firestore snapshots)
 * - Paginated loading of completed/cancelled trips
 * - Multi-dimension filtering (status, driver, vehicle, date range)
 * - Client-side search (tracking ID, addresses, vehicle/driver names)
 * - Admin override actions (cancel, force-complete, reassign, flag)
 * - Vehicle/driver name resolution with caching
 * - Tab-wise trip categorization with counts
 *
 * Architecture:
 * - Uses [AdminTripsRepository] for all data operations
 * - [TripFilterManager] for filter state management
 * - [TripPaginationController] for cursor-based pagination
 * - Exposes immutable StateFlows consumed by the Fragment
 */
class AdminTripsViewModel : BaseViewModel() {

    // ═══════════════════════════════════════════════════════════
    // REPOSITORIES & MANAGERS
    // ═══════════════════════════════════════════════════════════

    private val repository = AdminTripsRepository()
    private val vehicleRepository: VehicleRepository = VehicleRepositoryImpl()
    private val driverRepository: DriverRepository = DriverRepositoryImpl()

    val filterManager = TripFilterManager()
    val completedPagination = TripPaginationController()
    val cancelledPagination = TripPaginationController()

    // ═══════════════════════════════════════════════════════════
    // STATE FLOWS
    // ═══════════════════════════════════════════════════════════

    /** Current selected tab index (0=Ongoing, 1=Completed, 2=Cancelled, 3=All). */
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    /** Ongoing trips (real-time via Firestore snapshot listener). */
    private val _ongoingTrips = MutableStateFlow<ResultState<List<Trip>>>(ResultState.Loading)
    val ongoingTrips: StateFlow<ResultState<List<Trip>>> = _ongoingTrips.asStateFlow()

    /** Completed trips (paginated). */
    private val _completedTrips = MutableStateFlow<ResultState<List<Trip>>>(ResultState.Loading)
    val completedTrips: StateFlow<ResultState<List<Trip>>> = _completedTrips.asStateFlow()

    /** Cancelled trips (paginated). */
    private val _cancelledTrips = MutableStateFlow<ResultState<List<Trip>>>(ResultState.Loading)
    val cancelledTrips: StateFlow<ResultState<List<Trip>>> = _cancelledTrips.asStateFlow()

    /** All trips (combines real-time + paginated). */
    private val _allTrips = MutableStateFlow<ResultState<List<Trip>>>(ResultState.Loading)
    val allTrips: StateFlow<ResultState<List<Trip>>> = _allTrips.asStateFlow()

    /** Tab count badges. */
    private val _ongoingCount = MutableStateFlow(0)
    val ongoingCount: StateFlow<Int> = _ongoingCount.asStateFlow()

    private val _completedCount = MutableStateFlow(0)
    val completedCount: StateFlow<Int> = _completedCount.asStateFlow()

    private val _cancelledCount = MutableStateFlow(0)
    val cancelledCount: StateFlow<Int> = _cancelledCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    /** Admin action results (single-shot events). */
    private val _actionResult = MutableSharedFlow<ResultState<String>>()
    val actionResult: SharedFlow<ResultState<String>> = _actionResult.asSharedFlow()

    /** Filter applied indicator. */
    private val _filterActive = MutableStateFlow(false)
    val filterActive: StateFlow<Boolean> = _filterActive.asStateFlow()

    /** Active filter count for badge display. */
    private val _activeFilterCount = MutableStateFlow(0)
    val activeFilterCount: StateFlow<Int> = _activeFilterCount.asStateFlow()

    /** Eligible vehicles for reassignment. */
    private val _eligibleVehicles = MutableStateFlow<ResultState<List<EligibleVehicle>>>(ResultState.Idle)
    val eligibleVehicles: StateFlow<ResultState<List<EligibleVehicle>>> = _eligibleVehicles.asStateFlow()

    /** Company drivers for filter selection. */
    private val _companyDrivers = MutableStateFlow<List<Driver>>(emptyList())
    val companyDrivers: StateFlow<List<Driver>> = _companyDrivers.asStateFlow()

    /** Company vehicles for filter selection. */
    private val _companyVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val companyVehicles: StateFlow<List<Vehicle>> = _companyVehicles.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    // NAME RESOLUTION CACHES
    // ═══════════════════════════════════════════════════════════

    private val vehicleNameCache = mutableMapOf<String, String>()
    private val driverNameCache = mutableMapOf<String, String>()

    // ═══════════════════════════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════════════════════════

    private var companyId: String? = null
    private var ongoingObservationJob: Job? = null
    private var allRawTrips = mutableListOf<Trip>()

    // ═══════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Initialize the ViewModel. Call from fragment's initViews().
     * Sets up company scope and starts trip observation.
     */
    fun initialize() {
        viewModelScope.launch {
            val userId = SessionManager.getInstance().getCachedUserId()
            if (userId.isNullOrBlank()) {
                val error = ResultState.Error("Session expired. Please log in again.")
                _ongoingTrips.value = error
                _completedTrips.value = error
                _cancelledTrips.value = error
                _allTrips.value = error
                return@launch
            }
            companyId = userId
            startOngoingObservation()
            loadCompletedTrips()
            loadCancelledTrips()
            loadCompanyResources()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // TAB MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME OBSERVATION (Ongoing)
    // ═══════════════════════════════════════════════════════════

    private fun startOngoingObservation() {
        val id = companyId ?: return
        ongoingObservationJob?.cancel()
        ongoingObservationJob = viewModelScope.launch {
            repository.observeOngoingTrips(id)
                .catch { e ->
                    _ongoingTrips.value = ResultState.Error(
                        e.message ?: "Failed to observe trips", e
                    )
                }
                .collect { result ->
                    _ongoingTrips.value = result
                    if (result is ResultState.Success) {
                        val filtered = filterManager.filterBySearch(
                            result.data,
                            ::getVehicleName,
                            ::getDriverName
                        )
                        _ongoingTrips.value = ResultState.Success(filtered)
                        _ongoingCount.value = result.data.size
                        resolveNames(result.data)
                        rebuildAllTrips()
                    }
                }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PAGINATED LOADING (Completed / Cancelled)
    // ═══════════════════════════════════════════════════════════

    fun loadCompletedTrips(loadMore: Boolean = false) {
        val id = companyId ?: return
        if (!loadMore) {
            completedPagination.reset()
            _completedTrips.value = ResultState.Loading
        }
        if (!completedPagination.beginLoading()) return

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.getCompletedTripsPaginated(
                companyId = id,
                pageSize = completedPagination.pageSize,
                lastDocument = completedPagination.lastDocument
            )

            when (result) {
                is ResultState.Success -> {
                    val (trips, lastDoc) = result.data
                    completedPagination.onPageLoaded(trips, lastDoc)
                    val filtered = filterManager.filterBySearch(
                        completedPagination.accumulatedTrips,
                        ::getVehicleName,
                        ::getDriverName
                    )
                    _completedTrips.value = ResultState.Success(filtered)
                    _completedCount.value = completedPagination.accumulatedTrips.size
                    resolveNames(trips)
                    rebuildAllTrips()
                }
                is ResultState.Error -> {
                    completedPagination.onLoadError()
                    if (!loadMore) {
                        _completedTrips.value = result
                    }
                }
                else -> {}
            }
        }
    }

    fun loadCancelledTrips(loadMore: Boolean = false) {
        val id = companyId ?: return
        if (!loadMore) {
            cancelledPagination.reset()
            _cancelledTrips.value = ResultState.Loading
        }
        if (!cancelledPagination.beginLoading()) return

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.getCancelledTripsPaginated(
                companyId = id,
                pageSize = cancelledPagination.pageSize,
                lastDocument = cancelledPagination.lastDocument
            )

            when (result) {
                is ResultState.Success -> {
                    val (trips, lastDoc) = result.data
                    cancelledPagination.onPageLoaded(trips, lastDoc)
                    val filtered = filterManager.filterBySearch(
                        cancelledPagination.accumulatedTrips,
                        ::getVehicleName,
                        ::getDriverName
                    )
                    _cancelledTrips.value = ResultState.Success(filtered)
                    _cancelledCount.value = cancelledPagination.accumulatedTrips.size
                    resolveNames(trips)
                    rebuildAllTrips()
                }
                is ResultState.Error -> {
                    cancelledPagination.onLoadError()
                    if (!loadMore) {
                        _cancelledTrips.value = result
                    }
                }
                else -> {}
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ALL TRIPS TAB
    // ═══════════════════════════════════════════════════════════

    private fun rebuildAllTrips() {
        val ongoing = (_ongoingTrips.value as? ResultState.Success)?.data ?: emptyList()
        val completed = completedPagination.accumulatedTrips
        val cancelled = cancelledPagination.accumulatedTrips

        val combined = (ongoing + completed + cancelled)
            .distinctBy { it.tripId }
            .sortedByDescending { it.createdAt }

        allRawTrips.clear()
        allRawTrips.addAll(combined)

        val filtered = filterManager.filterBySearch(
            combined,
            ::getVehicleName,
            ::getDriverName
        )
        _allTrips.value = ResultState.Success(filtered)
        _totalCount.value = combined.size
    }

    // ═══════════════════════════════════════════════════════════
    // SEARCH & FILTER
    // ═══════════════════════════════════════════════════════════

    /**
     * Update search query and re-filter all tabs.
     */
    fun setSearchQuery(query: String) {
        filterManager.setSearch(query)
        reapplyFilters()
    }

    /**
     * Apply server-side filters and reload data.
     */
    fun applyFilters(
        status: TripStatus? = null,
        driverId: String? = null,
        vehicleId: String? = null,
        startDate: Long? = null,
        endDate: Long? = null
    ) {
        filterManager.setStatus(status)
        filterManager.setDriverId(driverId)
        filterManager.setVehicleId(vehicleId)
        filterManager.setDateRange(startDate, endDate)
        _filterActive.value = filterManager.hasActiveFilters
        _activeFilterCount.value = filterManager.activeFilterCount

        // Reload with new filters
        refreshAll()
    }

    /**
     * Clear all filters and reload.
     */
    fun clearFilters() {
        filterManager.clearAllFilters()
        _filterActive.value = false
        _activeFilterCount.value = 0
        refreshAll()
    }

    /**
     * Re-apply client-side search filter to current data.
     */
    private fun reapplyFilters() {
        // Re-filter ongoing
        val ongoingState = _ongoingTrips.value
        if (ongoingState is ResultState.Success) {
            val filtered = filterManager.filterBySearch(
                ongoingState.data, ::getVehicleName, ::getDriverName
            )
            _ongoingTrips.value = ResultState.Success(filtered)
        }

        // Re-filter completed
        val completedFiltered = filterManager.filterBySearch(
            completedPagination.accumulatedTrips, ::getVehicleName, ::getDriverName
        )
        _completedTrips.value = ResultState.Success(completedFiltered)

        // Re-filter cancelled
        val cancelledFiltered = filterManager.filterBySearch(
            cancelledPagination.accumulatedTrips, ::getVehicleName, ::getDriverName
        )
        _cancelledTrips.value = ResultState.Success(cancelledFiltered)

        // Rebuild all
        rebuildAllTrips()
    }

    // ═══════════════════════════════════════════════════════════
    // REFRESH
    // ═══════════════════════════════════════════════════════════

    /**
     * Full refresh of all tabs.
     */
    fun refreshAll() {
        _ongoingTrips.value = ResultState.Loading
        _completedTrips.value = ResultState.Loading
        _cancelledTrips.value = ResultState.Loading
        _allTrips.value = ResultState.Loading

        startOngoingObservation()
        loadCompletedTrips()
        loadCancelledTrips()
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN ACTIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Cancel a trip (admin override).
     */
    fun cancelTrip(tripId: String, reason: String) {
        val adminId = companyId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _actionResult.emit(ResultState.Loading)
            val result = repository.adminCancelTrip(tripId, adminId, reason)
            when (result) {
                is ResultState.Success -> {
                    _actionResult.emit(ResultState.Success("Trip cancelled successfully"))
                }
                is ResultState.Error -> {
                    _actionResult.emit(ResultState.Error(result.message, result.exception))
                }
                else -> {}
            }
        }
    }

    /**
     * Force-complete a trip (admin override).
     */
    fun forceCompleteTrip(tripId: String, reason: String) {
        val adminId = companyId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _actionResult.emit(ResultState.Loading)
            val result = repository.adminForceCompleteTrip(tripId, adminId, reason)
            when (result) {
                is ResultState.Success -> {
                    _actionResult.emit(ResultState.Success("Trip force-completed"))
                }
                is ResultState.Error -> {
                    _actionResult.emit(ResultState.Error(result.message, result.exception))
                }
                else -> {}
            }
        }
    }

    /**
     * Reassign driver for a trip (emergency override).
     */
    fun reassignDriver(tripId: String, vehicleId: String, driverId: String, reason: String) {
        val adminId = companyId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _actionResult.emit(ResultState.Loading)
            val result = repository.adminReassignDriver(
                tripId, vehicleId, driverId, adminId, reason
            )
            when (result) {
                is ResultState.Success -> {
                    _actionResult.emit(ResultState.Success("Driver reassigned successfully"))
                }
                is ResultState.Error -> {
                    _actionResult.emit(ResultState.Error(result.message, result.exception))
                }
                else -> {}
            }
        }
    }

    /**
     * Flag a trip for audit.
     */
    fun flagForAudit(tripId: String, reason: String) {
        val adminId = companyId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _actionResult.emit(ResultState.Loading)
            val result = repository.flagTripForAudit(tripId, adminId, reason)
            when (result) {
                is ResultState.Success -> {
                    _actionResult.emit(ResultState.Success("Trip flagged for audit"))
                }
                is ResultState.Error -> {
                    _actionResult.emit(ResultState.Error(result.message, result.exception))
                }
                else -> {}
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // REASSIGNMENT SUPPORT
    // ═══════════════════════════════════════════════════════════

    /**
     * Load available vehicles for reassignment.
     */
    fun loadEligibleVehicles() {
        val id = companyId ?: return
        _eligibleVehicles.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val vehicleResult = vehicleRepository.getAvailableVehicles(id)
                if (vehicleResult is ResultState.Success) {
                    val eligible = vehicleResult.data
                        .filter { it.status == VehicleStatus.AVAILABLE && !it.assignedDriverId.isNullOrBlank() }
                        .map { vehicle ->
                            val driverName = vehicle.assignedDriverId?.let { driverId ->
                                val driverResult = driverRepository.getDriverById(driverId)
                                (driverResult as? ResultState.Success)?.data?.licenseNumber
                            } ?: "Unknown"

                            EligibleVehicle(
                                vehicleId = vehicle.vehicleId,
                                driverId = vehicle.assignedDriverId ?: "",
                                vehicleNumber = vehicle.number,
                                vehicleType = vehicle.type.displayName,
                                driverName = driverName
                            )
                        }
                    _eligibleVehicles.value = ResultState.Success(eligible)
                } else {
                    _eligibleVehicles.value = ResultState.Error("Failed to load vehicles")
                }
            } catch (e: Exception) {
                _eligibleVehicles.value = ResultState.Error(
                    e.message ?: "Failed to load eligible vehicles", e
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // NAME RESOLUTION
    // ═══════════════════════════════════════════════════════════

    /**
     * Resolve vehicle numbers and driver names for display.
     * Results cached to minimize Firestore reads.
     */
    fun resolveNames(trips: List<Trip>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (trip in trips) {
                if (trip.vehicleId.isNotBlank() && !vehicleNameCache.containsKey(trip.vehicleId)) {
                    try {
                        val result = vehicleRepository.getVehicleById(trip.vehicleId)
                        if (result is ResultState.Success && result.data != null) {
                            vehicleNameCache[trip.vehicleId] = result.data.number
                        }
                    } catch (_: Exception) { }
                }

                if (trip.driverId.isNotBlank() && !driverNameCache.containsKey(trip.driverId)) {
                    try {
                        val result = driverRepository.getDriverById(trip.driverId)
                        if (result is ResultState.Success && result.data != null) {
                            driverNameCache[trip.driverId] = result.data.licenseNumber.ifBlank {
                                "Driver ${trip.driverId.take(6)}"
                            }
                        }
                    } catch (_: Exception) { }
                }
            }
        }
    }

    fun getVehicleName(vehicleId: String): String? = vehicleNameCache[vehicleId]

    fun getDriverName(driverId: String): String? = driverNameCache[driverId]

    // ═══════════════════════════════════════════════════════════
    // COMPANY RESOURCES
    // ═══════════════════════════════════════════════════════════

    private fun loadCompanyResources() {
        val id = companyId ?: return

        // Load company drivers for filter dropdown
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = driverRepository.getDriversByCompany(id)
                if (result is ResultState.Success) {
                    _companyDrivers.value = result.data
                }
            } catch (_: Exception) { }
        }

        // Load company vehicles for filter dropdown
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = vehicleRepository.getVehiclesByCompany(id)
                if (result is ResultState.Success) {
                    _companyVehicles.value = result.data
                }
            } catch (_: Exception) { }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        ongoingObservationJob?.cancel()
    }

    // ═══════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════

    /**
     * Represents a vehicle eligible for reassignment.
     */
    data class EligibleVehicle(
        val vehicleId: String,
        val driverId: String,
        val vehicleNumber: String,
        val vehicleType: String,
        val driverName: String
    )
}
