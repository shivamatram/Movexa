package com.example.movexa.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.example.movexa.R
import com.example.movexa.theme.AppColors
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * Reusable primary button component following the Movexa design system.
 * Supports filled, outlined, and text button styles with integrated loading state.
 *
 * XML Usage:
 * ```xml
 * <com.example.movexa.ui.components.PrimaryButton
 *     android:id="@+id/btnAction"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:pb_text="Submit"
 *     app:pb_style="filled" />
 * ```
 */
class PrimaryButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val button: MaterialButton
    private val progressIndicator: CircularProgressIndicator

    private var buttonText: String = ""
    private var isLoadingState: Boolean = false
    private var buttonStyle: ButtonStyle = ButtonStyle.FILLED

    enum class ButtonStyle {
        FILLED, OUTLINED, TEXT
    }

    init {
        // Inflate internal layout
        val innerLayout = LayoutInflater.from(context).inflate(
            R.layout.component_primary_button, this, true
        )
        button = innerLayout.findViewById(R.id.mbButton)
        progressIndicator = innerLayout.findViewById(R.id.progressIndicator)

        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.PrimaryButton)
        try {
            // Text
            buttonText = typedArray.getString(R.styleable.PrimaryButton_pb_text) ?: ""
            button.text = buttonText

            // Loading
            isLoadingState = typedArray.getBoolean(R.styleable.PrimaryButton_pb_loading, false)
            updateLoadingState()

            // Icon
            val iconRes = typedArray.getResourceId(R.styleable.PrimaryButton_pb_icon, 0)
            if (iconRes != 0) {
                button.setIconResource(iconRes)
            }

            // Style
            val styleValue = typedArray.getInt(R.styleable.PrimaryButton_pb_style, 0)
            buttonStyle = ButtonStyle.entries.getOrElse(styleValue) { ButtonStyle.FILLED }
            applyButtonStyle()
        } finally {
            typedArray.recycle()
        }
    }

    private fun applyButtonStyle() {
        when (buttonStyle) {
            ButtonStyle.FILLED -> {
                button.setBackgroundColor(AppColors.PRIMARY)
                button.setTextColor(AppColors.ON_PRIMARY)
            }
            ButtonStyle.OUTLINED -> {
                button.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                button.setTextColor(AppColors.PRIMARY)
                button.strokeColor = android.content.res.ColorStateList.valueOf(AppColors.PRIMARY)
                button.strokeWidth = resources.getDimensionPixelSize(R.dimen.spacing_xxs)
            }
            ButtonStyle.TEXT -> {
                button.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                button.setTextColor(AppColors.PRIMARY)
            }
        }
    }

    private fun updateLoadingState() {
        if (isLoadingState) {
            button.text = ""
            button.isEnabled = false
            progressIndicator.visibility = VISIBLE
        } else {
            button.text = buttonText
            button.isEnabled = true
            progressIndicator.visibility = GONE
        }
    }

    // ─── Public API ─────────────────────────────────────────────

    /**
     * Set the button text.
     */
    fun setText(text: String) {
        buttonText = text
        if (!isLoadingState) {
            button.text = text
        }
    }

    /**
     * Show or hide the loading state.
     */
    fun setLoading(loading: Boolean) {
        isLoadingState = loading
        updateLoadingState()
    }

    /**
     * Set the click listener.
     */
    override fun setOnClickListener(listener: OnClickListener?) {
        button.setOnClickListener(listener)
    }

    /**
     * Enable or disable the button.
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.5f
    }

    /**
     * Get the internal MaterialButton for additional customization.
     */
    fun getButton(): MaterialButton = button
}
