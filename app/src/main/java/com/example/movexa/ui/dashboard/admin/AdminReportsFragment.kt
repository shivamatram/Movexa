package com.example.movexa.ui.dashboard.admin

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movexa.R
import com.example.movexa.databinding.FragmentAdminReportsBinding
import com.example.movexa.service.AnalyticsEngine.DriverRanking
import com.example.movexa.service.AnalyticsEngine.VehicleCostEntry
import com.example.movexa.service.AnalyticsEngine.VehicleUtilization
import com.example.movexa.ui.base.BaseFragment

/**
 * AdminReportsFragment — Reports & Insights screen for the Admin.
 *
 * ─── Overview ───────────────────────────────────────────────────────────
 *  This fragment is the "Reports" sub-screen navigated from AdminFinanceFragment.
 *  It provides fleet insights that complement the financial overview:
 *
 *  1. **Vehicle Utilization Chart** — HorizontalBarChart showing each vehicle's
 *     utilization percentage (active trip time / total available time).
 *  2. **Driver Performance Ranking** — BarChart + RecyclerView list of top drivers
 *     ranked by their performance score from DriverSummary.
 *  3. **Vehicle Cost Ranking** — RecyclerView table showing per-vehicle costs
 *     (fuel + service + repair) sorted by total cost descending.
 *
 * ─── Architecture ───────────────────────────────────────────────────────
 *  • Shares [AdminAnalyticsViewModel] with AdminFinanceFragment via
 *    [activityViewModels] to avoid redundant data fetching.
 *  • Triggers [AdminAnalyticsViewModel.loadReportsData] on first appearance.
 *  • Chart rendering delegated to [ChartStyleHelper].
 *  • RecyclerView adapters: [DriverRankingAdapter], [VehicleCostAdapter].
 *  • Pull-to-refresh re-fetches reports data from engine.
 *
 * ─── Data Flows ─────────────────────────────────────────────────────────
 *  • [AdminAnalyticsViewModel.vehicleUtilization] → HorizontalBarChart
 *  • [AdminAnalyticsViewModel.driverRankings] → BarChart + DriverRankingAdapter
 *  • [AdminAnalyticsViewModel.vehicleCosts] → VehicleCostAdapter
 *  • [AdminAnalyticsViewModel.screenState] → loading/error/content toggles
 */
class AdminReportsFragment : BaseFragment<FragmentAdminReportsBinding>(
    FragmentAdminReportsBinding::inflate
) {

    // ─── ViewModel (shared with AdminFinanceFragment) ───────────────────
    private val viewModel: AdminAnalyticsViewModel by activityViewModels()

    // ─── Adapters ───────────────────────────────────────────────────────
    private lateinit var driverRankingAdapter: DriverRankingAdapter
    private lateinit var vehicleCostAdapter: VehicleCostAdapter

    // ─── State ──────────────────────────────────────────────────────────
    private var isChartsInitialized = false
    private var isDataLoaded = false

    // ═════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═════════════════════════════════════════════════════════════════════

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load reports data if not already loaded
        if (!isDataLoaded) {
            viewModel.loadReportsData()
            isDataLoaded = true
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  INIT VIEWS
    // ═════════════════════════════════════════════════════════════════════

    override fun initViews() {
        // ── Initialize charts ───────────────────────────────────────────
        initializeCharts()

        // ── Initialize RecyclerViews ────────────────────────────────────
        initRecyclerViews()
    }

    /**
     * Initialize all chart instances with styling.
     */
    private fun initializeCharts() {
        if (isChartsInitialized) return
        val ctx = requireContext()

        // Horizontal bar chart — vehicle utilization
        ChartStyleHelper.styleHorizontalBarChart(binding.barChartUtilization, ctx)

        // Bar chart — driver performance scores
        ChartStyleHelper.styleBarChart(binding.barChartDrivers, ctx)

        isChartsInitialized = true
    }

    /**
     * Set up RecyclerViews for driver rankings and vehicle costs.
     */
    private fun initRecyclerViews() {
        // Driver rankings RV
        driverRankingAdapter = DriverRankingAdapter()
        binding.rvDriverRankings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = driverRankingAdapter
            isNestedScrollingEnabled = false
        }

        // Vehicle costs RV
        vehicleCostAdapter = VehicleCostAdapter()
        binding.rvVehicleCosts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = vehicleCostAdapter
            isNestedScrollingEnabled = false
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  SETUP LISTENERS
    // ═════════════════════════════════════════════════════════════════════

    override fun setupListeners() {
        // ── Back button ─────────────────────────────────────────────────
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // ── Pull to Refresh ─────────────────────────────────────────────
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshAllData()
        }

        // ── Error Retry ─────────────────────────────────────────────────
        binding.btnRetry.setOnClickListener {
            viewModel.loadReportsData()
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  OBSERVE DATA
    // ═════════════════════════════════════════════════════════════════════

    override fun observeData() {
        // ── Screen State ────────────────────────────────────────────────
        collectLatestFlow(viewModel.screenState) { state ->
            handleScreenState(state)
        }

        // ── Vehicle Utilization → Horizontal bar chart ──────────────────
        collectLatestFlow(viewModel.vehicleUtilization) { utilization ->
            updateVehicleUtilization(utilization)
        }

        // ── Driver Rankings → Bar chart + list ──────────────────────────
        collectLatestFlow(viewModel.driverRankings) { rankings ->
            updateDriverRankings(rankings)
        }

        // ── Vehicle Costs → Cost ranking list ───────────────────────────
        collectLatestFlow(viewModel.vehicleCosts) { costs ->
            updateVehicleCosts(costs)
        }

        // ── Error events ────────────────────────────────────────────────
        collectFlow(viewModel.errorEvent) { msg ->
            showError(msg)
        }

        // ── Success events ──────────────────────────────────────────────
        collectFlow(viewModel.successEvent) { msg ->
            showSuccess(msg)
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  STATE MANAGEMENT
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Toggle loading / error / content state.
     */
    private fun handleScreenState(state: AdminAnalyticsViewModel.ScreenState) {
        binding.swipeRefresh.isRefreshing = false

        when (state) {
            is AdminAnalyticsViewModel.ScreenState.Loading -> {
                binding.layoutLoading.isVisible = true
                binding.layoutError.isVisible = false
                binding.swipeRefresh.isVisible = false
            }
            is AdminAnalyticsViewModel.ScreenState.Ready -> {
                binding.layoutLoading.isVisible = false
                binding.layoutError.isVisible = false
                binding.swipeRefresh.isVisible = true
            }
            is AdminAnalyticsViewModel.ScreenState.Error -> {
                binding.layoutLoading.isVisible = false
                binding.layoutError.isVisible = true
                binding.swipeRefresh.isVisible = false
                binding.tvErrorMessage.text = state.message
            }
            is AdminAnalyticsViewModel.ScreenState.Idle -> {
                binding.layoutLoading.isVisible = true
                binding.layoutError.isVisible = false
                binding.swipeRefresh.isVisible = false
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  VEHICLE UTILIZATION
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Update the vehicle utilization horizontal bar chart.
     */
    private fun updateVehicleUtilization(utilization: List<VehicleUtilization>) {
        val hasData = utilization.isNotEmpty()

        binding.barChartUtilization.isVisible = hasData
        binding.tvUtilizationEmpty.isVisible = !hasData

        if (hasData) {
            val utilLabels = utilization.map { it.vehicleNumber }
            val utilValues = utilization.map { (it.utilization * 100).toFloat() }
            ChartStyleHelper.setUtilizationData(
                binding.barChartUtilization,
                requireContext(),
                utilLabels,
                utilValues
            )
        }

        // Show/hide the entire card only if all sections are empty
        updateGlobalEmptyState()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DRIVER RANKINGS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Update the driver ranking bar chart and RecyclerView list.
     */
    private fun updateDriverRankings(rankings: List<DriverRanking>) {
        val hasData = rankings.isNotEmpty()

        binding.barChartDrivers.isVisible = hasData
        binding.rvDriverRankings.isVisible = hasData
        binding.tvRankingsEmpty.isVisible = !hasData

        if (hasData) {
            // Bar chart with top 10 scores
            val driverNames = rankings.map { it.driverName }
            val driverScores = rankings.map { it.score.toFloat() }
            ChartStyleHelper.setDriverScoreData(
                binding.barChartDrivers,
                requireContext(),
                driverNames,
                driverScores
            )

            // RecyclerView list
            driverRankingAdapter.submitList(rankings)
        }

        updateGlobalEmptyState()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  VEHICLE COSTS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Update the vehicle cost ranking RecyclerView.
     */
    private fun updateVehicleCosts(costs: List<VehicleCostEntry>) {
        val hasData = costs.isNotEmpty()

        binding.rvVehicleCosts.isVisible = hasData
        binding.tvCostsEmpty.isVisible = !hasData

        if (hasData) {
            vehicleCostAdapter.submitList(costs)
        }

        updateGlobalEmptyState()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  GLOBAL EMPTY STATE
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Show the global empty state only when ALL sections have no data.
     */
    private fun updateGlobalEmptyState() {
        val allEmpty = viewModel.vehicleUtilization.value.isEmpty()
                && viewModel.driverRankings.value.isEmpty()
                && viewModel.vehicleCosts.value.isEmpty()

        binding.layoutEmpty.isVisible = allEmpty

        // Hide section cards when all empty
        binding.cardUtilization.isVisible = !allEmpty || viewModel.vehicleUtilization.value.isNotEmpty()
        binding.cardDriverRanking.isVisible = !allEmpty || viewModel.driverRankings.value.isNotEmpty()
        binding.cardVehicleCosts.isVisible = !allEmpty || viewModel.vehicleCosts.value.isNotEmpty()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  CLEANUP
    // ═════════════════════════════════════════════════════════════════════

    override fun onDestroyView() {
        isChartsInitialized = false
        super.onDestroyView()
    }
}
