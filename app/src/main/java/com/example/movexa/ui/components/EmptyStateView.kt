package com.example.movexa.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import com.example.movexa.R
import com.example.movexa.databinding.ViewEmptyStateBinding
import com.example.movexa.utils.bounceIn
import com.example.movexa.utils.fadeSlideIn

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 *  EMPTY STATE VIEW
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * A reusable, self-contained empty state component showing:
 *   • Animated illustration icon
 *   • Title message
 *   • Description message
 *   • Optional call-to-action button
 *
 * ─── Usage in XML ─────────────────────────────────────────────────────────────
 *
 *   <com.example.movexa.ui.components.EmptyStateView
 *       android:id="@+id/emptyState"
 *       android:layout_width="match_parent"
 *       android:layout_height="wrap_content"
 *       android:visibility="gone" />
 *
 * ─── Usage in Kotlin ──────────────────────────────────────────────────────────
 *
 *   binding.emptyState.configure(
 *       icon = R.drawable.ic_local_shipping,
 *       title = "No trips yet",
 *       message = "Your active trips will appear here.",
 *       actionText = "Create Trip",
 *       onAction = { navigateToCreateTrip() }
 *   )
 *   binding.emptyState.show()
 *
 * ─── Preset Factories ────────────────────────────────────────────────────────
 *
 *   EmptyStateView.noTrips(context)
 *   EmptyStateView.noVehicles(context)
 *   EmptyStateView.noAlerts(context)
 *   EmptyStateView.noReports(context)
 *
 * @since 2026-02-22 — Final Polish Phase
 */
class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewEmptyStateBinding

    private var onActionClick: (() -> Unit)? = null

    init {
        binding = ViewEmptyStateBinding.inflate(
            LayoutInflater.from(context), this, true
        )

        binding.btnEmptyAction.setOnClickListener {
            onActionClick?.invoke()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Configure the empty state with all parameters.
     *
     * @param icon        Drawable resource for the illustration
     * @param title       Primary message
     * @param message     Secondary description
     * @param actionText  Button text (null to hide button)
     * @param onAction    Button click callback
     */
    fun configure(
        @DrawableRes icon: Int,
        title: String,
        message: String,
        actionText: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        binding.ivEmptyIcon.setImageResource(icon)
        binding.tvEmptyTitle.text = title
        binding.tvEmptyMessage.text = message

        if (actionText != null) {
            binding.btnEmptyAction.text = actionText
            binding.btnEmptyAction.visibility = View.VISIBLE
        } else {
            binding.btnEmptyAction.visibility = View.GONE
        }

        onActionClick = onAction
    }

    /**
     * Show with entrance animation.
     */
    fun show(animate: Boolean = true) {
        visibility = View.VISIBLE
        if (animate) {
            binding.ivEmptyIcon.bounceIn(delay = 100L)
            binding.tvEmptyTitle.fadeSlideIn(delay = 200L)
            binding.tvEmptyMessage.fadeSlideIn(delay = 300L)
            if (binding.btnEmptyAction.visibility == View.VISIBLE) {
                binding.btnEmptyAction.fadeSlideIn(delay = 400L)
            }
        }
    }

    /**
     * Hide the empty state.
     */
    fun hide() {
        visibility = View.GONE
    }

    // ═══════════════════════════════════════════════════════════════
    //  PRESET CONFIGURATIONS
    // ═══════════════════════════════════════════════════════════════

    companion object {

        /** No trips placeholder config. */
        fun configureNoTrips(
            view: EmptyStateView,
            onAction: (() -> Unit)? = null
        ) {
            view.configure(
                icon = R.drawable.ic_local_shipping,
                title = view.context.getString(R.string.empty_no_trips_title),
                message = view.context.getString(R.string.empty_no_trips_message),
                actionText = if (onAction != null) {
                    view.context.getString(R.string.empty_no_trips_action)
                } else null,
                onAction = onAction
            )
        }

        /** No vehicles placeholder config. */
        fun configureNoVehicles(
            view: EmptyStateView,
            onAction: (() -> Unit)? = null
        ) {
            view.configure(
                icon = R.drawable.ic_dashboard_vehicle,
                title = view.context.getString(R.string.empty_no_vehicles_title),
                message = view.context.getString(R.string.empty_no_vehicles_message),
                actionText = if (onAction != null) {
                    view.context.getString(R.string.empty_no_vehicles_action)
                } else null,
                onAction = onAction
            )
        }

        /** No alerts placeholder config. */
        fun configureNoAlerts(view: EmptyStateView) {
            view.configure(
                icon = R.drawable.ic_check_circle,
                title = view.context.getString(R.string.empty_no_alerts_title),
                message = view.context.getString(R.string.empty_no_alerts_message)
            )
        }

        /** No reports placeholder config. */
        fun configureNoReports(
            view: EmptyStateView,
            onAction: (() -> Unit)? = null
        ) {
            view.configure(
                icon = R.drawable.ic_analytics_report,
                title = view.context.getString(R.string.empty_no_reports_title),
                message = view.context.getString(R.string.empty_no_reports_message),
                actionText = if (onAction != null) {
                    view.context.getString(R.string.empty_no_reports_action)
                } else null,
                onAction = onAction
            )
        }
    }
}
