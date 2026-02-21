package com.example.movexa.ui.fleet

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.viewModels
import com.example.movexa.R
import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.ResultState
import com.example.movexa.databinding.FragmentDriversTabBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.ui.components.DriverCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Tab fragment displaying the list of drivers in the fleet.
 *
 * Features:
 * - Real-time driver list from Firestore
 * - Search bar with live filtering (by name, license, blood group)
 * - Status group chip filters (All / Active / Pending / Blocked / Unassigned)
 * - Driver card views with actions (verify, block/unblock, assign vehicle)
 * - Assignment bottom sheet for vehicle linking
 * - Driver name resolution from users collection
 * - Shimmer loading + empty + error states
 * - Pull-to-refresh
 *
 * Instantiated by AdminFleetFragment and ManagerFleetFragment as a child fragment.
 */
class DriversTabFragment : BaseFragment<FragmentDriversTabBinding>(
    FragmentDriversTabBinding::inflate
) {

    private val viewModel: DriverListViewModel by viewModels()

    private var isAdmin: Boolean = true

    // ── Lifecycle ───────────────────────────────────────────────

    override fun initViews() {
        isAdmin = arguments?.getBoolean(ARG_IS_ADMIN, true) ?: true

        // FAB is hidden by default in layout; show for admin if needed
        binding.fabAddDriver.visibility = View.GONE

        // Set initial chip selection
        binding.chipAll.isChecked = true

        // Start loading data
        viewModel.loadDrivers()
    }

    override fun setupListeners() {
        // ── Search ──────────────────────────────────────────────
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // ── Chip Filters ────────────────────────────────────────
        setupChipListeners()

        // ── Pull-to-Refresh ─────────────────────────────────────
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshDrivers()
        }

        // ── Retry ───────────────────────────────────────────────
        binding.btnRetry.setOnClickListener {
            viewModel.refreshDrivers()
        }
    }

    override fun observeData() {
        // ── Driver List ─────────────────────────────────────────
        collectLatestFlow(viewModel.drivers) { state ->
            when (state) {
                is ResultState.Loading -> showLoadingState()
                is ResultState.Success -> showDriverList(state.data)
                is ResultState.Error -> showErrorState(state.message)
                is ResultState.Idle -> {}
            }
            binding.swipeRefresh.isRefreshing = false
        }

        // ── Driver Count ────────────────────────────────────────
        collectLatestFlow(viewModel.driverCount) { count ->
            binding.tvDriverCount.text = getString(R.string.fleet_driver_count, count)
        }

        // ── Driver Names (triggers re-render when names are resolved) ──
        collectLatestFlow(viewModel.driverNames) { _ ->
            val currentState = viewModel.drivers.value
            if (currentState is ResultState.Success) {
                showDriverList(currentState.data)
            }
        }

        // ── Operation Results ───────────────────────────────────
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
                is ResultState.Loading -> {}
                is ResultState.Idle -> {}
            }
        }
    }

    // ── Chip Filtering ──────────────────────────────────────────

    private fun setupChipListeners() {
        val chipMap: Map<Chip, DriverListViewModel.DriverFilterGroup> = mapOf(
            binding.chipAll to DriverListViewModel.DriverFilterGroup.ALL,
            binding.chipActive to DriverListViewModel.DriverFilterGroup.ACTIVE,
            binding.chipPending to DriverListViewModel.DriverFilterGroup.PENDING,
            binding.chipBlocked to DriverListViewModel.DriverFilterGroup.BLOCKED,
            binding.chipUnassigned to DriverListViewModel.DriverFilterGroup.UNASSIGNED
        )

        for ((chip, filter) in chipMap) {
            chip.setOnClickListener {
                chipMap.keys.forEach { c -> c.isChecked = false }
                chip.isChecked = true
                viewModel.setDriverFilter(filter)
            }
        }
    }

    // ── UI State Rendering ──────────────────────────────────────

    private fun showLoadingState() {
        binding.layoutShimmer.visibility = View.VISIBLE
        binding.layoutDriverList.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.layoutDriverCount.visibility = View.GONE
    }

    private fun showDriverList(drivers: List<Driver>) {
        binding.layoutShimmer.visibility = View.GONE
        binding.layoutError.visibility = View.GONE

        if (drivers.isEmpty()) {
            binding.layoutDriverList.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.layoutDriverCount.visibility = View.GONE
            return
        }

        binding.layoutEmpty.visibility = View.GONE
        binding.layoutDriverList.visibility = View.VISIBLE
        binding.layoutDriverCount.visibility = View.VISIBLE

        // Clear and re-render driver cards
        binding.layoutDriverList.removeAllViews()

        for (driver in drivers) {
            val cardView = DriverCardView(requireContext())
            val driverName = viewModel.getDriverName(driver.driverId)
            cardView.bind(driver, driverName, isAdmin)

            // Card click
            cardView.onCardClick = { _ ->
                // Future: navigate to driver detail
            }

            // Verify
            cardView.onVerifyClick = { d ->
                showVerifyConfirmation(d)
            }

            // Reject
            cardView.onRejectClick = { d ->
                showRejectConfirmation(d)
            }

            // Block
            cardView.onBlockClick = { d ->
                showBlockConfirmation(d)
            }

            // Unblock
            cardView.onUnblockClick = { d ->
                viewModel.unblockDriver(d.driverId)
            }

            // Assign vehicle
            cardView.onAssignVehicleClick = { d ->
                showAssignVehicleSheet(d)
            }

            binding.layoutDriverList.addView(cardView)
        }
    }

    private fun showErrorState(message: String) {
        binding.layoutShimmer.visibility = View.GONE
        binding.layoutDriverList.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutDriverCount.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
    }

    // ── Verify Driver ───────────────────────────────────────────

    private fun showVerifyConfirmation(driver: Driver) {
        val name = viewModel.getDriverName(driver.driverId)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_verify_driver))
            .setMessage(getString(R.string.confirm_verify_driver_msg, name))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_confirm)) { _, _ ->
                viewModel.verifyDriver(driver.driverId)
            }
            .show()
    }

    // ── Reject Driver ───────────────────────────────────────────

    private fun showRejectConfirmation(driver: Driver) {
        val name = viewModel.getDriverName(driver.driverId)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_reject_driver))
            .setMessage(getString(R.string.confirm_reject_driver_msg, name))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_confirm)) { _, _ ->
                viewModel.rejectDriver(driver.driverId)
            }
            .show()
    }

    // ── Block Driver ────────────────────────────────────────────

    private fun showBlockConfirmation(driver: Driver) {
        val name = viewModel.getDriverName(driver.driverId)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_block_driver))
            .setMessage(getString(R.string.confirm_block_driver_msg, name))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_confirm)) { _, _ ->
                viewModel.blockDriver(driver.driverId)
            }
            .show()
    }

    // ── Assign Vehicle ──────────────────────────────────────────

    private fun showAssignVehicleSheet(driver: Driver) {
        val currentAssignment = if (!driver.assignedVehicleId.isNullOrBlank())
            driver.assignedVehicleId else null

        val sheet = AssignmentBottomSheet.newInstance(
            title = getString(R.string.fleet_assign_vehicle),
            subtitle = getString(R.string.assignment_select_vehicle),
            currentAssignment = currentAssignment
        )

        sheet.showLoading()

        // Fetch available vehicles
        viewModel.getAvailableVehicles { options ->
            sheet.setOptions(options)
            sheet.hideLoading()
        }

        sheet.onAssignConfirmed = { vehicleId ->
            viewModel.assignVehicleToDriver(driver.driverId, vehicleId)
            sheet.dismiss()
        }

        sheet.onUnassignRequested = {
            viewModel.unassignVehicleFromDriver(driver.driverId)
            sheet.dismiss()
        }

        sheet.show(childFragmentManager, AssignmentBottomSheet.TAG)
    }

    // ── Factory ─────────────────────────────────────────────────

    companion object {
        private const val ARG_IS_ADMIN = "is_admin"

        fun newInstance(isAdmin: Boolean): DriversTabFragment {
            return DriversTabFragment().apply {
                arguments = android.os.Bundle().apply {
                    putBoolean(ARG_IS_ADMIN, isAdmin)
                }
            }
        }
    }
}
