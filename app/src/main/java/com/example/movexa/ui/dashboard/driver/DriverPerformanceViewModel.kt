package com.example.movexa.ui.dashboard.driver

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.DriverSummary
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.repository.impl.DriverPerformanceRepositoryImpl
import com.example.movexa.data.repository.impl.DriverRepositoryImpl
import com.example.movexa.data.session.SessionManager
import com.example.movexa.service.DriverScoringEngine
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Driver Performance Scoring screen.
 *
 * ═══════════════════════════════════════════════════════════════
 * RESPONSIBILITIES
 * ═══════════════════════════════════════════════════════════════
 *
 * 1. Load the driver's cached performance summary from Firestore
 * 2. Trigger a full score recalculation on pull-to-refresh
 * 3. Generate driving suggestions based on violation patterns
 * 4. Observe real-time updates to the driver summary document
 * 5. Build violation breakdown items for the UI
 * 6. Provide formatted display data via StateFlows
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
 *       ├─► getDriverByUserId()   → driverId + companyId
 *       ├─► observeDriverSummary()  → real-time Flow
 *       └─► recalculateScore()    → initial score calc
 *
 *  Pull-to-Refresh
 *       │
 *       ▼
 *  refreshScore()
 *       │
 *       └─► recalculateScore() → scoring engine full recalc
 *
 */
class DriverPerformanceViewModel : BaseViewModel() {

    companion object {
        private const val TAG = "DriverPerfVM"
    }

    // ─── Repositories ───────────────────────────────────────────
    private val driverRepository = DriverRepositoryImpl()
    private val performanceRepository = DriverPerformanceRepositoryImpl()
    private val scoringEngine = DriverScoringEngine(viewModelScope)

    // ─── Cached IDs ─────────────────────────────────────────────
    private var driverId: String = ""
    private var companyId: String = ""

    // ═══════════════════════════════════════════════════════════
    //  STATE FLOWS
    // ═══════════════════════════════════════════════════════════

    /** Overall screen state: Loading, Success (with data), Error, Empty */
    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Loading)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    /** The driver summary (score, grade, stats, violations) */
    private val _summary = MutableStateFlow<DriverSummary?>(null)
    val summary: StateFlow<DriverSummary?> = _summary.asStateFlow()

    /** Violation breakdown items for RecyclerView */
    private val _violations = MutableStateFlow<List<ViolationItem>>(emptyList())
    val violations: StateFlow<List<ViolationItem>> = _violations.asStateFlow()

    /** Driving suggestions for RecyclerView */
    private val _suggestions = MutableStateFlow<List<DriverScoringEngine.DrivingSuggestion>>(
        emptyList()
    )
    val suggestions: StateFlow<List<DriverScoringEngine.DrivingSuggestion>> =
        _suggestions.asStateFlow()

    /** Whether a refresh is in progress (for SwipeRefreshLayout) */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  INITIALIZATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Initialize the ViewModel.
     * Resolves the driver's ID, starts real-time observation,
     * and triggers initial score calculation.
     *
     * Call from Fragment's `initViews()`.
     */
    fun initialize() {
        if (driverId.isNotBlank()) return // Already initialized

        viewModelScope.launch(Dispatchers.IO) {
            _screenState.value = ScreenState.Loading

            try {
                // ── 1. Get current user ─────────────────────────
                val userId = SessionManager.getInstance().getCachedUserId()
                if (userId.isNullOrBlank()) {
                    _screenState.value = ScreenState.Error("Not logged in. Please sign in again.")
                    return@launch
                }

                // ── 2. Look up driver record ────────────────────
                val driverResult = driverRepository.getDriverByUserId(userId)
                if (driverResult is ResultState.Success && driverResult.data != null) {
                    driverId = driverResult.data.driverId
                    companyId = driverResult.data.companyId
                } else {
                    _screenState.value = ScreenState.Error(
                        "Driver profile not found. Contact your manager."
                    )
                    return@launch
                }

                // ── 3. Start real-time observation ──────────────
                observeSummary()

                // ── 4. Trigger initial score calculation ────────
                val summary = scoringEngine.recalculateScore(driverId, companyId)
                if (summary != null) {
                    processSummary(summary)
                } else {
                    // Try reading existing
                    val existing = scoringEngine.getExistingSummary(driverId)
                    if (existing != null) {
                        processSummary(existing)
                    } else {
                        _screenState.value = ScreenState.Empty
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                _screenState.value = ScreenState.Error(
                    e.message ?: "Failed to load performance data"
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  REFRESH
    // ═══════════════════════════════════════════════════════════

    /**
     * Refresh the performance score.
     * Called from SwipeRefreshLayout or retry button.
     */
    fun refreshScore() {
        if (driverId.isBlank()) {
            initialize()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true

            try {
                val summary = scoringEngine.recalculateScore(driverId, companyId)
                if (summary != null) {
                    processSummary(summary)
                    emitSuccess("Score updated successfully")
                } else {
                    emitError("Failed to update score")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
                emitError("Failed to refresh: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  REAL-TIME OBSERVATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Observe real-time updates to the driver's summary document.
     * Any external change (e.g., from BehaviorAnalysisEngine
     * applying a penalty) will automatically update the UI.
     */
    private fun observeSummary() {
        viewModelScope.launch(Dispatchers.IO) {
            performanceRepository.observeDriverSummary(driverId).collect { result ->
                when (result) {
                    is ResultState.Success -> {
                        val data = result.data
                        if (data != null) {
                            processSummary(data)
                        }
                    }
                    is ResultState.Error -> {
                        Log.w(TAG, "Summary observation error: ${result.message}")
                    }
                    is ResultState.Loading -> { /* Ignore */ }
                    is ResultState.Idle -> { /* Ignore */ }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DATA PROCESSING
    // ═══════════════════════════════════════════════════════════

    /**
     * Process a [DriverSummary] into UI-ready state flows.
     */
    private fun processSummary(summary: DriverSummary) {
        _summary.value = summary

        // Build violation items
        _violations.value = buildViolationItems(summary)

        // Generate suggestions
        _suggestions.value = scoringEngine.generateSuggestions(summary)

        // Set screen state
        _screenState.value = if (summary.hasData) {
            ScreenState.Content
        } else {
            ScreenState.Empty
        }
    }

    /**
     * Build violation breakdown items from the summary.
     * Only includes types with count > 0.
     */
    private fun buildViolationItems(summary: DriverSummary): List<ViolationItem> {
        val items = mutableListOf<ViolationItem>()

        if (summary.overspeedCount > 0) {
            items.add(
                ViolationItem(
                    type = ViolationType.OVERSPEED,
                    name = "Overspeed",
                    count = summary.overspeedCount,
                    penaltyPerEvent = 5,
                    totalPenalty = summary.overspeedCount * 5
                )
            )
        }
        if (summary.harshBrakingCount > 0) {
            items.add(
                ViolationItem(
                    type = ViolationType.HARSH_BRAKING,
                    name = "Harsh Braking",
                    count = summary.harshBrakingCount,
                    penaltyPerEvent = 7,
                    totalPenalty = summary.harshBrakingCount * 7
                )
            )
        }
        if (summary.harshAccelCount > 0) {
            items.add(
                ViolationItem(
                    type = ViolationType.HARSH_ACCELERATION,
                    name = "Harsh Acceleration",
                    count = summary.harshAccelCount,
                    penaltyPerEvent = 4,
                    totalPenalty = summary.harshAccelCount * 4
                )
            )
        }
        if (summary.longIdleCount > 0) {
            items.add(
                ViolationItem(
                    type = ViolationType.LONG_IDLE,
                    name = "Long Idle",
                    count = summary.longIdleCount,
                    penaltyPerEvent = 3,
                    totalPenalty = summary.longIdleCount * 3
                )
            )
        }
        if (summary.routeDeviationCount > 0) {
            items.add(
                ViolationItem(
                    type = ViolationType.ROUTE_DEVIATION,
                    name = "Route Deviation",
                    count = summary.routeDeviationCount,
                    penaltyPerEvent = 4,
                    totalPenalty = summary.routeDeviationCount * 4
                )
            )
        }
        if (summary.accidentCount > 0) {
            items.add(
                ViolationItem(
                    type = ViolationType.ACCIDENT,
                    name = "Accident Suspected",
                    count = summary.accidentCount,
                    penaltyPerEvent = 20,
                    totalPenalty = summary.accidentCount * 20
                )
            )
        }

        // Sort by total penalty descending (worst offenders first)
        return items.sortedByDescending { it.totalPenalty }
    }

    // ═══════════════════════════════════════════════════════════
    //  CLEANUP
    // ═══════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        scoringEngine.clearAll()
    }

    // ═══════════════════════════════════════════════════════════
    //  UI DATA CLASSES
    // ═══════════════════════════════════════════════════════════

    /**
     * Represents the overall screen state.
     */
    sealed class ScreenState {
        data object Loading : ScreenState()
        data object Content : ScreenState()
        data object Empty : ScreenState()
        data class Error(val message: String) : ScreenState()
    }

    /**
     * A single violation type with its count and penalty.
     */
    data class ViolationItem(
        val type: ViolationType,
        val name: String,
        val count: Int,
        val penaltyPerEvent: Int,
        val totalPenalty: Int
    )

    /**
     * Enum mapping to violation types for icon/color resolution.
     */
    enum class ViolationType {
        OVERSPEED,
        HARSH_BRAKING,
        HARSH_ACCELERATION,
        LONG_IDLE,
        ROUTE_DEVIATION,
        ACCIDENT
    }
}
