package com.example.movexa.navigation

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavOptions

/**
 * Extension functions for NavController to simplify navigation operations.
 */

/**
 * Navigate safely, catching exceptions from invalid navigation attempts
 * (e.g., double-tap, navigating after fragment destruction).
 */
fun NavController.navigateSafe(actionId: Int, args: Bundle? = null) {
    try {
        navigate(actionId, args)
    } catch (e: IllegalArgumentException) {
        // Action/destination not found — likely double-tap or stale reference
        e.printStackTrace()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Navigate and clear the entire back stack up to the start destination.
 * Used when transitioning between major app sections (e.g., login → dashboard).
 */
fun NavController.navigateAndClearStack(destinationId: Int) {
    val startDestId = graph.startDestinationId
    val navOptions = NavOptions.Builder()
        .setPopUpTo(startDestId, true)
        .build()

    try {
        navigate(destinationId, null, navOptions)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Navigate with custom NavOptions for animations.
 */
fun NavController.navigateWithAnimation(
    actionId: Int,
    args: Bundle? = null,
    enterAnim: Int = 0,
    exitAnim: Int = 0,
    popEnterAnim: Int = 0,
    popExitAnim: Int = 0
) {
    val navOptions = NavOptions.Builder()
        .setEnterAnim(enterAnim)
        .setExitAnim(exitAnim)
        .setPopEnterAnim(popEnterAnim)
        .setPopExitAnim(popExitAnim)
        .build()

    try {
        navigate(actionId, args, navOptions)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Execute a NavigationCommand through the NavController.
 */
fun NavController.execute(command: NavigationCommand) {
    when (command) {
        is NavigationCommand.ToAction -> navigateSafe(command.actionId, command.args)
        is NavigationCommand.ToDestination -> navigateSafe(command.destinationId, command.args)
        is NavigationCommand.Back -> popBackStack()
        is NavigationCommand.PopUpTo -> popBackStack(command.destinationId, command.inclusive)
        is NavigationCommand.ClearStackAndNavigate -> navigateAndClearStack(command.destinationId)
        is NavigationCommand.DeepLink -> {
            try {
                navigate(Uri.parse(command.uri))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
