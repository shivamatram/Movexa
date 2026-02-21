package com.example.movexa.ui.dashboard.mechanic

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movexa.R
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.databinding.FragmentMechanicPartsBinding
import com.example.movexa.service.MaintenanceScheduler
import com.example.movexa.ui.base.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * ═══════════════════════════════════════════════════════════════════
 *  MECHANIC PARTS FRAGMENT
 * ═══════════════════════════════════════════════════════════════════
 *
 * Production implementation of the Parts Inventory tab for the
 * Mechanic dashboard. Enables tracking part replacements, generating
 * future replacement reminders, and monitoring parts health.
 *
 * Key features:
 *  ● Vehicle selector with status info
 *  ● Part replacement form (name, number, odometer, expected life,
 *    cost, brand, supplier, warranty, notes)
 *  ● Live next-replacement preview
 *  ● Parts health overview with usage progress bars
 *  ● Recent parts list (RecyclerView)
 *  ● Confirmation dialog before submission
 *
 * ═══════════════════════════════════════════════════════════════════
 */
class MechanicPartsFragment : BaseFragment<FragmentMechanicPartsBinding>(
    FragmentMechanicPartsBinding::inflate
) {

    // ─── ViewModel ──────────────────────────────────────────────
    private val viewModel: MaintenanceViewModel by viewModels()

    // ─── Adapters ───────────────────────────────────────────────
    private lateinit var partAdapter: PartRecordAdapter
    private lateinit var partHealthAdapter: PartHealthAdapter

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
        // Recent parts list
        partAdapter = PartRecordAdapter()
        binding.rvRecentParts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = partAdapter
        }

        // Parts health list
        partHealthAdapter = PartHealthAdapter()
        binding.rvPartsHealth.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = partHealthAdapter
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

        // Live next-replacement preview
        binding.etChangedKm.doAfterTextChanged { updateNextReplacementPreview() }
        binding.etLifeKm.doAfterTextChanged { updateNextReplacementPreview() }

        // Submit
        binding.btnSubmitPart.setOnClickListener {
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

        // Recent parts
        collectLatestFlow(viewModel.recentParts) { parts ->
            val vehicle = viewModel.selectedVehicle.value
            if (vehicle != null) {
                partAdapter.updateCurrentOdometer(vehicle.lastOdometer)
            }
            partAdapter.submitList(parts)
            val hasRecords = parts.isNotEmpty()
            binding.rvRecentParts.visibility = if (hasRecords) View.VISIBLE else View.GONE
            binding.tvNoRecentParts.visibility = if (hasRecords) View.GONE else View.VISIBLE
        }

        // Part statuses (health overview)
        collectLatestFlow(viewModel.partStatuses) { statuses ->
            partHealthAdapter.submitList(statuses)
            binding.layoutPartsOverview.visibility =
                if (statuses.isNotEmpty()) View.VISIBLE else View.GONE
        }

        // Submission state
        collectLatestFlow(viewModel.partSubmissionState) { state ->
            handleSubmissionState(state)
        }

        // Success events
        collectFlow(viewModel.partSuccess) { message ->
            showSuccess(message)
            clearPartForm()
            viewModel.resetPartForm()
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
            binding.layoutPartForm.visibility = View.VISIBLE
            binding.layoutRecentParts.visibility = View.VISIBLE

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

            // Pre-fill changed at km
            if (binding.etChangedKm.text.isNullOrBlank()) {
                binding.etChangedKm.setText(vehicle.lastOdometer.toString())
            }

            // Update existing parts with current odometer
            partAdapter.updateCurrentOdometer(vehicle.lastOdometer)
        } else {
            binding.layoutVehicleInfo.visibility = View.GONE
            binding.cardNoVehicle.visibility = View.VISIBLE
            binding.layoutPartForm.visibility = View.GONE
            binding.layoutRecentParts.visibility = View.GONE
            binding.layoutPartsOverview.visibility = View.GONE
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  NEXT REPLACEMENT PREVIEW
    // ═══════════════════════════════════════════════════════════

    private fun updateNextReplacementPreview() {
        val changedAtKm = binding.etChangedKm.text?.toString()?.toLongOrNull() ?: 0L
        val lifeKm = binding.etLifeKm.text?.toString()?.toLongOrNull() ?: 0L

        if (changedAtKm > 0 && lifeKm > 0) {
            val nextReplacementKm = changedAtKm + lifeKm
            binding.layoutNextReplacement.visibility = View.VISIBLE
            binding.tvNextReplacementValue.text =
                getString(R.string.maint_next_replacement, nextReplacementKm)
        } else {
            binding.layoutNextReplacement.visibility = View.GONE
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SUBMISSION
    // ═══════════════════════════════════════════════════════════

    private fun showSubmitConfirmation() {
        val vehicle = viewModel.selectedVehicle.value ?: return
        val partName = binding.etPartName.text?.toString() ?: ""
        val changedAtKm = binding.etChangedKm.text?.toString()?.toLongOrNull() ?: 0L
        val lifeKm = binding.etLifeKm.text?.toString()?.toLongOrNull() ?: 0L

        if (partName.isBlank()) {
            binding.tvValidationError.text =
                getString(R.string.maint_part_name_hint)
            binding.tvValidationError.visibility = View.VISIBLE
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.maint_confirm_submit_title))
            .setMessage(
                getString(
                    R.string.maint_confirm_part_msg,
                    partName,
                    vehicle.number,
                    changedAtKm,
                    lifeKm
                )
            )
            .setPositiveButton(getString(R.string.maint_confirm_yes)) { _, _ ->
                submitPart()
            }
            .setNegativeButton(getString(R.string.maint_confirm_no), null)
            .show()
    }

    private fun submitPart() {
        viewModel.submitPartReplacement(
            partName = binding.etPartName.text?.toString() ?: "",
            partNumber = binding.etPartNumber.text?.toString() ?: "",
            changedAtKmText = binding.etChangedKm.text?.toString() ?: "",
            expectedLifeKmText = binding.etLifeKm.text?.toString() ?: "",
            costText = binding.etPartCost.text?.toString() ?: "",
            brand = binding.etBrand.text?.toString() ?: "",
            supplierName = binding.etSupplier.text?.toString() ?: "",
            warrantyKmText = binding.etWarrantyKm.text?.toString() ?: "",
            notes = binding.etNotes.text?.toString() ?: ""
        )
    }

    private fun handleSubmissionState(state: MaintenanceViewModel.SubmissionState) {
        when (state) {
            is MaintenanceViewModel.SubmissionState.Idle -> {
                binding.btnSubmitPart.isEnabled = true
                binding.tvValidationError.visibility = View.GONE
            }
            is MaintenanceViewModel.SubmissionState.Validating -> {
                binding.btnSubmitPart.isEnabled = false
            }
            is MaintenanceViewModel.SubmissionState.Submitting -> {
                binding.btnSubmitPart.isEnabled = false
                binding.tvValidationError.visibility = View.GONE
            }
            is MaintenanceViewModel.SubmissionState.Success -> {
                binding.btnSubmitPart.isEnabled = true
            }
            is MaintenanceViewModel.SubmissionState.ValidationError -> {
                binding.btnSubmitPart.isEnabled = true
                binding.tvValidationError.text = state.message
                binding.tvValidationError.visibility = View.VISIBLE
            }
            is MaintenanceViewModel.SubmissionState.Error -> {
                binding.btnSubmitPart.isEnabled = true
                binding.tvValidationError.text = state.message
                binding.tvValidationError.visibility = View.VISIBLE
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FORM RESET
    // ═══════════════════════════════════════════════════════════

    private fun clearPartForm() {
        binding.etPartName.text?.clear()
        binding.etPartNumber.text?.clear()
        binding.etChangedKm.text?.clear()
        binding.etLifeKm.text?.clear()
        binding.etPartCost.text?.clear()
        binding.etBrand.text?.clear()
        binding.etSupplier.text?.clear()
        binding.etWarrantyKm.text?.clear()
        binding.etNotes.text?.clear()
        binding.layoutNextReplacement.visibility = View.GONE
        binding.tvValidationError.visibility = View.GONE

        // Re-fill changedAt from vehicle
        val vehicle = viewModel.selectedVehicle.value
        if (vehicle != null) {
            binding.etChangedKm.setText(vehicle.lastOdometer.toString())
        }
    }
}
