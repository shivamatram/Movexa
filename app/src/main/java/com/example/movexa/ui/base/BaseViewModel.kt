package com.example.movexa.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ResultState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel for all ViewModels in Movexa.
 * Provides common loading state, error handling, and coroutine utilities.
 */
abstract class BaseViewModel : ViewModel() {

    // ─── Loading State ──────────────────────────────────────────
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ─── Error Events ───────────────────────────────────────────
    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    // ─── Success Events ─────────────────────────────────────────
    private val _successEvent = MutableSharedFlow<String>()
    val successEvent: SharedFlow<String> = _successEvent.asSharedFlow()

    // ─── Navigation Events ──────────────────────────────────────
    private val _navigationEvent = MutableSharedFlow<Int>()
    val navigationEvent: SharedFlow<Int> = _navigationEvent.asSharedFlow()

    // ─── Loading Control ────────────────────────────────────────

    protected fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    // ─── Event Emitters ─────────────────────────────────────────

    protected fun emitError(message: String) {
        viewModelScope.launch {
            _errorEvent.emit(message)
        }
    }

    protected fun emitSuccess(message: String) {
        viewModelScope.launch {
            _successEvent.emit(message)
        }
    }

    protected fun emitNavigation(actionId: Int) {
        viewModelScope.launch {
            _navigationEvent.emit(actionId)
        }
    }

    // ─── Coroutine Launchers ────────────────────────────────────

    /**
     * Launch a coroutine with automatic loading and error handling.
     */
    protected fun launchWithLoading(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        showLoading: Boolean = true,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch(dispatcher) {
            try {
                if (showLoading) setLoading(true)
                block()
            } catch (e: Exception) {
                onError?.invoke(e) ?: emitError(e.message ?: "An unexpected error occurred")
            } finally {
                if (showLoading) setLoading(false)
            }
        }
    }

    /**
     * Launch a safe coroutine without loading indicator.
     */
    protected fun launchSafe(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend () -> Unit
    ) {
        launchWithLoading(
            dispatcher = dispatcher,
            showLoading = false,
            onError = onError,
            block = block
        )
    }

    /**
     * Execute an operation and emit the result through a StateFlow.
     */
    protected fun <T> executeWithResult(
        stateFlow: MutableStateFlow<ResultState<T>>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend () -> T
    ) {
        viewModelScope.launch(dispatcher) {
            stateFlow.value = ResultState.Loading
            try {
                val result = block()
                stateFlow.value = ResultState.Success(result)
            } catch (e: Exception) {
                stateFlow.value = ResultState.Error(
                    message = e.message ?: "Unknown error",
                    exception = e
                )
            }
        }
    }
}
