package com.example.movexa.ui.dashboard.manager

import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.databinding.FragmentManagerTripsBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.ui.trips.CreateTripBottomSheet
import com.example.movexa.ui.trips.ManagerTripsViewModel
import com.example.movexa.ui.trips.SmartAssignmentBottomSheet
import com.example.movexa.ui.trips.tabs.CompletedTripsTabFragment
import com.example.movexa.ui.trips.tabs.ManagerOngoingTabFragment
import com.example.movexa.ui.trips.tabs.UnassignedTripsTabFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.example.movexa.ui.trips.TripDetailsFragment
import androidx.navigation.fragment.findNavController

/**
 * Trip Management host for the Manager dashboard.
 *
 * Contains:
 * - TabLayout with 3 tabs: Unassigned, Ongoing, Completed
 * - FAB for trip creation
 * - Smart assignment bottom sheet flow
 * - Header summary counts
 *
 * Each tab is a child fragment sharing this fragment's ManagerTripsViewModel.
 */
class ManagerTripsFragment : BaseFragment<FragmentManagerTripsBinding>(
    FragmentManagerTripsBinding::inflate
) {

    // ── ViewModel ───────────────────────────────────────────────
    private val viewModel: ManagerTripsViewModel by viewModels()

    // ── Tab Fragments ───────────────────────────────────────────
    private val unassignedTab by lazy {
        UnassignedTripsTabFragment.newInstance().also { fragment ->
            fragment.onAssignClick = { trip -> showSmartAssignmentSheet(trip) }
            fragment.onViewDetailsClick = { trip -> navigateToTripDetails(trip) }
            fragment.onCancelClick = { trip -> showCancelConfirmation(trip) }
        }
    }

    private val ongoingTab by lazy {
        ManagerOngoingTabFragment.newInstance().also { fragment ->
            fragment.onCancelClick = { trip -> showCancelConfirmation(trip) }
            fragment.onViewDetailsClick = { trip -> navigateToTripDetails(trip) }
        }
    }

    private val completedTab by lazy {
        CompletedTripsTabFragment.newInstance().also { fragment ->
            fragment.onViewDetailsClick = { trip -> navigateToTripDetails(trip) }
        }
    }

    private var currentTab: Fragment? = null

    // ── Lifecycle ───────────────────────────────────────────────

    override fun initViews() {
        // Add tabs
        binding.tabLayout.apply {
            addTab(newTab().setText(R.string.tab_unassigned))
            addTab(newTab().setText(R.string.tab_ongoing))
            addTab(newTab().setText(R.string.tab_completed))
        }

        // Show initial tab
        switchToTab(unassignedTab, TAG_UNASSIGNED)

        // Start loading
        viewModel.loadTrips()
    }

    override fun setupListeners() {
        // ── Tab Selection ───────────────────────────────────────
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> switchToTab(unassignedTab, TAG_UNASSIGNED)
                    1 -> switchToTab(ongoingTab, TAG_ONGOING)
                    2 -> switchToTab(completedTab, TAG_COMPLETED)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // ── FAB: Create Trip ────────────────────────────────────
        binding.fabCreateTrip.setOnClickListener {
            showCreateTripSheet()
        }
    }

    override fun observeData() {
        // ── Header Counts ───────────────────────────────────────
        collectLatestFlow(viewModel.unassignedCount) { count ->
            binding.tvUnassignedCount.text = count.toString()
        }

        collectLatestFlow(viewModel.ongoingCount) { count ->
            binding.tvOngoingCount.text = count.toString()
        }

        collectLatestFlow(viewModel.completedCount) { count ->
            binding.tvCompletedCount.text = count.toString()
        }

        collectLatestFlow(viewModel.totalCount) { count ->
            binding.tvTotalTripCount.text = getString(R.string.trip_count_total, count)
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
                is ResultState.Loading -> { /* Loading handled locally */ }
                is ResultState.Idle -> {}
            }
        }
    }

    // ── Tab Switching ───────────────────────────────────────────

    private fun switchToTab(fragment: Fragment, tag: String) {
        if (currentTab === fragment) return

        val transaction = childFragmentManager.beginTransaction()

        // hide every tab that may have been added already to avoid overlapping
        listOf(unassignedTab, ongoingTab, completedTab).forEach { frag ->
            if (frag.isAdded) transaction.hide(frag)
        }

        if (fragment.isAdded) {
            transaction.show(fragment)
        } else {
            transaction.add(R.id.tripTabContainer, fragment, tag)
        }

        transaction.commitNowAllowingStateLoss()
        currentTab = fragment
    }

    // ── Create Trip ─────────────────────────────────────────────

    private fun showCreateTripSheet() {
        val sheet = CreateTripBottomSheet()

        sheet.onTripCreated = { data ->
            viewModel.createTrip(data)
            sheet.dismiss()
        }

        sheet.show(childFragmentManager, CreateTripBottomSheet.TAG)
    }

    // ── Smart Assignment ────────────────────────────────────────

    private fun showSmartAssignmentSheet(trip: Trip) {
        val sheet = SmartAssignmentBottomSheet.newInstance(
            routeSummary = "${trip.pickupAddress} → ${trip.dropAddress}",
            distanceSummary = if (trip.estimatedDistance > 0)
                getString(R.string.trip_distance_format, trip.estimatedDistance) else ""
        )

        sheet.showLoading()

        // Load eligible vehicle-driver pairs
        viewModel.loadEligibleOptions()

        // Observe eligible options
        collectLatestFlow(viewModel.eligibleOptions) { state ->
            when (state) {
                is ResultState.Success -> {
                    sheet.setOptions(state.data)
                    sheet.hideLoading()
                }
                is ResultState.Error -> {
                    sheet.hideLoading()
                    showError(state.message)
                }
                is ResultState.Loading -> sheet.showLoading()
                is ResultState.Idle -> {}
            }
        }

        sheet.onAssignSelected = { vehicleId, driverId ->
            viewModel.assignTrip(trip.tripId, vehicleId, driverId)
            sheet.dismiss()
        }

        sheet.show(childFragmentManager, SmartAssignmentBottomSheet.TAG)
    }

    // ── Cancel Trip ─────────────────────────────────────────────

    private fun showCancelConfirmation(trip: Trip) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_cancel_trip))
            .setMessage(getString(R.string.confirm_cancel_trip_msg))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.trip_action_cancel)) { _, _ ->
                viewModel.cancelTrip(trip.tripId)
            }
            .show()
    }

    // ── Navigation ──────────────────────────────────────────────

    private fun navigateToTripDetails(trip: Trip) {
        val bundle = android.os.Bundle().apply {
            putString(TripDetailsFragment.ARG_TRIP_ID, trip.tripId)
        }
        findNavController().navigate(
            R.id.action_managerTripsFragment_to_tripDetailsFragment,
            bundle
        )
    }

    // ── Constants ───────────────────────────────────────────────

    companion object {
        private const val TAG_UNASSIGNED = "tab_unassigned"
        private const val TAG_ONGOING = "tab_ongoing"
        private const val TAG_COMPLETED = "tab_completed"
    }
}
