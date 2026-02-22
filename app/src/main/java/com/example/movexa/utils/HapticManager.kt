package com.example.movexa.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 *  HAPTIC FEEDBACK MANAGER
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Centralized haptic feedback utility for the Movexa app.
 * Provides consistent tactile feedback for important user interactions.
 *
 * ─── Feedback Tiers ───────────────────────────────────────────────────────────
 *
 *  ● LIGHT    — subtle tick for toggles, swipes, selections
 *  ● MEDIUM   — standard click for button presses, confirmations
 *  ● HEAVY    — strong thud for important actions, alerts
 *  ● SUCCESS  — double-tap pattern for successful operations
 *  ● ERROR    — triple-buzz for error / failure feedback
 *  ● WARNING  — single long buzz for warnings
 *
 * ─── Usage ────────────────────────────────────────────────────────────────────
 *
 *     HapticManager.light(view)           // In any Fragment/Activity
 *     HapticManager.success(context)      // Without a View reference
 *     view.hapticLight()                  // Extension function shorthand
 *
 * @since 2026-02-22 — Final Polish Phase
 */
object HapticManager {

    // ─── Duration constants (ms) ────────────────────────────────
    private const val DURATION_LIGHT = 10L
    private const val DURATION_MEDIUM = 20L
    private const val DURATION_HEAVY = 40L
    private const val DURATION_WARNING = 80L

    // ─── Amplitude constants (1-255) ────────────────────────────
    private const val AMPLITUDE_LIGHT = 40
    private const val AMPLITUDE_MEDIUM = 120
    private const val AMPLITUDE_HEAVY = 200
    private const val AMPLITUDE_MAX = 255

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API — View-based (recommended)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Light tick — toggles, swipe gestures, small selections.
     */
    fun light(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * Medium click — button presses, card taps, tab switches.
     */
    fun medium(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /**
     * Heavy confirm — important confirms, delete, submit.
     */
    fun heavy(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /**
     * Reject / error feedback.
     */
    fun reject(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API — Context-based (when no View available)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Light vibration via system Vibrator.
     */
    fun lightVibrate(context: Context) {
        vibrate(context, DURATION_LIGHT, AMPLITUDE_LIGHT)
    }

    /**
     * Medium vibration via system Vibrator.
     */
    fun mediumVibrate(context: Context) {
        vibrate(context, DURATION_MEDIUM, AMPLITUDE_MEDIUM)
    }

    /**
     * Heavy vibration via system Vibrator.
     */
    fun heavyVibrate(context: Context) {
        vibrate(context, DURATION_HEAVY, AMPLITUDE_HEAVY)
    }

    /**
     * Success pattern: two short taps with a gap.
     * Pattern: tap · gap · tap
     */
    fun successPattern(context: Context) {
        vibratePattern(
            context,
            timings = longArrayOf(0, 15, 80, 15),
            amplitudes = intArrayOf(0, AMPLITUDE_MEDIUM, 0, AMPLITUDE_HEAVY)
        )
    }

    /**
     * Error pattern: three rapid buzzes.
     * Pattern: buzz · gap · buzz · gap · buzz
     */
    fun errorPattern(context: Context) {
        vibratePattern(
            context,
            timings = longArrayOf(0, 25, 50, 25, 50, 25),
            amplitudes = intArrayOf(0, AMPLITUDE_HEAVY, 0, AMPLITUDE_HEAVY, 0, AMPLITUDE_MAX)
        )
    }

    /**
     * Warning pattern: single long buzz.
     */
    fun warningPattern(context: Context) {
        vibrate(context, DURATION_WARNING, AMPLITUDE_HEAVY)
    }

    // ═══════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the system Vibrator service, handling API 31+ changes.
     */
    private fun getVibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Single vibration pulse.
     */
    private fun vibrate(context: Context, durationMs: Long, amplitude: Int) {
        val vibrator = getVibrator(context)
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createOneShot(durationMs, amplitude)
            } else {
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    /**
     * Patterned vibration with per-segment amplitudes.
     */
    private fun vibratePattern(context: Context, timings: LongArray, amplitudes: IntArray) {
        val vibrator = getVibrator(context)
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
//  VIEW EXTENSION SHORTCUTS
// ═══════════════════════════════════════════════════════════════════════════════

/** Light haptic tick on this View. */
fun View.hapticLight() = HapticManager.light(this)

/** Medium haptic click on this View. */
fun View.hapticMedium() = HapticManager.medium(this)

/** Heavy haptic thud on this View. */
fun View.hapticHeavy() = HapticManager.heavy(this)

/** Error haptic on this View. */
fun View.hapticReject() = HapticManager.reject(this)
