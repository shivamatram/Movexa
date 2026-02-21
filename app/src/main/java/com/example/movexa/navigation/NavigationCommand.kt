package com.example.movexa.navigation

import android.os.Bundle

/**
 * Sealed class representing navigation commands from ViewModels.
 * Allows ViewModels to emit navigation events without accessing NavController directly.
 */
sealed class NavigationCommand {

    /**
     * Navigate to a destination using an action ID.
     */
    data class ToAction(
        val actionId: Int,
        val args: Bundle? = null
    ) : NavigationCommand()

    /**
     * Navigate to a destination using graph destination ID directly.
     */
    data class ToDestination(
        val destinationId: Int,
        val args: Bundle? = null
    ) : NavigationCommand()

    /**
     * Navigate back in the navigation stack.
     */
    data object Back : NavigationCommand()

    /**
     * Pop back stack to a specific destination.
     */
    data class PopUpTo(
        val destinationId: Int,
        val inclusive: Boolean = false
    ) : NavigationCommand()

    /**
     * Navigate and clear the entire back stack (e.g., after login).
     */
    data class ClearStackAndNavigate(
        val destinationId: Int
    ) : NavigationCommand()

    /**
     * Navigate to a deep link URI.
     */
    data class DeepLink(
        val uri: String
    ) : NavigationCommand()
}
