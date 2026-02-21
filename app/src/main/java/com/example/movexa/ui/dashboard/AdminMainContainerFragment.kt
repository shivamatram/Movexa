package com.example.movexa.ui.dashboard

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.movexa.R
import com.example.movexa.data.model.UserRole
import com.example.movexa.databinding.FragmentAdminContainerBinding
import com.example.movexa.navigation.RoleNavigationConfig
import com.example.movexa.ui.auth.AuthViewModel
import com.example.movexa.ui.base.BaseFragment

/**
 * Admin container fragment hosting a child NavHostFragment and BottomNavigationView.
 *
 * Architecture:
 * - Hosts a child NavHostFragment for tab content (nav_admin graph)
 * - BottomNavigationView wired to child NavController for tab switching
 * - Toolbar title updates based on current child destination
 * - Sign-out via global action on the parent NavController
 *
 * The container is a leaf destination in the main nav_graph.
 * Tab navigation happens entirely within the child NavHostFragment.
 */
class AdminMainContainerFragment : BaseFragment<FragmentAdminContainerBinding>(
    FragmentAdminContainerBinding::inflate
) {

    private val authViewModel: AuthViewModel by activityViewModels()
    private lateinit var childNavController: NavController
    private val roleConfig = RoleNavigationConfig.getConfig(UserRole.ADMIN)
    private val destinationTitles = RoleNavigationConfig.getDestinationTitles(UserRole.ADMIN)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChildNavigation()
    }

    override fun initViews() {
        showToolbar(roleConfig.toolbarTitle)
        hideBottomNav() // Hide main activity bottom nav — we use our own
    }

    override fun setupListeners() {
        // No additional listeners — navigation handled by setupWithNavController
    }

    override fun observeData() {
        // Placeholder — data observation will be added in future modules
    }

    /**
     * Set up the child NavHostFragment and wire BottomNavigationView to childNavController.
     */
    private fun setupChildNavigation() {
        val navHostFragment = childFragmentManager
            .findFragmentById(R.id.childNavHostFragment) as NavHostFragment
        childNavController = navHostFragment.navController

        // Wire bottom navigation with child NavController
        binding.bottomNavigation.setupWithNavController(childNavController)

        // Update toolbar title based on child destination changes
        childNavController.addOnDestinationChangedListener { _, destination, _ ->
            val title = destinationTitles[destination.id] ?: destination.label?.toString()
            title?.let { showToolbar(it) }
        }
    }

    /**
     * Get the child NavController for external access (e.g., deep linking).
     */
    fun getChildNavController(): NavController = childNavController

    /**
     * Sign out and navigate back to login.
     * Called from profile tab or menu action.
     */
    fun signOut() {
        authViewModel.signOut()
        navigateTo(R.id.action_global_login)
    }
}
