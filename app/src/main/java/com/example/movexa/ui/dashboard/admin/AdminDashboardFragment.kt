package com.example.movexa.ui.dashboard.admin

import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.example.movexa.R
import com.example.movexa.data.model.ActivityLog
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.DashboardSummary
import com.example.movexa.data.model.ResultState
import com.example.movexa.databinding.FragmentAdminDashboardBinding
import com.example.movexa.theme.AppColors
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.ui.components.ActivityItemView
import com.example.movexa.ui.components.AlertItemView
import com.example.movexa.utils.TimeUtils
import java.text.NumberFormat
import java.util.Locale

/**
 * Admin Dashboard — the primary overview screen for fleet administrators.
 *
 * Displays real-time data from Firestore:
 * - Summary stat cards (vehicles, trips, drivers, revenue, alerts)
 * - Fleet status progress bars (active vs maintenance)
 * - Quick alerts panel (up to 5 most recent active alerts)
 * - Recent activity feed (up to 10 latest activity log entries)
 *
 * All data flows through [AdminDashboardViewModel] which holds snapshot
 * listeners. The fragment only observes StateFlows and renders UI — no
 * Firebase calls are made here.
 */
class AdminDashboardFragment : BaseFragment<FragmentAdminDashboardBinding>(
    FragmentAdminDashboardBinding::inflate
) {

    private val viewModel: AdminDashboardViewModel by viewModels()

    private val currencyFormatter: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    companion object {
        private const val MAX_ALERTS_SHOWN = 5
        private const val MAX_ACTIVITY_SHOWN = 10
    }

    // ═══════════════════════════════════════════════════════════
    //  Init Views
    // ═══════════════════════════════════════════════════════════

    override fun initViews() {
        setupStatCards()
        setupSwipeRefresh()
        viewModel.loadDashboard()
    }

    /**
     * Configure each stat card with its icon, label, and themed tint.
     * Values will be populated later when data arrives.
     */
    private fun setupStatCards() {
        with(binding) {
            statTotalVehicles.setData(
                iconRes = R.drawable.ic_dashboard_vehicle,
                label = getString(R.string.stat_total_vehicles),
                value = "—",
                iconTint = AppColors.PRIMARY
            )

            statActiveTrips.setData(
                iconRes = R.drawable.ic_trending_up,
                label = getString(R.string.stat_active_trips),
                value = "—",
                iconTint = AppColors.SECONDARY
            )

            statAvailableDrivers.setData(
                iconRes = R.drawable.ic_dashboard_driver,
                label = getString(R.string.stat_available_drivers),
                value = "—",
                iconTint = AppColors.SUCCESS
            )

            statTodayRevenue.setData(
                iconRes = R.drawable.ic_dashboard_revenue,
                label = getString(R.string.stat_today_revenue),
                value = "—",
                iconTint = AppColors.WARNING
            )

            statActiveAlerts.setData(
                iconRes = R.drawable.ic_warning,
                label = getString(R.string.stat_active_alerts),
                value = "—",
                iconTint = AppColors.ERROR
            )
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            AppColors.PRIMARY,
            AppColors.SECONDARY,
            AppColors.SUCCESS
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  Setup Listeners
    // ═══════════════════════════════════════════════════════════

    override fun setupListeners() {
        with(binding) {
            swipeRefresh.setOnRefreshListener {
                viewModel.refreshDashboard()
            }

            ivRefresh.setOnClickListener {
                viewModel.refreshDashboard()
            }

            btnRetry.setOnClickListener {
                viewModel.refreshDashboard()
            }

            tvViewAllAlerts.setOnClickListener {
                // Will navigate to full alerts screen in a future module
                showInfo(getString(R.string.feature_coming_soon))
            }

            tvViewAllActivity.setOnClickListener {
                // Will navigate to full activity log screen in a future module
                showInfo(getString(R.string.feature_coming_soon))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Observe Data
    // ═══════════════════════════════════════════════════════════

    override fun observeData() {
        collectLatestFlow(viewModel.dashboardSummary) { state ->
            handleDashboardSummary(state)
        }

        collectLatestFlow(viewModel.activeAlerts) { state ->
            handleActiveAlerts(state)
        }

        collectLatestFlow(viewModel.recentActivity) { state ->
            handleRecentActivity(state)
        }

        collectLatestFlow(viewModel.userName) { name ->
            binding.tvWelcome.text = getString(R.string.welcome_back, name)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  State Handlers
    // ═══════════════════════════════════════════════════════════

    private fun handleDashboardSummary(state: ResultState<DashboardSummary?>) {
        when (state) {
            is ResultState.Loading -> showLoadingState()
            is ResultState.Success -> {
                val summary = state.data
                if (summary != null) {
                    updateStatCards(summary)
                    updateFleetStatus(summary)
                    updateLastUpdated(summary.lastUpdated)
                    showContentState()
                } else {
                    showContentState()
                }
                binding.swipeRefresh.isRefreshing = false
            }
            is ResultState.Error -> {
                showErrorState(state.message)
                binding.swipeRefresh.isRefreshing = false
            }
            is ResultState.Idle -> { /* no-op */ }
        }
    }

    private fun handleActiveAlerts(state: ResultState<List<Alert>>) {
        when (state) {
            is ResultState.Success -> renderAlerts(state.data)
            is ResultState.Error -> {
                binding.alertsContainer.removeAllViews()
                binding.alertsEmptyState.isVisible = true
            }
            else -> { /* Loading/Idle handled by dashboard summary state */ }
        }
    }

    private fun handleRecentActivity(state: ResultState<List<ActivityLog>>) {
        when (state) {
            is ResultState.Success -> renderActivity(state.data)
            is ResultState.Error -> {
                binding.activityContainer.removeAllViews()
                binding.activityEmptyState.isVisible = true
            }
            else -> { /* Loading/Idle handled by dashboard summary state */ }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  UI Update Methods
    // ═══════════════════════════════════════════════════════════

    private fun updateStatCards(summary: DashboardSummary) {
        with(binding) {
            statTotalVehicles.setValue(summary.totalVehicles.toString())
            statTotalVehicles.setSubtitle(
                getString(R.string.stat_active_count, summary.activeVehicles)
            )

            statActiveTrips.setValue(summary.activeTrips.toString())
            statActiveTrips.setSubtitle(
                getString(R.string.stat_pending_count, summary.pendingTrips)
            )

            statAvailableDrivers.setValue(summary.availableDrivers.toString())
            statAvailableDrivers.setSubtitle(
                getString(R.string.stat_total_count, summary.totalDrivers)
            )

            statTodayRevenue.setValue(formatCurrency(summary.todayRevenue))
            statTodayRevenue.setSubtitle(
                getString(R.string.stat_month_revenue, formatCurrency(summary.monthRevenue))
            )

            statActiveAlerts.setValue(summary.activeAlerts.toString())
            if (summary.criticalAlerts > 0) {
                statActiveAlerts.setSubtitle(
                    getString(R.string.stat_critical_count, summary.criticalAlerts)
                )
                statActiveAlerts.setIconTint(AppColors.ERROR)
            } else {
                statActiveAlerts.setSubtitle(getString(R.string.stat_all_clear))
                statActiveAlerts.setIconTint(AppColors.SUCCESS)
            }
        }
    }

    private fun updateFleetStatus(summary: DashboardSummary) {
        with(binding) {
            val total = summary.totalVehicles.coerceAtLeast(1)
            val activePercent = (summary.activeVehicles * 100) / total
            val maintenancePercent = (summary.inMaintenanceVehicles * 100) / total

            tvFleetActiveCount.text = getString(
                R.string.fleet_ratio_format, summary.activeVehicles, total
            )
            progressFleetActive.progress = activePercent

            tvFleetMaintenanceCount.text = getString(
                R.string.fleet_ratio_format, summary.inMaintenanceVehicles, total
            )
            progressFleetMaintenance.progress = maintenancePercent
        }
    }

    private fun renderAlerts(alerts: List<Alert>) {
        with(binding) {
            alertsContainer.removeAllViews()

            val displayAlerts = alerts.take(MAX_ALERTS_SHOWN)

            if (displayAlerts.isEmpty()) {
                alertsEmptyState.isVisible = true
                alertsContainer.isVisible = false
            } else {
                alertsEmptyState.isVisible = false
                alertsContainer.isVisible = true

                for (alert in displayAlerts) {
                    val alertView = AlertItemView(requireContext())
                    alertView.bind(alert)
                    alertsContainer.addView(alertView)
                }
            }
        }
    }

    private fun renderActivity(logs: List<ActivityLog>) {
        with(binding) {
            activityContainer.removeAllViews()

            val displayLogs = logs.take(MAX_ACTIVITY_SHOWN)

            if (displayLogs.isEmpty()) {
                activityEmptyState.isVisible = true
                activityContainer.isVisible = false
            } else {
                activityEmptyState.isVisible = false
                activityContainer.isVisible = true

                for (log in displayLogs) {
                    val activityView = ActivityItemView(requireContext())
                    activityView.bind(log)
                    activityContainer.addView(activityView)
                }
            }
        }
    }

    private fun updateLastUpdated(timestamp: Long) {
        binding.tvLastUpdated.text = if (timestamp > 0) {
            TimeUtils.getLastUpdatedString(timestamp)
        } else {
            getString(R.string.dashboard_last_updated_never)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Visibility State Helpers
    // ═══════════════════════════════════════════════════════════

    private fun showLoadingState() {
        with(binding) {
            shimmerGroup.isVisible = true
            contentGroup.isVisible = false
            errorGroup.isVisible = false
        }
    }

    private fun showContentState() {
        with(binding) {
            shimmerGroup.isVisible = false
            contentGroup.isVisible = true
            errorGroup.isVisible = false
        }
    }

    private fun showErrorState(message: String) {
        with(binding) {
            shimmerGroup.isVisible = false
            contentGroup.isVisible = false
            errorGroup.isVisible = true
            tvErrorSubtitle.text = message
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Formatting Helpers
    // ═══════════════════════════════════════════════════════════

    private fun formatCurrency(amount: Double): String {
        return try {
            currencyFormatter.format(amount)
        } catch (e: Exception) {
            "₹${amount.toLong()}"
        }
    }
}
