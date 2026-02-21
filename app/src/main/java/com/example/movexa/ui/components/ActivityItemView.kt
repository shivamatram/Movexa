package com.example.movexa.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.example.movexa.R
import com.example.movexa.data.model.ActivityLog
import com.example.movexa.data.model.enums.ActivityLogType
import com.example.movexa.utils.TimeUtils

/**
 * Reusable activity log item view for dashboard activity feeds.
 *
 * Renders an activity log entry with a categorized icon,
 * message text, and relative timestamp.
 */
class ActivityItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val ivActivityIcon: ImageView
    private val tvActivityMessage: TextView
    private val tvActivityTime: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.item_activity, this, true)
        ivActivityIcon = findViewById(R.id.ivActivityIcon)
        tvActivityMessage = findViewById(R.id.tvActivityMessage)
        tvActivityTime = findViewById(R.id.tvActivityTime)
    }

    /**
     * Bind an ActivityLog model to this view.
     */
    fun bind(log: ActivityLog) {
        tvActivityMessage.text = log.message
        tvActivityTime.text = TimeUtils.getRelativeTimeString(log.timestamp)

        val iconRes = getIconForType(log.type)
        ivActivityIcon.setImageResource(iconRes)

        val iconColor = getColorForType(log.type)
        ivActivityIcon.setColorFilter(iconColor)
    }

    @DrawableRes
    private fun getIconForType(type: ActivityLogType): Int {
        return when (type) {
            // Auth events
            ActivityLogType.USER_LOGIN,
            ActivityLogType.USER_LOGOUT,
            ActivityLogType.USER_REGISTERED,
            ActivityLogType.PASSWORD_RESET -> R.drawable.ic_dashboard_driver

            // Vehicle events
            ActivityLogType.VEHICLE_ADDED,
            ActivityLogType.VEHICLE_UPDATED,
            ActivityLogType.VEHICLE_REMOVED,
            ActivityLogType.VEHICLE_ASSIGNED -> R.drawable.ic_dashboard_vehicle

            // Trip events
            ActivityLogType.TRIP_CREATED,
            ActivityLogType.TRIP_ASSIGNED,
            ActivityLogType.TRIP_STARTED,
            ActivityLogType.TRIP_COMPLETED,
            ActivityLogType.TRIP_CANCELLED -> R.drawable.ic_nav_trips

            // Maintenance events
            ActivityLogType.SERVICE_SCHEDULED,
            ActivityLogType.SERVICE_COMPLETED,
            ActivityLogType.REPAIR_LOGGED,
            ActivityLogType.PART_REPLACED -> R.drawable.ic_build

            // Fuel events
            ActivityLogType.FUEL_LOGGED -> R.drawable.ic_nav_fuel

            // Alert events
            ActivityLogType.ALERT_CREATED,
            ActivityLogType.ALERT_RESOLVED -> R.drawable.ic_warning

            // Admin events
            ActivityLogType.USER_VERIFIED,
            ActivityLogType.USER_BLOCKED,
            ActivityLogType.USER_UNBLOCKED,
            ActivityLogType.SETTINGS_CHANGED -> R.drawable.ic_nav_profile

            // System events
            ActivityLogType.SYSTEM -> R.drawable.ic_schedule
        }
    }

    private fun getColorForType(type: ActivityLogType): Int {
        return when (type) {
            ActivityLogType.TRIP_COMPLETED,
            ActivityLogType.SERVICE_COMPLETED,
            ActivityLogType.ALERT_RESOLVED,
            ActivityLogType.USER_VERIFIED ->
                ContextCompat.getColor(context, R.color.success)

            ActivityLogType.ALERT_CREATED,
            ActivityLogType.VEHICLE_REMOVED,
            ActivityLogType.TRIP_CANCELLED,
            ActivityLogType.USER_BLOCKED ->
                ContextCompat.getColor(context, R.color.error)

            ActivityLogType.TRIP_STARTED,
            ActivityLogType.TRIP_ASSIGNED,
            ActivityLogType.VEHICLE_ASSIGNED ->
                ContextCompat.getColor(context, R.color.info)

            else -> ContextCompat.getColor(context, R.color.text_secondary)
        }
    }
}
