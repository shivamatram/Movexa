package com.example.movexa.ui.dashboard.driver

import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.navGraphViewModels
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.databinding.FragmentDriverTripsBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.ui.trips.DriverTripsViewModel
import com.example.movexa.ui.trips.tabs.DriverOngoingTabFragment
import com.example.movexa.ui.trips.tabs.HistoryTabFragment
import com.example.movexa.ui.trips.tabs.NewRequestsTabFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import androidx.navigation.fragment.findNavController

/**
 * Trips host for the Driver dashboard.
 *
 * Contains:
 * - TabLayout with 3 tabs: New Requests, Ongoing, History
 * - Header summary counts
 * - Confirmation dialogs for accept/reject/start/complete actions
 */
class DriverTripsFragment : BaseFragment<FragmentDriverTripsBinding>(
    FragmentDriverTripsBinding::inflate
) {

    // tracks last selected tab so we can restore after returning
    // stored in viewModel.selectedTabIndex as well
    private var savedTabIndex: Int = 0

    companion object {
        private const val TAG_NEW_REQUESTS = "tab_new_requests"
        private const val TAG_ONGOING = "tab_ongoing"
        private const val TAG_HISTORY = "tab_history"
        private const val KEY_SELECTED_TAB = "key_selected_tab"
    }

    // ── ViewModel ───────────────────────────────────────────────
    // scope the viewmodel to the driver nav graph so it survives
    // navigation to TripDetailsFragment and back
    private val viewModel: DriverTripsViewModel by navGraphViewModels(R.id.nav_driver) {
        defaultViewModelProviderFactory
    }

    // ── Tab Fragments ───────────────────────────────────────────
    private val newRequestsTab by lazy {
        NewRequestsTabFragment.newInstance().also { fragment ->
            fragment.onAcceptClick = { trip -> showAcceptConfirmation(trip) }
            fragment.onRejectClick = { trip -> showRejectConfirmation(trip) }
            fragment.onViewDetailsClick = { trip -> navigateToTripDetails(trip) }
        }
    }

    private val ongoingTab by lazy {
        DriverOngoingTabFragment.newInstance().also { fragment ->
            fragment.onStartClick = { trip -> showStartConfirmation(trip) }
            fragment.onCompleteClick = { trip -> showCompleteConfirmation(trip) }
            fragment.onViewDetailsClick = { trip -> navigateToTripDetails(trip) }
        }
    }

    private val historyTab by lazy {
        HistoryTabFragment.newInstance().also { fragment ->
            fragment.onViewDetailsClick = { trip -> navigateToTripDetails(trip) }
        }
    }

    private var currentTab: Fragment? = null

    // ── Lifecycle ───────────────────────────────────────────────

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        savedTabIndex = savedInstanceState?.getInt(KEY_SELECTED_TAB, 0) ?: 0
    }

    override fun initViews() {
        binding.tabLayout.apply {
            addTab(newTab().setText(R.string.tab_new_requests))
            addTab(newTab().setText(R.string.tab_ongoing))
            addTab(newTab().setText(R.string.tab_history))
        }

        // restore selected tab from nav backstack saved state (most reliable)
        val entry = findNavController().getBackStackEntry(R.id.driverTripsFragment)
        val savedFromHandle = entry.savedStateHandle.get<Int>(KEY_SELECTED_TAB)
        val index = savedFromHandle
            ?: viewModel.selectedTabIndex.takeIf { it in 0..2 }
            ?: savedTabIndex
        viewModel.selectedTabIndex = index
        entry.savedStateHandle.set(KEY_SELECTED_TAB, index)
        when (index) {
            1 -> switchToTab(ongoingTab, TAG_ONGOING)
            2 -> switchToTab(historyTab, TAG_HISTORY)
            else -> switchToTab(newRequestsTab, TAG_NEW_REQUESTS)
        }

        viewModel.loadTrips()
    }

    override fun onResume() {
        super.onResume()
        // re-select the previously chosen tab in case view rebuilt
        val idx = viewModel.selectedTabIndex.coerceIn(0, 2)
        binding.tabLayout.getTabAt(idx)?.select()
    }

    override fun setupListeners() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectedTabIndex = tab.position
                // persist also to nav backstack entry
                findNavController().getBackStackEntry(R.id.driverTripsFragment)
                    .savedStateHandle.set(KEY_SELECTED_TAB, tab.position)
                when (tab.position) {
                    0 -> switchToTab(newRequestsTab, TAG_NEW_REQUESTS)
                    1 -> switchToTab(ongoingTab, TAG_ONGOING)
                    2 -> switchToTab(historyTab, TAG_HISTORY)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    override fun observeData() {
        // ── Header Counts ───────────────────────────────────────
        collectLatestFlow(viewModel.newRequestsCount) { count ->
            binding.tvNewRequestsCount.text = count.toString()
        }

        collectLatestFlow(viewModel.ongoingCount) { count ->
            binding.tvOngoingCount.text = count.toString()
        }

        collectLatestFlow(viewModel.historyCount) { count ->
            binding.tvHistoryCount.text = count.toString()
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

    // ── Tab Switching ───────────────────────────────────────────

    private fun switchToTab(fragment: Fragment, tag: String) {
        if (currentTab === fragment) return

        val transaction = childFragmentManager.beginTransaction()

        currentTab?.let { transaction.hide(it) }

        val existing = childFragmentManager.findFragmentByTag(tag)
        if (existing != null) {
            transaction.show(existing)
        } else {
            transaction.add(R.id.tripTabContainer, fragment, tag)
        }

        transaction.commitNowAllowingStateLoss()
        currentTab = fragment
    }

    // ── Confirmations ───────────────────────────────────────────

    private fun showAcceptConfirmation(trip: Trip) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_accept_trip))
            .setMessage(getString(R.string.confirm_accept_trip_msg))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.trip_action_accept)) { _, _ ->
                viewModel.acceptTrip(trip.tripId)
            }
            .show()
    }

    private fun showRejectConfirmation(trip: Trip) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_reject_trip))
            .setMessage(getString(R.string.confirm_reject_trip_msg))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.trip_action_reject)) { _, _ ->
                viewModel.rejectTrip(trip.tripId)
            }
            .show()
    }

    private fun showStartConfirmation(trip: Trip) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_start_trip))
            .setMessage(getString(R.string.confirm_start_trip_msg))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.trip_action_start)) { _, _ ->
                viewModel.startTrip(trip.tripId)
            }
            .show()
    }

    private fun showCompleteConfirmation(trip: Trip) {
        // Create a dialog with an optional distance input field
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
                viewModel.completeTrip(trip.tripId, distance)
            }
            .show()
    }

    // ── Navigation ──────────────────────────────────────────────

    private fun navigateToTripDetails(trip: Trip) {
        // use same navigation action as manager/admin screens
        val bundle = android.os.Bundle().apply {
            putString(com.example.movexa.ui.trips.TripDetailsFragment.ARG_TRIP_ID, trip.tripId)
        }
        findNavController().navigate(
            R.id.action_driverTripsFragment_to_tripDetailsFragment,
            bundle
        )
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, binding.tabLayout.selectedTabPosition)
    }

}
