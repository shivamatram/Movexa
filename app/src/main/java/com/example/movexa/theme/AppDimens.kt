package com.example.movexa.theme

import android.content.Context
import androidx.annotation.DimenRes
import com.example.movexa.R

/**
 * Centralized dimension definitions for the Movexa design system.
 * All spacing, sizing, and dimension values should reference this object.
 */
object AppDimens {

    // ─── Spacing (dp resource references) ───────────────────────
    @DimenRes val SPACING_XXS = R.dimen.spacing_xxs         // 2dp
    @DimenRes val SPACING_XS = R.dimen.spacing_xs            // 4dp
    @DimenRes val SPACING_SMALL = R.dimen.spacing_small      // 8dp
    @DimenRes val SPACING_MEDIUM = R.dimen.spacing_medium    // 16dp
    @DimenRes val SPACING_LARGE = R.dimen.spacing_large      // 24dp
    @DimenRes val SPACING_XL = R.dimen.spacing_xl            // 32dp
    @DimenRes val SPACING_XXL = R.dimen.spacing_xxl          // 48dp
    @DimenRes val SPACING_XXXL = R.dimen.spacing_xxxl        // 64dp

    // ─── Corner Radius ──────────────────────────────────────────
    @DimenRes val RADIUS_XS = R.dimen.radius_xs              // 4dp
    @DimenRes val RADIUS_SMALL = R.dimen.radius_small        // 8dp
    @DimenRes val RADIUS_MEDIUM = R.dimen.radius_medium      // 12dp
    @DimenRes val RADIUS_LARGE = R.dimen.radius_large        // 16dp
    @DimenRes val RADIUS_XL = R.dimen.radius_xl              // 24dp
    @DimenRes val RADIUS_CIRCLE = R.dimen.radius_circle      // 50dp

    // ─── Elevation ──────────────────────────────────────────────
    @DimenRes val ELEVATION_NONE = R.dimen.elevation_none    // 0dp
    @DimenRes val ELEVATION_XS = R.dimen.elevation_xs        // 1dp
    @DimenRes val ELEVATION_SMALL = R.dimen.elevation_small  // 2dp
    @DimenRes val ELEVATION_MEDIUM = R.dimen.elevation_medium // 4dp
    @DimenRes val ELEVATION_LARGE = R.dimen.elevation_large  // 8dp
    @DimenRes val ELEVATION_XL = R.dimen.elevation_xl        // 16dp

    // ─── Component Sizes ────────────────────────────────────────
    @DimenRes val BUTTON_HEIGHT = R.dimen.button_height           // 52dp
    @DimenRes val BUTTON_HEIGHT_SMALL = R.dimen.button_height_small // 40dp
    @DimenRes val INPUT_HEIGHT = R.dimen.input_height             // 56dp
    @DimenRes val TOOLBAR_HEIGHT = R.dimen.toolbar_height         // 56dp
    @DimenRes val BOTTOM_NAV_HEIGHT = R.dimen.bottom_nav_height   // 64dp

    // ─── Icon Sizes ─────────────────────────────────────────────
    @DimenRes val ICON_SMALL = R.dimen.icon_small            // 16dp
    @DimenRes val ICON_MEDIUM = R.dimen.icon_medium          // 24dp
    @DimenRes val ICON_LARGE = R.dimen.icon_large            // 48dp
    @DimenRes val ICON_XL = R.dimen.icon_xl                  // 64dp

    // ─── Avatar Sizes ───────────────────────────────────────────
    @DimenRes val AVATAR_SMALL = R.dimen.avatar_small        // 32dp
    @DimenRes val AVATAR_MEDIUM = R.dimen.avatar_medium      // 48dp
    @DimenRes val AVATAR_LARGE = R.dimen.avatar_large        // 80dp

    // ─── Screen Margins ─────────────────────────────────────────
    @DimenRes val SCREEN_MARGIN_H = R.dimen.screen_margin_horizontal  // 20dp
    @DimenRes val SCREEN_MARGIN_V = R.dimen.screen_margin_vertical    // 16dp

    // ─── Card ───────────────────────────────────────────────────
    @DimenRes val CARD_PADDING = R.dimen.card_padding        // 16dp
    @DimenRes val CARD_MIN_HEIGHT = R.dimen.card_min_height  // 80dp

    /**
     * Resolve a dimension resource to pixels.
     */
    fun resolvePixels(context: Context, @DimenRes dimenRes: Int): Float {
        return context.resources.getDimension(dimenRes)
    }

    /**
     * Resolve a dimension resource to dp value.
     */
    fun resolveDp(context: Context, @DimenRes dimenRes: Int): Int {
        return (context.resources.getDimension(dimenRes) /
                context.resources.displayMetrics.density).toInt()
    }

    /**
     * Convert dp to pixels.
     */
    fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    /**
     * Convert pixels to dp.
     */
    fun pxToDp(context: Context, px: Float): Float {
        return px / context.resources.displayMetrics.density
    }

    /**
     * Convert sp to pixels.
     */
    fun spToPx(context: Context, sp: Float): Float {
        return sp * context.resources.displayMetrics.scaledDensity
    }
}
