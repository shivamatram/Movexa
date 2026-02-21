package com.example.movexa.ui.fleet

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.viewModels
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.databinding.FragmentVehiclesTabBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.ui.components.VehicleCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Tab fragment displaying the list of vehicles in the fleet.
 *
 * Features:
 * - Real-time vehicle list from Firestore
 * - Search bar with live filtering
 * - Status chip filters (All / Available / On Trip / Service / Inactive)
 * - Vehicle card views with actions (edit, delete, status change, assign driver)
 * - Add vehicle bottom sheet (admin only)
 * - Assignment bottom sheet for driver↔vehicle linking
 * - Shimmer loading + empty + error states
 * - Pull-to-refresh
 *
 * Instantiated by AdminFleetFragment and ManagerFleetFragment as a child fragment.
 * Receives isAdmin flag to control action visibility (FAB, edit, delete).
 */
class VehiclesTabFragment : BaseFragment<FragmentVehiclesTabBinding>(
    FragmentVehiclesTabBinding::inflate
) {

    private val viewModel: VehicleListViewModel by viewModels()

    private var isAdmin: Boolean = true

    // ── Lifecycle ───────────────────────────────────────────────

    override fun initViews() {
        isAdmin = arguments?.getBoolean(ARG_IS_ADMIN, true) ?: true

        // Show/hide FAB based on role
        binding.fabAddVehicle.visibility = if (isAdmin) View.VISIBLE else View.GONE

        // Set initial chip selection
        binding.chipAll.isChecked = true

        // Start loading data
        viewModel.loadVehicles()
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
            viewModel.refreshVehicles()
        }

        // ── FAB ─────────────────────────────────────────────────
        binding.fabAddVehicle.setOnClickListener {
            showAddVehicleSheet()
        }

        // ── Retry ───────────────────────────────────────────────
        binding.btnRetry.setOnClickListener {
            viewModel.refreshVehicles()
        }
    }

    override fun observeData() {
        // ── Vehicle List ────────────────────────────────────────
        collectLatestFlow(viewModel.vehicles) { state ->
            when (state) {
                is ResultState.Loading -> showLoadingState()
                is ResultState.Success -> showVehicleList(state.data)
                is ResultState.Error -> showErrorState(state.message)
                is ResultState.Idle -> {}
            }
            binding.swipeRefresh.isRefreshing = false
        }

        // ── Vehicle Count ───────────────────────────────────────
        collectLatestFlow(viewModel.vehicleCount) { count ->
            binding.tvVehicleCount.text = getString(R.string.fleet_vehicle_count, count)
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
                is ResultState.Loading -> {
                    // Could show global loading if needed
                }
                is ResultState.Idle -> {}
            }
        }
    }

    // ── Chip Filtering ──────────────────────────────────────────

    private fun setupChipListeners() {
        val chipMap: Map<Chip, VehicleStatus?> = mapOf(
            binding.chipAll to null,
            binding.chipAvailable to VehicleStatus.AVAILABLE,
            binding.chipOnTrip to VehicleStatus.ON_TRIP,
            binding.chipService to VehicleStatus.SERVICE,
            binding.chipInactive to VehicleStatus.INACTIVE
        )

        for ((chip, status) in chipMap) {
            chip.setOnClickListener {
                // Uncheck all chips, then check the selected one
                chipMap.keys.forEach { c -> c.isChecked = false }
                chip.isChecked = true
                viewModel.setStatusFilter(status)
            }
        }
    }

    // ── UI State Rendering ──────────────────────────────────────

    private fun showLoadingState() {
        binding.layoutShimmer.visibility = View.VISIBLE
        binding.layoutVehicleList.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.layoutVehicleCount.visibility = View.GONE
    }

    private fun showVehicleList(vehicles: List<Vehicle>) {
        binding.layoutShimmer.visibility = View.GONE
        binding.layoutError.visibility = View.GONE

        if (vehicles.isEmpty()) {
            binding.layoutVehicleList.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.layoutVehicleCount.visibility = View.GONE
            return
        }

        binding.layoutEmpty.visibility = View.GONE
        binding.layoutVehicleList.visibility = View.VISIBLE
        binding.layoutVehicleCount.visibility = View.VISIBLE

        // Clear and re-render vehicle cards
        binding.layoutVehicleList.removeAllViews()

        for (vehicle in vehicles) {
            val cardView = VehicleCardView(requireContext())
            cardView.bind(vehicle, isAdmin)

            // Card click
            cardView.onCardClick = { v ->
                // Future: navigate to vehicle detail
            }

            // Edit
            cardView.onEditClick = { v ->
                showEditVehicleSheet(v)
            }

            // Delete
            cardView.onDeleteClick = { v ->
                showDeleteConfirmation(v)
            }

            // Status change
            cardView.onStatusChangeClick = { v ->
                showStatusChangeDialog(v)
            }

            // Assign driver
            cardView.onAssignDriverClick = { v ->
                showAssignDriverSheet(v)
            }

            binding.layoutVehicleList.addView(cardView)
        }
    }

    private fun showErrorState(message: String) {
        binding.layoutShimmer.visibility = View.GONE
        binding.layoutVehicleList.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutVehicleCount.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
    }

    // ── Add Vehicle ─────────────────────────────────────────────

    private fun showAddVehicleSheet() {
        val sheet = AddEditVehicleBottomSheet.newInstance()

        sheet.onCheckDuplicate = { number, callback ->
            viewModel.checkVehicleNumberExists(number, callback)
        }

        sheet.onSaveVehicle = { data ->
            viewModel.addVehicle(data)
            sheet.dismiss()
        }

        sheet.show(childFragmentManager, AddEditVehicleBottomSheet.TAG)
    }

    // ── Edit Vehicle ────────────────────────────────────────────

    private fun showEditVehicleSheet(vehicle: Vehicle) {
        val sheet = AddEditVehicleBottomSheet.newInstance(vehicle)

        sheet.onSaveVehicle = { data ->
            viewModel.updateVehicle(data)
            sheet.dismiss()
        }

        sheet.show(childFragmentManager, AddEditVehicleBottomSheet.TAG)
    }

    // ── Delete Vehicle ──────────────────────────────────────────

    private fun showDeleteConfirmation(vehicle: Vehicle) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_delete_vehicle))
            .setMessage(getString(R.string.confirm_delete_vehicle_msg))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                viewModel.deleteVehicle(vehicle.vehicleId)
            }
            .show()
    }

    // ── Status Change ───────────────────────────────────────────

    private fun showStatusChangeDialog(vehicle: Vehicle) {
        val statuses = VehicleStatus.entries.filter { it != vehicle.status }
        val statusNames = statuses.map { it.displayName }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.action_change_status))
            .setItems(statusNames) { _, which ->
                viewModel.changeVehicleStatus(vehicle.vehicleId, statuses[which])
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    // ── Assign Driver ───────────────────────────────────────────

    private fun showAssignDriverSheet(vehicle: Vehicle) {
        val currentAssignment = if (!vehicle.assignedDriverId.isNullOrBlank())
            vehicle.assignedDriverId else null

        val sheet = AssignmentBottomSheet.newInstance(
            title = getString(R.string.assign_driver_title),
            subtitle = getString(R.string.assignment_select_driver),
            currentAssignment = currentAssignment
        )

        sheet.showLoading()

        // Fetch unassigned drivers
        viewModel.getUnassignedDrivers { options ->
            sheet.setOptions(options)
            sheet.hideLoading()
        }

        sheet.onAssignConfirmed = { driverId ->
            viewModel.assignDriverToVehicle(vehicle.vehicleId, driverId)
            sheet.dismiss()
        }

        sheet.onUnassignRequested = {
            viewModel.unassignDriverFromVehicle(vehicle.vehicleId)
            sheet.dismiss()
        }

        sheet.show(childFragmentManager, AssignmentBottomSheet.TAG)
    }

    // ── Factory ─────────────────────────────────────────────────

    companion object {
        private const val ARG_IS_ADMIN = "is_admin"

        fun newInstance(isAdmin: Boolean): VehiclesTabFragment {
            return VehiclesTabFragment().apply {
                arguments = android.os.Bundle().apply {
                    putBoolean(ARG_IS_ADMIN, isAdmin)
                }
            }
        }
    }
}
