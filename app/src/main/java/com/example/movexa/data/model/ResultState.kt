package com.example.movexa.data.model

/**
 * Sealed class representing the state of an asynchronous operation.
 * Used across repositories and ViewModels for consistent result handling.
 *
 * @param T The type of data in the success state.
 */
sealed class ResultState<out T> {

    /**
     * Initial idle state before any operation begins.
     */
    data object Idle : ResultState<Nothing>()

    /**
     * Operation is in progress.
     */
    data object Loading : ResultState<Nothing>()

    /**
     * Operation completed successfully with data.
     */
    data class Success<T>(val data: T) : ResultState<T>()

    /**
     * Operation failed with an error message.
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null,
        val code: Int? = null
    ) : ResultState<Nothing>()

    // ─── Utility Extensions ─────────────────────────────────────

    /**
     * Whether this state represents a loading operation.
     */
    val isLoading: Boolean get() = this is Loading

    /**
     * Whether this state represents a successful result.
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Whether this state represents an error.
     */
    val isError: Boolean get() = this is Error

    /**
     * Get the data if this is a Success state, otherwise null.
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * Get the error message if this is an Error state, otherwise null.
     */
    fun errorMessageOrNull(): String? = (this as? Error)?.message

    /**
     * Transform the data in a Success state.
     */
    fun <R> map(transform: (T) -> R): ResultState<R> {
        return when (this) {
            is Idle -> Idle
            is Loading -> Loading
            is Success -> Success(transform(data))
            is Error -> Error(message, exception, code)
        }
    }

    /**
     * Execute an action only if this is a Success state.
     */
    fun onSuccess(action: (T) -> Unit): ResultState<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Execute an action only if this is an Error state.
     */
    fun onError(action: (String, Throwable?) -> Unit): ResultState<T> {
        if (this is Error) action(message, exception)
        return this
    }

    /**
     * Execute an action only if this is a Loading state.
     */
    fun onLoading(action: () -> Unit): ResultState<T> {
        if (this is Loading) action()
        return this
    }
}
