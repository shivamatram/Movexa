package com.example.movexa.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.example.movexa.R

/**
 * Reusable pending task item view for manager dashboard.
 *
 * Displays a task icon, title, subtitle, and count badge.
 * Typically used for pending approvals, leave requests, etc.
 */
class PendingTaskItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val ivTaskIcon: ImageView
    private val tvTaskTitle: TextView
    private val tvTaskSubtitle: TextView
    private val tvTaskCount: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.item_pending_task, this, true)
        ivTaskIcon = findViewById(R.id.ivTaskIcon)
        tvTaskTitle = findViewById(R.id.tvTaskTitle)
        tvTaskSubtitle = findViewById(R.id.tvTaskSubtitle)
        tvTaskCount = findViewById(R.id.tvTaskCount)
    }

    /**
     * Set pending task data.
     *
     * @param iconRes drawable resource for task icon
     * @param title task category name
     * @param count number of pending items
     */
    fun setData(
        @DrawableRes iconRes: Int,
        title: String,
        count: Int
    ) {
        ivTaskIcon.setImageResource(iconRes)
        tvTaskTitle.text = title
        tvTaskSubtitle.text = context.getString(R.string.pending_count_format, count)
        tvTaskCount.text = count.toString()

        // Hide if zero
        visibility = if (count > 0) VISIBLE else GONE
    }

    /**
     * Update only the count value.
     */
    fun updateCount(count: Int) {
        tvTaskSubtitle.text = context.getString(R.string.pending_count_format, count)
        tvTaskCount.text = count.toString()
        visibility = if (count > 0) VISIBLE else GONE
    }
}
