package com.example.movexa.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.movexa.R
import com.example.movexa.theme.AppColors
import com.example.movexa.theme.AppDimens
import com.google.android.material.card.MaterialCardView

/**
 * Reusable rounded card component following the Movexa design system.
 * Provides a consistent, elevation-aware card with customizable corner radius.
 *
 * XML Usage:
 * ```xml
 * <com.example.movexa.ui.components.RoundedCardView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:rcv_cornerRadius="16dp"
 *     app:rcv_elevation="4dp">
 *
 *     <!-- Card content here -->
 *
 * </com.example.movexa.ui.components.RoundedCardView>
 * ```
 */
class RoundedCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    init {
        applyDefaultStyle()
        attrs?.let { applyAttributes(it) }
    }

    private fun applyDefaultStyle() {
        // Default card styling from design system
        radius = resources.getDimension(R.dimen.radius_large)
        cardElevation = resources.getDimension(R.dimen.elevation_small)
        setCardBackgroundColor(AppColors.SURFACE)
        strokeWidth = 0
        useCompatPadding = true

        // Default content padding
        setContentPadding(
            resources.getDimensionPixelSize(R.dimen.card_padding),
            resources.getDimensionPixelSize(R.dimen.card_padding),
            resources.getDimensionPixelSize(R.dimen.card_padding),
            resources.getDimensionPixelSize(R.dimen.card_padding)
        )

        // Minimum height
        minimumHeight = resources.getDimensionPixelSize(R.dimen.card_min_height)
    }

    private fun applyAttributes(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.RoundedCardView)

        try {
            // Corner radius
            val cornerRadius = typedArray.getDimension(
                R.styleable.RoundedCardView_rcv_cornerRadius,
                resources.getDimension(R.dimen.radius_large)
            )
            radius = cornerRadius

            // Elevation
            val elevation = typedArray.getDimension(
                R.styleable.RoundedCardView_rcv_elevation,
                resources.getDimension(R.dimen.elevation_small)
            )
            cardElevation = elevation

            // Stroke
            val strokeWidth = typedArray.getDimension(
                R.styleable.RoundedCardView_rcv_strokeWidth, 0f
            ).toInt()

            if (strokeWidth > 0) {
                val strokeColor = typedArray.getColor(
                    R.styleable.RoundedCardView_rcv_strokeColor,
                    AppColors.DIVIDER
                )
                setStrokeWidth(strokeWidth)
                setStrokeColor(strokeColor)
            }
        } finally {
            typedArray.recycle()
        }
    }

    // ─── Public API ─────────────────────────────────────────────

    /**
     * Apply flat style (no elevation, subtle border).
     */
    fun applyFlatStyle() {
        cardElevation = 0f
        setStrokeWidth(1)
        setStrokeColor(AppColors.DIVIDER)
    }

    /**
     * Apply elevated style (shadow, no border).
     */
    fun applyElevatedStyle() {
        cardElevation = resources.getDimension(R.dimen.elevation_medium)
        strokeWidth = 0
    }

    /**
     * Apply outlined style (no elevation, colored border).
     */
    fun applyOutlinedStyle(borderColor: Int = AppColors.PRIMARY) {
        cardElevation = 0f
        setStrokeWidth(resources.getDimensionPixelSize(R.dimen.spacing_xxs))
        setStrokeColor(borderColor)
    }

    /**
     * Set custom corner radius in dp.
     */
    fun setCornerRadiusDp(dp: Float) {
        radius = dp * resources.displayMetrics.density
    }
}
