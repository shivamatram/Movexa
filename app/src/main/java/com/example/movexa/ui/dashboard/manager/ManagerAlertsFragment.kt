package com.example.movexa.ui.dashboard.manager

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.enums.AlertStatus
import com.example.movexa.databinding.FragmentManagerAlertsBinding
import com.example.movexa.ui.base.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout

/**
 * Manager Alerts Fragment — Driver Behaviour Monitoring & Alert System.
 *
 * ═══════════════════════════════════════════════════════════════
 * FEATURES
 * ═══════════════════════════════════════════════════════════════
 *
 * 1. REAL-TIME ALERT DISPLAY
 *    - Observes Firestore alerts via [ManagerAlertsViewModel]
 *    - Auto-updates when [BehaviorAnalysisEngine] creates new alerts
 *    - Smooth RecyclerView animations on insert/update/remove
 *
 * 2. STATISTICS PANEL
 *    - Active alert count (open alerts)
 *    - Critical alert count (critical priority + open)
 *    - Today's alert count (alerts generated today)
 *
 * 3. TAB NAVIGATION
 *    - Active: ACTIVE + ACKNOWLEDGED alerts
 *    - Resolved: RESOLVED + DISMISSED alerts
 *    - All: complete alert history
 *
 * 4. FILTER CHIPS
 *    - All, Critical, High, Overspeed, Harsh Braking, Long Idle, Accident
 *    - Single-select with immediate list update
 *
 * 5. SEARCH
 *    - Toggle search bar with animation
 *    - Searches: title, message, type, priority, vehicle, driver
 *    - Clear button for quick reset
 *
 * 6. SWIPE TO RESOLVE
 *    - Swipe right on active alerts to resolve
 *    - Green background with check icon during swipe
 *    - Snackbar with undo (not implemented yet — would need alert restore)
 *
 * 7. ALERT CARD INTERACTIONS
 *    - Tap to expand/collapse action buttons
 *    - Action buttons: Acknowledge, Resolve, Dismiss
 *    - Button visibility adapts to alert status
 *
 * 8. MORE OPTIONS MENU
 *    - Resolve all active alerts
 *    - Delete all resolved alerts
 *    - Confirmation dialogs for destructive actions
 *
 * 9. PULL TO REFRESH
 *    - SwipeRefreshLayout for data reload
 *
 * 10. EMPTY / LOADING / ERROR STATES
 *     - Context-aware empty states per tab
 *     - Loading indicator during data fetch
 *     - Error state with retry button
 *
 * ═══════════════════════════════════════════════════════════════
 * ARCHITECTURE
 * ═══════════════════════════════════════════════════════════════
 *
 * - Extends [BaseFragment] with ViewBinding
 * - [ManagerAlertsViewModel] handles all data operations
 * - [AlertListAdapter] handles list rendering
 * - Lifecycle-safe flow collection via [collectLatestFlow]/[collectFlow]
 */
class ManagerAlertsFragment : BaseFragment<FragmentManagerAlertsBinding>(
    FragmentManagerAlertsBinding::inflate
) {

    // ── ViewModel ───────────────────────────────────────────────
    private val viewModel: ManagerAlertsViewModel by viewModels()

    // ── Adapter ─────────────────────────────────────────────────
    private lateinit var alertAdapter: AlertListAdapter

    // ── ItemTouchHelper for swipe ───────────────────────────────
    private var itemTouchHelper: ItemTouchHelper? = null

    // ═══════════════════════════════════════════════════════════
    //  Lifecycle — initViews
    // ═══════════════════════════════════════════════════════════

    override fun initViews() {
        setupRecyclerView()
        setupSwipeToResolve()
        viewModel.loadAlerts()
    }

    // ═══════════════════════════════════════════════════════════
    //  Lifecycle — setupListeners
    // ═══════════════════════════════════════════════════════════

    override fun setupListeners() {
        setupTabListener()
        setupChipListeners()
        setupSearchListeners()
        setupButtonListeners()
        setupRefreshListener()
    }

    // ═══════════════════════════════════════════════════════════
    //  Lifecycle — observeData
    // ═══════════════════════════════════════════════════════════

    override fun observeData() {
        observeFilteredAlerts()
        observeStatistics()
        observeOperationResults()
        observeSearchState()
        observeRefreshState()
    }

    // ═══════════════════════════════════════════════════════════
    //  RecyclerView Setup
    // ═══════════════════════════════════════════════════════════

    /**
     * Configure the RecyclerView with adapter, layout manager, and animator.
     */
    private fun setupRecyclerView() {
        alertAdapter = AlertListAdapter().apply {
            // Resolve vehicle/driver names from ViewModel cache
            vehicleNameResolver = { vehicleId ->
                viewModel.getVehicleNumber(vehicleId)
            }
            driverNameResolver = { driverId ->
                viewModel.getDriverName(driverId)
            }

            // Card click — toggle action buttons
            onCardClick = { alert, position ->
                val isExpanded = toggleExpanded(alert.alertId)
                notifyItemChanged(position)
            }

            // Resolve button clicked
            onResolve = { alert ->
                viewModel.resolveAlert(alert.alertId)
            }

            // Acknowledge button clicked
            onAcknowledge = { alert ->
                viewModel.acknowledgeAlert(alert.alertId)
            }

            // Dismiss button clicked
            onDismiss = { alert ->
                viewModel.dismissAlert(alert.alertId)
            }
        }

        binding.recyclerAlerts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = alertAdapter
            itemAnimator = DefaultItemAnimator().apply {
                addDuration = 200
                removeDuration = 200
                changeDuration = 150
            }
            setHasFixedSize(false)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Swipe to Resolve
    // ═══════════════════════════════════════════════════════════

    /**
     * Set up ItemTouchHelper for swipe-to-resolve gesture.
     * Only active on the "Active" tab (tab index 0).
     */
    private fun setupSwipeToResolve() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val alert = alertAdapter.getAlertAt(position)

                if (alert != null && alert.status.isOpen()) {
                    // Resolve the alert
                    viewModel.resolveAlert(alert.alertId)

                    // Show snackbar
                    view?.let { rootView ->
                        Snackbar.make(
                            rootView,
                            "Alert resolved: ${alert.title}",
                            Snackbar.LENGTH_LONG
                        ).setAction("OK") { /* dismiss */ }
                            .setBackgroundTint(
                                ContextCompat.getColor(requireContext(), R.color.alert_resolve_bg)
                            )
                            .setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.alert_resolve_text)
                            )
                            .setActionTextColor(
                                ContextCompat.getColor(requireContext(), R.color.alert_resolve_text)
                            )
                            .show()
                    }
                } else {
                    // Reset the swipe if not swipe-able
                    alertAdapter.notifyItemChanged(position)
                }
            }

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                // Only allow swipe on Active tab and for open alerts
                if (viewModel.selectedTab.value != 0) return 0
                val position = viewHolder.bindingAdapterPosition
                val alert = alertAdapter.getAlertAt(position)
                return if (alert != null && alert.status.isOpen()) {
                    ItemTouchHelper.RIGHT
                } else {
                    0
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX > 0) {
                    val itemView = viewHolder.itemView
                    val paint = Paint().apply {
                        color = ContextCompat.getColor(
                            requireContext(), R.color.alert_card_swipe_resolve
                        )
                    }

                    // Draw green background
                    val bg = RectF(
                        itemView.left.toFloat(),
                        itemView.top.toFloat(),
                        itemView.left + dX,
                        itemView.bottom.toFloat()
                    )
                    val cornerRadius = resources.getDimension(R.dimen.radius_medium)
                    c.drawRoundRect(bg, cornerRadius, cornerRadius, paint)

                    // Draw check icon text
                    val textPaint = Paint().apply {
                        color = ContextCompat.getColor(requireContext(), android.R.color.white)
                        textSize = 36f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    val xPos = itemView.left + dX / 2
                    val yPos = (itemView.top + itemView.bottom) / 2f + textPaint.textSize / 3
                    c.drawText("✓", xPos, yPos, textPaint)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        itemTouchHelper = ItemTouchHelper(swipeCallback)
        itemTouchHelper?.attachToRecyclerView(binding.recyclerAlerts)
    }

    // ═══════════════════════════════════════════════════════════
    //  Tab Setup
    // ═══════════════════════════════════════════════════════════

    /**
     * Handle tab selection changes.
     */
    private fun setupTabListener() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: 0
                viewModel.setSelectedTab(position)
                // Collapse all expanded items when switching tabs
                alertAdapter.collapseAll()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Scroll to top on re-select
                binding.recyclerAlerts.smoothScrollToPosition(0)
            }
        })
    }

    // ═══════════════════════════════════════════════════════════
    //  Chip Filter Setup
    // ═══════════════════════════════════════════════════════════

    /**
     * Wire up filter chip click handlers.
     */
    @SuppressLint("NonConstantResourceId")
    private fun setupChipListeners() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when {
                checkedIds.contains(R.id.chipCritical) ->
                    ManagerAlertsViewModel.AlertFilter.CRITICAL
                checkedIds.contains(R.id.chipHigh) ->
                    ManagerAlertsViewModel.AlertFilter.HIGH
                checkedIds.contains(R.id.chipOverspeed) ->
                    ManagerAlertsViewModel.AlertFilter.OVERSPEED
                checkedIds.contains(R.id.chipBraking) ->
                    ManagerAlertsViewModel.AlertFilter.HARSH_BRAKING
                checkedIds.contains(R.id.chipIdle) ->
                    ManagerAlertsViewModel.AlertFilter.LONG_IDLE
                checkedIds.contains(R.id.chipAccident) ->
                    ManagerAlertsViewModel.AlertFilter.ACCIDENT
                else -> ManagerAlertsViewModel.AlertFilter.ALL
            }
            viewModel.setFilter(filter)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Search Setup
    // ═══════════════════════════════════════════════════════════

    /**
     * Set up search bar toggle, text input, and clear button.
     */
    private fun setupSearchListeners() {
        // Toggle search bar
        binding.btnSearch.setOnClickListener {
            viewModel.toggleSearch()
        }

        // Search text changes
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString() ?: "")
                binding.ivClearSearch.visibility =
                    if (s.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // IME search action
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        // Clear search
        binding.ivClearSearch.setOnClickListener {
            binding.etSearch.setText("")
            viewModel.setSearchQuery("")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Button Listeners
    // ═══════════════════════════════════════════════════════════

    /**
     * Set up more options menu and retry button.
     */
    private fun setupButtonListeners() {
        // More options menu (popup)
        binding.btnMoreOptions.setOnClickListener { anchor ->
            showMoreOptionsMenu(anchor)
        }

        // Retry button in error state
        binding.btnRetry.setOnClickListener {
            viewModel.refreshAlerts()
        }
    }

    /**
     * Show the popup menu with batch actions.
     */
    private fun showMoreOptionsMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, getString(R.string.alerts_resolve_all))
        popup.menu.add(0, 2, 1, getString(R.string.alerts_delete_resolved))

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    confirmResolveAll()
                    true
                }
                2 -> {
                    confirmDeleteResolved()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * Show confirmation dialog for resolving all active alerts.
     */
    private fun confirmResolveAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.alerts_resolve_all))
            .setMessage("Are you sure you want to resolve all active alerts? This action cannot be undone.")
            .setPositiveButton("Resolve All") { _, _ ->
                viewModel.resolveAllActive()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show confirmation dialog for deleting resolved alerts.
     */
    private fun confirmDeleteResolved() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.alerts_delete_resolved))
            .setMessage("Are you sure you want to permanently delete all resolved alerts? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteResolvedAlerts()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ═══════════════════════════════════════════════════════════
    //  Refresh
    // ═══════════════════════════════════════════════════════════

    /**
     * Set up pull-to-refresh.
     */
    private fun setupRefreshListener() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshAlerts()
        }
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.primary,
            R.color.alert_critical,
            R.color.alert_high
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  Data Observers
    // ═══════════════════════════════════════════════════════════

    /**
     * Observe the filtered alerts list and update the RecyclerView.
     */
    private fun observeFilteredAlerts() {
        collectLatestFlow(viewModel.filteredAlerts) { result ->
            when (result) {
                is ResultState.Loading -> showLoadingState()
                is ResultState.Success -> {
                    val alerts = result.data
                    if (alerts.isEmpty()) {
                        showEmptyState()
                    } else {
                        showContentState()
                        alertAdapter.submitList(alerts.toList())
                    }
                }
                is ResultState.Error -> showErrorState(result.message)
                is ResultState.Idle -> { /* Initial state — do nothing */ }
            }
        }
    }

    /**
     * Observe statistics and update the stats panel.
     */
    private fun observeStatistics() {
        collectLatestFlow(viewModel.activeCount) { count ->
            binding.tvStatActiveCount.text = count.toString()
        }

        collectLatestFlow(viewModel.criticalCount) { count ->
            binding.tvStatCriticalCount.text = count.toString()
        }

        collectLatestFlow(viewModel.todayCount) { count ->
            binding.tvStatTodayCount.text = count.toString()
        }
    }

    /**
     * Observe operation results for resolve/acknowledge/dismiss.
     */
    private fun observeOperationResults() {
        collectFlow(viewModel.operationResult) { result ->
            when (result) {
                is ResultState.Success -> {
                    showSuccess(result.data)
                    viewModel.clearOperationResult()
                }
                is ResultState.Error -> {
                    showError(result.message)
                    viewModel.clearOperationResult()
                }
                is ResultState.Loading -> {
                    // Could show a subtle indicator, but operations are fast
                }
                else -> {}
            }
        }
    }

    /**
     * Observe search bar visibility state.
     */
    private fun observeSearchState() {
        collectLatestFlow(viewModel.isSearchVisible) { isVisible ->
            binding.cardSearchBar.visibility = if (isVisible) View.VISIBLE else View.GONE
            if (isVisible) {
                binding.etSearch.requestFocus()
                showKeyboard()
            } else {
                binding.etSearch.clearFocus()
                hideKeyboard()
            }
        }
    }

    /**
     * Observe refresh state for SwipeRefreshLayout.
     */
    private fun observeRefreshState() {
        collectLatestFlow(viewModel.isRefreshing) { isRefreshing ->
            binding.swipeRefreshLayout.isRefreshing = isRefreshing
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  UI State Management
    // ═══════════════════════════════════════════════════════════

    /**
     * Show loading state — spinner visible, content hidden.
     */
    private fun showLoadingState() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.recyclerAlerts.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
    }

    /**
     * Show content state — RecyclerView visible.
     */
    private fun showContentState() {
        binding.layoutLoading.visibility = View.GONE
        binding.recyclerAlerts.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
    }

    /**
     * Show empty state — context-aware message per tab.
     */
    private fun showEmptyState() {
        binding.layoutLoading.visibility = View.GONE
        binding.recyclerAlerts.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.layoutError.visibility = View.GONE

        // Customize empty state text based on context
        val searchQuery = viewModel.searchQuery.value
        if (searchQuery.isNotBlank()) {
            binding.tvEmptyTitle.text = getString(R.string.alerts_empty_search_title)
            binding.tvEmptySubtitle.text = getString(R.string.alerts_empty_search_subtitle)
        } else {
            when (viewModel.selectedTab.value) {
                0 -> {
                    binding.tvEmptyTitle.text = getString(R.string.alerts_empty_active_title)
                    binding.tvEmptySubtitle.text = getString(R.string.alerts_empty_active_subtitle)
                }
                1 -> {
                    binding.tvEmptyTitle.text = getString(R.string.alerts_empty_resolved_title)
                    binding.tvEmptySubtitle.text = getString(R.string.alerts_empty_resolved_subtitle)
                }
                else -> {
                    binding.tvEmptyTitle.text = getString(R.string.alerts_empty_active_title)
                    binding.tvEmptySubtitle.text = getString(R.string.alerts_empty_active_subtitle)
                }
            }
        }
    }

    /**
     * Show error state with retry button.
     */
    private fun showErrorState(message: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.recyclerAlerts.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvErrorTitle.text = message
    }

    // ═══════════════════════════════════════════════════════════
    //  Keyboard Helpers
    // ═══════════════════════════════════════════════════════════

    private fun showKeyboard() {
        val imm = requireContext().getSystemService<InputMethodManager>()
        imm?.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService<InputMethodManager>()
        imm?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }
}
