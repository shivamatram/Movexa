package com.example.movexa.ui.trips

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movexa.R
import com.example.movexa.databinding.BottomSheetSmartAssignBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet dialog for smart trip assignment.
 *
 * Displays a list of eligible vehicle+driver pairs for assignment,
 * with radio-button single selection. Shows trip summary info
 * (route + distance) and handles loading / empty states.
 *
 * Usage:
 *   val sheet = SmartAssignmentBottomSheet.newInstance(
 *       routeSummary = "Mumbai → Pune",
 *       distanceSummary = "Est. 350 km"
 *   )
 *   sheet.onAssignSelected = { vehicleId, driverId -> viewModel.assignTrip(...) }
 *   sheet.show(childFragmentManager, SmartAssignmentBottomSheet.TAG)
 */
class SmartAssignmentBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSmartAssignBinding? = null
    private val binding get() = _binding!!

    // ── Adapter ─────────────────────────────────────────────────
    private val optionAdapter = SmartAssignOptionAdapter()

    // ── Callbacks ───────────────────────────────────────────────

    /**
     * Called when the user confirms assignment with the selected option.
     * Provides vehicleId and driverId of the selected pair.
     */
    var onAssignSelected: ((vehicleId: String, driverId: String) -> Unit)? = null

    // ── Lifecycle ───────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSmartAssignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Setup ───────────────────────────────────────────────────

    private fun setupViews() {
        // Trip summary from arguments
        val routeSummary = arguments?.getString(ARG_ROUTE_SUMMARY) ?: ""
        val distanceSummary = arguments?.getString(ARG_DISTANCE_SUMMARY) ?: ""
        binding.tvAssignTripRoute.text = routeSummary
        binding.tvAssignTripDistance.text = distanceSummary

        // RecyclerView
        binding.rvEligibleOptions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = optionAdapter
        }

        // Selection listener
        optionAdapter.onSelectionChanged = { selectedOption ->
            binding.btnAssignSelected.isEnabled = selectedOption != null
        }

        // Initially disabled
        binding.btnAssignSelected.isEnabled = false
    }

    private fun setupListeners() {
        binding.btnAssignSelected.setOnClickListener {
            val selected = optionAdapter.getSelectedOption() ?: return@setOnClickListener
            showLoading()
            onAssignSelected?.invoke(selected.vehicleId, selected.driverId)
        }
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Set the list of eligible vehicle+driver options.
     * If empty, shows the "no eligible" state.
     */
    fun setOptions(options: List<SmartAssignOptionAdapter.EligibleOption>) {
        _binding?.let { b ->
            if (options.isEmpty()) {
                b.rvEligibleOptions.visibility = View.GONE
                b.layoutNoEligible.visibility = View.VISIBLE
                b.btnAssignSelected.isEnabled = false
            } else {
                b.rvEligibleOptions.visibility = View.VISIBLE
                b.layoutNoEligible.visibility = View.GONE
                optionAdapter.submitList(options)
            }
        }
    }

    /**
     * Show loading state.
     */
    fun showLoading() {
        _binding?.let { b ->
            b.progressAssign.visibility = View.VISIBLE
            b.btnAssignSelected.isEnabled = false
        }
    }

    /**
     * Hide loading state.
     */
    fun hideLoading() {
        _binding?.let { b ->
            b.progressAssign.visibility = View.GONE
            val selected = optionAdapter.getSelectedOption()
            b.btnAssignSelected.isEnabled = selected != null
        }
    }

    // ── Factory ─────────────────────────────────────────────────

    companion object {
        const val TAG = "SmartAssignmentBottomSheet"
        private const val ARG_ROUTE_SUMMARY = "route_summary"
        private const val ARG_DISTANCE_SUMMARY = "distance_summary"

        fun newInstance(
            routeSummary: String,
            distanceSummary: String
        ): SmartAssignmentBottomSheet {
            return SmartAssignmentBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_ROUTE_SUMMARY, routeSummary)
                    putString(ARG_DISTANCE_SUMMARY, distanceSummary)
                }
            }
        }
    }
}
