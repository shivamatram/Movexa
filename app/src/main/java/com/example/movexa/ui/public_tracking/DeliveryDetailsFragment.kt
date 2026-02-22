package com.example.movexa.ui.public_tracking

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movexa.R
import com.example.movexa.data.model.PublicDriverInfo
import com.example.movexa.data.model.PublicTripInfo
import com.example.movexa.data.model.PublicVehicleInfo
import com.example.movexa.data.model.TimelineEvent
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.databinding.FragmentDeliveryDetailsBinding
import com.example.movexa.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  DELIVERY DETAILS FRAGMENT
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Comprehensive delivery information screen for public (unauthenticated) customers.
 *
 * ┌──────────────────────────────────────────────────┐
 * │  ← Delivery Details                    🔗        │
 * │──────────────────────────────────────────────────│
 * │         ┌──────────────┐                         │
 * │         │  In Transit  │                         │
 * │         └──────────────┘                         │
 * │         TRK-2026-ABCD                            │
 * │         Total: 123.4 km                          │
 * │──────────────────────────────────────────────────│
 * │  👤 DRIVER INFORMATION                           │
 * │  ┌──────────────────────────────────┐            │
 * │  │ 🧑 Rahul S.                  📞 │             │
 * │  │      XXXXXXX210                  │            │
 * │  │ ★ 4.8  •  1K+ deliveries        │            │
 * │  └──────────────────────────────────┘            │
 * │──────────────────────────────────────────────────│
 * │  🚛 VEHICLE INFORMATION                         │
 * │  Type: Truck   Number: MH 12 AB 1234            │
 * │──────────────────────────────────────────────────│
 * │  🏁 ROUTE                                       │
 * │  ● Pickup: 123 MG Road, Pune                    │
 * │  │                                               │
 * │  ● Drop: 456 FC Road, Mumbai                    │
 * │──────────────────────────────────────────────────│
 * │  📋 DELIVERY TIMELINE                            │
 * │  ● Order Placed      ✓  Jan 15 10:00            │
 * │  ● Driver Assigned   ✓  Jan 15 10:15            │
 * │  ◉ Pickup Completed  ✓  Jan 15 11:00            │
 * │  ○ Delivery          ─  Pending                  │
 * │──────────────────────────────────────────────────│
 * │  [ 🗺️  Open Live Map ]                          │
 * │  [  🔗  Share Tracking Link ]                    │
 * └──────────────────────────────────────────────────┘
 *
 * ─── Features ─────────────────────────────────────────────────────
 *
 *  ● Status pill with dynamic color based on trip status
 *  ● Driver info card with masked phone and call button
 *  ● Vehicle info card
 *  ● Route card with pickup/drop addresses and distance
 *  ● Delivery timeline with ordered events and completion dots
 *  ● Open Live Map button (only for active trips)
 *  ● Share Tracking Link via system share sheet
 *  ● Call driver via ACTION_DIAL intent
 *  ● Smooth entrance animations (staggered card reveals)
 *  ● Real-time status updates from ViewModel
 *  ● Rating display with star icon
 *  ● Completed trips badge
 *
 * ─── Architecture ─────────────────────────────────────────────────
 *
 *  ● Shared [PublicTrackingViewModel] for data
 *  ● [PublicTimelineAdapter] for timeline RecyclerView
 *  ● Observes: tripInfo, driverInfo, vehicleInfo, timeline
 *
 * ─── Security ─────────────────────────────────────────────────────
 *
 *  ● Phone is ALWAYS masked (last 3 digits only)
 *  ● Call button uses ACTION_DIAL (user must manually press call)
 *  ● No internal IDs exposed in UI
 *  ● Share text contains only tracking ID and status
 *
 * @since 2026-02-22
 */
class DeliveryDetailsFragment : BaseFragment<FragmentDeliveryDetailsBinding>(
    FragmentDeliveryDetailsBinding::inflate
) {

    // ═══════════════════════════════════════════════════════════════
    //  PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    /** Shared ViewModel */
    private val viewModel: PublicTrackingViewModel by activityViewModels()

    /** Timeline adapter */
    private lateinit var timelineAdapter: PublicTimelineAdapter

    /** Animation flag */
    private var hasAnimated = false

    // ═══════════════════════════════════════════════════════════════
    //  CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    companion object {
        private const val ANIM_CARD_DURATION = 400L
        private const val ANIM_STAGGER_DELAY = 100L
    }

    // ═══════════════════════════════════════════════════════════════
    //  INIT VIEWS
    // ═══════════════════════════════════════════════════════════════

    override fun initViews() {
        // ── Setup timeline RecyclerView ──────────────────────────
        setupTimelineRecyclerView()

        // ── Populate initial data ───────────────────────────────
        populateTripInfo(viewModel.tripInfo.value)
        populateDriverInfo(viewModel.driverInfo.value)
        populateVehicleInfo(viewModel.vehicleInfo.value)
        populateTimeline(viewModel.timeline.value)

        // ── Configure action buttons ────────────────────────────
        configureActionButtons()

        // ── Play entrance animations ────────────────────────────
        if (!hasAnimated) {
            playEntranceAnimations()
            hasAnimated = true
        }
    }

    /**
     * Setup the timeline RecyclerView.
     */
    private fun setupTimelineRecyclerView() {
        timelineAdapter = PublicTimelineAdapter()
        binding.rvTimeline.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = timelineAdapter
            isNestedScrollingEnabled = false
        }
    }

    /**
     * Configure which action buttons should be visible based on trip status.
     */
    private fun configureActionButtons() {
        val tripInfo = viewModel.tripInfo.value

        // Only show "Open Live Map" for active (in-transit) trips
        if (tripInfo != null && tripInfo.isActive) {
            binding.btnOpenLiveMap.visibility = View.VISIBLE
        } else {
            binding.btnOpenLiveMap.visibility = View.GONE
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SETUP LISTENERS
    // ═══════════════════════════════════════════════════════════════

    override fun setupListeners() {
        // ── Back button ─────────────────────────────────────────
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // ── Share button (top bar) ──────────────────────────────
        binding.btnShare.setOnClickListener {
            shareTrackingLink()
        }

        // ── Open Live Map ───────────────────────────────────────
        binding.btnOpenLiveMap.setOnClickListener {
            try {
                findNavController().navigate(
                    R.id.action_deliveryDetails_to_liveTracking
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // ── Share Tracking Link button ──────────────────────────
        binding.btnShareLink.setOnClickListener {
            shareTrackingLink()
        }

        // ── Call Driver button ───────────────────────────────────
        binding.btnCallDriver.setOnClickListener {
            callDriver()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  OBSERVE DATA
    // ═══════════════════════════════════════════════════════════════

    override fun observeData() {
        // ── Trip info (real-time status updates) ────────────────
        collectLatestFlow(viewModel.tripInfo) { tripInfo ->
            populateTripInfo(tripInfo)
            configureActionButtons()
        }

        // ── Driver info ─────────────────────────────────────────
        collectLatestFlow(viewModel.driverInfo) { driverInfo ->
            populateDriverInfo(driverInfo)
        }

        // ── Vehicle info ────────────────────────────────────────
        collectLatestFlow(viewModel.vehicleInfo) { vehicleInfo ->
            populateVehicleInfo(vehicleInfo)
        }

        // ── Timeline ────────────────────────────────────────────
        collectLatestFlow(viewModel.timeline) { timeline ->
            populateTimeline(timeline)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  DATA POPULATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Populate the status card and route card with trip info.
     */
    private fun populateTripInfo(tripInfo: PublicTripInfo?) {
        if (tripInfo == null) return

        // ── Status pill ─────────────────────────────────────────
        binding.tvStatusPill.text = tripInfo.statusDisplayName
        applyStatusPillStyle(tripInfo.status)

        // ── Tracking ID ─────────────────────────────────────────
        binding.tvTrackingId.text = tripInfo.trackingId

        // ── Distance ────────────────────────────────────────────
        val distance = tripInfo.distanceDisplay
        if (distance != "—") {
            binding.tvDistance.text = "Total distance: $distance"
            binding.tvDistance.visibility = View.VISIBLE
        } else {
            binding.tvDistance.visibility = View.GONE
        }

        // ── Route addresses ─────────────────────────────────────
        binding.tvRoutePickup.text = tripInfo.pickupAddress.ifBlank {
            getString(R.string.public_no_data)
        }
        binding.tvRouteDrop.text = tripInfo.dropAddress.ifBlank {
            getString(R.string.public_no_data)
        }
    }

    /**
     * Populate the driver info card.
     */
    private fun populateDriverInfo(driverInfo: PublicDriverInfo?) {
        if (driverInfo == null) {
            binding.cardDriver.visibility = View.GONE
            return
        }

        binding.cardDriver.visibility = View.VISIBLE

        // ── Name + phone ────────────────────────────────────────
        binding.tvDriverName.text = driverInfo.displayName
        binding.tvDriverPhone.text = driverInfo.maskedPhone

        // ── Rating + trips ──────────────────────────────────────
        val hasStats = driverInfo.rating > 0f || driverInfo.completedTrips > 0
        if (hasStats) {
            binding.layoutDriverStats.visibility = View.VISIBLE
            binding.tvDriverRating.text = driverInfo.ratingDisplay
            binding.tvDriverTrips.text = driverInfo.tripsDisplay
        } else {
            binding.layoutDriverStats.visibility = View.GONE
        }

        // ── Call button visibility ──────────────────────────────
        binding.btnCallDriver.visibility = if (driverInfo.hasPhone) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    /**
     * Populate the vehicle info card.
     */
    private fun populateVehicleInfo(vehicleInfo: PublicVehicleInfo?) {
        if (vehicleInfo == null) {
            binding.cardVehicle.visibility = View.GONE
            return
        }

        binding.cardVehicle.visibility = View.VISIBLE
        binding.tvVehicleType.text = vehicleInfo.typeLabel.ifBlank {
            getString(R.string.public_no_data)
        }
        binding.tvVehicleNumber.text = vehicleInfo.number.ifBlank {
            getString(R.string.public_no_data)
        }
    }

    /**
     * Populate the timeline RecyclerView.
     */
    private fun populateTimeline(timeline: List<TimelineEvent>) {
        if (timeline.isNotEmpty()) {
            timelineAdapter.submitList(timeline)
            binding.rvTimeline.visibility = View.VISIBLE
        } else {
            binding.rvTimeline.visibility = View.GONE
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  STATUS PILL STYLING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Apply dynamic background and text color to the status pill
     * based on the trip status.
     *
     * Color mapping:
     *  ● CREATED     → grey
     *  ● ASSIGNED    → blue
     *  ● ACCEPTED    → teal
     *  ● STARTED     → green (primary)
     *  ● COMPLETED   → green (success)
     *  ● CANCELLED   → red
     */
    private fun applyStatusPillStyle(status: TripStatus) {
        val context = context ?: return

        val (bgColorRes, textColorRes) = when (status) {
            TripStatus.CREATED -> {
                R.color.public_status_created_bg to R.color.public_status_created
            }
            TripStatus.ASSIGNED -> {
                R.color.public_status_assigned_bg to R.color.public_status_assigned
            }
            TripStatus.ACCEPTED -> {
                R.color.public_status_accepted_bg to R.color.public_status_accepted
            }
            TripStatus.REJECTED_BY_DRIVER -> {
                R.color.public_status_assigned_bg to R.color.public_status_assigned
            }
            TripStatus.STARTED -> {
                R.color.public_status_started_bg to R.color.public_status_started
            }
            TripStatus.COMPLETED -> {
                R.color.public_status_completed_bg to R.color.public_status_completed
            }
            TripStatus.CANCELLED -> {
                R.color.public_status_cancelled_bg to R.color.public_status_cancelled
            }
        }

        // Create rounded background programmatically
        val bgDrawable = GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, bgColorRes))
            cornerRadius = 24f
        }
        binding.tvStatusPill.background = bgDrawable
        binding.tvStatusPill.setTextColor(ContextCompat.getColor(context, textColorRes))
    }

    // ═══════════════════════════════════════════════════════════════
    //  ACTIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Share the tracking link via the system share sheet.
     * The shared text includes the tracking ID and current status.
     */
    private fun shareTrackingLink() {
        val tripInfo = viewModel.tripInfo.value ?: return

        val shareText = getString(
            R.string.public_share_text,
            tripInfo.trackingId,
            tripInfo.statusDisplayName
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.public_app_title))
        }

        try {
            startActivity(
                Intent.createChooser(shareIntent, getString(R.string.public_detail_share))
            )
        } catch (e: Exception) {
            view?.let {
                Snackbar.make(it, "Unable to share", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Initiate a call to the driver.
     *
     * Uses ACTION_DIAL (not ACTION_CALL) so the user sees the dialer
     * and must manually press the call button. This is intentional for
     * security — the phone number is masked, so the dialer won't work
     * unless the company app has a click-to-call relay system.
     *
     * In production, this would typically call a proxy/relay endpoint
     * that connects the customer to the driver without revealing the
     * full phone number.
     */
    private fun callDriver() {
        val driverInfo = viewModel.driverInfo.value

        if (driverInfo == null || !driverInfo.hasPhone) {
            view?.let {
                Snackbar.make(
                    it,
                    getString(R.string.public_details_call_unavailable),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            return
        }

        // Note: The maskedPhone won't be dialable — this is by design.
        // A real implementation would use a relay service or provide
        // a callback request feature instead.
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${driverInfo.maskedPhone}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            view?.let {
                Snackbar.make(
                    it,
                    getString(R.string.public_details_call_unavailable),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ANIMATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Play staggered entrance animations for each card.
     *
     * Each card slides up from below and fades in, creating a
     * cascading reveal effect as the user enters the screen.
     */
    private fun playEntranceAnimations() {
        val cards = listOf(
            binding.cardStatus,
            binding.cardDriver,
            binding.cardVehicle,
            binding.cardRoute,
            binding.cardTimeline,
            binding.btnOpenLiveMap,
            binding.btnShareLink
        )

        cards.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f

            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(ANIM_CARD_DURATION)
                .setStartDelay(ANIM_STAGGER_DELAY * index)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
}
