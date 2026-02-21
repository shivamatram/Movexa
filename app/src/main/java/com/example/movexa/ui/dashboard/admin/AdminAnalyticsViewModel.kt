package com.example.movexa.ui.dashboard.admin

import android.util.Log
import com.example.movexa.data.session.SessionManager
import com.example.movexa.service.AnalyticsEngine
import com.example.movexa.service.AnalyticsEngine.CostBreakdown
import com.example.movexa.service.AnalyticsEngine.DatePreset
import com.example.movexa.service.AnalyticsEngine.DriverRanking
import com.example.movexa.service.AnalyticsEngine.FinancialSnapshot
import com.example.movexa.service.AnalyticsEngine.FleetKPIs
import com.example.movexa.service.AnalyticsEngine.FuelTrendPoint
import com.example.movexa.service.AnalyticsEngine.FullAnalyticsReport
import com.example.movexa.service.AnalyticsEngine.MonthlyDataPoint
import com.example.movexa.service.AnalyticsEngine.VehicleCostEntry
import com.example.movexa.service.AnalyticsEngine.VehicleUtilization
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AdminAnalyticsViewModel — shared ViewModel for AdminFinanceFragment and AdminReportsFragment.
 *
 * ─── Responsibilities ────────────────────────────────────────────────────────
 *  • Delegates computation to [AnalyticsEngine] (all IO-bound).
 *  • Exposes [StateFlow]s for each data slice, so fragments observe only what they need.
 *  • Manages date range selection and filter state.
 *  • Caches computed results; re-fetches only when filters change.
 *  • Provides refresh capability for pull-to-refresh.
 *
 * ─── Fragment Usage ──────────────────────────────────────────────────────────
 *  AdminFinanceFragment: observes [financialSnapshot], [costBreakdown], [monthlyTrend],
 *      [fuelTrend], [fleetKPIs].
 *  AdminReportsFragment: observes [vehicleUtilization], [driverRankings], [vehicleCosts],
 *      [fullReport].
 */
class AdminAnalyticsViewModel : BaseViewModel() {

    companion object {
        private const val TAG = "AdminAnalyticsVM"
    }

    // ─── Engine ──────────────────────────────────────────────────────────
    private val engine = AnalyticsEngine()

    // ─── State ───────────────────────────────────────────────────────────
    private var companyId: String = ""
    private var isInitialized = false

    // Screen state
    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Idle)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    // Date range
    private val _selectedPreset = MutableStateFlow(DatePreset.THIS_MONTH)
    val selectedPreset: StateFlow<DatePreset> = _selectedPreset.asStateFlow()

    private val _dateRange = MutableStateFlow<Pair<Long, Long>>(0L to 0L)
    val dateRange: StateFlow<Pair<Long, Long>> = _dateRange.asStateFlow()

    // ── Finance Fragment data ────────────────────────────────────────────
    private val _financialSnapshot = MutableStateFlow<FinancialSnapshot?>(null)
    val financialSnapshot: StateFlow<FinancialSnapshot?> = _financialSnapshot.asStateFlow()

    private val _costBreakdown = MutableStateFlow<CostBreakdown?>(null)
    val costBreakdown: StateFlow<CostBreakdown?> = _costBreakdown.asStateFlow()

    private val _monthlyTrend = MutableStateFlow<List<MonthlyDataPoint>>(emptyList())
    val monthlyTrend: StateFlow<List<MonthlyDataPoint>> = _monthlyTrend.asStateFlow()

    private val _fuelTrend = MutableStateFlow<List<FuelTrendPoint>>(emptyList())
    val fuelTrend: StateFlow<List<FuelTrendPoint>> = _fuelTrend.asStateFlow()

    private val _fleetKPIs = MutableStateFlow<FleetKPIs?>(null)
    val fleetKPIs: StateFlow<FleetKPIs?> = _fleetKPIs.asStateFlow()

    // ── Reports Fragment data ────────────────────────────────────────────
    private val _vehicleUtilization = MutableStateFlow<List<VehicleUtilization>>(emptyList())
    val vehicleUtilization: StateFlow<List<VehicleUtilization>> = _vehicleUtilization.asStateFlow()

    private val _driverRankings = MutableStateFlow<List<DriverRanking>>(emptyList())
    val driverRankings: StateFlow<List<DriverRanking>> = _driverRankings.asStateFlow()

    private val _vehicleCosts = MutableStateFlow<List<VehicleCostEntry>>(emptyList())
    val vehicleCosts: StateFlow<List<VehicleCostEntry>> = _vehicleCosts.asStateFlow()

    private val _fullReport = MutableStateFlow<FullAnalyticsReport?>(null)
    val fullReport: StateFlow<FullAnalyticsReport?> = _fullReport.asStateFlow()

    // ═════════════════════════════════════════════════════════════════════
    //  INITIALIZATION
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Initialize the ViewModel — should be called once from the fragment.
     * Uses the admin's userId as companyId (same pattern as other admin ViewModels).
     */
    fun initialize() {
        if (isInitialized && _screenState.value is ScreenState.Ready) return

        launchWithLoading(Dispatchers.IO) {
            _screenState.value = ScreenState.Loading

            val cachedUserId = SessionManager.getInstance().getCachedUserId()
            if (cachedUserId.isNullOrBlank()) {
                _screenState.value = ScreenState.Error("Not logged in")
                emitError("Please sign in again.")
                return@launchWithLoading
            }
            companyId = cachedUserId

            // Set default date range
            val range = engine.resolveDateRange(DatePreset.THIS_MONTH)
            _dateRange.value = range
            _selectedPreset.value = DatePreset.THIS_MONTH

            // Load finance data
            loadFinanceData(range.first, range.second)

            isInitialized = true
            _screenState.value = ScreenState.Ready
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DATE RANGE SELECTION
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Called when the user selects a date preset from the filter chip group.
     */
    fun selectDatePreset(preset: DatePreset) {
        if (preset == _selectedPreset.value && _financialSnapshot.value != null) return

        _selectedPreset.value = preset
        val range = engine.resolveDateRange(preset)
        _dateRange.value = range

        refreshAllData()
    }

    /**
     * Called when the user picks a custom date range.
     */
    fun setCustomDateRange(startMs: Long, endMs: Long) {
        _dateRange.value = startMs to endMs
        refreshAllData()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DATA LOADING — FINANCE
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Load all financial data for the given date range.
     */
    private suspend fun loadFinanceData(startMs: Long, endMs: Long) {
        try {
            Log.d(TAG, "loadFinanceData range=$startMs..$endMs")

            val snapshot = engine.computeFinancialSnapshot(companyId, startMs, endMs)
            _financialSnapshot.value = snapshot
            Log.d(TAG, "Financial snapshot: revenue=${snapshot.revenueDisplay}, profit=${snapshot.profitDisplay}")

            val breakdown = engine.computeCostBreakdown(companyId, startMs, endMs)
            _costBreakdown.value = breakdown

            val trend = engine.computeMonthlyTrend(companyId, 6)
            _monthlyTrend.value = trend

            val fuelT = engine.computeFuelTrend(companyId, 6)
            _fuelTrend.value = fuelT

            val kpis = engine.computeFleetKPIs(companyId)
            _fleetKPIs.value = kpis

        } catch (e: Exception) {
            Log.e(TAG, "Error loading finance data", e)
            emitError("Failed to load financial data: ${e.message}")
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DATA LOADING — REPORTS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Load all reports data. Called when AdminReportsFragment is shown.
     */
    fun loadReportsData() {
        val range = _dateRange.value
        if (range.first == 0L) return

        launchSafe(Dispatchers.IO) {
            try {
                Log.d(TAG, "loadReportsData")

                val utilization = engine.computeVehicleUtilization(companyId, range.first, range.second)
                _vehicleUtilization.value = utilization

                val rankings = engine.computeDriverRankings(companyId)
                _driverRankings.value = rankings

                val costs = engine.computeVehicleCostRanking(companyId, range.first, range.second)
                _vehicleCosts.value = costs

            } catch (e: Exception) {
                Log.e(TAG, "Error loading reports data", e)
                emitError("Failed to load reports: ${e.message}")
            }
        }
    }

    /**
     * Generate the full analytics report (combines all data).
     */
    fun generateFullReport() {
        val range = _dateRange.value
        if (range.first == 0L) return

        launchWithLoading(Dispatchers.IO) {
            try {
                val report = engine.generateFullReport(companyId, range.first, range.second)
                _fullReport.value = report
                emitSuccess("Report generated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error generating report", e)
                emitError("Failed to generate report: ${e.message}")
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  REFRESH
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Pull-to-refresh — reload everything with current filters.
     */
    fun refreshAllData() {
        val range = _dateRange.value
        if (companyId.isBlank()) return

        launchWithLoading(Dispatchers.IO) {
            try {
                _screenState.value = ScreenState.Loading
                loadFinanceData(range.first, range.second)

                // Also reload reports if they've been loaded before
                if (_vehicleUtilization.value.isNotEmpty() || _driverRankings.value.isNotEmpty()) {
                    val utilization = engine.computeVehicleUtilization(companyId, range.first, range.second)
                    _vehicleUtilization.value = utilization

                    val rankings = engine.computeDriverRankings(companyId)
                    _driverRankings.value = rankings

                    val costs = engine.computeVehicleCostRanking(companyId, range.first, range.second)
                    _vehicleCosts.value = costs
                }

                _screenState.value = ScreenState.Ready
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing data", e)
                _screenState.value = ScreenState.Error(e.message ?: "Refresh failed")
                emitError("Failed to refresh: ${e.message}")
            }
        }
    }

    /**
     * Retry after an error state.
     */
    fun retry() {
        _screenState.value = ScreenState.Idle
        isInitialized = false
        initialize()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  CONVENIENCE GETTERS
    // ═════════════════════════════════════════════════════════════════════

    fun getDatePresets(): List<DatePreset> = DatePreset.entries

    fun getEngine(): AnalyticsEngine = engine

    // ═════════════════════════════════════════════════════════════════════
    //  SCREEN STATE
    // ═════════════════════════════════════════════════════════════════════

    sealed class ScreenState {
        data object Idle : ScreenState()
        data object Loading : ScreenState()
        data object Ready : ScreenState()
        data class Error(val message: String) : ScreenState()
    }
}
