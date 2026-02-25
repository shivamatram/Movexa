package com.example.movexa.ui.fleet

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.databinding.FragmentManagersTabBinding
import com.example.movexa.databinding.ItemManagerCardBinding
import com.example.movexa.ui.base.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tab fragment displaying the list of managers (Admin-only tab).
 *
 * Features:
 * - Manager list from Firestore users collection (role == MANAGER)
 * - Search bar with live filtering (by name, email, phone)
 * - Manager card views with name, email, phone, created date, active status
 * - Shimmer loading + empty + error states
 * - Pull-to-refresh
 * - FAB to create new managers (opens CreateManagerBottomSheet)
 * - Deactivate/Reactivate managers via card action menu with confirmation dialogs
 *
 * Uses [AdminManagerViewModel] (scoped to activity) for:
 * - Company-scoped manager fetching with RoleGuard enforcement
 * - Manager creation orchestration
 * - Deactivation/reactivation with proper security checks
 * - Client-side search filtering
 *
 * Backward-compatible: still observes `managers: StateFlow<ResultState<List<User>>>`
 * from the ViewModel, preserving the existing state rendering pipeline.
 *
 * This tab is only available in AdminFleetFragment.
 */
class ManagersTabFragment : BaseFragment<FragmentManagersTabBinding>(
    FragmentManagersTabBinding::inflate
) {

    private val viewModel: AdminManagerViewModel by activityViewModels()

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // ── Lifecycle ───────────────────────────────────────────────

    override fun initViews() {
        // Start loading data
        viewModel.loadManagers()
    }

    override fun setupListeners() {
        // ── Search ──────────────────────────────────────────────
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // ── Pull-to-Refresh ─────────────────────────────────────
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshManagers()
        }

        // ── Retry ───────────────────────────────────────────────
        binding.btnRetry.setOnClickListener {
            viewModel.refreshManagers()
        }

        // ── FAB: Create Manager ─────────────────────────────────
        binding.fabAddManager.setOnClickListener {
            showCreateManagerBottomSheet()
        }
    }

    override fun observeData() {
        // ── Manager List ────────────────────────────────────────
        collectLatestFlow(viewModel.managers) { state ->
            when (state) {
                is ResultState.Loading -> showLoadingState()
                is ResultState.Success -> showManagerList(state.data)
                is ResultState.Error -> showErrorState(state.message)
                is ResultState.Idle -> {}
            }
            binding.swipeRefresh.isRefreshing = false
        }

        // ── Manager Count ───────────────────────────────────────
        collectLatestFlow(viewModel.managerCount) { count ->
            binding.tvManagerCount.text = getString(R.string.fleet_manager_count, count)
        }

        // ── Deactivation State ──────────────────────────────────
        // Success/error messages are already handled by BaseViewModel's
        // emitSuccess()/emitError() channels which BaseFragment observes.
        // We observe deactivationState only to reset it after consumption.
        collectLatestFlow(viewModel.deactivationState) { state ->
            when (state) {
                is ResultState.Success -> viewModel.resetDeactivationState()
                is ResultState.Error -> viewModel.resetDeactivationState()
                else -> {}
            }
        }
    }

    // ── UI State Rendering ──────────────────────────────────────

    private fun showLoadingState() {
        binding.layoutShimmer.visibility = View.VISIBLE
        binding.layoutManagerList.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.layoutManagerCount.visibility = View.GONE
    }

    private fun showManagerList(managers: List<User>) {
        binding.layoutShimmer.visibility = View.GONE
        binding.layoutError.visibility = View.GONE

        if (managers.isEmpty()) {
            binding.layoutManagerList.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.layoutManagerCount.visibility = View.GONE
            return
        }

        binding.layoutEmpty.visibility = View.GONE
        binding.layoutManagerList.visibility = View.VISIBLE
        binding.layoutManagerCount.visibility = View.VISIBLE

        // Clear and re-render manager cards
        binding.layoutManagerList.removeAllViews()

        for (manager in managers) {
            val cardBinding = ItemManagerCardBinding.inflate(
                LayoutInflater.from(requireContext()),
                binding.layoutManagerList,
                false
            )

            cardBinding.tvManagerName.text = manager.displayName
            cardBinding.tvManagerEmail.text = manager.email

            if (manager.phone.isNotBlank()) {
                cardBinding.tvManagerPhone.text = manager.phone
                cardBinding.tvManagerPhone.visibility = View.VISIBLE
            } else {
                cardBinding.tvManagerPhone.visibility = View.GONE
            }

            // Created date
            if (manager.createdAt > 0) {
                cardBinding.tvCreatedDate.text = getString(
                    R.string.manager_created_on,
                    dateFormat.format(Date(manager.createdAt))
                )
                cardBinding.tvCreatedDate.visibility = View.VISIBLE
            } else {
                cardBinding.tvCreatedDate.visibility = View.GONE
            }

            // Active status badge
            if (manager.isActive) {
                cardBinding.tvActiveStatus.text = getString(R.string.status_active)
                cardBinding.tvActiveStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_approved)
                )
                cardBinding.tvActiveStatus.background?.setTint(
                    ContextCompat.getColor(requireContext(), R.color.status_approved_bg)
                )
            } else {
                cardBinding.tvActiveStatus.text = getString(R.string.status_inactive)
                cardBinding.tvActiveStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_inactive)
                )
                cardBinding.tvActiveStatus.background?.setTint(
                    ContextCompat.getColor(requireContext(), R.color.status_inactive_bg)
                )
            }

            // Action menu (deactivate/reactivate)
            cardBinding.btnManagerAction.setOnClickListener { anchor ->
                showManagerActionMenu(anchor, manager)
            }

            // Card click (future: navigate to manager detail)
            cardBinding.cardManager.setOnClickListener {
                // Future: showInfo("${manager.displayName}")
            }

            binding.layoutManagerList.addView(cardBinding.root)
        }
    }

    private fun showErrorState(message: String) {
        binding.layoutShimmer.visibility = View.GONE
        binding.layoutManagerList.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutManagerCount.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
    }

    // ── Manager Actions ─────────────────────────────────────────

    /**
     * Show a popup menu on the manager card with deactivate/reactivate options.
     */
    private fun showManagerActionMenu(anchor: View, manager: User) {
        val popup = PopupMenu(requireContext(), anchor)
        if (manager.isActive) {
            popup.menu.add(0, 1, 0, getString(R.string.deactivate_manager_confirm))
        } else {
            popup.menu.add(0, 2, 0, getString(R.string.reactivate_manager_confirm))
        }
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    showDeactivateConfirmation(manager)
                    true
                }
                2 -> {
                    showReactivateConfirmation(manager)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * Show confirmation dialog before deactivating a manager.
     * Uses MaterialAlertDialogBuilder to match the design system.
     */
    private fun showDeactivateConfirmation(manager: User) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.deactivate_manager_title))
            .setMessage(getString(R.string.deactivate_manager_message, manager.displayName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.deactivate_manager_confirm)) { _, _ ->
                viewModel.deactivateManager(manager.uid)
            }
            .show()
    }

    /**
     * Show confirmation dialog before reactivating a manager.
     */
    private fun showReactivateConfirmation(manager: User) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.reactivate_manager_title))
            .setMessage(getString(R.string.reactivate_manager_message, manager.displayName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.reactivate_manager_confirm)) { _, _ ->
                viewModel.reactivateManager(manager.uid)
            }
            .show()
    }

    // ── Create Manager Bottom Sheet ─────────────────────────────

    /**
     * Show the CreateManagerBottomSheet for adding a new manager.
     * On success, refresh the manager list.
     */
    private fun showCreateManagerBottomSheet() {
        val bottomSheet = CreateManagerBottomSheet.newInstance()
        bottomSheet.onManagerCreated = { newManager ->
            showInfo(getString(R.string.manager_created_success))
            viewModel.refreshManagers()
        }
        bottomSheet.show(childFragmentManager, CreateManagerBottomSheet.TAG)
    }

    // ── Factory ─────────────────────────────────────────────────

    companion object {
        fun newInstance(): ManagersTabFragment {
            return ManagersTabFragment()
        }
    }
}
