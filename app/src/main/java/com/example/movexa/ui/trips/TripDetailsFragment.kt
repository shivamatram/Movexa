package com.example.movexa.ui.trips

import android.view.View
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.TripEvent
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.data.model.UserRole
import com.example.movexa.databinding.FragmentTripDetailsBinding
import com.example.movexa.ui.base.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Trip Details screen.
 *
 * Displays:
 * - Status badge and tracking ID header
 * - Route information (pickup → drop with distance/duration)
 * - Trip info card (load, vehicle, driver, assignedBy, notes, timestamps)
 * - Timeline of trip events
 * - Role-aware action buttons
 *
 * Receives tripId via arguments bundle.
 * All data is observed in real time via TripDetailsViewModel.
 */
class TripDetailsFragment : BaseFragment<FragmentTripDetailsBinding>(
    FragmentTripDetailsBinding::inflate
) {

    // ── ViewModel ───────────────────────────────────────────────
    private val viewModel: TripDetailsViewModel by viewModels()

    // ── Adapters ────────────────────────────────────────────────
    private lateinit var timelineAdapter: TimelineEventAdapter

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    // ── Lifecycle ───────────────────────────────────────────────

    override fun initViews() {
        // Timeline RecyclerView
        timelineAdapter = TimelineEventAdapter()
        binding.rvTimeline.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTimeline.adapter = timelineAdapter
        binding.rvTimeline.isNestedScrollingEnabled = false

        // Load trip details
        val tripId = arguments?.getString(ARG_TRIP_ID) ?: ""
        if (tripId.isNotBlank()) {
            viewModel.loadTripDetails(tripId)
        } else {
            showError("Invalid trip ID")
        }

        // Initially hide action buttons until we know the role/status
        binding.layoutDetailActions.visibility = View.GONE
    }

    override fun setupListeners() {
        // Back button
        binding.btnBack.setOnClickListener {
            navigateBack()
        }

        // Refresh button
        binding.btnRefresh.setOnClickListener {
            viewModel.refresh()
        }
    }

    override fun observeData() {
        // ── Trip Data ───────────────────────────────────────────
        collectLatestFlow(viewModel.trip) { state ->
            when (state) {
                is ResultState.Loading -> showDetailLoading(true)
                is ResultState.Success -> {
                    showDetailLoading(false)
                    state.data?.let { bindTripData(it) }
                }
                is ResultState.Error -> {
                    showDetailLoading(false)
                    showError(state.message)
                }
                is ResultState.Idle -> {}
            }
        }

        // ── Timeline Events ─────────────────────────────────────
        collectLatestFlow(viewModel.events) { state ->
            when (state) {
                is ResultState.Success -> bindTimelineEvents(state.data)
                is ResultState.Error -> {
                    binding.tvNoTimeline.visibility = View.VISIBLE
                    binding.rvTimeline.visibility = View.GONE
                }
                else -> {}
            }
        }

        // ── Resolved Names ──────────────────────────────────────
        collectLatestFlow(viewModel.vehicleName) { name ->
            if (!name.isNullOrBlank()) {
                binding.tvVehicleDetail.text = name
                binding.layoutVehicleDetail.visibility = View.VISIBLE
            }
        }

        collectLatestFlow(viewModel.driverName) { name ->
            if (!name.isNullOrBlank()) {
                binding.tvDriverDetail.text = name
                binding.layoutDriverDetail.visibility = View.VISIBLE
            }
        }

        collectLatestFlow(viewModel.assignedByName) { name ->
            if (!name.isNullOrBlank()) {
                binding.tvAssignedBy.text = name
                binding.layoutAssignedBy.visibility = View.VISIBLE
            }
        }

        // ── User Role ───────────────────────────────────────────
        collectLatestFlow(viewModel.userRole) { role ->
            // Re-bind actions when role is resolved
            val tripState = viewModel.trip.value
            if (tripState is ResultState.Success && tripState.data != null) {
                configureActions(tripState.data, role)
            }
        }

        // ── Operation Result ────────────────────────────────────
        collectLatestFlow(viewModel.operationResult) { result ->
            when (result) {
                is ResultState.Success -> {
                    showSuccess(result.data)
                    viewModel.clearOperationResult()
                }
                is ResultState.Error -> {
                    showError(result.message)
                    viewModel.clearOperationResult()
                }
                is ResultState.Loading -> { /* Handled locally */ }
                is ResultState.Idle -> {}
            }
        }
    }

    // ── Data Binding ────────────────────────────────────────────

    private fun bindTripData(trip: Trip) {
        // ── Status Badge ────────────────────────────────────────
        binding.tvStatusBadge.text = trip.status.displayName
        applyStatusColors(trip.status)

        // ── Tracking ID ─────────────────────────────────────────
        binding.tvTrackingId.text = trip.trackingId.ifBlank { trip.tripId.take(12) }

        // ── Route ───────────────────────────────────────────────
        binding.tvPickupAddress.text = trip.pickupAddress.ifBlank { "—" }
        binding.tvDropAddress.text = trip.dropAddress.ifBlank { "—" }

        // ── Metrics ─────────────────────────────────────────────
        val displayDistance = if (trip.distance > 0) trip.distance else trip.estimatedDistance
        binding.tvDistance.text = if (displayDistance > 0)
            getString(R.string.trip_distance_format, displayDistance) else "—"

        binding.tvDuration.text = if (trip.duration > 0)
            formatDuration(trip.duration) else "—"

        // ── Trip Info Card ──────────────────────────────────────
        // Load description
        if (trip.notes.isNotBlank() || trip.metadata.containsKey("loadDescription")) {
            val loadDesc = trip.metadata["loadDescription"]?.toString() ?: ""
            if (loadDesc.isNotBlank()) {
                binding.tvLoadDescription.text = loadDesc
                binding.layoutLoadDesc.visibility = View.VISIBLE
            } else {
                binding.layoutLoadDesc.visibility = View.GONE
            }
        } else {
            binding.layoutLoadDesc.visibility = View.GONE
        }

        // Vehicle / Driver / AssignedBy — will be updated by name resolution flows
        binding.layoutVehicleDetail.visibility =
            if (trip.vehicleId.isNotBlank()) View.VISIBLE else View.GONE
        binding.layoutDriverDetail.visibility =
            if (trip.driverId.isNotBlank()) View.VISIBLE else View.GONE
        binding.layoutAssignedBy.visibility =
            if (trip.assignedBy.isNotBlank()) View.VISIBLE else View.GONE

        // Notes
        if (trip.notes.isNotBlank()) {
            binding.tvNotes.text = trip.notes
            binding.layoutNotes.visibility = View.VISIBLE
        } else {
            binding.layoutNotes.visibility = View.GONE
        }

        // Timestamps
        if (trip.startTime > 0) {
            binding.tvStartTime.text = dateFormat.format(Date(trip.startTime))
            binding.layoutTimestamps.visibility = View.VISIBLE
        }
        if (trip.endTime > 0) {
            binding.tvEndTime.text = dateFormat.format(Date(trip.endTime))
            binding.layoutEndTime.visibility = View.VISIBLE
        } else {
            binding.layoutEndTime.visibility = View.GONE
        }

        // ── Actions ─────────────────────────────────────────────
        configureActions(trip, viewModel.userRole.value)
    }

    // ── Timeline ────────────────────────────────────────────────

    private fun bindTimelineEvents(events: List<TripEvent>) {
        if (events.isEmpty()) {
            binding.tvNoTimeline.visibility = View.VISIBLE
            binding.rvTimeline.visibility = View.GONE
        } else {
            binding.tvNoTimeline.visibility = View.GONE
            binding.rvTimeline.visibility = View.VISIBLE
            timelineAdapter.submitList(events)
        }
    }

    // ── Status Colors ───────────────────────────────────────────

    private fun applyStatusColors(status: TripStatus) {
        val (textColor, bgColor) = when (status) {
            TripStatus.CREATED -> R.color.trip_created to R.color.trip_created_bg
            TripStatus.ASSIGNED -> R.color.trip_assigned to R.color.trip_assigned_bg
            TripStatus.ACCEPTED -> R.color.trip_accepted to R.color.trip_accepted_bg
            TripStatus.REJECTED_BY_DRIVER -> R.color.trip_rejected to R.color.trip_rejected_bg
            TripStatus.STARTED -> R.color.trip_started to R.color.trip_started_bg
            TripStatus.COMPLETED -> R.color.trip_completed to R.color.trip_completed_bg
            TripStatus.CANCELLED -> R.color.trip_cancelled to R.color.trip_cancelled_bg
        }

        binding.tvStatusBadge.setTextColor(
            ContextCompat.getColor(requireContext(), textColor)
        )
        binding.tvStatusBadge.setBackgroundColor(
            ContextCompat.getColor(requireContext(), bgColor)
        )
    }

    // ── Action Buttons ──────────────────────────────────────────

    private fun configureActions(trip: Trip, role: UserRole?) {
        binding.layoutDetailActions.visibility = View.GONE
        binding.btnDetailPrimary.visibility = View.GONE
        binding.btnDetailSecondary.visibility = View.GONE

        if (trip.status.isTerminal) return

        when (role) {
            UserRole.MANAGER -> configureManagerActions(trip)
            UserRole.DRIVER -> configureDriverActions(trip)
            else -> {}
        }
    }

    private fun configureManagerActions(trip: Trip) {
        when (trip.status) {
            TripStatus.CREATED, TripStatus.ASSIGNED, TripStatus.ACCEPTED -> {
                binding.layoutDetailActions.visibility = View.VISIBLE
                binding.btnDetailSecondary.visibility = View.VISIBLE
                binding.btnDetailSecondary.text = getString(R.string.trip_action_cancel)
                binding.btnDetailSecondary.setOnClickListener {
                    showCancelDialog()
                }
            }
            else -> {}
        }
    }

    private fun configureDriverActions(trip: Trip) {
        binding.layoutDetailActions.visibility = View.VISIBLE

        when (trip.status) {
            TripStatus.ASSIGNED -> {
                // Reject + Accept
                binding.btnDetailSecondary.visibility = View.VISIBLE
                binding.btnDetailSecondary.text = getString(R.string.trip_action_reject)
                binding.btnDetailSecondary.setOnClickListener {
                    showRejectDialog()
                }

                binding.btnDetailPrimary.visibility = View.VISIBLE
                binding.btnDetailPrimary.text = getString(R.string.trip_action_accept)
                binding.btnDetailPrimary.setOnClickListener {
                    showAcceptDialog()
                }
            }
            TripStatus.ACCEPTED -> {
                // Start Trip
                binding.btnDetailPrimary.visibility = View.VISIBLE
                binding.btnDetailPrimary.text = getString(R.string.trip_action_start)
                binding.btnDetailPrimary.setOnClickListener {
                    showStartDialog()
                }
            }
            TripStatus.STARTED -> {
                // Complete Trip
                binding.btnDetailPrimary.visibility = View.VISIBLE
                binding.btnDetailPrimary.text = getString(R.string.trip_action_complete)
                binding.btnDetailPrimary.setOnClickListener {
                    showCompleteDialog()
                }
            }
            else -> {
                binding.layoutDetailActions.visibility = View.GONE
            }
        }
    }

    // ── Confirmation Dialogs ────────────────────────────────────

    private fun showAcceptDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_accept_trip))
            .setMessage(getString(R.string.confirm_accept_trip_msg))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.trip_action_accept)) { _, _ ->
                viewModel.acceptTrip()
            }
            .show()
    }

    private fun showRejectDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_reject_trip))
            .setMessage(getString(R.string.confirm_reject_trip_msg))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.trip_action_reject)) { _, _ ->
                viewModel.rejectTrip()
            }
            .show()
    }

    private fun showStartDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_start_trip))
            .setMessage(getString(R.string.confirm_start_trip_msg))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.trip_action_start)) { _, _ ->
                viewModel.startTrip()
            }
            .show()
    }

    private fun showCompleteDialog() {
        val inputView = EditText(requireContext()).apply {
            hint = getString(R.string.trip_actual_distance_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(64, 32, 64, 16)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_complete_trip))
            .setMessage(getString(R.string.confirm_complete_trip_msg))
            .setView(inputView)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.trip_action_complete)) { _, _ ->
                val distance = inputView.text.toString().toDoubleOrNull() ?: 0.0
                viewModel.completeTrip(distance)
            }
            .show()
    }

    private fun showCancelDialog() {
        val inputView = EditText(requireContext()).apply {
            hint = getString(R.string.trip_cancel_reason_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            setPadding(64, 32, 64, 16)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_cancel_trip))
            .setMessage(getString(R.string.confirm_cancel_trip_msg))
            .setView(inputView)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.trip_action_cancel)) { _, _ ->
                val reason = inputView.text.toString().ifBlank { null }
                viewModel.cancelTrip(reason)
            }
            .show()
    }

    // ── Helpers ─────────────────────────────────────────────────

    private fun showDetailLoading(loading: Boolean) {
        binding.layoutDetailLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    // ── Factory ─────────────────────────────────────────────────

    companion object {
        const val ARG_TRIP_ID = "trip_id"

        fun newInstance(tripId: String): TripDetailsFragment {
            return TripDetailsFragment().apply {
                arguments = android.os.Bundle().apply {
                    putString(ARG_TRIP_ID, tripId)
                }
            }
        }
    }
}
