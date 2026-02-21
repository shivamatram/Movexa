package com.example.movexa.ui.dashboard.mechanic

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movexa.R
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.ServiceType
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.databinding.FragmentMechanicServiceBinding
import com.example.movexa.service.MaintenanceScheduler
import com.example.movexa.ui.base.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.NumberFormat
import java.util.Locale

/**
 * ═══════════════════════════════════════════════════════════════════
 *  MECHANIC SERVICE FRAGMENT
 * ═══════════════════════════════════════════════════════════════════
 *
 * Production implementation of the Service Tasks tab for the Mechanic
 * dashboard. Enables recording scheduled service events for fleet
 * vehicles — with auto-calculated next-service km, real-time preview,
 * maintenance overview, and recent service history.
 *
 * Key features:
 *  ● Vehicle selector with status info + quick actions
 *  ● Service type dropdown (14 types)
 *  ● Auto next-service-km preview
 *  ● Default interval hint per service type
 *  ● Confirmation dialog before submission
 *  ● Recent services list (RecyclerView)
 *  ● Maintenance overview (overdue / due-soon badges)
 *  ● Vehicle status quick-toggle (In Service / Available)
 *
 * ═══════════════════════════════════════════════════════════════════
 */
class MechanicServiceFragment : BaseFragment<FragmentMechanicServiceBinding>(
    FragmentMechanicServiceBinding::inflate
) {

    // ─── ViewModel ──────────────────────────────────────────────
    private val viewModel: MaintenanceViewModel by viewModels()

    // ─── Adapters ───────────────────────────────────────────────
    private lateinit var serviceAdapter: ServiceRecordAdapter
    private lateinit var statusAdapter: MaintenanceStatusAdapter

    // ─── State ──────────────────────────────────────────────────
    private var vehicleList: List<Vehicle> = emptyList()
    private var selectedServiceType: ServiceType = ServiceType.OIL_CHANGE
    private val numberFormat = NumberFormat.getNumberInstance(Locale("en", "IN"))

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
        // Service record list
        serviceAdapter = ServiceRecordAdapter()
        binding.rvRecentServices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = serviceAdapter
        }

        // Maintenance status list
        statusAdapter = MaintenanceStatusAdapter()
        binding.rvMaintenanceStatus.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = statusAdapter
        }

        // Service type dropdown
        val serviceTypeNames = ServiceType.entries.map { it.displayName }
        val typeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            serviceTypeNames
        )
        binding.actvServiceType.setAdapter(typeAdapter)
    }

    // ═══════════════════════════════════════════════════════════
    //  SETUP LISTENERS
    // ═══════════════════════════════════════════════════════════

    override fun setupListeners() {
        // Vehicle selection
        binding.actvVehicle.setOnItemClickListener { _, _, position, _ ->
            if (position in vehicleList.indices) {
                val vehicle = vehicleList[position]
                viewModel.selectVehicle(vehicle)
            }
        }

        // Service type selection
        binding.actvServiceType.setOnItemClickListener { _, _, position, _ ->
            val types = ServiceType.entries
            if (position in types.indices) {
                selectedServiceType = types[position]
                // Show default interval hint
                val interval = MaintenanceScheduler.SERVICE_INTERVALS[selectedServiceType] ?: 10_000L
                binding.tvDefaultInterval.text =
                    getString(R.string.maint_default_interval, interval)
                binding.tvDefaultInterval.visibility = View.VISIBLE

                // Update next service preview
                val odometerText = binding.etOdometer.text?.toString() ?: ""
                viewModel.updateNextServicePreview(odometerText, selectedServiceType)
            }
        }

        // Odometer text change → update next service preview
        binding.etOdometer.doAfterTextChanged { text ->
            viewModel.updateNextServicePreview(text?.toString() ?: "", selectedServiceType)
        }

        // Submit button
        binding.btnSubmitService.setOnClickListener {
            showSubmitConfirmation()
        }

        // Quick actions
        binding.btnSetInService.setOnClickListener {
            viewModel.markVehicleInService()
        }

        binding.btnSetAvailable.setOnClickListener {
            viewModel.markVehicleAvailable()
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

        // Vehicles list
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

        // Next service km preview
        collectLatestFlow(viewModel.nextServiceKmPreview) { nextKm ->
            if (nextKm > 0) {
                binding.layoutNextService.visibility = View.VISIBLE
                binding.tvNextServiceValue.text =
                    getString(R.string.maint_next_service_value, nextKm)
            } else {
                binding.layoutNextService.visibility = View.GONE
            }
        }

        // Recent services
        collectLatestFlow(viewModel.recentServices) { services ->
            serviceAdapter.submitList(services)
            val hasRecords = services.isNotEmpty()
            binding.rvRecentServices.visibility = if (hasRecords) View.VISIBLE else View.GONE
            binding.tvNoRecentServices.visibility = if (hasRecords) View.GONE else View.VISIBLE
        }

        // Maintenance statuses
        collectLatestFlow(viewModel.maintenanceStatuses) { statuses ->
            statusAdapter.submitList(statuses)
            binding.layoutMaintenanceOverview.visibility =
                if (statuses.isNotEmpty()) View.VISIBLE else View.GONE
        }

        // Submission state
        collectLatestFlow(viewModel.serviceSubmissionState) { state ->
            handleSubmissionState(state)
        }

        // Success events
        collectFlow(viewModel.serviceSuccess) { message ->
            showSuccess(message)
            clearServiceForm()
            viewModel.resetServiceForm()
        }

        // Error events
        collectFlow(viewModel.errorEvent) { message ->
            showError(message)
        }

        // Success events from base
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
            // Show vehicle info
            binding.layoutVehicleInfo.visibility = View.VISIBLE
            binding.layoutQuickActions.visibility = View.VISIBLE
            binding.cardNoVehicle.visibility = View.GONE
            binding.layoutServiceForm.visibility = View.VISIBLE
            binding.layoutRecentServices.visibility = View.VISIBLE

            // Status
            binding.tvVehicleStatus.text =
                getString(R.string.maint_vehicle_status, vehicle.status.name)
            binding.tvVehicleStatus.setTextColor(
                requireContext().getColor(
                    if (vehicle.status == VehicleStatus.SERVICE)
                        R.color.maint_vehicle_status_service
                    else R.color.maint_vehicle_status_available
                )
            )

            // Odometer
            binding.tvVehicleOdometer.text =
                getString(R.string.maint_vehicle_odometer, vehicle.lastOdometer)

            // Pre-fill odometer
            if (binding.etOdometer.text.isNullOrBlank()) {
                binding.etOdometer.setText(vehicle.lastOdometer.toString())
                viewModel.updateNextServicePreview(
                    vehicle.lastOdometer.toString(),
                    selectedServiceType
                )
            }
        } else {
            binding.layoutVehicleInfo.visibility = View.GONE
            binding.layoutQuickActions.visibility = View.GONE
            binding.cardNoVehicle.visibility = View.VISIBLE
            binding.layoutServiceForm.visibility = View.GONE
            binding.layoutRecentServices.visibility = View.GONE
            binding.layoutMaintenanceOverview.visibility = View.GONE
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SUBMISSION
    // ═══════════════════════════════════════════════════════════

    private fun showSubmitConfirmation() {
        val vehicle = viewModel.selectedVehicle.value ?: return
        val odometerText = binding.etOdometer.text?.toString() ?: ""
        val odometer = odometerText.toLongOrNull() ?: 0L

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.maint_confirm_submit_title))
            .setMessage(
                getString(
                    R.string.maint_confirm_service_msg,
                    selectedServiceType.displayName,
                    vehicle.number,
                    odometer
                )
            )
            .setPositiveButton(getString(R.string.maint_confirm_yes)) { _, _ ->
                submitService()
            }
            .setNegativeButton(getString(R.string.maint_confirm_no), null)
            .show()
    }

    private fun submitService() {
        val odometerText = binding.etOdometer.text?.toString() ?: ""
        val costText = binding.etCost.text?.toString() ?: ""
        val description = binding.etDescription.text?.toString() ?: ""
        val workshop = binding.etWorkshop.text?.toString() ?: ""
        val completed = binding.switchCompleted.isChecked

        viewModel.submitServiceRecord(
            odometerText = odometerText,
            serviceType = selectedServiceType,
            costText = costText,
            description = description,
            workshopName = workshop,
            markCompleted = completed
        )
    }

    private fun handleSubmissionState(state: MaintenanceViewModel.SubmissionState) {
        when (state) {
            is MaintenanceViewModel.SubmissionState.Idle -> {
                binding.btnSubmitService.isEnabled = true
                binding.tvValidationError.visibility = View.GONE
            }
            is MaintenanceViewModel.SubmissionState.Validating -> {
                binding.btnSubmitService.isEnabled = false
            }
            is MaintenanceViewModel.SubmissionState.Submitting -> {
                binding.btnSubmitService.isEnabled = false
                binding.tvValidationError.visibility = View.GONE
            }
            is MaintenanceViewModel.SubmissionState.Success -> {
                binding.btnSubmitService.isEnabled = true
            }
            is MaintenanceViewModel.SubmissionState.ValidationError -> {
                binding.btnSubmitService.isEnabled = true
                binding.tvValidationError.text = state.message
                binding.tvValidationError.visibility = View.VISIBLE
            }
            is MaintenanceViewModel.SubmissionState.Error -> {
                binding.btnSubmitService.isEnabled = true
                binding.tvValidationError.text = state.message
                binding.tvValidationError.visibility = View.VISIBLE
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FORM RESET
    // ═══════════════════════════════════════════════════════════

    private fun clearServiceForm() {
        binding.actvServiceType.setText("", false)
        binding.etOdometer.text?.clear()
        binding.etCost.text?.clear()
        binding.etDescription.text?.clear()
        binding.etWorkshop.text?.clear()
        binding.switchCompleted.isChecked = true
        binding.tvDefaultInterval.visibility = View.GONE
        binding.layoutNextService.visibility = View.GONE
        binding.tvValidationError.visibility = View.GONE
        selectedServiceType = ServiceType.OIL_CHANGE

        // Re-fill odometer from vehicle
        val vehicle = viewModel.selectedVehicle.value
        if (vehicle != null) {
            binding.etOdometer.setText(vehicle.lastOdometer.toString())
        }
    }
}
