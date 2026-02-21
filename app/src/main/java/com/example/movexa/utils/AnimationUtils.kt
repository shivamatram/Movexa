package com.example.movexa.utils

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Utility class for custom View animations used throughout Movexa.
 * Provides consistent, reusable animation patterns.
 */
object AnimationUtils {

    /**
     * Scale up and fade in (used for splash branding).
     */
    fun scaleUpFadeIn(view: View, duration: Long = 500L, onEnd: (() -> Unit)? = null) {
        view.alpha = 0f
        view.scaleX = 0.85f
        view.scaleY = 0.85f
        view.visibility = View.VISIBLE

        val alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
        val scaleXAnim = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.85f, 1f)
        val scaleYAnim = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.85f, 1f)

        AnimatorSet().apply {
            playTogether(alphaAnim, scaleXAnim, scaleYAnim)
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addListener(createEndListener(onEnd))
            start()
        }
    }

    /**
     * Fade in animation.
     */
    fun fadeIn(view: View, duration: Long = 400L, onEnd: (() -> Unit)? = null) {
        view.alpha = 0f
        view.visibility = View.VISIBLE

        ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addListener(createEndListener(onEnd))
            start()
        }
    }

    /**
     * Fade out animation.
     */
    fun fadeOut(view: View, duration: Long = 300L, onEnd: (() -> Unit)? = null) {
        ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addListener(createEndListener {
                view.visibility = View.GONE
                onEnd?.invoke()
            })
            start()
        }
    }

    /**
     * Slide up and fade in from below.
     */
    fun slideUpFadeIn(
        view: View,
        translationY: Float = 100f,
        duration: Long = 400L,
        startDelay: Long = 0L,
        onEnd: (() -> Unit)? = null
    ) {
        view.alpha = 0f
        view.translationY = translationY
        view.visibility = View.VISIBLE

        val alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
        val transYAnim = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, translationY, 0f)

        AnimatorSet().apply {
            playTogether(alphaAnim, transYAnim)
            this.duration = duration
            this.startDelay = startDelay
            interpolator = DecelerateInterpolator()
            addListener(createEndListener(onEnd))
            start()
        }
    }

    /**
     * Bounce scale animation (used for interactive feedback).
     */
    fun bounceScale(view: View, duration: Long = 300L) {
        val scaleXAnim = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.1f, 1f)
        val scaleYAnim = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.1f, 1f)

        AnimatorSet().apply {
            playTogether(scaleXAnim, scaleYAnim)
            this.duration = duration
            interpolator = OvershootInterpolator()
            start()
        }
    }

    /**
     * Pulse animation (subtle scale in/out loop).
     */
    fun pulse(view: View, duration: Long = 1000L): AnimatorSet {
        val scaleXAnim = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.05f, 1f)
        val scaleYAnim = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.05f, 1f)

        return AnimatorSet().apply {
            playTogether(scaleXAnim, scaleYAnim)
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    /**
     * Staggered animation for a list of views.
     * Each view animates with a small delay after the previous one.
     */
    fun staggeredFadeIn(
        views: List<View>,
        duration: Long = 300L,
        staggerDelay: Long = 80L
    ) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 30f
            view.visibility = View.VISIBLE

            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(index * staggerDelay)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /**
     * Shake animation (used for error feedback).
     */
    fun shake(view: View, duration: Long = 400L) {
        ObjectAnimator.ofFloat(
            view, View.TRANSLATION_X,
            0f, -10f, 10f, -10f, 10f, -5f, 5f, 0f
        ).apply {
            this.duration = duration
            start()
        }
    }

    // ─── Helpers ────────────────────────────────────────────────

    private fun createEndListener(onEnd: (() -> Unit)?): Animator.AnimatorListener {
        return object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) { onEnd?.invoke() }
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        }
    }
}
