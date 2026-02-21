package com.example.movexa.ui.trips

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.example.movexa.R
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.databinding.ItemTripCardBinding

/**
 * Custom view component for rendering a trip card in the trip list.
 *
 * Responsibilities:
 * - Binds Trip model data to layout
 * - Applies status-specific colors to the status badge
 * - Configures route visualization (pickup/drop dots and addresses)
 * - Shows/hides action buttons based on trip status and viewer role
 * - Exposes click callbacks for primary, secondary and view-details actions
 * - Shows optional vehicle, driver and load description info
 *
 * Supports two contexts:
 *   Manager: sees Assign/Cancel/Reassign buttons, vehicle/driver info
 *   Driver:  sees Accept/Reject/Start/Complete buttons
 */
class TripCardView(context: Context) : LinearLayout(context) {

    private val binding: ItemTripCardBinding

    // ── Click Callbacks ─────────────────────────────────────────
    var onPrimaryClick: ((Trip) -> Unit)? = null
    var onSecondaryClick: ((Trip) -> Unit)? = null
    var onViewDetailsClick: ((Trip) -> Unit)? = null
    var onCardClick: ((Trip) -> Unit)? = null
    var onMenuClick: ((Trip) -> Unit)? = null

    private var currentTrip: Trip? = null

    /** Whether this card is rendered for a manager (true) or driver (false). */
    var isManagerView: Boolean = true

    init {
        orientation = VERTICAL
        binding = ItemTripCardBinding.inflate(LayoutInflater.from(context), this, true)
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Bind a Trip model to this card view.
     *
     * @param trip       The trip to display
     * @param isManager  Whether the viewer is a manager (affects action buttons)
     * @param vehicleNumber Optional vehicle number to display (resolved externally)
     * @param driverName    Optional driver name to display (resolved externally)
     */
    fun bind(
        trip: Trip,
        isManager: Boolean = true,
        vehicleNumber: String? = null,
        driverName: String? = null
    ) {
        currentTrip = trip
        isManagerView = isManager

        // ── Status Badge ────────────────────────────────────────
        applyStatusBadge(trip.status)

        // ── Tracking ID ─────────────────────────────────────────
        binding.tvTrackingId.text = if (trip.trackingId.isNotBlank())
            trip.trackingId else "—"

        // ── Route Visualization ─────────────────────────────────
        binding.tvPickupAddress.text = trip.pickupAddress.ifBlank { "—" }
        binding.tvDropAddress.text = trip.dropAddress.ifBlank { "—" }

        // Dot colors based on status
        val pickupDotColor = when {
            trip.status.isOngoing() || trip.status == TripStatus.COMPLETED ->
                ContextCompat.getColor(context, R.color.success)
            else -> ContextCompat.getColor(context, R.color.timeline_dot_inactive)
        }
        val dropDotColor = when {
            trip.status == TripStatus.COMPLETED ->
                ContextCompat.getColor(context, R.color.error)
            else -> ContextCompat.getColor(context, R.color.timeline_dot_inactive)
        }
        binding.dotPickup.background.setTint(pickupDotColor)
        binding.dotDrop.background.setTint(dropDotColor)

        // ── Load Description ────────────────────────────────────
        val notes = trip.notes
        val metadata = trip.metadata
        val loadDesc = metadata["loadDescription"] as? String ?: ""
        if (loadDesc.isNotBlank()) {
            binding.tvLoadDescription.visibility = View.VISIBLE
            binding.tvLoadDescription.text = loadDesc
        } else {
            binding.tvLoadDescription.visibility = View.GONE
        }

        // ── Distance / Duration ─────────────────────────────────
        val displayDistance = if (trip.distance > 0) {
            context.getString(R.string.trip_distance_format, trip.distance)
        } else if (trip.estimatedDistance > 0) {
            context.getString(R.string.trip_distance_format, trip.estimatedDistance)
        } else {
            context.getString(R.string.trip_no_distance)
        }
        binding.tvDistance.text = displayDistance

        val displayDuration = if (trip.durationMinutes > 0) {
            formatDuration(trip.durationMinutes)
        } else if (trip.estimatedDuration > 0) {
            formatDuration(trip.estimatedDuration / 60_000)
        } else {
            context.getString(R.string.trip_no_distance)
        }
        binding.tvDuration.text = displayDuration

        // ── Vehicle Info ────────────────────────────────────────
        if (!vehicleNumber.isNullOrBlank()) {
            binding.layoutVehicleInfo.visibility = View.VISIBLE
            binding.dividerVehicle.visibility = View.VISIBLE
            binding.tvVehicleInfo.text = vehicleNumber
        } else {
            binding.layoutVehicleInfo.visibility = View.GONE
            binding.dividerVehicle.visibility = View.GONE
        }

        // ── Driver Info ─────────────────────────────────────────
        if (!driverName.isNullOrBlank() && isManager) {
            binding.layoutDriverInfo.visibility = View.VISIBLE
            binding.tvDriverName.text = driverName
        } else {
            binding.layoutDriverInfo.visibility = View.GONE
        }

        // ── Action Buttons ──────────────────────────────────────
        configureActions(trip, isManager)

        // ── Click Listeners ─────────────────────────────────────
        setupClickListeners(trip)
    }

    // ── Status Badge Colors ─────────────────────────────────────

    /**
     * Apply status-specific text color and background tint to the badge.
     */
    private fun applyStatusBadge(status: TripStatus) {
        val (textColorRes, bgColorRes) = when (status) {
            TripStatus.CREATED -> R.color.trip_created to R.color.trip_created_bg
            TripStatus.ASSIGNED -> R.color.trip_assigned to R.color.trip_assigned_bg
            TripStatus.ACCEPTED -> R.color.trip_accepted to R.color.trip_accepted_bg
            TripStatus.REJECTED_BY_DRIVER -> R.color.trip_rejected to R.color.trip_rejected_bg
            TripStatus.STARTED -> R.color.trip_started to R.color.trip_started_bg
            TripStatus.COMPLETED -> R.color.trip_completed to R.color.trip_completed_bg
            TripStatus.CANCELLED -> R.color.trip_cancelled to R.color.trip_cancelled_bg
        }

        binding.tvStatusBadge.text = status.displayName
        binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, textColorRes))
        binding.tvStatusBadge.background.setTint(ContextCompat.getColor(context, bgColorRes))
    }

    // ── Action Button Configuration ─────────────────────────────

    /**
     * Configure which action buttons to show and their labels,
     * depending on trip status and viewer role.
     */
    private fun configureActions(trip: Trip, isManager: Boolean) {
        binding.layoutActions.visibility = View.VISIBLE
        binding.btnPrimary.visibility = View.GONE
        binding.btnSecondary.visibility = View.GONE
        binding.btnViewDetails.visibility = View.VISIBLE

        if (isManager) {
            configureManagerActions(trip)
        } else {
            configureDriverActions(trip)
        }
    }

    /**
     * Manager-specific action buttons:
     * - CREATED:   primary = "Assign Driver"
     * - ASSIGNED:  secondary = "Cancel", primary = "Reassign"
     * - ACCEPTED:  secondary = "Cancel Trip"
     * - STARTED:   (view only, no actions)
     * - COMPLETED / CANCELLED / REJECTED: no actions
     */
    private fun configureManagerActions(trip: Trip) {
        when (trip.status) {
            TripStatus.CREATED -> {
                binding.btnPrimary.visibility = View.VISIBLE
                binding.btnPrimary.text = context.getString(R.string.trip_action_assign)
            }
            TripStatus.ASSIGNED -> {
                binding.btnSecondary.visibility = View.VISIBLE
                binding.btnSecondary.text = context.getString(R.string.trip_action_cancel)
                binding.btnPrimary.visibility = View.VISIBLE
                binding.btnPrimary.text = context.getString(R.string.trip_action_reassign)
            }
            TripStatus.ACCEPTED -> {
                binding.btnSecondary.visibility = View.VISIBLE
                binding.btnSecondary.text = context.getString(R.string.trip_action_cancel)
            }
            TripStatus.STARTED -> {
                // Manager can only view ongoing trips
            }
            TripStatus.COMPLETED, TripStatus.CANCELLED, TripStatus.REJECTED_BY_DRIVER -> {
                // Terminal — no actions
                binding.layoutActions.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Driver-specific action buttons:
     * - ASSIGNED:  secondary = "Reject", primary = "Accept"
     * - ACCEPTED:  primary = "Start Trip"
     * - STARTED:   primary = "Complete Trip"
     * - others:    no actions (view only)
     */
    private fun configureDriverActions(trip: Trip) {
        when (trip.status) {
            TripStatus.ASSIGNED -> {
                binding.btnSecondary.visibility = View.VISIBLE
                binding.btnSecondary.text = context.getString(R.string.trip_action_reject)
                binding.btnPrimary.visibility = View.VISIBLE
                binding.btnPrimary.text = context.getString(R.string.trip_action_accept)
            }
            TripStatus.ACCEPTED -> {
                binding.btnPrimary.visibility = View.VISIBLE
                binding.btnPrimary.text = context.getString(R.string.trip_action_start)
            }
            TripStatus.STARTED -> {
                binding.btnPrimary.visibility = View.VISIBLE
                binding.btnPrimary.text = context.getString(R.string.trip_action_complete)
            }
            TripStatus.CREATED, TripStatus.COMPLETED,
            TripStatus.CANCELLED, TripStatus.REJECTED_BY_DRIVER -> {
                // No actions for driver
            }
        }
    }

    // ── Click Listeners ─────────────────────────────────────────

    private fun setupClickListeners(trip: Trip) {
        binding.btnPrimary.setOnClickListener {
            onPrimaryClick?.invoke(trip)
        }

        binding.btnSecondary.setOnClickListener {
            onSecondaryClick?.invoke(trip)
        }

        binding.btnViewDetails.setOnClickListener {
            onViewDetailsClick?.invoke(trip)
        }

        binding.btnMenu.setOnClickListener { anchor ->
            showPopupMenu(anchor, trip)
        }

        // The entire card is clickable for details
        (binding.root as? View)?.setOnClickListener {
            onCardClick?.invoke(trip)
        }
    }

    /**
     * Show overflow popup menu with contextual options.
     */
    private fun showPopupMenu(anchor: View, trip: Trip) {
        val popup = PopupMenu(context, anchor)
        popup.menu.apply {
            add(0, MENU_VIEW_DETAILS, 0, R.string.trip_action_view_details)
            if (isManagerView && trip.status == TripStatus.CREATED) {
                add(0, MENU_ASSIGN, 1, R.string.trip_action_assign)
            }
            if (isManagerView && !trip.status.isTerminal) {
                add(0, MENU_CANCEL, 2, R.string.trip_action_cancel)
            }
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_VIEW_DETAILS -> {
                    onViewDetailsClick?.invoke(trip)
                    true
                }
                MENU_ASSIGN -> {
                    onPrimaryClick?.invoke(trip)
                    true
                }
                MENU_CANCEL -> {
                    onSecondaryClick?.invoke(trip)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Format duration in minutes to "Xh Ym" display string.
     */
    private fun formatDuration(minutes: Long): String {
        return when {
            minutes < 60 -> "${minutes}m"
            minutes % 60 == 0L -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }

    companion object {
        private const val MENU_VIEW_DETAILS = 1
        private const val MENU_ASSIGN = 2
        private const val MENU_CANCEL = 3
    }
}
