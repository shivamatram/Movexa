package com.example.movexa.ui.dashboard.manager

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.TrackingLocation
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.Trip
import com.example.movexa.data.repository.impl.DriverRepositoryImpl
import com.example.movexa.data.repository.impl.TrackingRepositoryImpl
import com.example.movexa.data.repository.impl.TripRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel for the Manager Live Tracking screen.
 *
 * Responsibilities:
 *  ● Observe all company vehicle locations in real time via TrackingRepository
 *  ● Resolve vehicle number + driver name for each tracked location
 *  ● Provide filter state (All / Moving / Idle)
 *  ● Manage selected-vehicle detail state
 *  ● Compute summary counts (total / moving / idle)
 */
class ManagerTrackingViewModel : BaseViewModel() {

    // ─── Repositories ───────────────────────────────────────────
    private val trackingRepository = TrackingRepositoryImpl()
    private val vehicleRepository = VehicleRepositoryImpl()
    private val driverRepository = DriverRepositoryImpl()
    private val tripRepository = TripRepositoryImpl()

    // ─── All Company Locations ──────────────────────────────────
    private val _allLocations = MutableStateFlow<ResultState<List<TrackingLocation>>>(ResultState.Idle)
    val allLocations: StateFlow<ResultState<List<TrackingLocation>>> = _allLocations.asStateFlow()

    // ─── Filtered Locations (displayed on map) ──────────────────
    private val _filteredLocations = MutableStateFlow<List<TrackingLocation>>(emptyList())
    val filteredLocations: StateFlow<List<TrackingLocation>> = _filteredLocations.asStateFlow()

    // ─── Filter Mode ────────────────────────────────────────────
    enum class FilterMode { ALL, MOVING, IDLE }

    private val _filterMode = MutableStateFlow(FilterMode.ALL)
    val filterMode: StateFlow<FilterMode> = _filterMode.asStateFlow()

    // ─── Summary Counts ─────────────────────────────────────────
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _movingCount = MutableStateFlow(0)
    val movingCount: StateFlow<Int> = _movingCount.asStateFlow()

    private val _idleCount = MutableStateFlow(0)
    val idleCount: StateFlow<Int> = _idleCount.asStateFlow()

    // ─── Selected Vehicle Detail ────────────────────────────────
    private val _selectedVehicleId = MutableStateFlow<String?>(null)
    val selectedVehicleId: StateFlow<String?> = _selectedVehicleId.asStateFlow()

    private val _selectedVehicleDetail = MutableStateFlow<VehicleDetail?>(null)
    val selectedVehicleDetail: StateFlow<VehicleDetail?> = _selectedVehicleDetail.asStateFlow()

    // ─── Caches ─────────────────────────────────────────────────
    private val vehicleCache = mutableMapOf<String, Vehicle>()
    private val driverCache = mutableMapOf<String, Driver>()

    private var currentCompanyId: String? = null
    private var observeJob: Job? = null

    // ─── Data class for selected vehicle detail card ────────────
    data class VehicleDetail(
        val vehicleId: String,
        val vehicleNumber: String,
        val driverName: String,
        val speed: Float,
        val heading: Float,
        val lastUpdate: Long,
        val isMoving: Boolean,
        val tripId: String,
        val lat: Double,
        val lng: Double,
        val accuracy: Float
    )

    // ═══════════════════════════════════════════════════════════
    //  Initialization
    // ═══════════════════════════════════════════════════════════

    /**
     * Load tracking data for the company. Call from fragment's initViews().
     */
    fun loadTracking() {
        viewModelScope.launch {
            val companyId = SessionManager.getInstance().getCachedUserId()
            if (companyId.isNullOrBlank()) {
                _allLocations.value = ResultState.Error("No company ID found. Please log in again.")
                return@launch
            }
            currentCompanyId = companyId

            // Pre-load vehicle + driver caches for name resolution
            loadVehicleCache(companyId)
            loadDriverCache(companyId)

            // Start real-time observation
            observeLocations(companyId)
        }
    }

    /**
     * Refresh tracking data.
     */
    fun refreshTracking() {
        val companyId = currentCompanyId ?: return
        _allLocations.value = ResultState.Loading
        observeLocations(companyId)
    }

    // ═══════════════════════════════════════════════════════════
    //  Real-Time Observation
    // ═══════════════════════════════════════════════════════════

    private fun observeLocations(companyId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            trackingRepository.observeAllCompanyLocations(companyId)
                .catch { e ->
                    _allLocations.value = ResultState.Error(
                        message = e.message ?: "Failed to observe tracking locations",
                        exception = e
                    )
                }
                .collect { result ->
                    _allLocations.value = result

                    if (result is ResultState.Success) {
                        val locations = result.data
                        updateCounts(locations)
                        applyFilter(locations)

                        // Update selected vehicle detail if one is selected
                        _selectedVehicleId.value?.let { selectedId ->
                            val selectedLoc = locations.find { it.vehicleId == selectedId }
                            if (selectedLoc != null) {
                                resolveVehicleDetail(selectedLoc)
                            } else {
                                // Vehicle went offline
                                _selectedVehicleDetail.value = null
                                _selectedVehicleId.value = null
                            }
                        }
                    }
                }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Filtering
    // ═══════════════════════════════════════════════════════════

    /**
     * Set the active filter mode.
     */
    fun setFilter(mode: FilterMode) {
        _filterMode.value = mode
        val currentResult = _allLocations.value
        if (currentResult is ResultState.Success) {
            applyFilter(currentResult.data)
        }
    }

    private fun applyFilter(locations: List<TrackingLocation>) {
        _filteredLocations.value = when (_filterMode.value) {
            FilterMode.ALL -> locations
            FilterMode.MOVING -> locations.filter { it.isMoving }
            FilterMode.IDLE -> locations.filter { !it.isMoving }
        }
    }

    private fun updateCounts(locations: List<TrackingLocation>) {
        _totalCount.value = locations.size
        _movingCount.value = locations.count { it.isMoving }
        _idleCount.value = locations.count { !it.isMoving }
    }

    // ═══════════════════════════════════════════════════════════
    //  Vehicle Selection
    // ═══════════════════════════════════════════════════════════

    /**
     * Select a vehicle to show its detail card.
     */
    fun selectVehicle(vehicleId: String) {
        _selectedVehicleId.value = vehicleId

        val locations = (_allLocations.value as? ResultState.Success)?.data ?: return
        val location = locations.find { it.vehicleId == vehicleId } ?: return
        resolveVehicleDetail(location)
    }

    /**
     * Deselect the currently selected vehicle.
     */
    fun deselectVehicle() {
        _selectedVehicleId.value = null
        _selectedVehicleDetail.value = null
    }

    /**
     * Resolve vehicle number + driver name from caches and build a VehicleDetail.
     */
    private fun resolveVehicleDetail(location: TrackingLocation) {
        viewModelScope.launch {
            val vehicle = vehicleCache[location.vehicleId]
            val vehicleNumber = vehicle?.number ?: location.vehicleId

            var driverName = "Unknown Driver"
            val driverId = location.driverId
            if (driverId.isNotBlank()) {
                driverCache[driverId]?.let { driver ->
                    // Driver model doesn't have fullName — use userId or get from User collection
                    driverName = driver.userId.takeIf { it.isNotBlank() }
                        ?: "Driver ${driver.driverId.takeLast(6)}"
                }
            }

            // Try to get the driver's name from their user profile via metadata if available
            if (driverName.startsWith("Driver ") && vehicle != null) {
                driverName = vehicle.assignedDriverId?.let { id ->
                    driverCache[id]?.let { d ->
                        d.metadata["fullName"] as? String
                            ?: "Driver ${d.driverId.takeLast(6)}"
                    }
                } ?: driverName
            }

            _selectedVehicleDetail.value = VehicleDetail(
                vehicleId = location.vehicleId,
                vehicleNumber = vehicleNumber,
                driverName = driverName,
                speed = location.speed,
                heading = location.heading,
                lastUpdate = location.timestamp,
                isMoving = location.isMoving,
                tripId = location.tripId,
                lat = location.lat,
                lng = location.lng,
                accuracy = location.accuracy
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Cache Loading
    // ═══════════════════════════════════════════════════════════

    private suspend fun loadVehicleCache(companyId: String) {
        val result = vehicleRepository.getVehiclesByCompany(companyId)
        if (result is ResultState.Success) {
            vehicleCache.clear()
            result.data.forEach { vehicle ->
                vehicleCache[vehicle.vehicleId] = vehicle
            }
        }
    }

    private suspend fun loadDriverCache(companyId: String) {
        val result = driverRepository.getDriversByCompany(companyId)
        if (result is ResultState.Success) {
            driverCache.clear()
            result.data.forEach { driver ->
                driverCache[driver.driverId] = driver
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Name Resolution Helpers (for Fragment marker labels)
    // ═══════════════════════════════════════════════════════════

    /**
     * Get a vehicle number from the cache. Returns vehicleId suffix as fallback.
     */
    fun getVehicleNumber(vehicleId: String): String {
        return vehicleCache[vehicleId]?.number ?: vehicleId.takeLast(8)
    }

    /**
     * Get a driver name from the cache. Returns "Unknown" as fallback.
     */
    fun getDriverName(driverId: String): String {
        if (driverId.isBlank()) return "Unassigned"
        val driver = driverCache[driverId] ?: return "Unknown"
        return driver.metadata["fullName"] as? String
            ?: "Driver ${driver.driverId.takeLast(6)}"
    }

    /**
     * Get cached vehicle object.
     */
    fun getVehicle(vehicleId: String): Vehicle? = vehicleCache[vehicleId]

    // ═══════════════════════════════════════════════════════════
    //  Cleanup
    // ═══════════════════════════════════════════════════════════

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }
}
