package com.example.movexa.ui.dashboard.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.databinding.BottomSheetAdminActionsBinding
import com.example.movexa.databinding.BottomSheetReassignDriverBinding
import com.example.movexa.databinding.BottomSheetTripFilterBinding
import com.example.movexa.databinding.FragmentAdminTripsBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.ui.trips.TripDetailsFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Admin Trip Operations fragment.
 *
 * Features:
 * - Segmented tabs: Ongoing, Completed, Cancelled, All
 * - Real-time Firestore updates for ongoing trips
 * - Cursor-based pagination for completed/cancelled
 * - Server-side filtering (status, driver, vehicle, date range)
 * - Client-side search (tracking ID, driver name, vehicle number)
 * - Admin actions: cancel, force-complete, reassign driver, flag for audit
 * - Shimmer loading states, animated card insertion, SwipeRefresh
 */
class AdminTripsFragment : BaseFragment<FragmentAdminTripsBinding>(
    FragmentAdminTripsBinding::inflate
) {

    // ── ViewModel ───────────────────────────────────────────────

    private val viewModel: AdminTripsViewModel by viewModels()

    // ── Adapter ─────────────────────────────────────────────────

    private lateinit var tripAdapter: AdminTripAdapter

    // ── State ───────────────────────────────────────────────────

    private var currentTab = 0
    private var searchDebounceRunnable: Runnable? = null
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // ── Tab Labels ──────────────────────────────────────────────

    private val tabTitles by lazy {
        arrayOf(
            getString(R.string.admin_trips_tab_ongoing),
            getString(R.string.admin_trips_tab_completed),
            getString(R.string.admin_trips_tab_cancelled),
            getString(R.string.admin_trips_tab_all)
        )
    }

    // ═══════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════

    override fun initViews() {
        setupToolbar()
        setupTabs()
        setupRecyclerView()
        setupSwipeRefresh()
        viewModel.initialize()
    }

    private fun setupToolbar() {
        binding.tvToolbarSubtitle.text = getString(R.string.admin_trips_toolbar_subtitle)
    }

    private fun setupTabs() {
        tabTitles.forEach { title ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(title))
        }
    }

    private fun setupRecyclerView() {
        tripAdapter = AdminTripAdapter(
            onTripClick = { trip -> navigateToTripDetails(trip) },
            onTripLongClick = { trip -> showAdminActionsSheet(trip) },
            driverNameResolver = { driverId ->
                viewModel.getDriverName(driverId) ?: getString(R.string.admin_trips_loading_name)
            },
            vehicleNumberResolver = { vehicleId ->
                viewModel.getVehicleName(vehicleId) ?: getString(R.string.admin_trips_loading_name)
            }
        )

        binding.rvTrips.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = tripAdapter
            setHasFixedSize(false)

            // Pagination scroll listener
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    val layoutMgr = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val lastVisible = layoutMgr.findLastVisibleItemPosition()
                    val totalItems = layoutMgr.itemCount

                    when (currentTab) {
                        TAB_COMPLETED -> {
                            if (lastVisible >= totalItems - PAGINATION_THRESHOLD) {
                                viewModel.loadCompletedTrips(loadMore = true)
                            }
                        }
                        TAB_CANCELLED -> {
                            if (lastVisible >= totalItems - PAGINATION_THRESHOLD) {
                                viewModel.loadCancelledTrips(loadMore = true)
                            }
                        }
                    }
                }
            })
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshAll()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // LISTENERS
    // ═══════════════════════════════════════════════════════════

    override fun setupListeners() {
        // Tab selection
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val index = tab?.position ?: 0
                currentTab = index
                viewModel.selectTab(index)
                tripAdapter.resetAnimations()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                binding.rvTrips.smoothScrollToPosition(0)
            }
        })

        // Search with debounce
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchDebounceRunnable?.let { binding.etSearch.removeCallbacks(it) }
                searchDebounceRunnable = Runnable {
                    viewModel.setSearchQuery(s?.toString()?.trim() ?: "")
                }
                binding.etSearch.postDelayed(searchDebounceRunnable!!, SEARCH_DEBOUNCE_MS)
            }
        })

        // Filter button
        binding.btnFilter.setOnClickListener {
            showFilterBottomSheet()
        }

        // Empty state clear filters
        binding.btnClearFilters.setOnClickListener {
            viewModel.clearFilters()
            binding.etSearch.text?.clear()
        }

        // Error retry
        binding.btnRetry.setOnClickListener {
            viewModel.refreshAll()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DATA OBSERVATION
    // ═══════════════════════════════════════════════════════════

    override fun observeData() {
        // Current tab data
        collectLatestFlow(viewModel.selectedTab) { tabIndex ->
            currentTab = tabIndex
            observeTripsForTab(tabIndex)
        }

        // Tab count badges
        collectFlow(viewModel.ongoingCount) { count ->
            updateTabBadge(TAB_ONGOING, count)
            binding.tvOngoingCount.text = count.toString()
        }
        collectFlow(viewModel.completedCount) { count ->
            updateTabBadge(TAB_COMPLETED, count)
            binding.tvCompletedCount.text = count.toString()
        }
        collectFlow(viewModel.cancelledCount) { count ->
            updateTabBadge(TAB_CANCELLED, count)
            binding.tvCancelledCount.text = count.toString()
        }
        collectFlow(viewModel.totalCount) { count ->
            updateTabBadge(TAB_ALL, count)
            binding.tvTotalCount.text = count.toString()
        }

        // Filter indicator
        collectFlow(viewModel.filterActive) { active ->
            binding.viewFilterBadge.visibility = if (active) View.VISIBLE else View.GONE
            binding.ivFilterIcon.setColorFilter(
                requireContext().getColor(if (active) R.color.primary else R.color.text_hint)
            )
        }

        // Action results
        collectFlow(viewModel.actionResult) { result ->
            binding.progressBar.visibility = View.GONE
            when (result) {
                is ResultState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is ResultState.Success -> {
                    showSuccess(result.data)
                }
                is ResultState.Error -> {
                    showError(result.message)
                }
                else -> {}
            }
        }
    }

    private fun observeTripsForTab(tabIndex: Int) {
        val flow = when (tabIndex) {
            TAB_ONGOING -> viewModel.ongoingTrips
            TAB_COMPLETED -> viewModel.completedTrips
            TAB_CANCELLED -> viewModel.cancelledTrips
            TAB_ALL -> viewModel.allTrips
            else -> viewModel.ongoingTrips
        }

        collectLatestFlow(flow) { state ->
            binding.swipeRefresh.isRefreshing = false

            when (state) {
                is ResultState.Loading -> showShimmerState()
                is ResultState.Success -> {
                    val trips = state.data
                    if (trips.isEmpty()) {
                        showEmptyState()
                    } else {
                        showDataState(trips)
                    }
                }
                is ResultState.Error -> showErrorState(state.message)
                is ResultState.Idle -> showEmptyState()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // UI STATE MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    private fun showShimmerState() {
        binding.shimmerContainer.visibility = View.VISIBLE
        binding.rvTrips.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.GONE
        binding.layoutErrorState.visibility = View.GONE
    }

    private fun showDataState(trips: List<Trip>) {
        binding.shimmerContainer.visibility = View.GONE
        binding.rvTrips.visibility = View.VISIBLE
        binding.layoutEmptyState.visibility = View.GONE
        binding.layoutErrorState.visibility = View.GONE

        tripAdapter.submitList(trips) {
            // Resolve names after list is submitted
            viewModel.resolveNames(trips)
        }
    }

    private fun showEmptyState() {
        binding.shimmerContainer.visibility = View.GONE
        binding.rvTrips.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.VISIBLE
        binding.layoutErrorState.visibility = View.GONE

        // Show "clear filters" if filters are active
        val filterActive = viewModel.filterActive.value
        binding.btnClearFilters.visibility = if (filterActive) View.VISIBLE else View.GONE

        val (title, subtitle) = when {
            filterActive -> getString(R.string.admin_trips_empty_filter_title) to
                    getString(R.string.admin_trips_empty_filter_subtitle)
            currentTab == TAB_ONGOING -> getString(R.string.admin_trips_empty_ongoing_title) to
                    getString(R.string.admin_trips_empty_ongoing_subtitle)
            currentTab == TAB_COMPLETED -> getString(R.string.admin_trips_empty_completed_title) to
                    getString(R.string.admin_trips_empty_completed_subtitle)
            currentTab == TAB_CANCELLED -> getString(R.string.admin_trips_empty_cancelled_title) to
                    getString(R.string.admin_trips_empty_cancelled_subtitle)
            else -> getString(R.string.admin_trips_empty_title) to
                    getString(R.string.admin_trips_empty_subtitle)
        }

        binding.tvEmptyTitle.text = title
        binding.tvEmptySubtitle.text = subtitle
    }

    private fun showErrorState(message: String) {
        binding.shimmerContainer.visibility = View.GONE
        binding.rvTrips.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.GONE
        binding.layoutErrorState.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
    }

    // ═══════════════════════════════════════════════════════════
    // TAB BADGE
    // ═══════════════════════════════════════════════════════════

    private fun updateTabBadge(tabIndex: Int, count: Int) {
        val tab = binding.tabLayout.getTabAt(tabIndex) ?: return
        if (count > 0) {
            val badge = tab.orCreateBadge
            badge.number = count
            badge.isVisible = true
            badge.backgroundColor = requireContext().getColor(R.color.primary)
        } else {
            tab.removeBadge()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN ACTIONS BOTTOM SHEET
    // ═══════════════════════════════════════════════════════════

    private fun showAdminActionsSheet(trip: Trip) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetAdminActionsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Trip summary
        val pickupShort = trip.pickupAddress.take(25).let {
            if (trip.pickupAddress.length > 25) "$it…" else it
        }
        val dropShort = trip.dropAddress.take(25).let {
            if (trip.dropAddress.length > 25) "$it…" else it
        }
        val trackingId = trip.trackingId.ifBlank { trip.tripId.take(12) }
        sheetBinding.tvActionTripSummary.text = "$trackingId • $pickupShort → $dropShort"

        // Disable actions based on trip status
        val isTerminal = trip.status.isTerminal
        sheetBinding.btnActionCancel.apply {
            isEnabled = !isTerminal
            alpha = if (isTerminal) 0.4f else 1f
        }
        sheetBinding.btnActionForceComplete.apply {
            isEnabled = !isTerminal && trip.status != TripStatus.CREATED
            alpha = if (isTerminal || trip.status == TripStatus.CREATED) 0.4f else 1f
        }
        sheetBinding.btnActionReassign.apply {
            isEnabled = !isTerminal
            alpha = if (isTerminal) 0.4f else 1f
        }

        // Click handlers
        sheetBinding.btnActionCancel.setOnClickListener {
            dialog.dismiss()
            showCancelConfirmation(trip)
        }

        sheetBinding.btnActionForceComplete.setOnClickListener {
            dialog.dismiss()
            showForceCompleteConfirmation(trip)
        }

        sheetBinding.btnActionReassign.setOnClickListener {
            dialog.dismiss()
            showReassignSheet(trip)
        }

        sheetBinding.btnActionFlag.setOnClickListener {
            dialog.dismiss()
            showFlagConfirmation(trip)
        }

        sheetBinding.btnActionViewDetails.setOnClickListener {
            dialog.dismiss()
            navigateToTripDetails(trip)
        }

        dialog.show()
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN ACTION CONFIRMATIONS
    // ═══════════════════════════════════════════════════════════

    private fun showCancelConfirmation(trip: Trip) {
        val editText = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = getString(R.string.admin_trips_reason_hint)
            setPadding(48, 32, 48, 16)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.admin_trips_cancel_confirm_title))
            .setMessage(getString(R.string.admin_trips_cancel_confirm_message, trip.trackingId.ifBlank { trip.tripId.take(12) }))
            .setView(editText)
            .setPositiveButton(getString(R.string.admin_trips_action_cancel)) { _, _ ->
                val reason = editText.text?.toString()?.trim() ?: "Admin cancelled"
                viewModel.cancelTrip(trip.tripId, reason)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showForceCompleteConfirmation(trip: Trip) {
        val editText = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = getString(R.string.admin_trips_reason_hint)
            setPadding(48, 32, 48, 16)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.admin_trips_force_complete_title))
            .setMessage(getString(R.string.admin_trips_force_complete_message, trip.trackingId.ifBlank { trip.tripId.take(12) }))
            .setView(editText)
            .setPositiveButton(getString(R.string.admin_trips_action_force_complete)) { _, _ ->
                val reason = editText.text?.toString()?.trim() ?: "Admin force-completed"
                viewModel.forceCompleteTrip(trip.tripId, reason)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showFlagConfirmation(trip: Trip) {
        val editText = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = getString(R.string.admin_trips_flag_reason_hint)
            setPadding(48, 32, 48, 16)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.admin_trips_flag_confirm_title))
            .setMessage(getString(R.string.admin_trips_flag_confirm_message))
            .setView(editText)
            .setPositiveButton(getString(R.string.admin_trips_action_flag)) { _, _ ->
                val reason = editText.text?.toString()?.trim() ?: "Flagged for audit"
                viewModel.flagForAudit(trip.tripId, reason)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    // ═══════════════════════════════════════════════════════════
    // REASSIGN DRIVER BOTTOM SHEET
    // ═══════════════════════════════════════════════════════════

    private fun showReassignSheet(trip: Trip) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetReassignDriverBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        var selectedVehicle: AdminTripsViewModel.EligibleVehicle? = null

        val vehicleAdapter = EligibleVehicleAdapter { vehicle ->
            selectedVehicle = vehicle
            sheetBinding.btnConfirmReassign.isEnabled = true
        }

        sheetBinding.rvEligibleVehicles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = vehicleAdapter
        }

        // Load eligible vehicles
        viewModel.loadEligibleVehicles()

        collectLatestFlow(viewModel.eligibleVehicles) { state ->
            when (state) {
                is ResultState.Loading -> {
                    sheetBinding.rvEligibleVehicles.visibility = View.GONE
                    sheetBinding.tvNoVehicles.visibility = View.GONE
                }
                is ResultState.Success -> {
                    val vehicles = state.data.filter {
                        it.vehicleId != trip.vehicleId
                    }
                    if (vehicles.isEmpty()) {
                        sheetBinding.tvNoVehicles.visibility = View.VISIBLE
                        sheetBinding.rvEligibleVehicles.visibility = View.GONE
                    } else {
                        sheetBinding.tvNoVehicles.visibility = View.GONE
                        sheetBinding.rvEligibleVehicles.visibility = View.VISIBLE
                        vehicleAdapter.submitList(vehicles)
                    }
                }
                is ResultState.Error -> {
                    sheetBinding.tvNoVehicles.text = state.message
                    sheetBinding.tvNoVehicles.visibility = View.VISIBLE
                    sheetBinding.rvEligibleVehicles.visibility = View.GONE
                }
                else -> {}
            }
        }

        sheetBinding.btnConfirmReassign.setOnClickListener {
            val vehicle = selectedVehicle ?: return@setOnClickListener
            val reason = sheetBinding.etReassignReason.text?.toString()?.trim() ?: "Admin reassignment"
            dialog.dismiss()
            viewModel.reassignDriver(trip.tripId, vehicle.vehicleId, vehicle.driverId, reason)
        }

        dialog.show()
    }

    // ═══════════════════════════════════════════════════════════
    // FILTER BOTTOM SHEET
    // ═══════════════════════════════════════════════════════════

    private fun showFilterBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetTripFilterBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        var selectedStartDate: Long? = null
        var selectedEndDate: Long? = null

        // Driver dropdown
        collectFlow(viewModel.companyDrivers) { drivers ->
            val items = listOf(getString(R.string.admin_trips_filter_all_drivers)) +
                    drivers.map { it.licenseNumber.ifBlank { "Driver ${it.driverId.take(6)}" } }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
            sheetBinding.actvDriverFilter.setAdapter(adapter)
        }

        // Vehicle dropdown
        collectFlow(viewModel.companyVehicles) { vehicles ->
            val items = listOf(getString(R.string.admin_trips_filter_all_vehicles)) +
                    vehicles.map { it.number }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
            sheetBinding.actvVehicleFilter.setAdapter(adapter)
        }

        // Date pickers
        sheetBinding.etStartDate.setOnClickListener {
            showDatePicker(getString(R.string.admin_trips_filter_start_date)) { millis ->
                selectedStartDate = millis
                sheetBinding.etStartDate.setText(dateFormat.format(Date(millis)))
            }
        }

        sheetBinding.etEndDate.setOnClickListener {
            showDatePicker(getString(R.string.admin_trips_filter_end_date)) { millis ->
                selectedEndDate = millis
                sheetBinding.etEndDate.setText(dateFormat.format(Date(millis)))
            }
        }

        // Clear all
        sheetBinding.btnClearAllFilters.setOnClickListener {
            sheetBinding.chipGroupStatus.check(R.id.chipStatusAll)
            sheetBinding.actvDriverFilter.setText("", false)
            sheetBinding.actvVehicleFilter.setText("", false)
            sheetBinding.etStartDate.text?.clear()
            sheetBinding.etEndDate.text?.clear()
            selectedStartDate = null
            selectedEndDate = null
        }

        // Apply filters
        sheetBinding.btnApplyFilters.setOnClickListener {
            val statusFilter = when (sheetBinding.chipGroupStatus.checkedChipId) {
                R.id.chipStatusCreated -> TripStatus.CREATED
                R.id.chipStatusAssigned -> TripStatus.ASSIGNED
                R.id.chipStatusStarted -> TripStatus.STARTED
                R.id.chipStatusCompleted -> TripStatus.COMPLETED
                R.id.chipStatusCancelled -> TripStatus.CANCELLED
                else -> null
            }

            val selectedDriverText = sheetBinding.actvDriverFilter.text?.toString() ?: ""
            val driverId = if (selectedDriverText.isNotBlank() &&
                selectedDriverText != getString(R.string.admin_trips_filter_all_drivers)
            ) {
                viewModel.companyDrivers.value.firstOrNull {
                    it.licenseNumber == selectedDriverText ||
                            "Driver ${it.driverId.take(6)}" == selectedDriverText
                }?.driverId
            } else null

            val selectedVehicleText = sheetBinding.actvVehicleFilter.text?.toString() ?: ""
            val vehicleId = if (selectedVehicleText.isNotBlank() &&
                selectedVehicleText != getString(R.string.admin_trips_filter_all_vehicles)
            ) {
                viewModel.companyVehicles.value.firstOrNull {
                    it.number == selectedVehicleText
                }?.vehicleId
            } else null

            viewModel.applyFilters(
                status = statusFilter,
                driverId = driverId,
                vehicleId = vehicleId,
                startDate = selectedStartDate,
                endDate = selectedEndDate
            )

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDatePicker(title: String, onDateSelected: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(title)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            onDateSelected(millis)
        }
        picker.show(childFragmentManager, "date_picker")
    }

    // ═══════════════════════════════════════════════════════════
    // NAVIGATION
    // ═══════════════════════════════════════════════════════════

    private fun navigateToTripDetails(trip: Trip) {
        val detailsFragment = TripDetailsFragment.newInstance(trip.tripId)
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.navHostFragment, detailsFragment)
            .addToBackStack(null)
            .commit()
    }

    // ═══════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════

    override fun onDestroyView() {
        searchDebounceRunnable?.let { binding.etSearch.removeCallbacks(it) }
        super.onDestroyView()
    }

    // ═══════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════

    companion object {
        private const val TAB_ONGOING = 0
        private const val TAB_COMPLETED = 1
        private const val TAB_CANCELLED = 2
        private const val TAB_ALL = 3

        private const val SEARCH_DEBOUNCE_MS = 350L
        private const val PAGINATION_THRESHOLD = 5
    }
}
