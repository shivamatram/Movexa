package com.example.movexa.theme

import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import com.example.movexa.R

/**
 * Centralized shape definitions for the Movexa design system.
 * Provides consistent corner radius and shape styling.
 */
object AppShapes {

    // ─── Corner Radius Resource References ──────────────────────
    val RADIUS_XS = R.dimen.radius_xs           // 4dp
    val RADIUS_SMALL = R.dimen.radius_small      // 8dp
    val RADIUS_MEDIUM = R.dimen.radius_medium    // 12dp
    val RADIUS_LARGE = R.dimen.radius_large      // 16dp
    val RADIUS_XL = R.dimen.radius_xl            // 24dp
    val RADIUS_CIRCLE = R.dimen.radius_circle    // 50dp

    /**
     * Shape type enumeration for component shapes.
     */
    enum class ShapeType {
        RECTANGLE,
        ROUNDED_SMALL,
        ROUNDED_MEDIUM,
        ROUNDED_LARGE,
        PILL,
        CIRCLE
    }

    /**
     * Get the corner radius in dp for a given shape type.
     */
    fun getCornerRadiusDp(shapeType: ShapeType): Float {
        return when (shapeType) {
            ShapeType.RECTANGLE -> 0f
            ShapeType.ROUNDED_SMALL -> 8f
            ShapeType.ROUNDED_MEDIUM -> 12f
            ShapeType.ROUNDED_LARGE -> 16f
            ShapeType.PILL -> 50f
            ShapeType.CIRCLE -> 50f
        }
    }

    /**
     * Create a rounded rectangle drawable programmatically.
     */
    fun createRoundedDrawable(
        @ColorInt fillColor: Int,
        cornerRadiusPx: Float,
        @ColorInt strokeColor: Int = 0,
        strokeWidthPx: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            setCornerRadius(cornerRadiusPx)
            if (strokeWidthPx > 0 && strokeColor != 0) {
                setStroke(strokeWidthPx, strokeColor)
            }
        }
    }

    /**
     * Create a pill-shaped drawable (fully rounded corners).
     */
    fun createPillDrawable(
        @ColorInt fillColor: Int,
        @ColorInt strokeColor: Int = 0,
        strokeWidthPx: Int = 0
    ): GradientDrawable {
        return createRoundedDrawable(
            fillColor = fillColor,
            cornerRadiusPx = 999f,
            strokeColor = strokeColor,
            strokeWidthPx = strokeWidthPx
        )
    }

    /**
     * Create a circle drawable.
     */
    fun createCircleDrawable(
        @ColorInt fillColor: Int,
        @ColorInt strokeColor: Int = 0,
        strokeWidthPx: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            if (strokeWidthPx > 0 && strokeColor != 0) {
                setStroke(strokeWidthPx, strokeColor)
            }
        }
    }

    /**
     * Create a drawable with different corner radii for each corner.
     */
    fun createCustomCornersDrawable(
        @ColorInt fillColor: Int,
        topLeftPx: Float = 0f,
        topRightPx: Float = 0f,
        bottomRightPx: Float = 0f,
        bottomLeftPx: Float = 0f
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadii = floatArrayOf(
                topLeftPx, topLeftPx,
                topRightPx, topRightPx,
                bottomRightPx, bottomRightPx,
                bottomLeftPx, bottomLeftPx
            )
        }
    }
}
