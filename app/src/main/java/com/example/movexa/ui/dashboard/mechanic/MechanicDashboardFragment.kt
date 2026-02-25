package com.example.movexa.ui.dashboard.mechanic

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.example.movexa.R
import com.example.movexa.data.model.ActivityLog
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.ResultState
import com.example.movexa.databinding.FragmentMechanicDashboardBinding
import com.example.movexa.databinding.ItemMaintenanceTaskBinding
import com.example.movexa.theme.AppColors
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.ui.components.ActivityItemView
import com.example.movexa.ui.components.AlertItemView
import com.example.movexa.ui.dashboard.common.DashboardStatsCalculator
import com.example.movexa.ui.dashboard.common.MaintenanceTask
import com.example.movexa.ui.dashboard.common.MaintenanceUrgency
import com.example.movexa.ui.dashboard.common.MechanicStats
import com.example.movexa.ui.dashboard.common.StatCardData
import com.example.movexa.utils.TimeUtils
import java.text.NumberFormat
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════════
//  MECHANIC DASHBOARD FRAGMENT
// ═══════════════════════════════════════════════════════════════════════════════
//
//  Full-featured dashboard for mechanics showing:
//  1. Welcome header with last updated timestamp
//  2. Stat cards (2x2 grid): Due for Service, Under Repair, Completed Today, Urgent Alerts
//  3. Pending cost summary card with total estimated cost
//  4. Sorted maintenance queue — color-coded by urgency (Critical → Overdue → Due Soon → Scheduled)
//  5. Quick alerts panel with real-time Firestore listener
//  6. Recent activity feed with real-time updates
//  7. Shimmer loading state + full-screen error state + SwipeRefresh
//
//  All data flows from MechanicDashboardViewModel which aggregates from
//  ServiceRepository, RepairRepository, AlertRepository, VehicleRepository,
//  and ActivityLogRepository. Fragment is purely a render component.
// ═══════════════════════════════════════════════════════════════════════════════

class MechanicDashboardFragment : BaseFragment<FragmentMechanicDashboardBinding>(
    FragmentMechanicDashboardBinding::inflate
) {

    private val viewModel: MechanicDashboardViewModel by viewModels()

    private val currencyFormatter: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    companion object {
        private const val MAX_ALERTS_SHOWN = 5
        private const val MAX_ACTIVITY_SHOWN = 10
        private const val MAX_QUEUE_SHOWN = 10
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
            statDueForService.setData(
                iconRes = R.drawable.ic_build,
                label = getString(R.string.mechanic_service_title),
                value = "—",
                iconTint = AppColors.WARNING
            )

            statUnderRepair.setData(
                iconRes = R.drawable.ic_build,
                label = getString(R.string.mechanic_repairs_title),
                value = "—",
                iconTint = AppColors.SECONDARY
            )

            statCompletedToday.setData(
                iconRes = R.drawable.ic_check_circle,
                label = "Completed Today",
                value = "—",
                iconTint = AppColors.SUCCESS
            )

            statUrgentAlerts.setData(
                iconRes = R.drawable.ic_warning,
                label = "Urgent Alerts",
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
                showInfo(getString(R.string.feature_coming_soon))
            }

            tvViewAllActivity.setOnClickListener {
                showInfo(getString(R.string.feature_coming_soon))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Observe Data
    // ═══════════════════════════════════════════════════════════

    override fun observeData() {
        // Loading state
        collectLatestFlow(viewModel.isLoading) { isLoading ->
            if (isLoading) {
                showLoadingState()
            }
        }

        // Error state
        collectLatestFlow(viewModel.error) { errorMsg ->
            if (errorMsg != null && viewModel.hasCriticalError()) {
                showErrorState(errorMsg)
                binding.swipeRefresh.isRefreshing = false
            }
        }

        // Welcome name
        collectLatestFlow(viewModel.userName) { name ->
            binding.tvWelcome.text = getString(R.string.welcome_back, name)
        }

        // Stat cards
        collectLatestFlow(viewModel.statCards) { cards ->
            if (cards.isNotEmpty()) {
                updateStatCards(cards)
                showContentState()
                binding.swipeRefresh.isRefreshing = false
            }
        }

        // Mechanic stats (for cost summary)
        collectLatestFlow(viewModel.mechanicStats) { stats ->
            updateCostSummary(stats)
        }

        // Maintenance queue
        collectLatestFlow(viewModel.maintenanceQueue) { queue ->
            renderMaintenanceQueue(queue)
        }

        // Last updated
        collectLatestFlow(viewModel.lastUpdated) { timestamp ->
            updateLastUpdated(timestamp)
        }

        // Alerts
        collectLatestFlow(viewModel.activeAlerts) { state ->
            handleActiveAlerts(state)
        }

        // Activity
        collectLatestFlow(viewModel.recentActivity) { state ->
            handleRecentActivity(state)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  State Handlers
    // ═══════════════════════════════════════════════════════════

    private fun handleActiveAlerts(state: ResultState<List<Alert>>) {
        when (state) {
            is ResultState.Success -> renderAlerts(state.data)
            is ResultState.Error -> {
                binding.alertsContainer.removeAllViews()
                binding.alertsEmptyState.isVisible = true
            }
            else -> { /* Loading handled by main isLoading state */ }
        }
    }

    private fun handleRecentActivity(state: ResultState<List<ActivityLog>>) {
        when (state) {
            is ResultState.Success -> renderActivity(state.data)
            is ResultState.Error -> {
                binding.activityContainer.removeAllViews()
                binding.activityEmptyState.isVisible = true
            }
            else -> { /* Loading handled by main isLoading state */ }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  UI Update Methods
    // ═══════════════════════════════════════════════════════════

    private fun updateStatCards(cards: List<StatCardData>) {
        with(binding) {
            // Map cards by ID for safe access
            val cardMap = cards.associateBy { it.id }

            cardMap["due_for_service"]?.let { data ->
                statDueForService.setValue(data.value)
                if (data.subtitle.isNotBlank()) {
                    statDueForService.setSubtitle(data.subtitle)
                }
            }

            cardMap["under_repair"]?.let { data ->
                statUnderRepair.setValue(data.value)
                if (data.subtitle.isNotBlank()) {
                    statUnderRepair.setSubtitle(data.subtitle)
                }
            }

            cardMap["completed_today"]?.let { data ->
                statCompletedToday.setValue(data.value)
                if (data.subtitle.isNotBlank()) {
                    statCompletedToday.setSubtitle(data.subtitle)
                }
            }

            cardMap["urgent_alerts"]?.let { data ->
                statUrgentAlerts.setValue(data.value)
                if (data.subtitle.isNotBlank()) {
                    statUrgentAlerts.setSubtitle(data.subtitle)
                }
                // Color code: red if has alerts, green if clear
                val tint = if (data.value != "0") AppColors.ERROR else AppColors.SUCCESS
                statUrgentAlerts.setIconTint(tint)
            }
        }
    }

    private fun updateCostSummary(stats: MechanicStats) {
        binding.tvPendingCost.text = formatCurrency(stats.totalPendingCost)
    }

    /**
     * Render the sorted maintenance queue as individual task items.
     * Each task is color-coded by urgency.
     */
    private fun renderMaintenanceQueue(queue: List<MaintenanceTask>) {
        with(binding) {
            maintenanceContainer.removeAllViews()

            val displayTasks = queue.take(MAX_QUEUE_SHOWN)

            if (displayTasks.isEmpty()) {
                maintenanceEmptyState.isVisible = true
                maintenanceContainer.isVisible = false
                tvQueueCount.text = getString(R.string.mechanic_queue_count_format, 0)
            } else {
                maintenanceEmptyState.isVisible = false
                maintenanceContainer.isVisible = true
                tvQueueCount.text = getString(R.string.mechanic_queue_count_format, queue.size)

                for (task in displayTasks) {
                    val taskBinding = ItemMaintenanceTaskBinding.inflate(
                        LayoutInflater.from(requireContext()),
                        maintenanceContainer,
                        false
                    )

                    taskBinding.tvVehicleNumber.text = task.vehicleNumber
                    taskBinding.tvServiceType.text = task.serviceType
                    taskBinding.tvDueInfo.text = task.dueInfo

                    // Color-coded urgency badge
                    val (badgeColor, badgeText) = getUrgencyStyle(task.urgency)
                    taskBinding.tvUrgencyBadge.text = badgeText
                    val badgeBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = resources.getDimension(R.dimen.radius_small)
                        setColor(badgeColor)
                    }
                    taskBinding.tvUrgencyBadge.background = badgeBg

                    // Urgency bar color
                    taskBinding.viewUrgencyBar.setBackgroundColor(badgeColor)

                    // Click to navigate to service details (future)
                    taskBinding.root.setOnClickListener {
                        showInfo("${task.vehicleNumber} — ${task.serviceType}")
                    }

                    maintenanceContainer.addView(taskBinding.root)
                }
            }
        }
    }

    /**
     * Get badge color and label text for a maintenance urgency level.
     */
    private fun getUrgencyStyle(urgency: MaintenanceUrgency): Pair<Int, String> {
        return when (urgency) {
            MaintenanceUrgency.CRITICAL -> AppColors.ERROR to getString(R.string.mechanic_urgency_critical)
            MaintenanceUrgency.OVERDUE -> AppColors.WARNING to getString(R.string.mechanic_urgency_overdue)
            MaintenanceUrgency.DUE_SOON -> AppColors.INFO to getString(R.string.mechanic_urgency_due_soon)
            MaintenanceUrgency.SCHEDULED -> AppColors.SURFACE_VARIANT to getString(R.string.mechanic_urgency_scheduled)
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
