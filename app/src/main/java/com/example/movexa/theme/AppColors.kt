package com.example.movexa.theme

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.example.movexa.R

/**
 * Centralized color definitions for the Movexa design system.
 * All color references should go through this object for consistency.
 * Uses the teal/blue professional palette defined in colors.xml.
 */
object AppColors {

    // ─── Primary Palette ────────────────────────────────────────
    @ColorInt val PRIMARY = Color.parseColor("#FF00897B")
    @ColorInt val PRIMARY_DARK = Color.parseColor("#FF00695C")
    @ColorInt val PRIMARY_LIGHT = Color.parseColor("#FF4DB6AC")
    @ColorInt val PRIMARY_VARIANT = Color.parseColor("#FF00796B")
    @ColorInt val PRIMARY_SURFACE = Color.parseColor("#FFE0F2F1")
    @ColorInt val PRIMARY_CONTAINER = Color.parseColor("#FFB2DFDB")
    @ColorInt val ON_PRIMARY = Color.WHITE
    @ColorInt val ON_PRIMARY_CONTAINER = Color.parseColor("#FF004D40")

    // ─── Secondary Palette ──────────────────────────────────────
    @ColorInt val SECONDARY = Color.parseColor("#FF1565C0")
    @ColorInt val SECONDARY_DARK = Color.parseColor("#FF0D47A1")
    @ColorInt val SECONDARY_LIGHT = Color.parseColor("#FF42A5F5")
    @ColorInt val SECONDARY_CONTAINER = Color.parseColor("#FFBBDEFB")
    @ColorInt val ON_SECONDARY = Color.WHITE
    @ColorInt val ON_SECONDARY_CONTAINER = Color.parseColor("#FF0D47A1")

    // ─── Tertiary ───────────────────────────────────────────────
    @ColorInt val TERTIARY = Color.parseColor("#FF6A1B9A")
    @ColorInt val TERTIARY_CONTAINER = Color.parseColor("#FFE1BEE7")
    @ColorInt val ON_TERTIARY = Color.WHITE

    // ─── Background & Surface ───────────────────────────────────
    @ColorInt val BACKGROUND = Color.WHITE
    @ColorInt val SURFACE = Color.WHITE
    @ColorInt val SURFACE_VARIANT = Color.parseColor("#FFF5F5F5")
    @ColorInt val SURFACE_ELEVATED = Color.parseColor("#FFFAFAFA")
    @ColorInt val ON_BACKGROUND = Color.parseColor("#FF1C1B1F")
    @ColorInt val ON_SURFACE = Color.parseColor("#FF1C1B1F")
    @ColorInt val ON_SURFACE_VARIANT = Color.parseColor("#FF49454F")

    // ─── Status Colors ──────────────────────────────────────────
    @ColorInt val ERROR = Color.parseColor("#FFD32F2F")
    @ColorInt val ERROR_CONTAINER = Color.parseColor("#FFFFCDD2")
    @ColorInt val ON_ERROR = Color.WHITE
    @ColorInt val SUCCESS = Color.parseColor("#FF2E7D32")
    @ColorInt val SUCCESS_CONTAINER = Color.parseColor("#FFC8E6C9")
    @ColorInt val WARNING = Color.parseColor("#FFEF6C00")
    @ColorInt val WARNING_CONTAINER = Color.parseColor("#FFFFE0B2")
    @ColorInt val INFO = Color.parseColor("#FF0277BD")
    @ColorInt val INFO_CONTAINER = Color.parseColor("#FFB3E5FC")

    // ─── Text Colors ────────────────────────────────────────────
    @ColorInt val TEXT_PRIMARY = Color.parseColor("#FF212121")
    @ColorInt val TEXT_SECONDARY = Color.parseColor("#FF757575")
    @ColorInt val TEXT_DISABLED = Color.parseColor("#FFBDBDBD")
    @ColorInt val TEXT_HINT = Color.parseColor("#FF9E9E9E")

    // ─── Utility ────────────────────────────────────────────────
    @ColorInt val DIVIDER = Color.parseColor("#FFE0E0E0")
    @ColorInt val OUTLINE = Color.parseColor("#FF79747E")
    @ColorInt val SCRIM = Color.parseColor("#33000000")
    @ColorInt val OVERLAY_DARK = Color.parseColor("#80000000")

    /**
     * Resolve a color resource from context.
     */
    fun resolve(context: Context, @ColorRes colorRes: Int): Int {
        return ContextCompat.getColor(context, colorRes)
    }

    /**
     * Get color with specified alpha (0f-1f).
     */
    fun withAlpha(@ColorInt color: Int, alpha: Float): Int {
        val alphaInt = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return Color.argb(alphaInt, Color.red(color), Color.green(color), Color.blue(color))
    }
}
