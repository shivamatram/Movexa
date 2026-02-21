package com.example.movexa.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.movexa.R

/**
 * Reusable dashboard stat card component.
 *
 * Displays an icon, label, large value, and optional subtitle.
 * Used in both admin and manager dashboard grids.
 *
 * Usage:
 * ```xml
 * <com.example.movexa.ui.components.StatCardView
 *     android:id="@+id/statVehicles"
 *     android:layout_width="0dp"
 *     android:layout_height="wrap_content" />
 * ```
 *
 * Then in code:
 * ```kotlin
 * statVehicles.setData(
 *     iconRes = R.drawable.ic_dashboard_vehicle,
 *     label = "Total Vehicles",
 *     value = "42",
 *     subtitle = "5 in maintenance",
 *     iconTint = ContextCompat.getColor(context, R.color.primary)
 * )
 * ```
 */
class StatCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val ivStatIcon: ImageView
    private val tvStatLabel: TextView
    private val tvStatValue: TextView
    private val tvStatSubtitle: TextView
    private val iconContainer: View

    init {
        LayoutInflater.from(context).inflate(R.layout.view_stat_card, this, true)
        ivStatIcon = findViewById(R.id.ivStatIcon)
        tvStatLabel = findViewById(R.id.tvStatLabel)
        tvStatValue = findViewById(R.id.tvStatValue)
        tvStatSubtitle = findViewById(R.id.tvStatSubtitle)
        iconContainer = findViewById(R.id.iconContainer)
    }

    /**
     * Set all stat card properties at once.
     */
    fun setData(
        @DrawableRes iconRes: Int,
        label: String,
        value: String,
        subtitle: String? = null,
        @ColorInt iconTint: Int? = null
    ) {
        ivStatIcon.setImageResource(iconRes)
        tvStatLabel.text = label
        tvStatValue.text = value

        if (subtitle != null) {
            tvStatSubtitle.text = subtitle
            tvStatSubtitle.isVisible = true
        } else {
            tvStatSubtitle.isVisible = false
        }

        if (iconTint != null) {
            ivStatIcon.setColorFilter(iconTint)
        }
    }

    /**
     * Update only the value text (for real-time updates).
     */
    fun setValue(value: String) {
        tvStatValue.text = value
    }

    /**
     * Update the subtitle text.
     */
    fun setSubtitle(subtitle: String?) {
        if (subtitle != null) {
            tvStatSubtitle.text = subtitle
            tvStatSubtitle.isVisible = true
        } else {
            tvStatSubtitle.isVisible = false
        }
    }

    /**
     * Set the icon tint color.
     */
    fun setIconTint(@ColorInt color: Int) {
        ivStatIcon.setColorFilter(color)
    }

    /**
     * Set icon background color (the circular container).
     */
    fun setIconBackgroundColor(@ColorInt color: Int) {
        iconContainer.background?.setTint(color)
    }

    /**
     * Show shimmer/loading placeholder state.
     */
    fun showLoading() {
        tvStatValue.text = context.getString(R.string.stat_no_data)
        tvStatSubtitle.isVisible = false
    }
}
