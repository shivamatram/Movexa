package com.example.movexa.ui.common

import android.view.Menu
import android.view.MenuItem
import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.movexa.data.model.UserRole
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Abstraction layer for Bottom Navigation management.
 *
 * Features:
 * - Dynamic menu loading based on user role
 * - Fragment switching without recreation (preserves state)
 * - Smooth switching with animation support
 * - Role-based menu configuration
 *
 * Usage:
 * 1. Initialize with BottomNavigationView and FragmentManager
 * 2. Configure menu for the current user role
 * 3. Register fragment factories for each menu item
 */
class BottomNavController(
    private val bottomNavigationView: BottomNavigationView,
    private val fragmentManager: FragmentManager,
    private val containerId: Int
) {

    // ─── State ──────────────────────────────────────────────────

    private val fragmentMap = mutableMapOf<Int, Fragment>()
    private val fragmentFactories = mutableMapOf<Int, () -> Fragment>()
    private var activeFragmentTag: String? = null
    private var currentMenuItemId: Int = -1
    private var onTabSelectedListener: ((Int) -> Unit)? = null

    // ─── Configuration ──────────────────────────────────────────

    /**
     * Menu configuration for each user role.
     * Maps menu item IDs to their display order.
     */
    data class MenuConfig(
        val menuRes: Int,
        val defaultSelectedId: Int,
        val visibleItems: List<Int>? = null // null = show all
    )

    // ─── Role-Based Menu Configs ────────────────────────────────

    private val roleMenuConfigs = mutableMapOf<UserRole, MenuConfig>()

    /**
     * Register a menu configuration for a specific role.
     */
    fun registerRoleMenu(role: UserRole, config: MenuConfig) {
        roleMenuConfigs[role] = config
    }

    /**
     * Register a fragment factory for a menu item.
     * The factory creates the fragment lazily on first selection.
     */
    fun registerFragment(@IdRes menuItemId: Int, factory: () -> Fragment) {
        fragmentFactories[menuItemId] = factory
    }

    // ─── Setup ──────────────────────────────────────────────────

    /**
     * Initialize the bottom navigation for a specific role.
     */
    fun setupForRole(role: UserRole) {
        val config = roleMenuConfigs[role] ?: return

        // Load menu resource
        bottomNavigationView.menu.clear()
        bottomNavigationView.inflateMenu(config.menuRes)

        // Filter visible items if specified
        config.visibleItems?.let { visibleIds ->
            for (i in 0 until bottomNavigationView.menu.size()) {
                val item = bottomNavigationView.menu.getItem(i)
                item.isVisible = item.itemId in visibleIds
            }
        }

        // Set up item selection listener
        bottomNavigationView.setOnItemSelectedListener { item ->
            switchToFragment(item.itemId)
            onTabSelectedListener?.invoke(item.itemId)
            true
        }

        // Select default item
        bottomNavigationView.selectedItemId = config.defaultSelectedId
    }

    /**
     * Set up with a static menu resource (non role-based).
     */
    fun setup(@MenuRes menuRes: Int, @IdRes defaultSelectedId: Int) {
        bottomNavigationView.menu.clear()
        bottomNavigationView.inflateMenu(menuRes)

        bottomNavigationView.setOnItemSelectedListener { item ->
            switchToFragment(item.itemId)
            onTabSelectedListener?.invoke(item.itemId)
            true
        }

        bottomNavigationView.selectedItemId = defaultSelectedId
    }

    // ─── Fragment Switching ─────────────────────────────────────

    /**
     * Switch to the fragment associated with a menu item ID.
     * Preserves fragment state by using show/hide instead of replace.
     */
    private fun switchToFragment(@IdRes menuItemId: Int): Boolean {
        if (menuItemId == currentMenuItemId) return false

        val tag = makeTag(menuItemId)

        val transaction = fragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )

        // Hide current fragment
        activeFragmentTag?.let { activeTag ->
            fragmentManager.findFragmentByTag(activeTag)?.let { fragment ->
                transaction.hide(fragment)
            }
        }

        // Show or create the target fragment
        var targetFragment = fragmentManager.findFragmentByTag(tag)

        if (targetFragment == null) {
            // Create new fragment from factory
            targetFragment = fragmentFactories[menuItemId]?.invoke()
            if (targetFragment != null) {
                transaction.add(containerId, targetFragment, tag)
                fragmentMap[menuItemId] = targetFragment
            }
        } else {
            transaction.show(targetFragment)
        }

        transaction.commitAllowingStateLoss()

        activeFragmentTag = tag
        currentMenuItemId = menuItemId

        return true
    }

    // ─── Public API ─────────────────────────────────────────────

    /**
     * Set a listener for tab selection events.
     */
    fun setOnTabSelectedListener(listener: (Int) -> Unit) {
        onTabSelectedListener = listener
    }

    /**
     * Get the currently active fragment.
     */
    fun getActiveFragment(): Fragment? {
        return activeFragmentTag?.let { fragmentManager.findFragmentByTag(it) }
    }

    /**
     * Select a specific tab programmatically.
     */
    fun selectTab(@IdRes menuItemId: Int) {
        bottomNavigationView.selectedItemId = menuItemId
    }

    /**
     * Get a badge on a menu item.
     */
    fun setBadge(@IdRes menuItemId: Int, count: Int) {
        val badge = bottomNavigationView.getOrCreateBadge(menuItemId)
        if (count > 0) {
            badge.isVisible = true
            badge.number = count
        } else {
            badge.isVisible = false
        }
    }

    /**
     * Clear a badge from a menu item.
     */
    fun clearBadge(@IdRes menuItemId: Int) {
        bottomNavigationView.removeBadge(menuItemId)
    }

    /**
     * Clear all fragments and reset state.
     */
    fun reset() {
        val transaction = fragmentManager.beginTransaction()
        fragmentMap.values.forEach { fragment ->
            transaction.remove(fragment)
        }
        transaction.commitAllowingStateLoss()
        fragmentMap.clear()
        activeFragmentTag = null
        currentMenuItemId = -1
    }

    // ─── Helpers ────────────────────────────────────────────────

    private fun makeTag(@IdRes menuItemId: Int): String {
        return "bottom_nav_fragment_$menuItemId"
    }
}
