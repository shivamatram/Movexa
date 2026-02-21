package com.example.movexa.ui.dashboard.admin

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.movexa.R
import com.example.movexa.databinding.FragmentAdminFinanceBinding
import com.example.movexa.service.AnalyticsEngine
import com.example.movexa.service.AnalyticsEngine.CostBreakdown
import com.example.movexa.service.AnalyticsEngine.DatePreset
import com.example.movexa.service.AnalyticsEngine.FinancialSnapshot
import com.example.movexa.service.AnalyticsEngine.FleetKPIs
import com.example.movexa.service.AnalyticsEngine.FuelTrendPoint
import com.example.movexa.service.AnalyticsEngine.MonthlyDataPoint
import com.example.movexa.ui.base.BaseFragment

/**
 * AdminFinanceFragment — Financial analytics dashboard for the Admin.
 *
 * ─── Screens Overview ───────────────────────────────────────────────────
 *  This fragment is the "Finance" tab of the admin dashboard. It presents:
 *
 *  1. **KPI Summary Cards** — Revenue, Expenses, Profit, Profit Margin
 *  2. **Cost Breakdown Row** — Fuel / Service / Repair quick-view cards
 *  3. **Trip Statistics** — Total trips, completed, distance, avg mileage
 *  4. **Cost Breakdown Pie Chart** — Interactive MPAndroidChart PieChart
 *  5. **Revenue vs Expense Trend** — Monthly LineChart
 *  6. **Fuel Cost Trend** — Monthly fuel cost + litres LineChart
 *  7. **Fleet KPIs** — Vehicle / driver counts and utilization
 *  8. **Navigation to Reports** — Opens AdminReportsFragment
 *
 * ─── Architecture ───────────────────────────────────────────────────────
 *  • Uses shared [AdminAnalyticsViewModel] (activityViewModels) so both
 *    Finance and Reports fragments share the same data cache.
 *  • All heavy computation happens in [AnalyticsEngine] on Dispatchers.IO.
 *  • Chart styling delegated to [ChartStyleHelper].
 *  • Date range filters via Material ChipGroup (7 presets).
 *  • Pull-to-refresh via SwipeRefreshLayout.
 *  • Loading / Error / Content state management.
 *
 * ─── Data Flows Observed ────────────────────────────────────────────────
 *  • [AdminAnalyticsViewModel.screenState] → show/hide loading/error/content
 *  • [AdminAnalyticsViewModel.financialSnapshot] → KPI cards + trip stats
 *  • [AdminAnalyticsViewModel.costBreakdown] → PieChart + legend
 *  • [AdminAnalyticsViewModel.monthlyTrend] → Revenue LineChart
 *  • [AdminAnalyticsViewModel.fuelTrend] → Fuel LineChart
 *  • [AdminAnalyticsViewModel.fleetKPIs] → Fleet KPI section
 *  • [AdminAnalyticsViewModel.selectedPreset] → chip selection sync
 */
class AdminFinanceFragment : BaseFragment<FragmentAdminFinanceBinding>(
    FragmentAdminFinanceBinding::inflate
) {

    // ─── ViewModel (shared with AdminReportsFragment) ───────────────────
    private val viewModel: AdminAnalyticsViewModel by activityViewModels()

    // ─── State Tracking ─────────────────────────────────────────────────
    private var isChartsInitialized = false

    // ═════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═════════════════════════════════════════════════════════════════════

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.initialize()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  INIT VIEWS
    // ═════════════════════════════════════════════════════════════════════

    override fun initViews() {
        // ── Chart initialization ────────────────────────────────────────
        initializeCharts()

        // ── Default chip selection ──────────────────────────────────────
        binding.chipThisMonth.isChecked = true
    }

    /**
     * Initialize all MPAndroidChart instances with proper styling.
     * Only runs once per fragment creation.
     */
    private fun initializeCharts() {
        if (isChartsInitialized) return
        val ctx = requireContext()

        // Pie chart — cost breakdown
        ChartStyleHelper.stylePieChart(binding.pieChartCost, ctx)

        // Line chart — revenue vs expenses
        ChartStyleHelper.styleLineChart(binding.lineChartRevenue, ctx)

        // Line chart — fuel trend
        ChartStyleHelper.styleLineChart(binding.lineChartFuel, ctx)

        isChartsInitialized = true
    }

    // ═════════════════════════════════════════════════════════════════════
    //  SETUP LISTENERS
    // ═════════════════════════════════════════════════════════════════════

    override fun setupListeners() {
        // ── Pull to Refresh ─────────────────────────────────────────────
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshAllData()
        }

        // ── Date Filter Chips ───────────────────────────────────────────
        binding.chipThisMonth.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.selectDatePreset(DatePreset.THIS_MONTH)
        }
        binding.chipLastMonth.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.selectDatePreset(DatePreset.LAST_MONTH)
        }
        binding.chipLast3Months.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.selectDatePreset(DatePreset.LAST_3_MONTHS)
        }
        binding.chipLast6Months.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.selectDatePreset(DatePreset.LAST_6_MONTHS)
        }
        binding.chipThisYear.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.selectDatePreset(DatePreset.THIS_YEAR)
        }
        binding.chipLastYear.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.selectDatePreset(DatePreset.LAST_YEAR)
        }
        binding.chipAllTime.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.selectDatePreset(DatePreset.ALL_TIME)
        }

        // ── View Reports Button ─────────────────────────────────────────
        binding.btnViewReports.setOnClickListener {
            findNavController().navigate(R.id.action_adminFinance_to_adminReports)
        }

        // ── Error Retry ─────────────────────────────────────────────────
        binding.btnRetry.setOnClickListener {
            viewModel.retry()
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

        // ── Financial Snapshot → KPI cards + trip stats ─────────────────
        collectLatestFlow(viewModel.financialSnapshot) { snapshot ->
            snapshot?.let { updateKpiCards(it) }
        }

        // ── Cost Breakdown → Pie chart ──────────────────────────────────
        collectLatestFlow(viewModel.costBreakdown) { breakdown ->
            breakdown?.let { updateCostBreakdown(it) }
        }

        // ── Monthly Trend → Revenue line chart ──────────────────────────
        collectLatestFlow(viewModel.monthlyTrend) { trend ->
            if (trend.isNotEmpty()) updateRevenueTrend(trend)
        }

        // ── Fuel Trend → Fuel line chart ────────────────────────────────
        collectLatestFlow(viewModel.fuelTrend) { trend ->
            if (trend.isNotEmpty()) updateFuelTrend(trend)
        }

        // ── Fleet KPIs ──────────────────────────────────────────────────
        collectLatestFlow(viewModel.fleetKPIs) { kpis ->
            kpis?.let { updateFleetKpis(it) }
        }

        // ── Selected Preset → sync chip ─────────────────────────────────
        collectLatestFlow(viewModel.selectedPreset) { preset ->
            syncChipSelection(preset)
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
     * Toggle visibility of loading, error, and content states.
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
                // Initial state — show loading
                binding.layoutLoading.isVisible = true
                binding.layoutError.isVisible = false
                binding.swipeRefresh.isVisible = false
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  KPI CARDS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Populate the 4 main KPI cards and the 3 cost breakdown mini-cards.
     * Also populates the trip statistics section.
     */
    private fun updateKpiCards(snapshot: FinancialSnapshot) {
        // ── Main KPI row 1 ──────────────────────────────────────────────
        binding.tvKpiRevenue.text = snapshot.revenueDisplay
        binding.tvKpiExpenses.text = snapshot.totalExpensesDisplay

        // ── Main KPI row 2 ──────────────────────────────────────────────
        binding.tvKpiProfit.text = snapshot.profitDisplay
        binding.tvKpiProfitMargin.text = snapshot.profitMarginDisplay

        // Profit card color
        val profitBgColor = if (snapshot.isProfitable)
            R.color.analytics_profit_positive_bg
        else
            R.color.analytics_profit_negative_bg
        binding.cardKpiProfit.setCardBackgroundColor(
            requireContext().getColor(profitBgColor)
        )

        // Profit icon tint
        val profitIconTint = if (snapshot.isProfitable)
            R.color.analytics_revenue else R.color.analytics_expense
        binding.ivProfitIcon.setColorFilter(requireContext().getColor(profitIconTint))

        // ── Cost breakdown mini-cards ───────────────────────────────────
        binding.tvKpiFuelCost.text = snapshot.fuelCostDisplay
        binding.tvKpiServiceCost.text = snapshot.serviceCostDisplay
        binding.tvKpiRepairCost.text = snapshot.repairCostDisplay

        // ── Trip statistics ─────────────────────────────────────────────
        binding.tvStatTotalTrips.text = snapshot.totalTrips.toString()
        binding.tvStatCompletedTrips.text = snapshot.completedTrips.toString()
        binding.tvStatTotalDistance.text = formatShortDistance(snapshot.totalDistanceKm)
        binding.tvStatAvgMileage.text = String.format("%.1f", snapshot.avgMileageKmPerL)

        // ── Fuel stats under fuel chart ─────────────────────────────────
        binding.tvFuelTotalLitres.text = String.format("%.0f L", snapshot.totalFuelLitres)
        binding.tvFuelAvgMileage.text = String.format("%.1f km/L", snapshot.avgMileageKmPerL)
        binding.tvFuelLogCount.text = snapshot.totalFuelLogs.toString()

        // ── Empty state ─────────────────────────────────────────────────
        val hasData = snapshot.totalTrips > 0 || snapshot.totalFuelLogs > 0
        binding.layoutEmpty.isVisible = !hasData
        binding.cardCostBreakdown.isVisible = hasData
        binding.cardRevenueTrend.isVisible = hasData
        binding.cardFuelTrend.isVisible = hasData
    }

    /**
     * Format distance for compact display: "3.2K" for 3200, "456" for 456.
     */
    private fun formatShortDistance(km: Double): String {
        return when {
            km >= 100_000 -> String.format("%.0fK", km / 1000)
            km >= 1_000 -> String.format("%.1fK", km / 1000)
            else -> String.format("%.0f", km)
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  COST BREAKDOWN PIE CHART
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Update the cost breakdown pie chart and legend labels.
     */
    private fun updateCostBreakdown(breakdown: CostBreakdown) {
        if (!breakdown.hasData) {
            binding.cardCostBreakdown.isVisible = false
            return
        }
        binding.cardCostBreakdown.isVisible = true

        // Set chart data
        ChartStyleHelper.setCostBreakdownData(
            binding.pieChartCost,
            requireContext(),
            breakdown.fuelCost.toFloat(),
            breakdown.serviceCost.toFloat(),
            breakdown.repairCost.toFloat(),
            breakdown.totalDisplay
        )

        // Update legend text
        binding.tvLegendFuel.text = String.format(
            "Fuel %s", AnalyticsEngine.formatPercent(breakdown.fuelPercent)
        )
        binding.tvLegendService.text = String.format(
            "Service %s", AnalyticsEngine.formatPercent(breakdown.servicePercent)
        )
        binding.tvLegendRepair.text = String.format(
            "Repair %s", AnalyticsEngine.formatPercent(breakdown.repairPercent)
        )
    }

    // ═════════════════════════════════════════════════════════════════════
    //  REVENUE TREND LINE CHART
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Update the revenue vs expense line chart with monthly data points.
     */
    private fun updateRevenueTrend(trend: List<MonthlyDataPoint>) {
        if (trend.isEmpty()) {
            binding.cardRevenueTrend.isVisible = false
            return
        }
        binding.cardRevenueTrend.isVisible = true

        val revenueLabels = trend.map { it.label }
        val revenueValues = trend.map { it.revenue.toFloat() }
        val expenseValues = trend.map { it.totalExpense.toFloat() }
        ChartStyleHelper.setRevenueTrendData(
            binding.lineChartRevenue,
            requireContext(),
            revenueLabels,
            revenueValues,
            expenseValues
        )
    }

    // ═════════════════════════════════════════════════════════════════════
    //  FUEL TREND LINE CHART
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Update the fuel cost trend line chart.
     */
    private fun updateFuelTrend(trend: List<FuelTrendPoint>) {
        if (trend.isEmpty()) {
            binding.cardFuelTrend.isVisible = false
            return
        }
        binding.cardFuelTrend.isVisible = true

        val fuelLabels = trend.map { it.label }
        val costValues = trend.map { it.totalCost.toFloat() }
        val litreValues = trend.map { it.totalLitres.toFloat() }
        ChartStyleHelper.setFuelTrendData(
            binding.lineChartFuel,
            requireContext(),
            fuelLabels,
            costValues,
            litreValues
        )
    }

    // ═════════════════════════════════════════════════════════════════════
    //  FLEET KPIs
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Populate the fleet KPI section with vehicle/driver counts.
     */
    private fun updateFleetKpis(kpis: FleetKPIs) {
        binding.tvFleetTotalVehicles.text = kpis.totalVehicles.toString()
        binding.tvFleetAvailable.text = kpis.availableVehicles.toString()
        binding.tvFleetOnTrip.text = kpis.onTripVehicles.toString()
        binding.tvFleetInService.text = kpis.inServiceVehicles.toString()
        binding.tvFleetTotalDrivers.text = kpis.totalDrivers.toString()
        binding.tvFleetActiveDrivers.text = kpis.activeDrivers.toString()
        binding.tvFleetUtilization.text = kpis.utilizationDisplay
        binding.tvFleetActiveTrips.text = kpis.activeTrips.toString()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  CHIP SYNC
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Sync chip selection without triggering listener callbacks.
     */
    private fun syncChipSelection(preset: DatePreset) {
        val chipId = when (preset) {
            DatePreset.THIS_MONTH -> R.id.chipThisMonth
            DatePreset.LAST_MONTH -> R.id.chipLastMonth
            DatePreset.LAST_3_MONTHS -> R.id.chipLast3Months
            DatePreset.LAST_6_MONTHS -> R.id.chipLast6Months
            DatePreset.THIS_YEAR -> R.id.chipThisYear
            DatePreset.LAST_YEAR -> R.id.chipLastYear
            DatePreset.ALL_TIME -> R.id.chipAllTime
        }
        val chip = binding.chipGroupDate.findViewById<com.google.android.material.chip.Chip>(chipId)
        if (chip != null && !chip.isChecked) {
            chip.isChecked = true
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  CLEANUP
    // ═════════════════════════════════════════════════════════════════════

    override fun onDestroyView() {
        isChartsInitialized = false
        super.onDestroyView()
    }
}
