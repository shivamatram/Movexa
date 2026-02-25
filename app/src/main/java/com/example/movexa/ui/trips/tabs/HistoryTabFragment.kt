package com.example.movexa.ui.trips.tabs

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.databinding.FragmentTripTabBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.ui.trips.DriverTripsViewModel
import com.example.movexa.ui.trips.TripListAdapter

/**
 * Tab fragment showing the driver's trip history (COMPLETED, REJECTED_BY_DRIVER, CANCELLED).
 *
 * Hosted inside DriverTripsFragment.
 * Shares the parent fragment's DriverTripsViewModel.
 */
class HistoryTabFragment : BaseFragment<FragmentTripTabBinding>(
    FragmentTripTabBinding::inflate
) {

    private val viewModel: DriverTripsViewModel by navGraphViewModels(R.id.nav_driver) {
        defaultViewModelProviderFactory
    }

    private lateinit var adapter: TripListAdapter

    /** View details callback. */
    var onViewDetailsClick: ((Trip) -> Unit)? = null

    // ── Lifecycle ───────────────────────────────────────────────

    override fun initViews() {
        adapter = TripListAdapter(isManager = false)
        adapter.vehicleNameResolver = { viewModel.getVehicleNumber(it) }

        binding.rvTrips.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTrips.adapter = adapter

        binding.tvEmptyTitle.text = getString(R.string.trip_empty_history)
        binding.tvEmptySubtitle.text = getString(R.string.trip_empty_history_subtitle)
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
        collectLatestFlow(viewModel.historyTrips) { state ->
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
        fun newInstance(): HistoryTabFragment = HistoryTabFragment()
    }
}
