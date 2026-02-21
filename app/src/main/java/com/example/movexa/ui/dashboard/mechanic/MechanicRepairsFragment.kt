package com.example.movexa.ui.dashboard.mechanic

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movexa.R
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.databinding.FragmentMechanicRepairsBinding
import com.example.movexa.ui.base.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * ═══════════════════════════════════════════════════════════════════
 *  MECHANIC REPAIRS FRAGMENT
 * ═══════════════════════════════════════════════════════════════════
 *
 * Production implementation of the Repairs tab for the Mechanic
 * dashboard. Enables recording unexpected breakdown entries — issue
 * description, repair action, cost, and optional vehicle-availability
 * toggle after repair completion.
 *
 * Key features:
 *  ● Vehicle selector with status info
 *  ● Issue + Repair Done description fields
 *  ● Cost + Odometer tracking
 *  ● Parts replaced (comma-separated)
 *  ● Workshop + Notes
 *  ● Mark Available switch (vehicle → AVAILABLE after repair)
 *  ● Confirmation dialog before submission
 *  ● Recent repairs list (RecyclerView)
 *
 * ═══════════════════════════════════════════════════════════════════
 */
class MechanicRepairsFragment : BaseFragment<FragmentMechanicRepairsBinding>(
    FragmentMechanicRepairsBinding::inflate
) {

    // ─── ViewModel ──────────────────────────────────────────────
    private val viewModel: MaintenanceViewModel by viewModels()

    // ─── Adapters ───────────────────────────────────────────────
    private lateinit var repairAdapter: RepairRecordAdapter

    // ─── State ──────────────────────────────────────────────────
    private var vehicleList: List<Vehicle> = emptyList()

    // ═══════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.initialize()
    }

    // ═══════════════════════════════════════════════════════════
    //  INIT VIEWS
    // ═══════════════════════════════════════════════════════════

    override fun initViews() {
        // Repair records list
        repairAdapter = RepairRecordAdapter()
        binding.rvRecentRepairs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = repairAdapter
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SETUP LISTENERS
    // ═══════════════════════════════════════════════════════════

    override fun setupListeners() {
        // Vehicle selection
        binding.actvVehicle.setOnItemClickListener { _, _, position, _ ->
            if (position in vehicleList.indices) {
                viewModel.selectVehicle(vehicleList[position])
            }
        }

        // Submit
        binding.btnSubmitRepair.setOnClickListener {
            showSubmitConfirmation()
        }

        // Retry
        binding.btnRetry.setOnClickListener {
            viewModel.initialize()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  OBSERVE DATA
    // ═══════════════════════════════════════════════════════════

    override fun observeData() {
        // Screen state
        collectLatestFlow(viewModel.screenState) { state ->
            when (state) {
                is MaintenanceViewModel.ScreenState.Loading -> showScreenLoading()
                is MaintenanceViewModel.ScreenState.Ready -> showScreenReady()
                is MaintenanceViewModel.ScreenState.Error -> showScreenError(state.message)
            }
        }

        // Vehicles
        collectLatestFlow(viewModel.vehicles) { vehicles ->
            vehicleList = vehicles
            val labels = vehicles.map { it.displayLabel }
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                labels
            )
            binding.actvVehicle.setAdapter(adapter)
        }

        // Selected vehicle
        collectLatestFlow(viewModel.selectedVehicle) { vehicle ->
            updateVehicleUI(vehicle)
        }

        // Recent repairs
        collectLatestFlow(viewModel.recentRepairs) { repairs ->
            repairAdapter.submitList(repairs)
            val hasRecords = repairs.isNotEmpty()
            binding.rvRecentRepairs.visibility = if (hasRecords) View.VISIBLE else View.GONE
            binding.tvNoRecentRepairs.visibility = if (hasRecords) View.GONE else View.VISIBLE
        }

        // Submission state
        collectLatestFlow(viewModel.repairSubmissionState) { state ->
            handleSubmissionState(state)
        }

        // Success events
        collectFlow(viewModel.repairSuccess) { message ->
            showSuccess(message)
            clearRepairForm()
            viewModel.resetRepairForm()
        }

        // Error events
        collectFlow(viewModel.errorEvent) { message ->
            showError(message)
        }

        collectFlow(viewModel.successEvent) { message ->
            showSuccess(message)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SCREEN STATE HELPERS
    // ═══════════════════════════════════════════════════════════

    private fun showScreenLoading() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
    }

    private fun showScreenReady() {
        binding.layoutLoading.visibility = View.GONE
        binding.scrollContent.visibility = View.VISIBLE
        binding.layoutError.visibility = View.GONE
    }

    private fun showScreenError(message: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.scrollContent.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
    }

    // ═══════════════════════════════════════════════════════════
    //  VEHICLE UI
    // ═══════════════════════════════════════════════════════════

    private fun updateVehicleUI(vehicle: Vehicle?) {
        if (vehicle != null) {
            binding.layoutVehicleInfo.visibility = View.VISIBLE
            binding.cardNoVehicle.visibility = View.GONE
            binding.layoutRepairForm.visibility = View.VISIBLE
            binding.layoutRecentRepairs.visibility = View.VISIBLE

            binding.tvVehicleStatus.text =
                getString(R.string.maint_vehicle_status, vehicle.status.name)
            binding.tvVehicleStatus.setTextColor(
                requireContext().getColor(
                    if (vehicle.status == VehicleStatus.SERVICE)
                        R.color.maint_vehicle_status_service
                    else R.color.maint_vehicle_status_available
                )
            )

            binding.tvVehicleOdometer.text =
                getString(R.string.maint_vehicle_odometer, vehicle.lastOdometer)

            // Pre-fill odometer
            if (binding.etRepairOdometer.text.isNullOrBlank()) {
                binding.etRepairOdometer.setText(vehicle.lastOdometer.toString())
            }
        } else {
            binding.layoutVehicleInfo.visibility = View.GONE
            binding.cardNoVehicle.visibility = View.VISIBLE
            binding.layoutRepairForm.visibility = View.GONE
            binding.layoutRecentRepairs.visibility = View.GONE
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SUBMISSION
    // ═══════════════════════════════════════════════════════════

    private fun showSubmitConfirmation() {
        val vehicle = viewModel.selectedVehicle.value ?: return
        val issue = binding.etIssue.text?.toString() ?: ""
        val cost = binding.etRepairCost.text?.toString()?.toDoubleOrNull() ?: 0.0

        if (issue.isBlank()) {
            binding.tvValidationError.text =
                getString(R.string.maint_issue_hint)
            binding.tvValidationError.visibility = View.VISIBLE
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.maint_confirm_submit_title))
            .setMessage(
                getString(
                    R.string.maint_confirm_repair_msg,
                    vehicle.number,
                    issue,
                    cost
                )
            )
            .setPositiveButton(getString(R.string.maint_confirm_yes)) { _, _ ->
                submitRepair()
            }
            .setNegativeButton(getString(R.string.maint_confirm_no), null)
            .show()
    }

    private fun submitRepair() {
        viewModel.submitRepairRecord(
            issue = binding.etIssue.text?.toString() ?: "",
            repairDone = binding.etRepairDone.text?.toString() ?: "",
            costText = binding.etRepairCost.text?.toString() ?: "",
            odometerText = binding.etRepairOdometer.text?.toString() ?: "",
            partsReplaced = binding.etPartsReplaced.text?.toString() ?: "",
            notes = binding.etNotes.text?.toString() ?: "",
            workshopName = binding.etWorkshop.text?.toString() ?: "",
            markAvailable = binding.switchMarkAvailable.isChecked
        )
    }

    private fun handleSubmissionState(state: MaintenanceViewModel.SubmissionState) {
        when (state) {
            is MaintenanceViewModel.SubmissionState.Idle -> {
                binding.btnSubmitRepair.isEnabled = true
                binding.tvValidationError.visibility = View.GONE
            }
            is MaintenanceViewModel.SubmissionState.Validating -> {
                binding.btnSubmitRepair.isEnabled = false
            }
            is MaintenanceViewModel.SubmissionState.Submitting -> {
                binding.btnSubmitRepair.isEnabled = false
                binding.tvValidationError.visibility = View.GONE
            }
            is MaintenanceViewModel.SubmissionState.Success -> {
                binding.btnSubmitRepair.isEnabled = true
            }
            is MaintenanceViewModel.SubmissionState.ValidationError -> {
                binding.btnSubmitRepair.isEnabled = true
                binding.tvValidationError.text = state.message
                binding.tvValidationError.visibility = View.VISIBLE
            }
            is MaintenanceViewModel.SubmissionState.Error -> {
                binding.btnSubmitRepair.isEnabled = true
                binding.tvValidationError.text = state.message
                binding.tvValidationError.visibility = View.VISIBLE
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FORM RESET
    // ═══════════════════════════════════════════════════════════

    private fun clearRepairForm() {
        binding.etIssue.text?.clear()
        binding.etRepairDone.text?.clear()
        binding.etRepairCost.text?.clear()
        binding.etRepairOdometer.text?.clear()
        binding.etPartsReplaced.text?.clear()
        binding.etWorkshop.text?.clear()
        binding.etNotes.text?.clear()
        binding.switchMarkAvailable.isChecked = true
        binding.tvValidationError.visibility = View.GONE

        // Re-fill odometer from current vehicle
        val vehicle = viewModel.selectedVehicle.value
        if (vehicle != null) {
            binding.etRepairOdometer.setText(vehicle.lastOdometer.toString())
        }
    }
}
