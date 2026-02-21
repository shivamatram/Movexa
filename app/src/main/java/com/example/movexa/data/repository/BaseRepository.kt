package com.example.movexa.data.repository

import com.example.movexa.data.model.ResultState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Base repository providing common data operations.
 * All repositories should extend this class for consistent error handling.
 */
abstract class BaseRepository {

    /**
     * Default dispatcher for IO operations.
     */
    protected open val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * Execute an IO operation safely, wrapping the result in ResultState.
     * Handles exceptions and returns appropriate error states.
     */
    protected suspend fun <T> safeCall(
        dispatcher: CoroutineDispatcher = ioDispatcher,
        block: suspend () -> T
    ): ResultState<T> {
        return withContext(dispatcher) {
            try {
                ResultState.Success(block())
            } catch (e: Exception) {
                ResultState.Error(
                    message = e.message ?: "An unknown error occurred",
                    exception = e
                )
            }
        }
    }

    /**
     * Execute an IO operation with loading callback.
     */
    protected suspend fun <T> safeCallWithProgress(
        onLoading: (Boolean) -> Unit,
        dispatcher: CoroutineDispatcher = ioDispatcher,
        block: suspend () -> T
    ): ResultState<T> {
        onLoading(true)
        val result = safeCall(dispatcher, block)
        onLoading(false)
        return result
    }

    /**
     * Execute a Firebase operation safely.
     * Adds Firebase-specific error handling on top of safeCall.
     */
    protected suspend fun <T> firebaseSafeCall(
        block: suspend () -> T
    ): ResultState<T> {
        return safeCall(ioDispatcher) {
            block()
        }
    }

    /**
     * Parse error messages for user-friendly display.
     */
    protected open fun parseErrorMessage(exception: Throwable): String {
        return when {
            exception.message?.contains("PERMISSION_DENIED") == true ->
                "You don't have permission to perform this action."
            exception.message?.contains("NOT_FOUND") == true ->
                "The requested resource was not found."
            exception.message?.contains("ALREADY_EXISTS") == true ->
                "This resource already exists."
            exception.message?.contains("UNAUTHENTICATED") == true ->
                "Please sign in to continue."
            exception.message?.contains("UNAVAILABLE") == true ->
                "Service is temporarily unavailable. Please try again later."
            exception.message?.contains("network") == true ->
                "Network error. Please check your connection."
            else -> exception.message ?: "An unexpected error occurred."
        }
    }
}
