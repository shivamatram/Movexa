package com.example.movexa.ui.dashboard.driver

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Build
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.example.movexa.R
import com.example.movexa.data.model.DriverSummary
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.User
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.data.repository.DriverHomeRepository
import com.example.movexa.databinding.FragmentDriverHomeBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.utils.HapticManager
import com.example.movexa.utils.startShimmerPulse
import com.example.movexa.utils.stopShimmerPulse
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Driver Home screen — the driver's operational control center.
 *
 * ═══════════════════════════════════════════════════════════════
 * SECTIONS
 * ═══════════════════════════════════════════════════════════════
 *
 * 1. **Greeting Header** — time-based greeting with driver name,
 *    avatar, date display, and performance score badge.
 *
 * 2. **Active Trip Card** — real-time trip card showing status
 *    badge, tracking ID, pickup → drop-off route with timeline
 *    dots, distance/duration/ETA metrics, and status description.
 *    When no trip is assigned: friendly empty state card.
 *
 * 3. **Quick Action Button** — status-dependent primary action
 *    (Accept Trip → Start Trip → Mark Delivered) with confirmation
 *    dialogs, haptic feedback, and double-tap prevention.
 *
 * 4. **Vehicle Info Card** — assigned vehicle number, type, fuel
 *    type, and service-overdue warning banner.
 *
 * 5. **Today Summary** — 2×2 grid of stat cards showing trips
 *    completed, distance driven, drive time, and alerts — all
 *    updating in real-time via Firestore listeners.
 *
 * ═══════════════════════════════════════════════════════════════
 * STATES
 * ═══════════════════════════════════════════════════════════════
 *
 * - **Loading**  → shimmer placeholder UI
 * - **Content**  → full dashboard with all sections
 * - **Error**    → error message card with retry button
 *
 * Pull-to-refresh reloads all data without shimmer.
 * Offline banner appears when network connectivity is lost.
 *
 * ═══════════════════════════════════════════════════════════════
 * ANIMATIONS
 * ═══════════════════════════════════════════════════════════════
 *
 * - Content sections slide + fade in with staggered delays
 * - Score badge scales with overshoot interpolator
 * - Trip card pulses when ASSIGNED (needs attention)
 * - Action button ripple + haptic on press
 * - Stats counters animate from 0 to value
 *
 * @since 2026-02-23 — Driver Home Module
 */
class DriverHomeFragment : BaseFragment<FragmentDriverHomeBinding>(
    FragmentDriverHomeBinding::inflate
) {

    // ─── ViewModel ──────────────────────────────────────────────
    private val viewModel: DriverHomeViewModel by viewModels()


    // ─── Pulse Animation ────────────────────────────────────────
    private var pulseAnimator: ObjectAnimator? = null

    // ─── Track content animation ────────────────────────────────
    private var hasAnimatedContent = false

    // ═══════════════════════════════════════════════════════════
    //  INIT VIEWS
    // ═══════════════════════════════════════════════════════════

    override fun initViews() {
        // Initialize ViewModel
        viewModel.initialize()
    }

    // ═══════════════════════════════════════════════════════════
    //  SETUP LISTENERS
    // ═══════════════════════════════════════════════════════════

    override fun setupListeners() {
        // ── Pull-to-Refresh ─────────────────────────────────────
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
        binding.swipeRefresh.setColorSchemeResources(
            R.color.primary,
            R.color.secondary,
            R.color.score_excellent
        )

        // ── Trip Action Button ──────────────────────────────────
        binding.btnTripAction.setOnClickListener { btn ->
            HapticManager.medium(btn)
            handleTripAction()
        }

        // ── Retry Button ────────────────────────────────────────
        binding.btnRetry.setOnClickListener {
            viewModel.initialize()
        }

        // ── Vehicle Card Tap ────────────────────────────────────
        binding.cardVehicle.setOnClickListener { card ->
            HapticManager.light(card)
            // Could navigate to vehicle details in future
        }

        // ── Score Badge Tap → Performance ───────────────────────
        binding.layoutScoreBadge.setOnClickListener { badge ->
            HapticManager.light(badge)
            navigateToPerformance()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  OBSERVE DATA
    // ═══════════════════════════════════════════════════════════

    override fun observeData() {
        // ── Screen State ────────────────────────────────────────
        collectLatestFlow(viewModel.screenState) { state ->
            updateScreenState(state)
        }

        // ── User Profile (greeting) ────────────────────────────
        collectLatestFlow(viewModel.userProfile) { user ->
            user?.let { updateGreeting(it) }
        }

        // ── Active Trip ─────────────────────────────────────────
        collectLatestFlow(viewModel.activeTrip) { result ->
            updateTripCard(result)
        }

        // ── Vehicle ─────────────────────────────────────────────
        collectLatestFlow(viewModel.vehicle) { vehicle ->
            updateVehicleCard(vehicle)
        }

        // ── Performance Score Badge ─────────────────────────────
        collectLatestFlow(viewModel.driverSummary) { summary ->
            updateScoreBadge(summary)
        }

        // ── Today Stats ─────────────────────────────────────────
        collectLatestFlow(viewModel.todayStats) { stats ->
            updateTodayStats(stats)
        }

        // ── Alert Count ─────────────────────────────────────────
        collectLatestFlow(viewModel.todayAlertCount) { count ->
            binding.tvStatAlerts.text = count.toString()
        }

        // ── Refreshing ──────────────────────────────────────────
        collectLatestFlow(viewModel.isRefreshing) { refreshing ->
            binding.swipeRefresh.isRefreshing = refreshing
        }

        // ── Action Loading ──────────────────────────────────────
        collectLatestFlow(viewModel.isActionLoading) { loading ->
            binding.btnTripAction.isEnabled = !loading
            binding.progressAction.visibility = if (loading) View.VISIBLE else View.GONE
            if (loading) {
                binding.btnTripAction.alpha = 0.5f
            } else {
                binding.btnTripAction.alpha = 1f
            }
        }

        // ── Offline Banner ──────────────────────────────────────
        collectLatestFlow(viewModel.isOffline) { offline ->
            binding.tvOfflineBanner.visibility = if (offline) View.VISIBLE else View.GONE
        }

        // ── Action Results ──────────────────────────────────────
        collectFlow(viewModel.actionResult) { result ->
            handleActionResult(result)
        }

        // ── Error Events ────────────────────────────────────────
        collectFlow(viewModel.errorEvent) { message ->
            showError(message)
        }

        // ── Success Events ──────────────────────────────────────
        collectFlow(viewModel.successEvent) { message ->
            showSuccess(message)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SCREEN STATE
    // ═══════════════════════════════════════════════════════════

    /**
     * Transition between Loading / Content / Error states.
     */
    private fun updateScreenState(state: DriverHomeViewModel.ScreenState) {
        // Hide all containers first
        binding.layoutShimmer.visibility = View.GONE
        binding.swipeRefresh.visibility = View.GONE
        binding.layoutError.visibility = View.GONE

        when (state) {
            is DriverHomeViewModel.ScreenState.Loading -> {
                binding.layoutShimmer.visibility = View.VISIBLE
                binding.layoutShimmer.startShimmerPulse()
            }
            is DriverHomeViewModel.ScreenState.Content -> {
                binding.layoutShimmer.stopShimmerPulse()
                binding.layoutShimmer.visibility = View.GONE
                binding.swipeRefresh.visibility = View.VISIBLE
                if (!hasAnimatedContent) {
                    animateContentEntrance()
                    hasAnimatedContent = true
                }
            }
            is DriverHomeViewModel.ScreenState.Error -> {
                binding.layoutShimmer.stopShimmerPulse()
                binding.layoutShimmer.visibility = View.GONE
                binding.layoutError.visibility = View.VISIBLE
                binding.tvErrorMessage.text = state.message
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  GREETING
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the greeting header with user name and date.
     */
    private fun updateGreeting(user: User) {
        val greeting = viewModel.getGreeting()
        val name = user.firstName
        binding.tvGreeting.text = getString(
            R.string.driver_home_greeting_format, greeting, name
        )
        binding.tvDate.text = viewModel.getTodayDate()
    }

    // ═══════════════════════════════════════════════════════════
    //  ACTIVE TRIP CARD
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the trip card based on the ResultState.
     * Shows either the active trip card or the empty state card.
     */
    private fun updateTripCard(result: ResultState<Trip?>) {
        when (result) {
            is ResultState.Loading, is ResultState.Idle -> {
                // Keep whatever is shown
            }
            is ResultState.Success -> {
                val trip = result.data
                if (trip != null) {
                    showActiveTrip(trip)
                } else {
                    showNoTrip()
                }
            }
            is ResultState.Error -> {
                showNoTrip()
            }
        }
    }

    /**
     * Populate all fields of the active trip card.
     */
    private fun showActiveTrip(trip: Trip) {
        binding.cardActiveTrip.visibility = View.VISIBLE
        binding.cardNoTrip.visibility = View.GONE

        // ── Status Badge ────────────────────────────────────────
        binding.tvTripStatusBadge.text = trip.status.name
        updateStatusBadgeColor(trip.status)

        // ── Tracking ID ─────────────────────────────────────────
        val trackingId = trip.trackingId.ifBlank { trip.tripId.take(8) }
        binding.tvTrackingId.text = getString(
            R.string.driver_home_tracking_id_format, trackingId
        )

        // ── Route Addresses ─────────────────────────────────────
        binding.tvPickupAddress.text = trip.pickupAddress.ifBlank {
            getString(R.string.driver_home_address_unknown)
        }
        binding.tvDropAddress.text = trip.dropAddress.ifBlank {
            getString(R.string.driver_home_address_unknown)
        }

        // ── Metrics ─────────────────────────────────────────────
        val distance = if (trip.estimatedDistance > 0)
            trip.estimatedDistance else trip.distance
        binding.tvTripDistance.text = if (distance > 0) {
            getString(R.string.driver_home_stat_distance_format, distance)
        } else "—"

        val durationMins: Int = if (trip.estimatedDuration > 0) {
            (trip.estimatedDuration / 60000).toInt()
        } else {
            trip.durationMinutes.toInt()
        }
        binding.tvTripDuration.text = if (durationMins > 0) {
            "${durationMins} min"
        } else "—"

        // ── ETA (STARTED only) ──────────────────────────────────
        val showEta = viewModel.tripStateManager.shouldShowEta(trip.status)
        binding.layoutEta.visibility = if (showEta) View.VISIBLE else View.GONE
        if (showEta && trip.estimatedDuration > 0 && trip.startTime > 0) {
            val elapsed = System.currentTimeMillis() - trip.startTime
            val remaining = ((trip.estimatedDuration - elapsed) / 60000).toInt()
            binding.tvLiveEta.text = getString(
                R.string.driver_home_eta_format,
                remaining.coerceAtLeast(1)
            )
        }

        // ── Status Description ──────────────────────────────────
        binding.tvStatusDescription.text =
            viewModel.tripStateManager.getStatusDescription(trip.status)

        // ── Action Button ───────────────────────────────────────
        updateActionButton(trip.status)

        // ── Pulse for ASSIGNED ──────────────────────────────────
        if (viewModel.tripStateManager.shouldPulse(trip.status)) {
            startPulseAnimation()
        } else {
            stopPulseAnimation()
        }
    }

    /**
     * Show the empty "No Active Trip" card.
     */
    private fun showNoTrip() {
        binding.cardActiveTrip.visibility = View.GONE
        binding.cardNoTrip.visibility = View.VISIBLE
        binding.btnTripAction.visibility = View.GONE
        stopPulseAnimation()
    }

    /**
     * Update the status badge background color based on trip status.
     */
    private fun updateStatusBadgeColor(status: TripStatus) {
        val colorRes = when (status) {
            TripStatus.ASSIGNED -> R.color.warning
            TripStatus.ACCEPTED -> R.color.info
            TripStatus.STARTED -> R.color.primary
            TripStatus.COMPLETED -> R.color.score_excellent
            TripStatus.CANCELLED -> R.color.error
            else -> R.color.text_hint
        }
        val color = ContextCompat.getColor(requireContext(), colorRes)
        binding.tvTripStatusBadge.background.setTint(color)
    }

    // ═══════════════════════════════════════════════════════════
    //  ACTION BUTTON
    // ═══════════════════════════════════════════════════════════

    /**
     * Configure the primary action button for the current trip status.
     */
    private fun updateActionButton(status: TripStatus) {
        val config = viewModel.tripStateManager.getActionConfig(status)

        if (config == null) {
            binding.btnTripAction.visibility = View.GONE
            return
        }

        binding.btnTripAction.visibility = View.VISIBLE

        // Set label text
        val labelResId = getStringResId(config.labelResName)
        if (labelResId != 0) {
            binding.btnTripAction.text = getString(labelResId)
        }

        // Set icon
        val iconResId = getDrawableResId(config.iconResName)
        if (iconResId != 0) {
            binding.btnTripAction.setIconResource(iconResId)
        }

        // Priority-based coloring
        when (viewModel.tripStateManager.getActionPriority(status)) {
            2 -> { // Urgent — accent/warning color
                binding.btnTripAction.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
            }
            1 -> { // Standard primary
                binding.btnTripAction.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
            }
            else -> { // Info / neutral
                binding.btnTripAction.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.secondary)
                )
            }
        }
    }

    /**
     * Handle tap on the trip action button.
     * Shows confirmation dialog for destructive / important actions.
     */
    private fun handleTripAction() {
        val trip = (viewModel.activeTrip.value as? ResultState.Success)?.data ?: return
        val config = viewModel.tripStateManager.getActionConfig(trip.status) ?: return

        if (config.requiresConfirmation) {
            showConfirmationDialog(config.action)
        } else {
            viewModel.performAction(config.action)
        }
    }

    /**
     * Show a MaterialAlertDialog for action confirmation.
     */
    private fun showConfirmationDialog(action: DriverTripStateManager.TripAction) {
        val (title, message) = when (action) {
            DriverTripStateManager.TripAction.ACCEPT -> Pair(
                getString(R.string.driver_confirm_accept_title),
                getString(R.string.driver_confirm_accept_message)
            )
            DriverTripStateManager.TripAction.START -> Pair(
                getString(R.string.driver_confirm_start_title),
                getString(R.string.driver_confirm_start_message)
            )
            DriverTripStateManager.TripAction.MARK_DELIVERED -> Pair(
                getString(R.string.driver_confirm_complete_title),
                getString(R.string.driver_confirm_complete_message)
            )
            else -> return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.driver_confirm_yes) { _, _ ->
                HapticManager.heavy(binding.btnTripAction)
                viewModel.performAction(action)
            }
            .setNegativeButton(R.string.driver_confirm_no, null)
            .setCancelable(true)
            .show()
    }

    /**
     * Handle the result of a trip action (success / failure).
     */
    private fun handleActionResult(result: DriverHomeViewModel.ActionResult) {
        if (result.success) {
            showSuccess(result.message)
            view?.let { HapticManager.heavy(it) }
        } else {
            showError(result.message)
            view?.let { HapticManager.reject(it) }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  VEHICLE CARD
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the vehicle info card with assigned vehicle data.
     */
    private fun updateVehicleCard(vehicle: Vehicle?) {
        if (vehicle == null) {
            binding.cardVehicle.visibility = View.GONE
            if (viewModel.hasVehicleAssigned()) {
                binding.layoutNoVehicle.visibility = View.GONE
            } else {
                binding.layoutNoVehicle.visibility = View.VISIBLE
            }
            return
        }

        binding.cardVehicle.visibility = View.VISIBLE
        binding.layoutNoVehicle.visibility = View.GONE

        // Vehicle number
        binding.tvVehicleNumber.text = vehicle.number

        // Vehicle type + fuel info
        val typeDisplay = vehicle.type.displayName
        val fuelDisplay = vehicle.fuelType.ifBlank { "Unknown" }
            .replaceFirstChar { it.uppercase() }
        binding.tvVehicleType.text = getString(
            R.string.driver_home_vehicle_type_format,
            typeDisplay, fuelDisplay
        )

        // Service overdue warning
        val serviceOverdue = viewModel.isVehicleServiceOverdue(vehicle)
        binding.layoutServiceWarning.visibility =
            if (serviceOverdue) View.VISIBLE else View.GONE
    }

    // ═══════════════════════════════════════════════════════════
    //  SCORE BADGE
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the score badge in the greeting header.
     */
    private fun updateScoreBadge(summary: DriverSummary?) {
        if (summary == null) {
            binding.layoutScoreBadge.visibility = View.GONE
            return
        }

        binding.layoutScoreBadge.visibility = View.VISIBLE
        binding.tvScoreBadge.text = summary.score.toString()

        // Color based on score tier
        val score = summary.score
        val colorRes = when {
            score >= 90 -> R.color.score_excellent
            score >= 75 -> R.color.score_good
            score >= 50 -> R.color.score_average
            else -> R.color.score_risky
        }
        val color = ContextCompat.getColor(requireContext(), colorRes)
        binding.tvScoreBadge.setTextColor(color)

        // Animate badge entrance
        animateScoreBadge()
    }

    // ═══════════════════════════════════════════════════════════
    //  TODAY STATS
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the today summary stat cards.
     */
    private fun updateTodayStats(stats: DriverHomeRepository.TodayStats) {
        // Trips completed
        binding.tvStatTrips.text = stats.tripsCompleted.toString()

        // Distance driven
        binding.tvStatDistance.text = getString(
            R.string.driver_home_stat_distance_format,
            stats.distanceDriven
        )

        // Duration (convert minutes to hours + minutes)
        val hours = stats.durationMinutes / 60
        val mins = stats.durationMinutes % 60
        binding.tvStatDuration.text = getString(
            R.string.driver_home_stat_duration_format,
            hours, mins
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  ANIMATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Staggered entrance animation for content sections.
     * Each section slides up and fades in with a delay.
     */
    private fun animateContentEntrance() {
        val views = listOf(
            binding.layoutGreeting,
            binding.cardActiveTrip,
            binding.cardNoTrip,
            binding.cardVehicle
        ).filter { it.visibility == View.VISIBLE }

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 40f

            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setStartDelay((index * 80).toLong())
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /**
     * Scale-bounce animation for the score badge.
     */
    private fun animateScoreBadge() {
        binding.layoutScoreBadge.scaleX = 0f
        binding.layoutScoreBadge.scaleY = 0f

        binding.layoutScoreBadge.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.5f))
            .setStartDelay(300)
            .start()
    }

    /**
     * Pulse animation for the trip card when ASSIGNED status
     * (needs driver's attention).
     */
    private fun startPulseAnimation() {
        stopPulseAnimation()
        pulseAnimator = ObjectAnimator.ofFloat(
            binding.cardActiveTrip, "alpha", 1f, 0.7f, 1f
        ).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Stop pulse animation.
     */
    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.cardActiveTrip.alpha = 1f
    }

    // ═══════════════════════════════════════════════════════════
    //  NAVIGATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Navigate to the Performance tab.
     */
    private fun navigateToPerformance() {
        try {
            navigateTo(R.id.driverPerformanceFragment)
        } catch (_: Exception) {
            // Navigation already in progress, ignore
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Get a string resource ID by name.
     */
    private fun getStringResId(name: String): Int {
        return resources.getIdentifier(name, "string", requireContext().packageName)
    }

    /**
     * Get a drawable resource ID by name.
     */
    private fun getDrawableResId(name: String): Int {
        return resources.getIdentifier(name, "drawable", requireContext().packageName)
    }

    // ═══════════════════════════════════════════════════════════
    //  CLEANUP
    // ═══════════════════════════════════════════════════════════

    override fun onDestroyView() {
        stopPulseAnimation()
        super.onDestroyView()
    }
}
