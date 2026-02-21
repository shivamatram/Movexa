package com.example.movexa.ui.fleet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.example.movexa.R
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.VehicleType
import com.example.movexa.databinding.BottomSheetAddVehicleBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet dialog for adding or editing a vehicle.
 *
 * Features:
 * - Form validation with inline error display
 * - Vehicle type dropdown with all VehicleType enum values
 * - Fuel type dropdown (Petrol, Diesel, CNG, Electric, Hybrid)
 * - Duplicate vehicle number check callback
 * - Loading state during save operation
 * - Pre-fill for edit mode
 *
 * Usage:
 *   val sheet = AddEditVehicleBottomSheet.newInstance(existingVehicle)
 *   sheet.onSaveVehicle = { vehicleData -> ... }
 *   sheet.show(parentFragmentManager, sheet.tag)
 */
class AddEditVehicleBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddVehicleBinding? = null
    private val binding get() = _binding!!

    // ── Callbacks ───────────────────────────────────────────────
    /**
     * Called when the user taps Save with validated vehicle data.
     * The map contains all form field values.
     */
    var onSaveVehicle: ((Map<String, Any?>) -> Unit)? = null

    /**
     * Called to check if a vehicle number is already in use.
     * Should invoke the callback with true if duplicate.
     */
    var onCheckDuplicate: ((String, (Boolean) -> Unit) -> Unit)? = null

    // ── State ───────────────────────────────────────────────────
    private var editVehicle: Vehicle? = null
    private var selectedVehicleType: VehicleType? = null
    private var isEditMode: Boolean = false

    // ── Fuel Type Options ───────────────────────────────────────
    private val fuelTypes = listOf("Petrol", "Diesel", "CNG", "Electric", "Hybrid")

    // ── Lifecycle ───────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddVehicleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDropdowns()
        setupListeners()

        // Pre-fill if editing
        editVehicle?.let { prefillForm(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Setup ───────────────────────────────────────────────────

    private fun setupDropdowns() {
        // Vehicle type dropdown
        val vehicleTypeNames = VehicleType.entries.map { it.displayName }
        val typeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            vehicleTypeNames
        )
        binding.actvVehicleType.setAdapter(typeAdapter)
        binding.actvVehicleType.setOnItemClickListener { _, _, position, _ ->
            selectedVehicleType = VehicleType.entries[position]
            binding.tilVehicleType.error = null
        }

        // Fuel type dropdown
        val fuelAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            fuelTypes
        )
        binding.actvFuelType.setAdapter(fuelAdapter)
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            validateAndSave()
        }
    }

    // ── Pre-fill (Edit Mode) ────────────────────────────────────

    private fun prefillForm(vehicle: Vehicle) {
        isEditMode = true
        binding.tvTitle.text = getString(R.string.vehicle_edit_title)

        binding.etVehicleNumber.setText(vehicle.number)
        binding.etVehicleNumber.isEnabled = false // Can't change vehicle number

        binding.actvVehicleType.setText(vehicle.type.displayName, false)
        selectedVehicleType = vehicle.type

        binding.etCapacity.setText(vehicle.capacity.toString())
        binding.etMake.setText(vehicle.make)
        binding.etModel.setText(vehicle.model)

        if (vehicle.year > 0) {
            binding.etYear.setText(vehicle.year.toString())
        }

        if (vehicle.fuelType.isNotBlank()) {
            binding.actvFuelType.setText(vehicle.fuelType, false)
        }
    }

    // ── Validation ──────────────────────────────────────────────

    private fun validateAndSave() {
        clearErrors()
        var isValid = true

        val vehicleNumber = binding.etVehicleNumber.text?.toString()?.trim()?.uppercase() ?: ""
        val capacityText = binding.etCapacity.text?.toString()?.trim() ?: ""
        val make = binding.etMake.text?.toString()?.trim() ?: ""
        val model = binding.etModel.text?.toString()?.trim() ?: ""
        val yearText = binding.etYear.text?.toString()?.trim() ?: ""
        val fuelType = binding.actvFuelType.text?.toString()?.trim() ?: ""

        // Vehicle number validation
        if (vehicleNumber.isBlank()) {
            binding.tilVehicleNumber.error = getString(R.string.error_field_required)
            isValid = false
        } else if (vehicleNumber.length < 4) {
            binding.tilVehicleNumber.error = "Vehicle number must be at least 4 characters"
            isValid = false
        }

        // Vehicle type validation
        if (selectedVehicleType == null) {
            binding.tilVehicleType.error = getString(R.string.error_field_required)
            isValid = false
        }

        // Capacity validation
        val capacity = capacityText.toIntOrNull()
        if (capacityText.isBlank()) {
            binding.tilCapacity.error = getString(R.string.error_field_required)
            isValid = false
        } else if (capacity == null || capacity <= 0 || capacity > 100) {
            binding.tilCapacity.error = "Enter a valid capacity (1-100)"
            isValid = false
        }

        // Year validation (optional)
        val year = if (yearText.isNotBlank()) {
            val parsedYear = yearText.toIntOrNull()
            if (parsedYear == null || parsedYear < 1980 || parsedYear > 2030) {
                binding.tilYear.error = "Enter a valid year (1980-2030)"
                isValid = false
                null
            } else {
                parsedYear
            }
        } else {
            0
        }

        if (!isValid) return

        // Check for duplicate vehicle number (only in create mode)
        if (!isEditMode && onCheckDuplicate != null) {
            setLoadingState(true)
            onCheckDuplicate?.invoke(vehicleNumber) { isDuplicate ->
                _binding?.let { b ->
                    b.root.post {
                        setLoadingState(false)
                        if (isDuplicate) {
                            b.tilVehicleNumber.error = "This vehicle number already exists"
                        } else {
                            emitSaveData(vehicleNumber, capacity!!, make, model, year ?: 0, fuelType)
                        }
                    }
                }
            }
        } else {
            emitSaveData(vehicleNumber, capacity!!, make, model, year ?: 0, fuelType)
        }
    }

    private fun emitSaveData(
        vehicleNumber: String,
        capacity: Int,
        make: String,
        model: String,
        year: Int,
        fuelType: String
    ) {
        val data = mapOf(
            "number" to vehicleNumber,
            "type" to (selectedVehicleType ?: VehicleType.OTHER),
            "capacity" to capacity,
            "make" to make,
            "model" to model,
            "year" to year,
            "fuelType" to fuelType,
            "isEdit" to isEditMode,
            "vehicleId" to (editVehicle?.vehicleId ?: "")
        )
        onSaveVehicle?.invoke(data)
    }

    private fun clearErrors() {
        binding.tilVehicleNumber.error = null
        binding.tilVehicleType.error = null
        binding.tilCapacity.error = null
        binding.tilMake.error = null
        binding.tilModel.error = null
        binding.tilYear.error = null
    }

    // ── Loading State ───────────────────────────────────────────

    fun setLoadingState(loading: Boolean) {
        _binding?.let { b ->
            b.progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
            b.btnSave.isEnabled = !loading
            b.btnCancel.isEnabled = !loading
            b.etVehicleNumber.isEnabled = !loading && !isEditMode
            b.actvVehicleType.isEnabled = !loading
            b.etCapacity.isEnabled = !loading
            b.etMake.isEnabled = !loading
            b.etModel.isEnabled = !loading
            b.etYear.isEnabled = !loading
            b.actvFuelType.isEnabled = !loading
        }
    }

    // ── Factory ─────────────────────────────────────────────────

    companion object {
        const val TAG = "AddEditVehicleBottomSheet"

        /**
         * Create a new instance for adding a vehicle.
         */
        fun newInstance(): AddEditVehicleBottomSheet {
            return AddEditVehicleBottomSheet()
        }

        /**
         * Create a new instance for editing an existing vehicle.
         */
        fun newInstance(vehicle: Vehicle): AddEditVehicleBottomSheet {
            return AddEditVehicleBottomSheet().apply {
                editVehicle = vehicle
            }
        }
    }
}
