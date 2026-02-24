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
     * Uses [parseErrorMessage] to convert technical exceptions into
     * user-friendly messages.
     */
    protected suspend fun <T> firebaseSafeCall(
        block: suspend () -> T
    ): ResultState<T> {
        return withContext(ioDispatcher) {
            try {
                ResultState.Success(block())
            } catch (e: Exception) {
                ResultState.Error(
                    message = parseErrorMessage(e),
                    exception = e
                )
            }
        }
    }

    /**
     * Parse error messages for user-friendly display.
     */
    protected open fun parseErrorMessage(exception: Throwable): String {
        val msg = exception.message ?: ""
        return when {
            // ── Firebase Storage errors ──────────────────────
            msg.contains("Object does not exist at location", ignoreCase = true) ->
                "The requested file was not found. It may not have been uploaded yet."
            msg.contains("does not exist", ignoreCase = true)
                    && msg.contains("bucket", ignoreCase = true) ->
                "Storage is not configured. Please contact support."
            msg.contains("StorageException", ignoreCase = true) ->
                "A file storage error occurred. Please try again."

            // ── Firestore / Auth errors ─────────────────────
            msg.contains("PERMISSION_DENIED") ->
                "You don't have permission to perform this action."
            msg.contains("NOT_FOUND") ->
                "The requested resource was not found."
            msg.contains("ALREADY_EXISTS") ->
                "This resource already exists."
            msg.contains("UNAUTHENTICATED") ->
                "Please sign in to continue."
            msg.contains("UNAVAILABLE") ->
                "Service is temporarily unavailable. Please try again later."
            msg.contains("network", ignoreCase = true) ->
                "Network error. Please check your connection."
            else -> msg.ifBlank { "An unexpected error occurred." }
        }
    }
}
