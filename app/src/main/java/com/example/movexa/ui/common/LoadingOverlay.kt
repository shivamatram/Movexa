package com.example.movexa.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.example.movexa.databinding.LayoutLoadingOverlayBinding

/**
 * Global full-screen loading overlay component.
 * Can be attached to any activity or fragment for blocking loading states.
 *
 * Usage via activity:
 *   (activity as? MainActivity)?.showLoading()
 *   (activity as? MainActivity)?.hideLoading()
 */
class LoadingOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: LayoutLoadingOverlayBinding

    init {
        binding = LayoutLoadingOverlayBinding.inflate(
            LayoutInflater.from(context), this, true
        )
        visibility = View.GONE
    }

    /**
     * Show the loading overlay with optional message.
     */
    fun show(message: String? = null) {
        visibility = View.VISIBLE
        binding.tvLoadingMessage.text = message ?: ""
        binding.tvLoadingMessage.visibility =
            if (message.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /**
     * Hide the loading overlay.
     */
    fun hide() {
        visibility = View.GONE
    }

    /**
     * Whether the overlay is currently showing.
     */
    fun isShowing(): Boolean = visibility == View.VISIBLE
}
