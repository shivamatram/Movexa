package com.example.movexa.data.model

/**
 * Represents the state of the manager list screen.
 *
 * Used by AdminManagerViewModel to communicate list loading
 * status to the UI layer.
 */
sealed class ManagerListState {

    /** Initial loading state. */
    data object Loading : ManagerListState()

    /** Managers loaded successfully. */
    data class Success(
        val managers: List<User>,
        val totalCount: Int,
        val filteredCount: Int
    ) : ManagerListState()

    /** Empty state — no managers found. */
    data class Empty(val message: String = "No managers found") : ManagerListState()

    /** Error loading managers. */
    data class Error(val message: String) : ManagerListState()

    /** Refreshing (pull-to-refresh). */
    data object Refreshing : ManagerListState()
}
