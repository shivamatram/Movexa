package com.example.movexa.ui.trips

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.movexa.R
import com.example.movexa.databinding.BottomSheetCreateTripBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet dialog for creating a new trip.
 *
 * Features:
 * - Form fields: pickup address, drop address, load description,
 *   estimated distance, notes
 * - Inline form validation with error messages
 * - Loading state during trip creation
 * - Callback with trip data map on successful submission
 *
 * Usage:
 *   val sheet = CreateTripBottomSheet()
 *   sheet.onTripCreated = { tripData -> viewModel.createTrip(tripData) }
 *   sheet.show(childFragmentManager, CreateTripBottomSheet.TAG)
 */
class CreateTripBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateTripBinding? = null
    private val binding get() = _binding!!

    // ── Callbacks ───────────────────────────────────────────────

    /**
     * Called when the user submits a validated trip creation form.
     * The map contains:
     *   "pickupAddress"     -> String
     *   "dropAddress"       -> String
     *   "loadDescription"   -> String (may be blank)
     *   "estimatedDistance"  -> Double (0.0 if empty)
     *   "notes"             -> String (may be blank)
     */
    var onTripCreated: ((Map<String, Any?>) -> Unit)? = null

    // ── Lifecycle ───────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCreateTripBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Setup ───────────────────────────────────────────────────

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnCreateTrip.setOnClickListener {
            if (validateForm()) {
                submitForm()
            }
        }
    }

    // ── Validation ──────────────────────────────────────────────

    /**
     * Validate all required form fields. Returns true if valid.
     * Sets inline error messages on invalid fields.
     */
    private fun validateForm(): Boolean {
        var isValid = true

        // Pickup address — required
        val pickup = binding.etPickupAddress.text?.toString()?.trim() ?: ""
        if (pickup.isBlank()) {
            binding.tilPickupAddress.error = getString(R.string.error_field_required)
            isValid = false
        } else if (pickup.length < 5) {
            binding.tilPickupAddress.error = getString(R.string.error_address_too_short)
            isValid = false
        } else {
            binding.tilPickupAddress.error = null
        }

        // Drop address — required
        val drop = binding.etDropAddress.text?.toString()?.trim() ?: ""
        if (drop.isBlank()) {
            binding.tilDropAddress.error = getString(R.string.error_field_required)
            isValid = false
        } else if (drop.length < 5) {
            binding.tilDropAddress.error = getString(R.string.error_address_too_short)
            isValid = false
        } else {
            binding.tilDropAddress.error = null
        }

        // Estimated distance — optional but must be numeric if provided
        val distanceStr = binding.etEstimatedDistance.text?.toString()?.trim() ?: ""
        if (distanceStr.isNotBlank()) {
            val distance = distanceStr.toDoubleOrNull()
            if (distance == null || distance <= 0) {
                binding.tilEstimatedDistance.error = getString(R.string.error_invalid_number)
                isValid = false
            } else {
                binding.tilEstimatedDistance.error = null
            }
        } else {
            binding.tilEstimatedDistance.error = null
        }

        return isValid
    }

    // ── Submission ──────────────────────────────────────────────

    /**
     * Collect form data and invoke the callback.
     */
    private fun submitForm() {
        showLoading()

        val pickup = binding.etPickupAddress.text?.toString()?.trim() ?: ""
        val drop = binding.etDropAddress.text?.toString()?.trim() ?: ""
        val loadDescription = binding.etLoadDescription.text?.toString()?.trim() ?: ""
        val distanceStr = binding.etEstimatedDistance.text?.toString()?.trim() ?: ""
        val estimatedDistance = distanceStr.toDoubleOrNull() ?: 0.0
        val notes = binding.etNotes.text?.toString()?.trim() ?: ""

        val tripData = mapOf<String, Any?>(
            "pickupAddress" to pickup,
            "dropAddress" to drop,
            "loadDescription" to loadDescription,
            "estimatedDistance" to estimatedDistance,
            "notes" to notes
        )

        onTripCreated?.invoke(tripData)
    }

    // ── Loading State ───────────────────────────────────────────

    /**
     * Show loading state — disable form, show progress.
     */
    fun showLoading() {
        _binding?.let { b ->
            b.progressCreate.visibility = View.VISIBLE
            b.btnCreateTrip.isEnabled = false
            b.btnCancel.isEnabled = false
            b.etPickupAddress.isEnabled = false
            b.etDropAddress.isEnabled = false
            b.etLoadDescription.isEnabled = false
            b.etEstimatedDistance.isEnabled = false
            b.etNotes.isEnabled = false
        }
    }

    /**
     * Hide loading state — re-enable form.
     */
    fun hideLoading() {
        _binding?.let { b ->
            b.progressCreate.visibility = View.GONE
            b.btnCreateTrip.isEnabled = true
            b.btnCancel.isEnabled = true
            b.etPickupAddress.isEnabled = true
            b.etDropAddress.isEnabled = true
            b.etLoadDescription.isEnabled = true
            b.etEstimatedDistance.isEnabled = true
            b.etNotes.isEnabled = true
        }
    }

    companion object {
        const val TAG = "CreateTripBottomSheet"
    }
}
