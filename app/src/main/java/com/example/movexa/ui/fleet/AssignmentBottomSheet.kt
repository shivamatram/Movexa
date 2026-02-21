package com.example.movexa.ui.fleet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import com.example.movexa.R
import com.example.movexa.databinding.BottomSheetAssignmentBinding
import com.example.movexa.databinding.ItemAssignmentOptionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet dialog for assigning a driver to a vehicle (or vice versa).
 *
 * Features:
 * - Radio option list for selectable items
 * - Current assignment display with unassign option
 * - Loading state while fetching options
 * - Empty state when no options available
 *
 * This is a generic assignment sheet that works in two modes:
 * 1. Assign Driver to Vehicle: shows list of unassigned drivers
 * 2. Assign Vehicle to Driver: shows list of available vehicles
 *
 * Usage:
 *   val sheet = AssignmentBottomSheet.newInstance("Assign Driver", "Select a driver")
 *   sheet.setOptions(listOf(AssignmentOption("id1", "Name", "Info"), ...))
 *   sheet.onAssignConfirmed = { selectedId -> ... }
 *   sheet.onUnassignRequested = { ... }
 *   sheet.show(parentFragmentManager, sheet.tag)
 */
class AssignmentBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAssignmentBinding? = null
    private val binding get() = _binding!!

    // ── Callbacks ───────────────────────────────────────────────
    var onAssignConfirmed: ((String) -> Unit)? = null
    var onUnassignRequested: (() -> Unit)? = null

    // ── State ───────────────────────────────────────────────────
    private var title: String = ""
    private var subtitle: String = ""
    private var currentAssignmentLabel: String? = null
    private var options: List<AssignmentOption> = emptyList()
    private var selectedOptionId: String? = null
    private val radioButtons = mutableListOf<Pair<RadioButton, String>>()

    // ── Lifecycle ───────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAssignmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupListeners()
        renderOptions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        radioButtons.clear()
        _binding = null
    }

    // ── Setup ───────────────────────────────────────────────────

    private fun setupUI() {
        binding.tvTitle.text = title
        binding.tvSubtitle.text = subtitle

        // Current assignment
        if (!currentAssignmentLabel.isNullOrBlank()) {
            binding.cardCurrentAssignment.visibility = View.VISIBLE
            binding.tvCurrentAssignment.text = currentAssignmentLabel
        } else {
            binding.cardCurrentAssignment.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnConfirmAssignment.setOnClickListener {
            selectedOptionId?.let { id ->
                onAssignConfirmed?.invoke(id)
            }
        }

        binding.btnUnassign.setOnClickListener {
            onUnassignRequested?.invoke()
        }
    }

    // ── Options Rendering ───────────────────────────────────────

    /**
     * Set the list of options dynamically (call before or after showing).
     */
    fun setOptions(newOptions: List<AssignmentOption>) {
        options = newOptions
        if (_binding != null) {
            renderOptions()
        }
    }

    /**
     * Show loading indicator while options are being fetched.
     */
    fun showLoading() {
        _binding?.let { b ->
            b.progressLoading.visibility = View.VISIBLE
            b.layoutOptions.visibility = View.GONE
            b.tvEmpty.visibility = View.GONE
        }
    }

    /**
     * Hide loading and show options or empty state.
     */
    fun hideLoading() {
        _binding?.let { b ->
            b.progressLoading.visibility = View.GONE
            renderOptions()
        }
    }

    /**
     * Enable/disable the confirm button during processing.
     */
    fun setProcessing(processing: Boolean) {
        _binding?.let { b ->
            b.btnConfirmAssignment.isEnabled = !processing && selectedOptionId != null
            b.btnCancel.isEnabled = !processing
            b.btnUnassign.isEnabled = !processing
        }
    }

    private fun renderOptions() {
        val b = _binding ?: return
        b.layoutOptions.removeAllViews()
        radioButtons.clear()

        if (options.isEmpty()) {
            b.layoutOptions.visibility = View.GONE
            b.tvEmpty.visibility = View.VISIBLE
            b.btnConfirmAssignment.isEnabled = false
            return
        }

        b.layoutOptions.visibility = View.VISIBLE
        b.tvEmpty.visibility = View.GONE

        for (option in options) {
            val optionBinding = ItemAssignmentOptionBinding.inflate(
                LayoutInflater.from(requireContext()),
                b.layoutOptions,
                false
            )

            optionBinding.rbOption.text = ""
            optionBinding.tvOptionTitle.text = option.title
            optionBinding.tvOptionSubtitle.text = option.subtitle

            if (option.subtitle.isBlank()) {
                optionBinding.tvOptionSubtitle.visibility = View.GONE
            }

            radioButtons.add(optionBinding.rbOption to option.id)

            // Click on the entire row selects the radio button
            optionBinding.root.setOnClickListener {
                selectOption(option.id)
            }
            optionBinding.rbOption.setOnClickListener {
                selectOption(option.id)
            }

            b.layoutOptions.addView(optionBinding.root)
        }
    }

    private fun selectOption(optionId: String) {
        selectedOptionId = optionId
        // Update radio button states
        for ((radio, id) in radioButtons) {
            radio.isChecked = (id == optionId)
        }
        _binding?.btnConfirmAssignment?.isEnabled = true
    }

    // ── Data Class ──────────────────────────────────────────────

    /**
     * Represents a selectable option in the assignment list.
     */
    data class AssignmentOption(
        val id: String,
        val title: String,
        val subtitle: String = ""
    )

    // ── Factory ─────────────────────────────────────────────────

    companion object {
        const val TAG = "AssignmentBottomSheet"

        /**
         * Create a new assignment bottom sheet.
         *
         * @param title Sheet title (e.g., "Assign Driver")
         * @param subtitle Description text (e.g., "Select a driver to assign")
         * @param currentAssignment Current assignment label for unassign UI, or null
         */
        fun newInstance(
            title: String,
            subtitle: String,
            currentAssignment: String? = null
        ): AssignmentBottomSheet {
            return AssignmentBottomSheet().apply {
                this.title = title
                this.subtitle = subtitle
                this.currentAssignmentLabel = currentAssignment
            }
        }
    }
}
