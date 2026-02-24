package com.example.movexa.ui.dashboard.manager

import androidx.fragment.app.Fragment
import com.example.movexa.R
import com.example.movexa.databinding.FragmentManagerFleetBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.ui.fleet.DriversTabFragment
import com.example.movexa.ui.fleet.VehiclesTabFragment
import com.google.android.material.tabs.TabLayout

/**
 * Fleet Management host for the Manager dashboard.
 *
 * Contains a TabLayout with 2 tabs: Vehicles, Drivers.
 * Manager has read-only access to vehicles and drivers (no add/edit/delete).
 * Manager can view driver status and vehicle assignments but cannot modify them.
 */
class ManagerFleetFragment : BaseFragment<FragmentManagerFleetBinding>(
    FragmentManagerFleetBinding::inflate
) {

    // ── Tab Fragments ───────────────────────────────────────────
    private val vehiclesTab by lazy { VehiclesTabFragment.newInstance(isAdmin = false) }
    private val driversTab by lazy { DriversTabFragment.newInstance(isAdmin = false) }

    private var currentTab: Fragment? = null

    // ── Lifecycle ───────────────────────────────────────────────

    override fun initViews() {
        // Add tabs
        binding.tabLayout.apply {
            addTab(newTab().setText(R.string.tab_vehicles))
            addTab(newTab().setText(R.string.tab_drivers))
        }

        // Show initial tab
        switchToTab(vehiclesTab, TAG_VEHICLES)
    }

    override fun setupListeners() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> switchToTab(vehiclesTab, TAG_VEHICLES)
                    1 -> switchToTab(driversTab, TAG_DRIVERS)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    override fun observeData() {
        // Data observation is handled by each child fragment's ViewModel
    }

    // ── Tab Switching ───────────────────────────────────────────

    /**
     * Switch the tab content container to show the given fragment.
     */
    private fun switchToTab(fragment: Fragment, tag: String) {
        if (currentTab === fragment) return

        val transaction = childFragmentManager.beginTransaction()

        // hide all known tabs to avoid any stray views lingering
        listOf(vehiclesTab, driversTab).forEach { frag ->
            if (frag.isAdded) transaction.hide(frag)
        }

        // Show or add the requested fragment
        if (fragment.isAdded) {
            transaction.show(fragment)
        } else {
            transaction.add(R.id.fleetTabContainer, fragment, tag)
        }

        transaction.commitNowAllowingStateLoss()
        currentTab = fragment
    }

    companion object {
        private const val TAG_VEHICLES = "tab_vehicles"
        private const val TAG_DRIVERS = "tab_drivers"
    }
}
