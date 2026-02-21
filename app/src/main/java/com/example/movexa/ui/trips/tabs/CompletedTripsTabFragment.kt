package com.example.movexa.ui.trips.tabs

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.databinding.FragmentTripTabBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.ui.trips.ManagerTripsViewModel
import com.example.movexa.ui.trips.TripListAdapter

/**
 * Tab fragment showing completed/terminal trips for the manager.
 *
 * Hosted inside ManagerTripsFragment.
 * Shares the parent fragment's ManagerTripsViewModel.
 */
class CompletedTripsTabFragment : BaseFragment<FragmentTripTabBinding>(
    FragmentTripTabBinding::inflate
) {

    private val viewModel: ManagerTripsViewModel by viewModels({ requireParentFragment() })

    private lateinit var adapter: TripListAdapter

    /** Navigate to trip details. */
    var onViewDetailsClick: ((Trip) -> Unit)? = null

    // ── Lifecycle ───────────────────────────────────────────────

    override fun initViews() {
        adapter = TripListAdapter(isManager = true)
        adapter.vehicleNameResolver = { viewModel.getVehicleNumber(it) }
        adapter.driverNameResolver = { viewModel.getDriverName(it) }

        binding.rvTrips.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTrips.adapter = adapter

        binding.tvEmptyTitle.text = getString(R.string.trip_empty_completed)
        binding.tvEmptySubtitle.text = getString(R.string.trip_empty_completed_subtitle)
    }

    override fun setupListeners() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnRetry.setOnClickListener {
            viewModel.refreshTrips()
        }

        adapter.onViewDetailsClick = { trip ->
            onViewDetailsClick?.invoke(trip)
        }

        adapter.onCardClick = { trip ->
            onViewDetailsClick?.invoke(trip)
        }
    }

    override fun observeData() {
        collectLatestFlow(viewModel.completedTrips) { state ->
            when (state) {
                is ResultState.Loading -> showLoadingState()
                is ResultState.Success -> showTripList(state.data)
                is ResultState.Error -> showErrorState(state.message)
                is ResultState.Idle -> {}
            }
        }
    }

    // ── UI State Rendering ──────────────────────────────────────

    private fun showLoadingState() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutContent.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
    }

    private fun showTripList(trips: List<Trip>) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutError.visibility = View.GONE

        if (trips.isEmpty()) {
            binding.layoutContent.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            return
        }

        binding.layoutEmpty.visibility = View.GONE
        binding.layoutContent.visibility = View.VISIBLE
        adapter.submitList(trips)
    }

    private fun showErrorState(message: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutContent.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
    }

    companion object {
        fun newInstance(): CompletedTripsTabFragment = CompletedTripsTabFragment()
    }
}
