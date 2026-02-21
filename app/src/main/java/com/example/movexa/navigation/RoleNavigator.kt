package com.example.movexa.navigation

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import com.example.movexa.R
import com.example.movexa.data.model.UserRole

/**
 * Centralized navigation router for role-based routing.
 *
 * Handles:
 * - Auth → Dashboard routing based on user role
 * - Global navigation actions (e.g., sign out → login)
 * - Deep link destination resolution
 *
 * This class decouples navigation decisions from fragments,
 * making it easy to modify routing logic in one place.
 */
object RoleNavigator {

    /**
     * Navigate from login to the appropriate dashboard container based on role.
     *
     * @param navController The main NavController (activity-level)
     * @param role The authenticated user's role
     * @param extras Optional bundle for passing data to the destination
     */
    fun navigateToDashboard(
        navController: NavController,
        role: UserRole,
        extras: Bundle? = null
    ) {
        val actionId = getDashboardActionId(role)
        navController.navigate(actionId, extras)
    }

    /**
     * Navigate back to login screen (e.g., after sign out).
     *
     * @param navController The main NavController
     */
    fun navigateToLogin(navController: NavController) {
        navController.navigate(R.id.action_global_login)
    }

    /**
     * Get the navigation action ID for a given role's dashboard.
     */
    @IdRes
    fun getDashboardActionId(role: UserRole): Int {
        return when (role) {
            UserRole.ADMIN -> R.id.action_login_to_admin
            UserRole.MANAGER -> R.id.action_login_to_manager
            UserRole.DRIVER -> R.id.action_login_to_driver
            UserRole.MECHANIC -> R.id.action_login_to_mechanic
        }
    }

    /**
     * Get the destination ID for a given role's container fragment.
     */
    @IdRes
    fun getContainerDestinationId(role: UserRole): Int {
        return RoleNavigationConfig.getConfig(role).mainNavDestinationId
    }

    /**
     * Check if a destination ID belongs to any role's container fragment.
     */
    fun isContainerDestination(@IdRes destinationId: Int): Boolean {
        return RoleNavigationConfig.getAllConfigs().any {
            it.mainNavDestinationId == destinationId
        }
    }

    /**
     * Get all container destination IDs (for toolbar/bottom nav visibility logic).
     */
    fun getAllContainerDestinationIds(): List<Int> {
        return RoleNavigationConfig.getAllConfigs().map { it.mainNavDestinationId }
    }
}
