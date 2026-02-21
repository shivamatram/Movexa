package com.example.movexa.navigation

import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import androidx.annotation.NavigationRes
import com.example.movexa.R
import com.example.movexa.data.model.UserRole

/**
 * Central configuration provider for role-based navigation.
 *
 * Each role has:
 * - A menu resource for the BottomNavigationView
 * - A navigation graph resource for the child NavHostFragment
 * - A toolbar title
 * - A start destination ID matching the first tab's fragment
 *
 * This decouples navigation structure from fragment implementations,
 * allowing easy modification of menus and graphs per role without
 * touching container fragment code.
 */
object RoleNavigationConfig {

    /**
     * Data class representing complete navigation configuration for a role.
     */
    data class RoleNavConfig(
        val role: UserRole,
        @MenuRes val menuRes: Int,
        @NavigationRes val navGraphRes: Int,
        @IdRes val startDestination: Int,
        val toolbarTitle: String,
        @IdRes val mainNavDestinationId: Int
    )

    /**
     * Get navigation configuration for a specific role.
     */
    fun getConfig(role: UserRole): RoleNavConfig {
        return when (role) {
            UserRole.ADMIN -> RoleNavConfig(
                role = UserRole.ADMIN,
                menuRes = R.menu.menu_admin,
                navGraphRes = R.navigation.nav_admin,
                startDestination = R.id.adminDashboardFragment,
                toolbarTitle = "Admin Dashboard",
                mainNavDestinationId = R.id.adminMainContainerFragment
            )
            UserRole.MANAGER -> RoleNavConfig(
                role = UserRole.MANAGER,
                menuRes = R.menu.menu_manager,
                navGraphRes = R.navigation.nav_manager,
                startDestination = R.id.managerDashboardFragment,
                toolbarTitle = "Manager Dashboard",
                mainNavDestinationId = R.id.managerMainContainerFragment
            )
            UserRole.DRIVER -> RoleNavConfig(
                role = UserRole.DRIVER,
                menuRes = R.menu.menu_driver,
                navGraphRes = R.navigation.nav_driver,
                startDestination = R.id.driverHomeFragment,
                toolbarTitle = "Driver Home",
                mainNavDestinationId = R.id.driverMainContainerFragment
            )
            UserRole.MECHANIC -> RoleNavConfig(
                role = UserRole.MECHANIC,
                menuRes = R.menu.menu_mechanic,
                navGraphRes = R.navigation.nav_mechanic,
                startDestination = R.id.mechanicDashboardFragment,
                toolbarTitle = "Mechanic Dashboard",
                mainNavDestinationId = R.id.mechanicMainContainerFragment
            )
        }
    }

    /**
     * Get all role configurations. Useful for pre-loading or validation.
     */
    fun getAllConfigs(): List<RoleNavConfig> {
        return UserRole.entries.map { getConfig(it) }
    }

    /**
     * Get the main nav graph destination ID for routing from login.
     */
    @IdRes
    fun getMainNavDestination(role: UserRole): Int {
        return getConfig(role).mainNavDestinationId
    }

    /**
     * Map of tab destination labels per role for toolbar title updates.
     * Key: destination fragment ID, Value: toolbar title to display.
     */
    fun getDestinationTitles(role: UserRole): Map<Int, String> {
        return when (role) {
            UserRole.ADMIN -> mapOf(
                R.id.adminDashboardFragment to "Dashboard",
                R.id.adminFleetFragment to "Fleet Management",
                R.id.adminTripsFragment to "Trip Management",
                R.id.adminFinanceFragment to "Finance",
                R.id.adminProfileFragment to "Profile"
            )
            UserRole.MANAGER -> mapOf(
                R.id.managerDashboardFragment to "Dashboard",
                R.id.managerFleetFragment to "Fleet Management",
                R.id.managerTripsFragment to "Trip Management",
                R.id.managerTrackingFragment to "Live Tracking",
                R.id.managerAlertsFragment to "Alerts",
                R.id.managerProfileFragment to "Profile"
            )
            UserRole.DRIVER -> mapOf(
                R.id.driverHomeFragment to "Home",
                R.id.driverTripsFragment to "My Trips",
                R.id.driverNavigationFragment to "Navigation",
                R.id.driverFuelFragment to "Fuel Log",
                R.id.driverPerformanceFragment to "Performance",
                R.id.driverProfileFragment to "Profile"
            )
            UserRole.MECHANIC -> mapOf(
                R.id.mechanicDashboardFragment to "Dashboard",
                R.id.mechanicServiceFragment to "Service Tasks",
                R.id.mechanicRepairsFragment to "Repairs",
                R.id.mechanicPartsFragment to "Parts Inventory",
                R.id.mechanicProfileFragment to "Profile"
            )
        }
    }
}
