package com.example.movexa.navigation

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.movexa.data.model.UserRole
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Helper component for managing role-based bottom navigation.
 *
 * Handles:
 * - Wiring BottomNavigationView with child NavController
 * - Toolbar title updates on destination changes
 * - Tab switch animations (fade + slight slide)
 * - Fragment state preservation across tab switches
 *
 * Usage from a container fragment:
 * ```
 * val helper = RoleBasedBottomNavigation(
 *     containerFragment = this,
 *     bottomNavView = binding.bottomNavigation,
 *     childNavHostFragmentId = R.id.childNavHostFragment,
 *     role = UserRole.ADMIN
 * )
 * helper.setup()
 * ```
 */
class RoleBasedBottomNavigation(
    private val containerFragment: Fragment,
    private val bottomNavView: BottomNavigationView,
    @IdRes private val childNavHostFragmentId: Int,
    private val role: UserRole
) {

    private lateinit var childNavController: NavController
    private val roleConfig = RoleNavigationConfig.getConfig(role)
    private val destinationTitles = RoleNavigationConfig.getDestinationTitles(role)
    private var onDestinationChanged: ((Int, String) -> Unit)? = null

    /**
     * Set up the bottom navigation with the child NavController.
     * Must be called after the view is created (onViewCreated).
     *
     * @param onDestinationChanged callback when tab changes, receives (destinationId, title)
     */
    fun setup(onDestinationChanged: ((Int, String) -> Unit)? = null) {
        this.onDestinationChanged = onDestinationChanged

        // Get child NavController
        val navHostFragment = containerFragment.childFragmentManager
            .findFragmentById(childNavHostFragmentId) as NavHostFragment
        childNavController = navHostFragment.navController

        // Wire bottom nav with child NavController
        bottomNavView.setupWithNavController(childNavController)

        // Listen for destination changes
        childNavController.addOnDestinationChangedListener { _, destination, _ ->
            val title = destinationTitles[destination.id]
                ?: destination.label?.toString()
                ?: ""
            onDestinationChanged?.invoke(destination.id, title)

            // Animate the content area on tab switch
            animateTabSwitch(navHostFragment.requireView())
        }
    }

    /**
     * Get the child NavController for programmatic navigation.
     */
    fun getNavController(): NavController = childNavController

    /**
     * Navigate to a specific tab by destination ID.
     */
    fun navigateToTab(@IdRes destinationId: Int) {
        bottomNavView.selectedItemId = destinationId
    }

    /**
     * Get the currently selected tab destination ID.
     */
    @IdRes
    fun getCurrentDestination(): Int? = childNavController.currentDestination?.id

    /**
     * Animate tab content transition with fade + slight slide up.
     */
    private fun animateTabSwitch(contentView: View) {
        val fadeIn = ObjectAnimator.ofFloat(contentView, "alpha", 0.85f, 1f)
        val slideUp = ObjectAnimator.ofFloat(contentView, "translationY", 12f, 0f)

        AnimatorSet().apply {
            playTogether(fadeIn, slideUp)
            duration = 200
            interpolator = DecelerateInterpolator()
            start()
        }
    }
}
