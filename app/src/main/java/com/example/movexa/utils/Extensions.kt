package com.example.movexa.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Collection of Kotlin extension functions used throughout Movexa.
 * Organized by target type for clarity.
 */

// ─── View Extensions ────────────────────────────────────────────

/**
 * Show this view (VISIBLE).
 */
fun View.show() {
    visibility = View.VISIBLE
}

/**
 * Hide this view (GONE).
 */
fun View.gone() {
    visibility = View.GONE
}

/**
 * Make this view invisible (INVISIBLE, still takes space).
 */
fun View.invisible() {
    visibility = View.INVISIBLE
}

/**
 * Toggle visibility between VISIBLE and GONE.
 */
fun View.toggleVisibility() {
    visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
}

/**
 * Set visibility based on boolean condition.
 */
fun View.visibleIf(condition: Boolean, useInvisible: Boolean = false) {
    visibility = when {
        condition -> View.VISIBLE
        useInvisible -> View.INVISIBLE
        else -> View.GONE
    }
}

/**
 * Fade in view with animation.
 */
fun View.fadeIn(duration: Long = 300L) {
    if (visibility == View.VISIBLE && alpha == 1f) return
    alpha = 0f
    visibility = View.VISIBLE
    animate()
        .alpha(1f)
        .setDuration(duration)
        .setListener(null)
        .start()
}

/**
 * Fade out view with animation.
 */
fun View.fadeOut(duration: Long = 300L, goneAfter: Boolean = true) {
    animate()
        .alpha(0f)
        .setDuration(duration)
        .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (goneAfter) {
                    visibility = View.GONE
                }
            }
        })
        .start()
}

/**
 * Set a debounced click listener to prevent rapid multiple clicks.
 */
fun View.setDebouncedClickListener(debounceTimeMs: Long = 600L, action: (View) -> Unit) {
    var lastClickTime = 0L
    setOnClickListener { view ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= debounceTimeMs) {
            lastClickTime = currentTime
            action(view)
        }
    }
}

/**
 * Enable or disable a view with alpha feedback.
 */
fun View.setEnabledWithAlpha(enabled: Boolean, disabledAlpha: Float = 0.5f) {
    isEnabled = enabled
    alpha = if (enabled) 1f else disabledAlpha
}

// ─── EditText Extensions ────────────────────────────────────────

/**
 * Get trimmed text from EditText.
 */
fun EditText.trimmedText(): String = text?.toString()?.trim() ?: ""

/**
 * Add a text change listener with a simplified callback.
 */
fun EditText.onTextChanged(action: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            action(s?.toString() ?: "")
        }
    })
}

/**
 * Clear error on TextInputLayout when text changes.
 */
fun TextInputLayout.clearErrorOnTextChange() {
    editText?.onTextChanged { _ ->
        error = null
        isErrorEnabled = false
    }
}

// ─── Context Extensions ─────────────────────────────────────────

/**
 * Resolve a color resource.
 */
fun Context.color(@ColorRes colorRes: Int): Int {
    return ContextCompat.getColor(this, colorRes)
}

/**
 * Show a short Toast.
 */
fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/**
 * Show a long Toast.
 */
fun Context.toastLong(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

/**
 * Hide the keyboard.
 */
fun Context.hideKeyboard(view: View) {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}

/**
 * Show the keyboard for an EditText.
 */
fun Context.showKeyboard(editText: EditText) {
    editText.requestFocus()
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
}

// ─── Fragment Extensions ────────────────────────────────────────

/**
 * Hide the keyboard from a Fragment.
 */
fun Fragment.hideKeyboard() {
    view?.let { v ->
        requireContext().hideKeyboard(v)
    }
}

/**
 * Collect a Flow lifecycle-aware from a Fragment.
 */
fun <T> Fragment.collectFlowOnLifecycle(
    flow: Flow<T>,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    action: suspend (T) -> Unit
) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(state) {
            flow.collect { action(it) }
        }
    }
}

// ─── String Extensions ──────────────────────────────────────────

/**
 * Validate email format.
 */
fun String.isValidEmail(): Boolean {
    return isNotBlank() && Constants.EMAIL_PATTERN.matches(this)
}

/**
 * Validate phone number format.
 */
fun String.isValidPhone(): Boolean {
    return isNotBlank() && Constants.PHONE_PATTERN.matches(this)
}

/**
 * Validate password strength.
 */
fun String.isValidPassword(): Boolean {
    return length >= 8 && Constants.PASSWORD_PATTERN.matches(this)
}

/**
 * Capitalize each word in the string.
 */
fun String.capitalizeWords(): String {
    return split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

// ─── Long Extensions ────────────────────────────────────────────

/**
 * Format timestamp to a human-readable elapsed time string.
 */
fun Long.toTimeAgo(): String {
    val now = System.currentTimeMillis()
    val diff = now - this

    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} hr ago"
        diff < 604_800_000 -> "${diff / 86_400_000} days ago"
        else -> "${diff / 604_800_000} weeks ago"
    }
}

// ─── Number Extensions ──────────────────────────────────────────

/**
 * Format distance in meters to a readable string (m or km).
 */
fun Float.toDistanceString(): String {
    return if (this < 1000) {
        "%.0f m".format(this)
    } else {
        "%.1f km".format(this / 1000)
    }
}

/**
 * Format speed in m/s to km/h string.
 */
fun Float.toSpeedString(): String {
    val kmh = this * 3.6f
    return "%.0f km/h".format(kmh)
}
