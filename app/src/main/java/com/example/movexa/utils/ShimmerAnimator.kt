package com.example.movexa.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.example.movexa.R

/**
 * ShimmerAnimator — Lightweight shimmer effect for placeholder views.
 *
 * Uses alpha pulsing animation on placeholder view groups to simulate
 * a loading shimmer without requiring a third-party library.
 *
 * Usage:
 * ```
 * val shimmer = ShimmerAnimator()
 * shimmer.attach(binding.shimmerContainer)   // start pulsing
 * shimmer.detach()                           // stop + reset
 * ```
 *
 * Or use extension functions on any View / ViewGroup:
 * ```
 * binding.shimmerContainer.startShimmerPulse()
 * binding.shimmerContainer.stopShimmerPulse()
 * ```
 */
class ShimmerAnimator(
    private val minAlpha: Float = 0.35f,
    private val maxAlpha: Float = 1.0f,
    private val duration: Long = 1000L
) {

    private var animator: ObjectAnimator? = null
    private var targetView: View? = null

    /**
     * Attach to a container and start the shimmer pulse animation.
     * If already attached to a different view, detaches first.
     */
    fun attach(view: View) {
        if (targetView === view && animator?.isRunning == true) return
        detach()
        targetView = view
        view.visibility = View.VISIBLE

        animator = ObjectAnimator.ofFloat(view, View.ALPHA, maxAlpha, minAlpha).apply {
            this.duration = this@ShimmerAnimator.duration
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    /**
     * Stop shimmer and hide the container.
     */
    fun detach() {
        animator?.cancel()
        animator = null
        targetView?.alpha = 1f
        targetView?.visibility = View.GONE
        targetView = null
    }

    /**
     * Pause the shimmer without hiding the view.
     */
    fun pause() {
        animator?.pause()
    }

    /**
     * Resume a paused shimmer.
     */
    fun resume() {
        animator?.resume()
    }

    /**
     * Whether the shimmer is currently running.
     */
    val isRunning: Boolean
        get() = animator?.isRunning == true

    companion object {
        /** Default shared durations */
        const val SHIMMER_FAST = 700L
        const val SHIMMER_NORMAL = 1000L
        const val SHIMMER_SLOW = 1400L
    }
}

// ──────────────────────────────────────────────────────────────
//  View Extension Functions
// ──────────────────────────────────────────────────────────────

private val shimmerAnimators = mutableMapOf<Int, ObjectAnimator>()

/**
 * Start a shimmer pulse animation on this view.
 * Sets visibility to VISIBLE and begins alpha oscillation.
 */
fun View.startShimmerPulse(
    minAlpha: Float = 0.35f,
    maxAlpha: Float = 1.0f,
    duration: Long = ShimmerAnimator.SHIMMER_NORMAL
) {
    stopShimmerPulse()
    visibility = View.VISIBLE

    val anim = ObjectAnimator.ofFloat(this, View.ALPHA, maxAlpha, minAlpha).apply {
        this.duration = duration
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        start()
    }
    shimmerAnimators[System.identityHashCode(this)] = anim
}

/**
 * Stop shimmer pulse and hide this view.
 */
fun View.stopShimmerPulse() {
    val key = System.identityHashCode(this)
    shimmerAnimators.remove(key)?.cancel()
    alpha = 1f
    visibility = View.GONE
}

/**
 * Cross-fade from shimmer container to content views.
 * Smoothly fades out the shimmer while fading in the content.
 *
 * @param shimmerView The shimmer placeholder container.
 * @param contentView The real content to reveal.
 * @param duration    Cross-fade duration in ms.
 */
fun crossFadeShimmerToContent(
    shimmerView: View,
    contentView: View,
    duration: Long = 300L
) {
    shimmerView.stopShimmerPulse()
    shimmerView.visibility = View.VISIBLE
    shimmerView.alpha = 1f

    contentView.alpha = 0f
    contentView.visibility = View.VISIBLE

    // Fade out shimmer
    shimmerView.animate()
        .alpha(0f)
        .setDuration(duration)
        .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                shimmerView.visibility = View.GONE
                shimmerView.alpha = 1f
            }
        })
        .start()

    // Fade in content
    contentView.animate()
        .alpha(1f)
        .setDuration(duration)
        .setListener(null)
        .start()
}

/**
 * Show shimmer and hide content with a single call.
 * For starting a loading state.
 *
 * @param shimmerView The shimmer placeholder to show.
 * @param contentView The content to hide.
 */
fun showShimmerHideContent(
    shimmerView: View,
    contentView: View,
    duration: Long = ShimmerAnimator.SHIMMER_NORMAL
) {
    contentView.visibility = View.GONE
    shimmerView.startShimmerPulse(duration = duration)
}

/**
 * Animate individual child views in a shimmer container with staggered alpha
 * for a more lively loading effect.
 *
 * @param container ViewGroup containing shimmer placeholder children.
 * @param staggerDelay Delay between each child's animation start.
 */
fun ViewGroup.staggeredShimmerPulse(
    staggerDelay: Long = 80L,
    minAlpha: Float = 0.3f,
    maxAlpha: Float = 1.0f,
    duration: Long = ShimmerAnimator.SHIMMER_NORMAL
) {
    visibility = View.VISIBLE
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        val anim = ObjectAnimator.ofFloat(child, View.ALPHA, maxAlpha, minAlpha).apply {
            this.duration = duration
            startDelay = i * staggerDelay
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        shimmerAnimators[System.identityHashCode(child)] = anim
    }
}

/**
 * Stop all staggered shimmer animations on child views.
 */
fun ViewGroup.stopStaggeredShimmerPulse() {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        val key = System.identityHashCode(child)
        shimmerAnimators.remove(key)?.cancel()
        child.alpha = 1f
    }
    visibility = View.GONE
}
