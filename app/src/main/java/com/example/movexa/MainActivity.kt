package com.example.movexa

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.movexa.databinding.ActivityMainBinding

/**
 * Single Activity architecture host for the Movexa app.
 *
 * Responsibilities:
 * - Host NavHostFragment for all fragment navigation
 * - Provide global loading indicator control
 * - Provide toolbar visibility/title control
 * - Provide bottom navigation visibility control
 * - Handle system window insets (edge-to-edge)
 *
 * Fragments control toolbar/bottom nav visibility through BaseFragment helpers
 * that delegate to methods exposed here.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // ─── Lifecycle ──────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        setupWindowInsets()
        setupNavigation()
        setupToolbar()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    // ─── Setup ──────────────────────────────────────────────────

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        // Wire bottom navigation with NavController
        binding.bottomNavigation.setupWithNavController(navController)

        // Listen for destination changes to handle UI state
        navController.addOnDestinationChangedListener { _, destination, _ ->
            onDestinationChanged(destination.id)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    /**
     * Handle destination changes to automatically toggle toolbar/bottom nav.
     * Splash and auth screens hide both; dashboard screens show toolbar.
     */
    private fun onDestinationChanged(destinationId: Int) {
        when (destinationId) {
            // Splash & Auth — hide everything
            R.id.splashLogoFragment,
            R.id.splashBrandFragment,
            R.id.loginFragment,
            R.id.signupFragment -> {
                hideToolbar()
                hideBottomNav()
            }

            // Dashboard containers — show toolbar, hide bottom nav (placeholder)
            R.id.adminMainContainerFragment,
            R.id.managerMainContainerFragment,
            R.id.mechanicMainContainerFragment,
            R.id.driverHomeFragment,
            R.id.driverProfileFragment -> {
                showToolbar()
                hideBottomNav()
            }
        }
    }

    // ─── Public API (called by BaseFragment) ────────────────────

    /**
     * Show the global linear loading indicator at the top of the screen.
     */
    fun showLoading() {
        binding.globalLoadingIndicator.visibility = View.VISIBLE
    }

    /**
     * Hide the global linear loading indicator.
     */
    fun hideLoading() {
        binding.globalLoadingIndicator.visibility = View.GONE
    }

    /**
     * Show the toolbar with an optional title.
     */
    fun showToolbar(title: String? = null) {
        binding.toolbar.visibility = View.VISIBLE
        title?.let {
            supportActionBar?.title = it
        }
    }

    /**
     * Hide the toolbar.
     */
    fun hideToolbar() {
        binding.toolbar.visibility = View.GONE
    }

    /**
     * Show the bottom navigation bar.
     */
    fun showBottomNav() {
        binding.bottomNavigation.visibility = View.VISIBLE
    }

    /**
     * Hide the bottom navigation bar.
     */
    fun hideBottomNav() {
        binding.bottomNavigation.visibility = View.GONE
    }

    /**
     * Get the NavController for advanced navigation operations.
     */
    fun getNavController(): NavController = navController
}