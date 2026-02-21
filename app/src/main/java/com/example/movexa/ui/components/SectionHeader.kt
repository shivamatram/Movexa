package com.example.movexa.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.movexa.R
import com.example.movexa.theme.AppColors
import com.google.android.material.button.MaterialButton

/**
 * Reusable section header component following the Movexa design system.
 * Displays a title with an optional action text/button on the right.
 *
 * XML Usage:
 * ```xml
 * <com.example.movexa.ui.components.SectionHeader
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sh_title="Active Vehicles"
 *     app:sh_actionText="View All"
 *     app:sh_showAction="true" />
 * ```
 */
class SectionHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val tvTitle: TextView
    private val btnAction: MaterialButton
    private val divider: View

    private var onActionClickListener: (() -> Unit)? = null

    init {
        val view = LayoutInflater.from(context).inflate(
            R.layout.component_section_header, this, true
        )
        tvTitle = view.findViewById(R.id.tvSectionTitle)
        btnAction = view.findViewById(R.id.btnSectionAction)
        divider = view.findViewById(R.id.sectionDivider)

        orientation = VERTICAL

        btnAction.setOnClickListener {
            onActionClickListener?.invoke()
        }

        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SectionHeader)
        try {
            // Title
            val title = typedArray.getString(R.styleable.SectionHeader_sh_title) ?: ""
            tvTitle.text = title

            // Action text
            val actionText = typedArray.getString(R.styleable.SectionHeader_sh_actionText) ?: ""
            btnAction.text = actionText

            // Show action
            val showAction = typedArray.getBoolean(R.styleable.SectionHeader_sh_showAction, false)
            btnAction.visibility = if (showAction && actionText.isNotBlank()) VISIBLE else GONE

            // Show divider
            val showDivider = typedArray.getBoolean(R.styleable.SectionHeader_sh_showDivider, false)
            divider.visibility = if (showDivider) VISIBLE else GONE
        } finally {
            typedArray.recycle()
        }
    }

    // ─── Public API ─────────────────────────────────────────────

    /**
     * Set the section title.
     */
    fun setTitle(title: String) {
        tvTitle.text = title
    }

    /**
     * Set the action button text and show it.
     */
    fun setAction(text: String, onClick: () -> Unit) {
        btnAction.text = text
        btnAction.visibility = VISIBLE
        onActionClickListener = onClick
    }

    /**
     * Hide the action button.
     */
    fun hideAction() {
        btnAction.visibility = GONE
    }

    /**
     * Show or hide the bottom divider.
     */
    fun setDividerVisible(visible: Boolean) {
        divider.visibility = if (visible) VISIBLE else GONE
    }

    /**
     * Set action click listener.
     */
    fun setOnActionClickListener(listener: () -> Unit) {
        onActionClickListener = listener
    }
}
