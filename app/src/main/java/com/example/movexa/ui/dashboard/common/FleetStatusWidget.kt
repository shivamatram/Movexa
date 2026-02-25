package com.example.movexa.ui.dashboard.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.example.movexa.R
import com.example.movexa.theme.AppColors

// ═══════════════════════════════════════════════════════════════════════════════
//  FLEET STATUS WIDGET
// ═══════════════════════════════════════════════════════════════════════════════
//
//  Displays a segmented horizontal bar showing fleet breakdown:
//    • Moving (green)    • Idle (amber)
//    • In Service (blue) • Offline (grey)
//
//  Below the bar, legend items show counts and labels.
//
//  Features:
//  - Animated bar segments on data change
//  - Supports any FleetStatusData input
//  - Smooth color transitions
//  - Accessible labels
//  - Reusable across Admin & Manager dashboards
// ═══════════════════════════════════════════════════════════════════════════════

class FleetStatusWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ── Views ───────────────────────────────────────────────────────────────

    private val segmentBar: SegmentedBarView
    private val legendContainer: LinearLayout
    private val tvTotal: TextView

    // Legend item views
    private val legendMoving: LegendItem
    private val legendIdle: LegendItem
    private val legendService: LegendItem
    private val legendOffline: LegendItem

    init {
        val root = LayoutInflater.from(context).inflate(
            R.layout.widget_fleet_status, this, true
        )

        segmentBar = root.findViewById(R.id.segmentBar)
        legendContainer = root.findViewById(R.id.legendContainer)
        tvTotal = root.findViewById(R.id.tvFleetTotal)

        legendMoving = root.findViewById(R.id.legendMoving)
        legendIdle = root.findViewById(R.id.legendIdle)
        legendService = root.findViewById(R.id.legendService)
        legendOffline = root.findViewById(R.id.legendOffline)
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Update the widget with new fleet status data.
     * Animates the segmented bar and updates legend counts.
     */
    fun setData(data: FleetStatusData) {
        tvTotal.text = context.getString(R.string.fleet_total_format, data.total)

        // Update legend
        legendMoving.setCount(data.moving)
        legendIdle.setCount(data.idle)
        legendService.setCount(data.inService)
        legendOffline.setCount(data.offline)

        // Update segmented bar with animation
        segmentBar.setSegments(
            listOf(
                SegmentedBarView.Segment(data.movingPercent, COLOR_MOVING),
                SegmentedBarView.Segment(data.idlePercent, COLOR_IDLE),
                SegmentedBarView.Segment(data.inServicePercent, COLOR_SERVICE),
                SegmentedBarView.Segment(data.offlinePercent, COLOR_OFFLINE)
            ),
            animate = true
        )
    }

    /**
     * Show loading state.
     */
    fun showLoading() {
        tvTotal.text = "—"
        legendMoving.setCount(0)
        legendIdle.setCount(0)
        legendService.setCount(0)
        legendOffline.setCount(0)
        segmentBar.setSegments(emptyList(), animate = false)
    }

    companion object {
        val COLOR_MOVING = AppColors.SUCCESS
        val COLOR_IDLE = AppColors.WARNING
        val COLOR_SERVICE = AppColors.SECONDARY
        val COLOR_OFFLINE = AppColors.SURFACE_VARIANT
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  LEGEND ITEM — inner custom view for each status label
    // ═════════════════════════════════════════════════════════════════════════

    class LegendItem @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : LinearLayout(context, attrs, defStyleAttr) {

        private val tvCount: TextView
        private val tvLabel: TextView
        private val colorDot: View

        init {
            orientation = VERTICAL
            val root = LayoutInflater.from(context).inflate(
                R.layout.item_fleet_legend, this, true
            )
            tvCount = root.findViewById(R.id.tvLegendCount)
            tvLabel = root.findViewById(R.id.tvLegendLabel)
            colorDot = root.findViewById(R.id.viewLegendDot)
        }

        fun setCount(count: Int) {
            tvCount.text = count.toString()
        }

        fun setLabel(label: String) {
            tvLabel.text = label
        }

        fun setDotColor(@ColorInt color: Int) {
            colorDot.background?.setTint(color)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SEGMENTED BAR VIEW — Custom drawn bar chart with animated segments
// ═════════════════════════════════════════════════════════════════════════════

class SegmentedBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Segment(
        val percent: Float,
        @ColorInt val color: Int
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var segments: List<Segment> = emptyList()
    private var animationProgress = 1f
    private var animator: ValueAnimator? = null

    private val cornerRadius = context.resources.getDimension(R.dimen.radius_small)
    private val segmentGap = 2f

    fun setSegments(newSegments: List<Segment>, animate: Boolean = true) {
        segments = newSegments

        if (animate) {
            animator?.cancel()
            animationProgress = 0f
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener {
                    animationProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animationProgress = 1f
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalWidth = width.toFloat()
        val barHeight = height.toFloat()

        if (segments.isEmpty() || totalWidth <= 0) {
            // Draw empty state background
            paint.color = AppColors.SURFACE_VARIANT
            rect.set(0f, 0f, totalWidth, barHeight)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            return
        }

        var currentX = 0f
        val totalPercent = segments.sumOf { it.percent.toDouble() }.toFloat()
            .coerceAtLeast(1f) // avoid divide by zero

        for ((index, segment) in segments.withIndex()) {
            if (segment.percent <= 0) continue

            val segmentWidth = (segment.percent / totalPercent) * totalWidth * animationProgress
            if (segmentWidth < 1f) continue

            paint.color = segment.color

            val left = currentX + if (index > 0) segmentGap else 0f
            rect.set(left, 0f, left + segmentWidth - segmentGap, barHeight)

            // Apply corner radius only to first and last visible segments
            if (index == 0 && index == segments.lastIndex) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            } else if (index == 0) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                // Overdraw right corners to make them square
                canvas.drawRect(rect.right - cornerRadius, 0f, rect.right, barHeight, paint)
            } else if (index == segments.lastIndex) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                // Overdraw left corners
                canvas.drawRect(rect.left, 0f, rect.left + cornerRadius, barHeight, paint)
            } else {
                canvas.drawRect(rect, paint)
            }

            currentX = left + segmentWidth
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = context.resources.getDimensionPixelSize(R.dimen.spacing_small)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
