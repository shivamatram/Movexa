package com.example.movexa.ui.components

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.example.movexa.R
import com.example.movexa.databinding.ViewNetworkBannerBinding
import com.example.movexa.utils.ANIM_DURATION_NORMAL

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 *  NETWORK STATUS BANNER
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * A slide-down banner that displays network connectivity status.
 * Designed to be placed at the top of the activity layout.
 *
 * ─── States ───────────────────────────────────────────────────────────────────
 *
 *  ● OFFLINE   — Red banner with cloud-off icon, "No internet connection"
 *  ● BACK_ONLINE — Green banner with check icon, "Back online", auto-hides
 *
 * ─── Usage ────────────────────────────────────────────────────────────────────
 *
 *  In activity_main.xml:
 *
 *    <com.example.movexa.ui.components.NetworkStatusBanner
 *        android:id="@+id/networkBanner"
 *        android:layout_width="match_parent"
 *        android:layout_height="wrap_content"
 *        android:visibility="gone" />
 *
 *  In MainActivity.kt:
 *
 *    binding.networkBanner.showOffline()
 *    binding.networkBanner.showBackOnline()
 *
 * @since 2026-02-22 — Final Polish Phase
 */
class NetworkStatusBanner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewNetworkBannerBinding

    /** Auto-hide delay when showing "back online". */
    private val autoHideDelayMs = 3000L

    /** Runnable for auto-hide. */
    private val autoHideRunnable = Runnable { slideOut() }

    init {
        binding = ViewNetworkBannerBinding.inflate(
            LayoutInflater.from(context), this, true
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Show the "No internet connection" offline banner.
     * Stays visible until [showBackOnline] or [hide] is called.
     */
    fun showOffline() {
        removeCallbacks(autoHideRunnable)

        val rootCard = binding.root as? com.google.android.material.card.MaterialCardView
        rootCard?.setCardBackgroundColor(
            ContextCompat.getColor(context, R.color.error)
        )
        binding.ivBannerIcon.setImageResource(R.drawable.ic_cloud_off)
        binding.tvBannerMessage.setText(R.string.network_offline)
        binding.ivBannerDismiss.visibility = View.GONE

        slideIn()
    }

    /**
     * Show the "Back online" success banner, then auto-hide after 3 seconds.
     */
    fun showBackOnline() {
        removeCallbacks(autoHideRunnable)

        val rootCard = binding.root as? com.google.android.material.card.MaterialCardView
        rootCard?.setCardBackgroundColor(
            ContextCompat.getColor(context, R.color.success)
        )
        binding.ivBannerIcon.setImageResource(R.drawable.ic_check_circle)
        binding.tvBannerMessage.setText(R.string.network_back_online)
        binding.ivBannerDismiss.visibility = View.GONE

        slideIn()

        // Auto-hide after delay
        postDelayed(autoHideRunnable, autoHideDelayMs)
    }

    /**
     * Hide the banner immediately.
     */
    fun hide() {
        removeCallbacks(autoHideRunnable)
        visibility = View.GONE
    }

    // ═══════════════════════════════════════════════════════════════
    //  PRIVATE ANIMATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Slide in from top.
     */
    private fun slideIn() {
        if (visibility == View.VISIBLE) return

        translationY = -height.toFloat().coerceAtLeast(80f)
        alpha = 0f
        visibility = View.VISIBLE

        animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(ANIM_DURATION_NORMAL)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }

    /**
     * Slide out upward.
     */
    private fun slideOut() {
        animate()
            .translationY(-height.toFloat())
            .alpha(0f)
            .setDuration(ANIM_DURATION_NORMAL)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                visibility = View.GONE
                translationY = 0f
                alpha = 1f
            }
            .start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(autoHideRunnable)
    }
}
