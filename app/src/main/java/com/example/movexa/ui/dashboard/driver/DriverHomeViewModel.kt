package com.example.movexa.ui.dashboard.driver

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.DriverSummary
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.User
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.repository.DriverHomeRepository
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
import kotlinx.coroutines.launch

/**
 * ViewModel for the Driver Home screen — the driver's operational
 * control center.
 *
 * ═══════════════════════════════════════════════════════════════
 * DATA FLOW
 * ═══════════════════════════════════════════════════════════════
 *
 *  Fragment.initViews()
 *       │
 *       ▼
 *  initialize()
 *       │
 *       ├─► getCachedUserId()
 *       ├─► getUserProfile()          → user name, avatar
 *       ├─► getDriverByUserId()       → driverId, vehicleId
 *       ├─► observeActiveTrip()       → real-time trip listener
 *       ├─► observeVehicle()          → real-time vehicle info
 *       ├─► observeDriverSummary()    → real-time score
 *       └─► observeTodayStats()       → real-time daily stats
 *
 *  Trip Action
 *       │
 *       ▼
 *  performAction(action)
 *       │
 *       ├─► validate via DriverTripStateManager
 *       ├─► update Firestore
 *       ├─► log TripEvent
 *       └─► emit actionResult
 *
 * ═══════════════════════════════════════════════════════════════
 * STATE MANAGEMENT
 * ═══════════════════════════════════════════════════════════════
 *
 *  ScreenState sealed class:
 *   - Loading     → shimmer UI
 *   - Content     → full dashboard
 *   - Error(msg)  → error with retry
 */
class DriverHomeViewModel : BaseViewModel() {

    companion object {
        private const val TAG = "DriverHomeVM"
    }

    // ─── Repository ─────────────────────────────────────────────
    private val repository = DriverHomeRepository()

    // ─── State Manager ──────────────────────────────────────────
    val tripStateManager = DriverTripStateManager()

    // ─── Cached IDs ─────────────────────────────────────────────
    private var userId: String = ""
    private var driverId: String = ""
    private var companyId: String = ""
    private var assignedVehicleId: String? = null

    // ─── Flow Jobs (for safe cancellation) ──────────────────────
    private var activeTripJob: Job? = null
    private var vehicleJob: Job? = null
    private var summaryJob: Job? = null
    private var todayStatsJob: Job? = null

    // ═══════════════════════════════════════════════════════════
    //  SCREEN STATE
    // ═══════════════════════════════════════════════════════════

    sealed class ScreenState {
        data object Loading : ScreenState()
        data object Content : ScreenState()
        data class Error(val message: String) : ScreenState()
    }

    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Loading)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  USER & DRIVER
    // ═══════════════════════════════════════════════════════════

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile: StateFlow<User?> = _userProfile.asStateFlow()

    private val _driverProfile = MutableStateFlow<Driver?>(null)
    val driverProfile: StateFlow<Driver?> = _driverProfile.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  ACTIVE TRIP
    // ═══════════════════════════════════════════════════════════

    private val _activeTrip = MutableStateFlow<ResultState<Trip?>>(ResultState.Idle)
    val activeTrip: StateFlow<ResultState<Trip?>> = _activeTrip.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  VEHICLE
    // ═══════════════════════════════════════════════════════════

    private val _vehicle = MutableStateFlow<Vehicle?>(null)
    val vehicle: StateFlow<Vehicle?> = _vehicle.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  PERFORMANCE
    // ═══════════════════════════════════════════════════════════

    private val _driverSummary = MutableStateFlow<DriverSummary?>(null)
    val driverSummary: StateFlow<DriverSummary?> = _driverSummary.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  TODAY STATS
    // ═══════════════════════════════════════════════════════════

    private val _todayStats = MutableStateFlow(DriverHomeRepository.TodayStats())
    val todayStats: StateFlow<DriverHomeRepository.TodayStats> = _todayStats.asStateFlow()

    private val _todayAlertCount = MutableStateFlow(0)
    val todayAlertCount: StateFlow<Int> = _todayAlertCount.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  ACTION EVENTS
    // ═══════════════════════════════════════════════════════════

    private val _actionResult = MutableSharedFlow<ActionResult>()
    val actionResult: SharedFlow<ActionResult> = _actionResult.asSharedFlow()

    private val _isActionLoading = MutableStateFlow(false)
    val isActionLoading: StateFlow<Boolean> = _isActionLoading.asStateFlow()

    // ─── Refreshing ─────────────────────────────────────────────
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // ─── Offline Banner ─────────────────────────────────────────
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  INITIALIZATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Initialize the ViewModel.
     * Resolves user → driver, starts real-time listeners.
     *
     * Call from Fragment's `initViews()`.
     * Safe to call multiple times — no-op after first init.
     */
    fun initialize() {
        if (driverId.isNotBlank()) return // Already initialized

        viewModelScope.launch(Dispatchers.IO) {
            _screenState.value = ScreenState.Loading

            try {
                // ── 1. Get current user ID ──────────────────────
                val cachedUserId = SessionManager.getInstance().getCachedUserId()
                if (cachedUserId.isNullOrBlank()) {
                    _screenState.value = ScreenState.Error(
                        "Not logged in. Please sign in again."
                    )
                    return@launch
                }
                userId = cachedUserId

                // ── 2. Fetch user profile ───────────────────────
                val userResult = repository.getUserProfile(userId)
                if (userResult is ResultState.Success) {
                    _userProfile.value = userResult.data
                }

                // ── 3. Resolve driver record (auto-create if missing)
                val driverResult = repository.getOrCreateDriverByUserId(userId)
                if (driverResult is ResultState.Success) {
                    val driver = driverResult.data
                    driverId = driver.driverId
                    companyId = driver.companyId
                    assignedVehicleId = driver.assignedVehicleId
                    _driverProfile.value = driver
                } else {
                    _screenState.value = ScreenState.Error(
                        (driverResult as? ResultState.Error)?.message
                            ?: "Failed to initialize driver profile. Please try again."
                    )
                    return@launch
                }

                // ── 4. Start real-time listeners ────────────────
                startActiveTripObserver()
                startVehicleObserver()
                startSummaryObserver()
                startTodayStatsObserver()
                loadTodayAlertCount()

                // ── 5. Show content ─────────────────────────────
                _screenState.value = ScreenState.Content

            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                _screenState.value = ScreenState.Error(
                    e.message ?: "Failed to load home screen"
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  REAL-TIME OBSERVERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Observe the driver's active trip.
     * Auto-updates UI when trip status changes from another source.
     */
    private fun startActiveTripObserver() {
        activeTripJob?.cancel()
        activeTripJob = viewModelScope.launch(Dispatchers.IO) {
            repository.observeActiveTrip(driverId).collect { result ->
                _activeTrip.value = result
            }
        }
    }

    /**
     * Observe the assigned vehicle for detail changes.
     */
    private fun startVehicleObserver() {
        val vehicleId = assignedVehicleId ?: return
        vehicleJob?.cancel()
        vehicleJob = viewModelScope.launch(Dispatchers.IO) {
            repository.observeVehicle(vehicleId).collect { result ->
                if (result is ResultState.Success) {
                    _vehicle.value = result.data
                }
            }
        }
    }

    /**
     * Observe the driver's performance score.
     */
    private fun startSummaryObserver() {
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch(Dispatchers.IO) {
            repository.observeDriverSummary(driverId).collect { result ->
                if (result is ResultState.Success) {
                    _driverSummary.value = result.data
                }
            }
        }
    }

    /**
     * Observe today's completed trip stats in real-time.
     */
    private fun startTodayStatsObserver() {
        todayStatsJob?.cancel()
        todayStatsJob = viewModelScope.launch(Dispatchers.IO) {
            repository.observeTodayStats(driverId).collect { result ->
                if (result is ResultState.Success) {
                    _todayStats.value = result.data
                }
            }
        }
    }

    /**
     * Load today's alert count (one-shot, refresh-able).
     */
    private fun loadTodayAlertCount() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.getTodayAlertCount(driverId)
            if (result is ResultState.Success) {
                _todayAlertCount.value = result.data
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  REFRESH
    // ═══════════════════════════════════════════════════════════

    /**
     * Pull-to-refresh. Re-fetches all data.
     */
    fun refresh() {
        if (driverId.isBlank()) {
            initialize()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true

            try {
                // Re-fetch user + driver
                val userResult = repository.getUserProfile(userId)
                if (userResult is ResultState.Success) {
                    _userProfile.value = userResult.data
                }

                val driverResult = repository.getDriverByUserId(userId)
                if (driverResult is ResultState.Success && driverResult.data != null) {
                    _driverProfile.value = driverResult.data
                    assignedVehicleId = driverResult.data.assignedVehicleId
                }

                // Re-fetch vehicle
                assignedVehicleId?.let { vid ->
                    val vehicleResult = repository.getVehicle(vid)
                    if (vehicleResult is ResultState.Success) {
                        _vehicle.value = vehicleResult.data
                    }
                }

                // Re-fetch today stats
                val statsResult = repository.getTodayStats(driverId)
                if (statsResult is ResultState.Success) {
                    _todayStats.value = statsResult.data
                }

                loadTodayAlertCount()

                emitSuccess("Data refreshed")

            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
                emitError(e.message ?: "Refresh failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  TRIP ACTIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Perform a trip action (accept, start, complete).
     * Validates via DriverTripStateManager, prevents double-tap,
     * updates Firestore, and logs trip event.
     */
    fun performAction(action: DriverTripStateManager.TripAction) {
        val trip = (_activeTrip.value as? ResultState.Success)?.data ?: return

        // ── Double-tap guard ────────────────────────────────────
        if (!tripStateManager.tryAcquireActionLock()) return

        viewModelScope.launch(Dispatchers.IO) {
            _isActionLoading.value = true

            try {
                val result = when (action) {
                    DriverTripStateManager.TripAction.ACCEPT -> {
                        repository.acceptTrip(trip, driverId)
                    }
                    DriverTripStateManager.TripAction.START -> {
                        repository.startTrip(trip, driverId)
                    }
                    DriverTripStateManager.TripAction.MARK_DELIVERED -> {
                        // Calculate distance and duration from trip data
                        val distance = if (trip.estimatedDistance > 0)
                            trip.estimatedDistance else trip.distance
                        val duration = if (trip.startTime > 0)
                            System.currentTimeMillis() - trip.startTime else 0L
                        repository.completeTrip(trip, driverId, distance, duration)
                    }
                    else -> {
                        // Non-state-changing actions (view details, etc.)
                        ResultState.Success(Unit)
                    }
                }

                when (result) {
                    is ResultState.Success -> {
                        _actionResult.emit(
                            ActionResult(
                                action = action,
                                success = true,
                                message = getSuccessMessage(action)
                            )
                        )
                    }
                    is ResultState.Error -> {
                        _actionResult.emit(
                            ActionResult(
                                action = action,
                                success = false,
                                message = result.message
                            )
                        )
                    }
                    else -> { /* Loading / Idle — no-op */ }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Action failed: ${action.name}", e)
                _actionResult.emit(
                    ActionResult(
                        action = action,
                        success = false,
                        message = e.message ?: "Action failed"
                    )
                )
            } finally {
                _isActionLoading.value = false
                tripStateManager.releaseActionLock()
            }
        }
    }

    /**
     * Get a user-friendly success message for each action.
     */
    private fun getSuccessMessage(action: DriverTripStateManager.TripAction): String {
        return when (action) {
            DriverTripStateManager.TripAction.ACCEPT -> "Trip accepted!"
            DriverTripStateManager.TripAction.START -> "Trip started. Drive safely!"
            DriverTripStateManager.TripAction.MARK_DELIVERED -> "Trip completed! Great job."
            else -> "Action completed"
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Get greeting based on time of day.
     */
    fun getGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    /**
     * Get today's date formatted as "Mon, 23 Feb 2026".
     */
    fun getTodayDate(): String {
        val formatter = java.text.SimpleDateFormat(
            "EEE, dd MMM yyyy", java.util.Locale.getDefault()
        )
        return formatter.format(java.util.Date())
    }

    /**
     * Whether the driver has a vehicle assigned.
     */
    fun hasVehicleAssigned(): Boolean = assignedVehicleId != null

    /**
     * Check if vehicle service is overdue based on fitness expiry.
     */
    fun isVehicleServiceOverdue(vehicle: Vehicle): Boolean {
        if (vehicle.fitnessExpiry <= 0L) return false
        return vehicle.fitnessExpiry < System.currentTimeMillis()
    }

    // ═══════════════════════════════════════════════════════════
    //  CLEANUP
    // ═══════════════════════════════════════════════════════════

    override fun onCleared() {
        activeTripJob?.cancel()
        vehicleJob?.cancel()
        summaryJob?.cancel()
        todayStatsJob?.cancel()
        super.onCleared()
    }

    // ═══════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════════════════════════

    /**
     * Result of a trip action attempt.
     */
    data class ActionResult(
        val action: DriverTripStateManager.TripAction,
        val success: Boolean,
        val message: String
    )
}
