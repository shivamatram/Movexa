package com.example.movexa.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.movexa.R
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.enums.AlertPriority
import com.example.movexa.utils.TimeUtils

/**
 * Reusable alert item view for dashboard alert panels.
 *
 * Renders an alert with a colored priority indicator strip,
 * title, message, priority badge, and relative timestamp.
 */
class AlertItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val viewPriorityIndicator: View
    private val tvAlertTitle: TextView
    private val tvPriorityBadge: TextView
    private val tvAlertMessage: TextView
    private val tvAlertTime: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.item_alert, this, true)
        viewPriorityIndicator = findViewById(R.id.viewPriorityIndicator)
        tvAlertTitle = findViewById(R.id.tvAlertTitle)
        tvPriorityBadge = findViewById(R.id.tvPriorityBadge)
        tvAlertMessage = findViewById(R.id.tvAlertMessage)
        tvAlertTime = findViewById(R.id.tvAlertTime)
    }

    /**
     * Bind an Alert model to this view.
     */
    fun bind(alert: Alert) {
        tvAlertTitle.text = alert.title
        tvAlertMessage.text = alert.message
        tvAlertTime.text = TimeUtils.getRelativeTimeString(alert.timestamp)

        val priorityColor = getPriorityColor(alert.priority)
        viewPriorityIndicator.setBackgroundColor(priorityColor)
        tvPriorityBadge.text = alert.priority.name

        // Set badge background color
        val badgeBackground = tvPriorityBadge.background
        if (badgeBackground is GradientDrawable) {
            badgeBackground.setColor(priorityColor)
        } else {
            val drawable = GradientDrawable().apply {
                setColor(priorityColor)
                cornerRadius = context.resources.getDimension(R.dimen.radius_xs)
            }
            tvPriorityBadge.background = drawable
        }
    }

    private fun getPriorityColor(priority: AlertPriority): Int {
        return when (priority) {
            AlertPriority.CRITICAL -> ContextCompat.getColor(context, R.color.error)
            AlertPriority.HIGH -> ContextCompat.getColor(context, R.color.warning)
            AlertPriority.MEDIUM -> ContextCompat.getColor(context, R.color.info)
            AlertPriority.LOW -> ContextCompat.getColor(context, R.color.success)
        }
    }
}
