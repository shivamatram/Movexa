package com.example.movexa.ui.fleet

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.databinding.FragmentManagersTabBinding
import com.example.movexa.databinding.ItemManagerCardBinding
import com.example.movexa.ui.base.BaseFragment

/**
 * Tab fragment displaying the list of managers (Admin-only tab).
 *
 * Features:
 * - Manager list from Firestore users collection (role == MANAGER)
 * - Search bar with live filtering (by name, email, phone)
 * - Manager card views with name, email, phone, active status
 * - Shimmer loading + empty + error states
 * - Pull-to-refresh
 *
 * This tab is only available in AdminFleetFragment.
 */
class ManagersTabFragment : BaseFragment<FragmentManagersTabBinding>(
    FragmentManagersTabBinding::inflate
) {

    private val viewModel: ManagerListViewModel by viewModels()

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

    // ── Factory ─────────────────────────────────────────────────

    companion object {
        fun newInstance(): ManagersTabFragment {
            return ManagersTabFragment()
        }
    }
}
