package com.example.movexa.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import com.example.movexa.R
import com.example.movexa.databinding.ViewErrorStateBinding
import com.example.movexa.utils.fadeSlideIn
import com.example.movexa.utils.wiggle

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 *  ERROR STATE VIEW
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * A reusable error state component for handling:
 *   • No internet connection
 *   • Request timeout
 *   • Server errors
 *   • Permission denied
 *   • Generic failures
 *
 * ─── Usage ────────────────────────────────────────────────────────────────────
 *
 *   binding.errorState.configure(
 *       type = ErrorType.NO_INTERNET,
 *       onRetry = { viewModel.retry() }
 *   )
 *   binding.errorState.show()
 *
 * @since 2026-02-22 — Final Polish Phase
 */
class ErrorStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewErrorStateBinding

    private var onRetryClick: (() -> Unit)? = null

    init {
        binding = ViewErrorStateBinding.inflate(
            LayoutInflater.from(context), this, true
        )

        binding.btnRetry.setOnClickListener {
            onRetryClick?.invoke()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ERROR TYPES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Predefined error types with icon, title, and message.
     */
    enum class ErrorType(
        @DrawableRes val icon: Int,
        val titleRes: Int,
        val messageRes: Int
    ) {
        NO_INTERNET(
            R.drawable.ic_cloud_off,
            R.string.error_no_internet_title,
            R.string.error_no_internet_message
        ),
        TIMEOUT(
            R.drawable.ic_schedule,
            R.string.error_timeout_title,
            R.string.error_timeout_message
        ),
        PERMISSION_DENIED(
            R.drawable.ic_block,
            R.string.error_permission_title,
            R.string.error_permission_message
        ),
        GENERIC(
            R.drawable.ic_warning,
            R.string.error_generic_title,
            R.string.error_generic_message
        ),
        SERVER(
            R.drawable.ic_cloud_off,
            R.string.error_server_title,
            R.string.error_server_message
        ),
        EMPTY_RESULT(
            R.drawable.ic_search,
            R.string.error_empty_result_title,
            R.string.error_empty_result_message
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Configure with a predefined error type.
     */
    fun configure(type: ErrorType, onRetry: (() -> Unit)? = null) {
        binding.ivErrorIcon.setImageResource(type.icon)
        binding.tvErrorTitle.setText(type.titleRes)
        binding.tvErrorMessage.setText(type.messageRes)

        onRetryClick = onRetry
        binding.btnRetry.visibility = if (onRetry != null) View.VISIBLE else View.GONE
    }

    /**
     * Configure with custom strings.
     */
    fun configure(
        @DrawableRes icon: Int,
        title: String,
        message: String,
        retryText: String? = null,
        onRetry: (() -> Unit)? = null
    ) {
        binding.ivErrorIcon.setImageResource(icon)
        binding.tvErrorTitle.text = title
        binding.tvErrorMessage.text = message

        if (retryText != null) {
            binding.btnRetry.text = retryText
        }

        onRetryClick = onRetry
        binding.btnRetry.visibility = if (onRetry != null) View.VISIBLE else View.GONE
    }

    /**
     * Show with entrance animation + icon wiggle.
     */
    fun show(animate: Boolean = true) {
        visibility = View.VISIBLE
        if (animate) {
            binding.ivErrorIcon.fadeSlideIn(delay = 0L)
            binding.tvErrorTitle.fadeSlideIn(delay = 100L)
            binding.tvErrorMessage.fadeSlideIn(delay = 200L)
            binding.btnRetry.fadeSlideIn(delay = 300L)

            // Subtle wiggle on the icon for attention
            binding.ivErrorIcon.postDelayed({
                binding.ivErrorIcon.wiggle(8f)
            }, 500L)
        }
    }

    /**
     * Hide the error state.
     */
    fun hide() {
        visibility = View.GONE
    }
}
