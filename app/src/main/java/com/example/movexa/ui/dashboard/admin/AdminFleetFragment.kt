package com.example.movexa.ui.dashboard.admin

import androidx.fragment.app.Fragment
import com.example.movexa.R
import com.example.movexa.databinding.FragmentAdminFleetBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.ui.fleet.DriversTabFragment
import com.example.movexa.ui.fleet.ManagersTabFragment
import com.example.movexa.ui.fleet.VehiclesTabFragment
import com.google.android.material.tabs.TabLayout

/**
 * Fleet Management host for the Admin dashboard.
 *
 * Contains a TabLayout with 3 tabs: Vehicles, Drivers, Managers.
 * Each tab loads a child fragment via childFragmentManager.
 * Admin has full CRUD permissions on vehicles, verification/blocking on drivers,
 * and read-only view of managers.
 */
class AdminFleetFragment : BaseFragment<FragmentAdminFleetBinding>(
    FragmentAdminFleetBinding::inflate
) {

    // ── Tab Fragments ───────────────────────────────────────────
    private val vehiclesTab by lazy { VehiclesTabFragment.newInstance(isAdmin = true) }
    private val driversTab by lazy { DriversTabFragment.newInstance(isAdmin = true) }
    private val managersTab by lazy { ManagersTabFragment.newInstance() }

    private var currentTab: Fragment? = null

    // ── Lifecycle ───────────────────────────────────────────────

    override fun initViews() {
        // Add tabs
        binding.tabLayout.apply {
            addTab(newTab().setText(R.string.tab_vehicles))
            addTab(newTab().setText(R.string.tab_drivers))
            addTab(newTab().setText(R.string.tab_managers))
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
                    2 -> switchToTab(managersTab, TAG_MANAGERS)
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
     * Uses childFragmentManager to keep fragments alive while tabs are switched.
     */
    private fun switchToTab(fragment: Fragment, tag: String) {
        if (currentTab === fragment) return

        val transaction = childFragmentManager.beginTransaction()

        // hide all tabs that have been added so far; safer than relying on currentTab
        listOf(vehiclesTab, driversTab, managersTab).forEach { frag ->
            if (frag.isAdded) {
                transaction.hide(frag)
            }
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
        private const val TAG_MANAGERS = "tab_managers"
    }
}
