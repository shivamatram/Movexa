package com.example.movexa.ui.dashboard

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.movexa.R
import com.example.movexa.data.model.UserRole
import com.example.movexa.databinding.FragmentMechanicContainerBinding
import com.example.movexa.navigation.RoleNavigationConfig
import com.example.movexa.ui.auth.AuthViewModel
import com.example.movexa.ui.base.BaseFragment

/**
 * Mechanic container fragment hosting a child NavHostFragment and BottomNavigationView.
 *
 * Architecture:
 * - Hosts a child NavHostFragment for tab content (nav_mechanic graph)
 * - BottomNavigationView wired to child NavController for tab switching
 * - Toolbar title updates based on current child destination
 * - Sign-out via global action on the parent NavController
 */
class MechanicMainContainerFragment : BaseFragment<FragmentMechanicContainerBinding>(
    FragmentMechanicContainerBinding::inflate
) {

    private val authViewModel: AuthViewModel by activityViewModels()
    private lateinit var childNavController: NavController
    private val roleConfig = RoleNavigationConfig.getConfig(UserRole.MECHANIC)
    private val destinationTitles = RoleNavigationConfig.getDestinationTitles(UserRole.MECHANIC)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChildNavigation()
    }

    override fun initViews() {
        showToolbar(roleConfig.toolbarTitle)
        hideBottomNav()
    }

    override fun setupListeners() {
        // Navigation handled by setupWithNavController
    }

    override fun observeData() {
        // Placeholder — data observation will be added in future modules
    }

    private fun setupChildNavigation() {
        val navHostFragment = childFragmentManager
            .findFragmentById(R.id.childNavHostFragment) as NavHostFragment
        childNavController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(childNavController)

        childNavController.addOnDestinationChangedListener { _, destination, _ ->
            val title = destinationTitles[destination.id] ?: destination.label?.toString()
            title?.let { showToolbar(it) }
        }
    }

    fun getChildNavController(): NavController = childNavController

    fun signOut() {
        authViewModel.signOut()
        navigateTo(R.id.action_global_login)
    }
}
