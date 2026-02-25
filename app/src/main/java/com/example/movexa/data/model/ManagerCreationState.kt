package com.example.movexa.data.model

/**
 * Represents the state of manager creation flow.
 *
 * Used by AdminManagerViewModel to communicate creation progress
 * to the UI layer (bottom sheet / fragment).
 */
sealed class ManagerCreationState {

    /** Initial state — form is ready for input. */
    data object Idle : ManagerCreationState()

    /** Validating input fields. */
    data object Validating : ManagerCreationState()

    /** Creating Firebase Auth account. */
    data object CreatingAuth : ManagerCreationState()

    /** Writing Firestore document. */
    data object WritingProfile : ManagerCreationState()

    /** Sending password reset email. */
    data object SendingResetEmail : ManagerCreationState()

    /** Manager created successfully. */
    data class Success(val manager: User) : ManagerCreationState()

    /** Creation failed with error message. */
    data class Error(val message: String) : ManagerCreationState()
}
