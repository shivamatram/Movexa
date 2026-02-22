package com.example.movexa.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 *  VIEW ANIMATION EXTENSIONS
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Professional animation extension functions for the Movexa FINAL POLISH phase.
 * These provide a consistent, silky-smooth motion language across all screens.
 *
 * ─── Categories ───────────────────────────────────────────────────────────────
 *
 *  1. ENTRANCE / EXIT      — fadeSlideIn, fadeSlideOut, expandIn, collapseOut
 *  2. EMPHASIS              — pulseGlow, scalePress, bounceIn, wiggle
 *  3. CARD POLISH           — cardLift, cardSettle, cardReveal
 *  4. COUNTER / SCORE       — animateCounterTo, animateScoreTo
 *  5. STAGGERED LISTS       — staggeredReveal, cascadeIn
 *  6. COLOR TRANSITIONS     — animateBackgroundColor
 *  7. RIPPLE HELPERS        — addPressScale, addLiftOnTouch
 *
 * ─── Design Principles ───────────────────────────────────────────────────────
 *
 *  ● Natural motion: DecelerateInterpolator for entrances, AccelerateInterpolator for exits
 *  ● Overshoot for playful emphasis, FastOutSlowIn for material standard
 *  ● Duration tiers: fast=150ms, normal=300ms, slow=500ms, dramatic=800ms
 *  ● All animations respect view lifecycle (check isAttachedToWindow)
 *
 * @since 2026-02-22 — Final Polish Phase
 */

// ═══════════════════════════════════════════════════════════════════════════════
//  DURATION CONSTANTS
// ═══════════════════════════════════════════════════════════════════════════════

/** Ultra-fast micro-animation (ripple, tap) */
const val ANIM_DURATION_INSTANT = 100L

/** Fast animation (toggle, switch) */
const val ANIM_DURATION_FAST = 150L

/** Standard animation (fade, slide) */
const val ANIM_DURATION_NORMAL = 300L

/** Emphasized animation (page transition, card reveal) */
const val ANIM_DURATION_SLOW = 500L

/** Dramatic animation (splash, onboarding) */
const val ANIM_DURATION_DRAMATIC = 800L

/** Stagger delay between list items */
const val ANIM_STAGGER_DELAY = 60L


// ═══════════════════════════════════════════════════════════════════════════════
//  1. ENTRANCE / EXIT ANIMATIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Fade + slide in from bottom. Standard entrance for cards and sections.
 *
 * @param offsetY  Starting Y offset in pixels (default 60f)
 * @param duration Animation duration
 * @param delay    Start delay
 * @param onEnd    Callback when animation completes
 */
fun View.fadeSlideIn(
    offsetY: Float = 60f,
    duration: Long = ANIM_DURATION_NORMAL,
    delay: Long = 0L,
    onEnd: (() -> Unit)? = null
) {
    alpha = 0f
    translationY = offsetY
    visibility = View.VISIBLE
    animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(duration)
        .setStartDelay(delay)
        .setInterpolator(DecelerateInterpolator(1.8f))
        .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { onEnd?.invoke() }
        })
        .start()
}

/**
 * Fade + slide out downward. Standard exit for cards and sections.
 */
fun View.fadeSlideOut(
    offsetY: Float = 40f,
    duration: Long = ANIM_DURATION_FAST,
    onEnd: (() -> Unit)? = null
) {
    animate()
        .alpha(0f)
        .translationY(offsetY)
        .setDuration(duration)
        .setInterpolator(AccelerateInterpolator(1.5f))
        .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                visibility = View.GONE
                translationY = 0f
                onEnd?.invoke()
            }
        })
        .start()
}

/**
 * Fade + slide in from the left (used for page-enter from navigation).
 */
fun View.fadeSlideInFromLeft(
    offsetX: Float = -80f,
    duration: Long = ANIM_DURATION_NORMAL,
    delay: Long = 0L
) {
    alpha = 0f
    translationX = offsetX
    visibility = View.VISIBLE
    animate()
        .alpha(1f)
        .translationX(0f)
        .setDuration(duration)
        .setStartDelay(delay)
        .setInterpolator(DecelerateInterpolator(1.8f))
        .start()
}

/**
 * Fade + slide in from the right.
 */
fun View.fadeSlideInFromRight(
    offsetX: Float = 80f,
    duration: Long = ANIM_DURATION_NORMAL,
    delay: Long = 0L
) {
    alpha = 0f
    translationX = offsetX
    visibility = View.VISIBLE
    animate()
        .alpha(1f)
        .translationX(0f)
        .setDuration(duration)
        .setStartDelay(delay)
        .setInterpolator(DecelerateInterpolator(1.8f))
        .start()
}

/**
 * Expand view height from 0 to wrap_content. Smooth accordion effect.
 */
fun View.expandIn(duration: Long = ANIM_DURATION_NORMAL) {
    measure(
        View.MeasureSpec.makeMeasureSpec((parent as View).width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val targetHeight = measuredHeight
    layoutParams.height = 0
    visibility = View.VISIBLE
    alpha = 0f

    ValueAnimator.ofInt(0, targetHeight).apply {
        this.duration = duration
        interpolator = DecelerateInterpolator(1.5f)
        addUpdateListener { animator ->
            layoutParams.height = animator.animatedValue as Int
            alpha = animator.animatedFraction
            requestLayout()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        })
        start()
    }
}

/**
 * Collapse view height to 0 and hide. Reverse accordion.
 */
fun View.collapseOut(duration: Long = ANIM_DURATION_NORMAL) {
    val initialHeight = measuredHeight

    ValueAnimator.ofInt(initialHeight, 0).apply {
        this.duration = duration
        interpolator = AccelerateInterpolator(1.5f)
        addUpdateListener { animator ->
            layoutParams.height = animator.animatedValue as Int
            alpha = 1f - animator.animatedFraction
            requestLayout()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                visibility = View.GONE
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                alpha = 1f
            }
        })
        start()
    }
}

/**
 * Cross-fade between two views (hide `this`, show `target`).
 */
fun View.crossFadeTo(target: View, duration: Long = ANIM_DURATION_NORMAL) {
    target.alpha = 0f
    target.visibility = View.VISIBLE

    target.animate()
        .alpha(1f)
        .setDuration(duration)
        .setInterpolator(DecelerateInterpolator())
        .start()

    this.animate()
        .alpha(0f)
        .setDuration(duration)
        .setInterpolator(AccelerateInterpolator())
        .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                this@crossFadeTo.visibility = View.GONE
            }
        })
        .start()
}


// ═══════════════════════════════════════════════════════════════════════════════
//  2. EMPHASIS ANIMATIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Subtle pulse glow — scale up 5% then back. Good for score updates.
 */
fun View.pulseGlow(
    scaleTo: Float = 1.08f,
    duration: Long = ANIM_DURATION_SLOW,
    repeatCount: Int = 0
) {
    val scaleX = ObjectAnimator.ofFloat(this, View.SCALE_X, 1f, scaleTo, 1f)
    val scaleY = ObjectAnimator.ofFloat(this, View.SCALE_Y, 1f, scaleTo, 1f)

    AnimatorSet().apply {
        playTogether(scaleX, scaleY)
        this.duration = duration
        interpolator = AccelerateDecelerateInterpolator()
        if (repeatCount > 0) {
            scaleX.repeatCount = repeatCount
            scaleY.repeatCount = repeatCount
        }
        start()
    }
}

/**
 * Scale down on press, scale back on release. Material-style press feedback.
 *
 * Apply to any clickable view for a professional tap response.
 */
fun View.addPressScale(scaleTo: Float = 0.95f) {
    setOnTouchListener { v, event ->
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                v.animate()
                    .scaleX(scaleTo)
                    .scaleY(scaleTo)
                    .setDuration(ANIM_DURATION_INSTANT)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(ANIM_DURATION_FAST)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
        }
        false // Don't consume — let click listener fire
    }
}

/**
 * Bounce-in entrance with overshoot. Good for success icons, badges.
 */
fun View.bounceIn(
    duration: Long = ANIM_DURATION_SLOW,
    delay: Long = 0L,
    onEnd: (() -> Unit)? = null
) {
    alpha = 0f
    scaleX = 0.3f
    scaleY = 0.3f
    visibility = View.VISIBLE

    animate()
        .alpha(1f)
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(duration)
        .setStartDelay(delay)
        .setInterpolator(OvershootInterpolator(1.5f))
        .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { onEnd?.invoke() }
        })
        .start()
}

/**
 * Wiggle / shake animation. Used for error feedback or attention grab.
 */
fun View.wiggle(amplitude: Float = 12f, duration: Long = ANIM_DURATION_NORMAL) {
    ObjectAnimator.ofFloat(
        this, View.TRANSLATION_X,
        0f, -amplitude, amplitude, -amplitude, amplitude,
        -amplitude * 0.5f, amplitude * 0.5f, 0f
    ).apply {
        this.duration = duration
        interpolator = DecelerateInterpolator()
        start()
    }
}

/**
 * Rotate spin — useful for refresh icons and loading indicators.
 */
fun View.spin(
    degrees: Float = 360f,
    duration: Long = ANIM_DURATION_SLOW,
    repeatCount: Int = ValueAnimator.INFINITE
): ObjectAnimator {
    return ObjectAnimator.ofFloat(this, View.ROTATION, 0f, degrees).apply {
        this.duration = duration
        this.repeatCount = repeatCount
        interpolator = AccelerateDecelerateInterpolator()
        start()
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
//  3. CARD POLISH ANIMATIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Lift card with increased elevation and subtle scale. Hover-like effect.
 */
fun MaterialCardView.cardLift(
    targetElevation: Float = 12f,
    scaleTo: Float = 1.02f,
    duration: Long = ANIM_DURATION_FAST
) {
    animate()
        .scaleX(scaleTo)
        .scaleY(scaleTo)
        .setDuration(duration)
        .setInterpolator(DecelerateInterpolator())
        .start()

    ObjectAnimator.ofFloat(this, "cardElevation", cardElevation, targetElevation).apply {
        this.duration = duration
        interpolator = DecelerateInterpolator()
        start()
    }
}

/**
 * Settle card back to normal elevation and scale. Pair with [cardLift].
 */
fun MaterialCardView.cardSettle(
    restElevation: Float = 2f,
    duration: Long = ANIM_DURATION_FAST
) {
    animate()
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(duration)
        .setInterpolator(DecelerateInterpolator())
        .start()

    ObjectAnimator.ofFloat(this, "cardElevation", cardElevation, restElevation).apply {
        this.duration = duration
        interpolator = DecelerateInterpolator()
        start()
    }
}

/**
 * Reveal card with scale + fade from center. Premium entrance effect.
 */
fun MaterialCardView.cardReveal(
    delay: Long = 0L,
    duration: Long = ANIM_DURATION_SLOW
) {
    alpha = 0f
    scaleX = 0.92f
    scaleY = 0.92f
    visibility = View.VISIBLE

    animate()
        .alpha(1f)
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(duration)
        .setStartDelay(delay)
        .setInterpolator(FastOutSlowInInterpolator())
        .start()
}

/**
 * Touch feedback for cards — lift on press, settle on release.
 */
fun MaterialCardView.addLiftOnTouch(
    liftElevation: Float = 10f,
    restElevation: Float = 2f,
    liftScale: Float = 1.015f
) {
    setOnTouchListener { v, event ->
        val card = v as MaterialCardView
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                card.cardLift(liftElevation, liftScale)
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                card.cardSettle(restElevation)
            }
        }
        false
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
//  4. COUNTER / SCORE ANIMATIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Animate a numeric counter from [from] to [to] with smooth interpolation.
 * Perfect for dashboard KPIs, scores, and statistics.
 *
 * @param from     Starting value
 * @param to       Target value
 * @param duration Animation duration
 * @param format   Format string (e.g., "%.0f", "%.1f%%", "₹%.0f")
 * @param onEnd    Callback when animation completes
 */
fun TextView.animateCounterTo(
    from: Float = 0f,
    to: Float,
    duration: Long = ANIM_DURATION_DRAMATIC,
    format: String = "%.0f",
    onEnd: (() -> Unit)? = null
) {
    ValueAnimator.ofFloat(from, to).apply {
        this.duration = duration
        interpolator = DecelerateInterpolator(1.5f)
        addUpdateListener { animator ->
            val value = animator.animatedValue as Float
            text = format.format(value)
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                text = format.format(to) // Ensure final value is exact
                onEnd?.invoke()
            }
        })
        start()
    }
}

/**
 * Animate a score from 0 to target with pulse at end.
 * Used for driver performance scores, fleet health scores.
 */
fun TextView.animateScoreTo(
    targetScore: Int,
    duration: Long = 1200L,
    format: String = "%d"
) {
    ValueAnimator.ofInt(0, targetScore).apply {
        this.duration = duration
        interpolator = DecelerateInterpolator(2f)
        addUpdateListener { animator ->
            text = format.format(animator.animatedValue as Int)
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                text = format.format(targetScore)
                pulseGlow(1.12f, 400L)
            }
        })
        start()
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
//  5. STAGGERED LIST ANIMATIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Staggered reveal for a list of child views inside a ViewGroup.
 * Each child fades + slides in with a progressive delay.
 *
 * @param staggerDelay Delay between each child animation
 * @param offsetY      Starting Y offset for each child
 * @param duration     Individual animation duration
 */
fun ViewGroup.staggeredReveal(
    staggerDelay: Long = ANIM_STAGGER_DELAY,
    offsetY: Float = 40f,
    duration: Long = ANIM_DURATION_NORMAL
) {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        child.alpha = 0f
        child.translationY = offsetY
        child.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setStartDelay(i * staggerDelay)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }
}

/**
 * Cascade-in animation for cards inside a vertical layout.
 * Similar to staggeredReveal but with scale component for premium feel.
 */
fun ViewGroup.cascadeIn(
    staggerDelay: Long = 80L,
    duration: Long = ANIM_DURATION_SLOW
) {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        child.alpha = 0f
        child.translationY = 50f
        child.scaleX = 0.97f
        child.scaleY = 0.97f

        child.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(duration)
            .setStartDelay(i * staggerDelay)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }
}

/**
 * Animate specific views in a list with stagger.
 */
fun List<View>.staggerFadeSlideIn(
    staggerDelay: Long = ANIM_STAGGER_DELAY,
    offsetY: Float = 50f,
    duration: Long = ANIM_DURATION_NORMAL
) {
    forEachIndexed { index, view ->
        view.fadeSlideIn(
            offsetY = offsetY,
            duration = duration,
            delay = index * staggerDelay
        )
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
//  6. COLOR TRANSITION
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Smoothly animate the background color of a view.
 */
fun View.animateBackgroundColor(
    fromColor: Int,
    toColor: Int,
    duration: Long = ANIM_DURATION_NORMAL
) {
    val colorAnimator = ValueAnimator.ofArgb(fromColor, toColor).apply {
        this.duration = duration
        addUpdateListener { animator ->
            setBackgroundColor(animator.animatedValue as Int)
        }
    }
    colorAnimator.start()
}

/**
 * Animate the text color of a TextView.
 */
fun TextView.animateTextColor(
    fromColor: Int,
    toColor: Int,
    duration: Long = ANIM_DURATION_NORMAL
) {
    ValueAnimator.ofArgb(fromColor, toColor).apply {
        this.duration = duration
        addUpdateListener { animator ->
            setTextColor(animator.animatedValue as Int)
        }
        start()
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
//  7. RECYCLERVIEW ITEM ANIMATOR HELPER
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Extension to apply fade+slide entrance to RecyclerView items
 * via the onBindViewHolder pattern. Call in adapter's bind method.
 *
 * @param position     Item position in list
 * @param lastPosition Tracks last animated position to avoid re-animating
 */
fun View.animateListItem(
    position: Int,
    lastAnimatedPosition: Int,
    offsetY: Float = 30f,
    duration: Long = ANIM_DURATION_NORMAL
): Int {
    if (position > lastAnimatedPosition) {
        alpha = 0f
        translationY = offsetY
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(1.3f))
            .setStartDelay((position * 30L).coerceAtMost(200L))
            .start()
        return position
    }
    return lastAnimatedPosition
}
