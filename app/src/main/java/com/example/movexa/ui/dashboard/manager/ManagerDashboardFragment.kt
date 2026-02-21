package com.example.movexa.ui.dashboard.manager

import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.example.movexa.R
import com.example.movexa.data.model.ActivityLog
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.OperationsSummary
import com.example.movexa.data.model.ResultState
import com.example.movexa.databinding.FragmentManagerDashboardBinding
import com.example.movexa.theme.AppColors
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.ui.components.ActivityItemView
import com.example.movexa.ui.components.AlertItemView
import com.example.movexa.ui.components.PendingTaskItemView
import com.example.movexa.utils.TimeUtils

/**
 * Manager Dashboard — the primary overview screen for fleet managers.
 *
 * Displays real-time operational data from Firestore:
 * - Operations stat cards (assigned vehicles, active trips, drivers, efficiency)
 * - Pending actions panel (fuel approvals, leave requests, maintenance)
 * - Live activity feed (recent activity log entries)
 * - Quick alerts panel (active alerts requiring attention)
 *
 * All data flows through [ManagerDashboardViewModel] which holds Firestore
 * snapshot listeners. The fragment only observes StateFlows and renders UI —
 * no Firebase calls are made here.
 */
class ManagerDashboardFragment : BaseFragment<FragmentManagerDashboardBinding>(
    FragmentManagerDashboardBinding::inflate
) {

    private val viewModel: ManagerDashboardViewModel by viewModels()

    // Pending task views (created once, updated dynamically)
    private lateinit var taskFuelApprovals: PendingTaskItemView
    private lateinit var taskLeaveRequests: PendingTaskItemView
    private lateinit var taskMaintenance: PendingTaskItemView
    private lateinit var taskGeneral: PendingTaskItemView

    companion object {
        private const val MAX_ALERTS_SHOWN = 5
        private const val MAX_ACTIVITY_SHOWN = 10
    }

    // ═══════════════════════════════════════════════════════════
    //  Init Views
    // ═══════════════════════════════════════════════════════════

    override fun initViews() {
        setupStatCards()
        setupPendingTasks()
        setupSwipeRefresh()
        viewModel.loadDashboard()
    }

    /**
     * Configure each stat card with its icon, label, and themed tint.
     */
    private fun setupStatCards() {
        with(binding) {
            statAssignedVehicles.setData(
                iconRes = R.drawable.ic_dashboard_vehicle,
                label = getString(R.string.stat_assigned_vehicles),
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

            statCompletedTrips.setData(
                iconRes = R.drawable.ic_check_circle,
                label = getString(R.string.stat_completed_trips),
                value = "—",
                iconTint = AppColors.INFO
            )

            statTeamEfficiency.setData(
                iconRes = R.drawable.ic_speed,
                label = getString(R.string.stat_team_efficiency),
                value = "—",
                iconTint = AppColors.WARNING
            )

            statOnTimeDelivery.setData(
                iconRes = R.drawable.ic_schedule,
                label = getString(R.string.stat_on_time_delivery),
                value = "—",
                iconTint = AppColors.PRIMARY
            )
        }
    }

    /**
     * Create and configure pending task item views in the container.
     */
    private fun setupPendingTasks() {
        val container = binding.pendingTasksContainer

        taskFuelApprovals = PendingTaskItemView(requireContext())
        taskFuelApprovals.setData(
            iconRes = R.drawable.ic_dashboard_revenue,
            title = getString(R.string.pending_fuel_approvals),
            count = 0
        )
        container.addView(taskFuelApprovals)

        taskLeaveRequests = PendingTaskItemView(requireContext())
        taskLeaveRequests.setData(
            iconRes = R.drawable.ic_assignment,
            title = getString(R.string.pending_leave_requests),
            count = 0
        )
        container.addView(taskLeaveRequests)

        taskMaintenance = PendingTaskItemView(requireContext())
        taskMaintenance.setData(
            iconRes = R.drawable.ic_build,
            title = getString(R.string.pending_maintenance),
            count = 0
        )
        container.addView(taskMaintenance)

        taskGeneral = PendingTaskItemView(requireContext())
        taskGeneral.setData(
            iconRes = R.drawable.ic_pending,
            title = getString(R.string.pending_general),
            count = 0
        )
        container.addView(taskGeneral)
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

            tvViewAllActivity.setOnClickListener {
                showInfo(getString(R.string.feature_coming_soon))
            }

            tvViewAllAlerts.setOnClickListener {
                showInfo(getString(R.string.feature_coming_soon))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Observe Data
    // ═══════════════════════════════════════════════════════════

    override fun observeData() {
        collectLatestFlow(viewModel.operationsSummary) { state ->
            handleOperationsSummary(state)
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

    private fun handleOperationsSummary(state: ResultState<OperationsSummary?>) {
        when (state) {
            is ResultState.Loading -> showLoadingState()
            is ResultState.Success -> {
                val summary = state.data
                if (summary != null) {
                    updateStatCards(summary)
                    updatePendingActions(summary)
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
            else -> { /* Loading/Idle handled by operations summary state */ }
        }
    }

    private fun handleRecentActivity(state: ResultState<List<ActivityLog>>) {
        when (state) {
            is ResultState.Success -> renderActivity(state.data)
            is ResultState.Error -> {
                binding.activityContainer.removeAllViews()
                binding.activityEmptyState.isVisible = true
            }
            else -> { /* Loading/Idle handled by operations summary state */ }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  UI Update Methods
    // ═══════════════════════════════════════════════════════════

    private fun updateStatCards(summary: OperationsSummary) {
        with(binding) {
            statAssignedVehicles.setValue(summary.assignedVehicles.toString())
            statAssignedVehicles.setSubtitle(
                getString(R.string.stat_active_count, summary.activeVehicles)
            )

            statActiveTrips.setValue(summary.activeTrips.toString())
            statActiveTrips.setSubtitle(
                getString(R.string.stat_pending_count, summary.pendingTrips)
            )

            statAvailableDrivers.setValue(summary.availableDrivers.toString())
            statAvailableDrivers.setSubtitle(
                getString(R.string.stat_total_count, summary.assignedDrivers)
            )

            statCompletedTrips.setValue(summary.completedTripsToday.toString())
            if (summary.delayedTrips > 0) {
                statCompletedTrips.setSubtitle(
                    getString(R.string.stat_pending_count, summary.delayedTrips)
                )
            }

            statTeamEfficiency.setValue(
                String.format("%.0f%%", summary.teamEfficiencyPercent)
            )

            statOnTimeDelivery.setValue(
                String.format("%.0f%%", summary.onTimeDeliveryPercent)
            )
        }
    }

    private fun updatePendingActions(summary: OperationsSummary) {
        with(binding) {
            val totalPending = summary.totalPendingActions

            // Update badge
            tvTotalPending.text = totalPending.toString()
            tvTotalPending.isVisible = totalPending > 0

            // Update individual task counts
            taskFuelApprovals.updateCount(summary.pendingFuelApprovals)
            taskLeaveRequests.updateCount(summary.pendingLeaveRequests)
            taskMaintenance.updateCount(summary.pendingMaintenanceRequests)
            taskGeneral.updateCount(summary.pendingApprovals)

            // Show/hide empty state
            val hasAnyPending = totalPending > 0
            pendingEmptyState.isVisible = !hasAnyPending
            pendingTasksContainer.isVisible = hasAnyPending
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
}
