package com.example.movexa.ui.dashboard.admin

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.example.movexa.R
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * ChartStyleHelper — centralised chart styling for the Analytics module.
 *
 * Uses MPAndroidChart and applies Material design colors from the app theme.
 * All methods are stateless and take a [Context] for resource access.
 */
object ChartStyleHelper {

    // ═══════════════════════════════════════════════════════════
    //  1. PIE / DONUT CHART — Cost Breakdown
    // ═══════════════════════════════════════════════════════════

    /**
     * Style a [PieChart] as a modern donut chart for cost breakdowns.
     */
    fun stylePieChart(chart: PieChart, context: Context) {
        chart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 58f
            transparentCircleRadius = 62f
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(110)
            setDrawCenterText(true)
            setCenterTextSize(14f)
            setCenterTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setCenterTextTypeface(Typeface.DEFAULT_BOLD)
            setUsePercentValues(true)
            setEntryLabelTextSize(11f)
            setEntryLabelColor(ContextCompat.getColor(context, R.color.text_primary))
            setDrawEntryLabels(false)
            rotationAngle = 270f
            isRotationEnabled = false
            isHighlightPerTapEnabled = true
            setExtraOffsets(8f, 8f, 8f, 8f)

            legend.apply {
                isEnabled = true
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
                textSize = 12f
                textColor = ContextCompat.getColor(context, R.color.text_secondary)
                formSize = 12f
                xEntrySpace = 16f
                yEntrySpace = 4f
                formToTextSpace = 6f
            }
        }
    }

    /**
     * Set cost breakdown data on a [PieChart].
     */
    fun setCostBreakdownData(
        chart: PieChart,
        context: Context,
        fuelCost: Float,
        serviceCost: Float,
        repairCost: Float,
        centerText: String = ""
    ) {
        val entries = mutableListOf<PieEntry>()
        if (fuelCost > 0) entries.add(PieEntry(fuelCost, "Fuel"))
        if (serviceCost > 0) entries.add(PieEntry(serviceCost, "Services"))
        if (repairCost > 0) entries.add(PieEntry(repairCost, "Repairs"))

        if (entries.isEmpty()) {
            chart.clear()
            chart.centerText = "No Data"
            chart.invalidate()
            return
        }

        val colors = mutableListOf<Int>()
        if (fuelCost > 0) colors.add(ContextCompat.getColor(context, R.color.analytics_fuel))
        if (serviceCost > 0) colors.add(ContextCompat.getColor(context, R.color.analytics_service))
        if (repairCost > 0) colors.add(ContextCompat.getColor(context, R.color.analytics_repair))

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            sliceSpace = 3f
            selectionShift = 6f
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            valueTypeface = Typeface.DEFAULT_BOLD
            valueFormatter = PercentFormatter()
        }

        chart.data = PieData(dataSet)
        chart.centerText = centerText
        chart.animateY(800, Easing.EaseInOutQuad)
        chart.invalidate()
    }

    // ═══════════════════════════════════════════════════════════
    //  2. LINE CHART — Revenue/Expense Trends, Fuel Trends
    // ═══════════════════════════════════════════════════════════

    /**
     * Style a [LineChart] for trend visualisation.
     */
    fun styleLineChart(chart: LineChart, context: Context) {
        chart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            setExtraOffsets(12f, 12f, 12f, 12f)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = ContextCompat.getColor(context, R.color.text_secondary)
                textSize = 10f
                labelRotationAngle = 0f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(context, R.color.outline_variant)
                textColor = ContextCompat.getColor(context, R.color.text_secondary)
                textSize = 10f
                axisMinimum = 0f
                valueFormatter = CurrencyAxisFormatter()
            }

            axisRight.isEnabled = false

            legend.apply {
                isEnabled = true
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
                textSize = 11f
                textColor = ContextCompat.getColor(context, R.color.text_secondary)
                formSize = 10f
                xEntrySpace = 12f
            }
        }
    }

    /**
     * Set revenue vs expense trend data on a [LineChart].
     */
    fun setRevenueTrendData(
        chart: LineChart,
        context: Context,
        labels: List<String>,
        revenueValues: List<Float>,
        expenseValues: List<Float>
    ) {
        if (labels.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        val revenueEntries = revenueValues.mapIndexed { i, v -> Entry(i.toFloat(), v) }
        val expenseEntries = expenseValues.mapIndexed { i, v -> Entry(i.toFloat(), v) }

        val revenueSet = LineDataSet(revenueEntries, "Revenue").apply {
            color = ContextCompat.getColor(context, R.color.analytics_revenue)
            lineWidth = 2.5f
            setDrawCircles(true)
            circleRadius = 4f
            setCircleColor(ContextCompat.getColor(context, R.color.analytics_revenue))
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(context, R.color.analytics_revenue_fill)
            fillAlpha = 60
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val expenseSet = LineDataSet(expenseEntries, "Expenses").apply {
            color = ContextCompat.getColor(context, R.color.analytics_expense)
            lineWidth = 2.5f
            setDrawCircles(true)
            circleRadius = 4f
            setCircleColor(ContextCompat.getColor(context, R.color.analytics_expense))
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(context, R.color.analytics_expense_fill)
            fillAlpha = 40
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.xAxis.labelCount = labels.size

        chart.data = LineData(revenueSet, expenseSet)
        chart.animateX(1000, Easing.EaseInOutCubic)
        chart.invalidate()
    }

    /**
     * Set fuel cost trend data on a [LineChart].
     */
    fun setFuelTrendData(
        chart: LineChart,
        context: Context,
        labels: List<String>,
        costValues: List<Float>,
        litreValues: List<Float>
    ) {
        if (labels.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        val costEntries = costValues.mapIndexed { i, v -> Entry(i.toFloat(), v) }

        val costSet = LineDataSet(costEntries, "Fuel Cost").apply {
            color = ContextCompat.getColor(context, R.color.analytics_fuel)
            lineWidth = 2.5f
            setDrawCircles(true)
            circleRadius = 4f
            setCircleColor(ContextCompat.getColor(context, R.color.analytics_fuel))
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(context, R.color.analytics_fuel_fill)
            fillAlpha = 50
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.xAxis.labelCount = labels.size
        chart.axisLeft.valueFormatter = CurrencyAxisFormatter()

        chart.data = LineData(costSet)
        chart.animateX(800, Easing.EaseInOutCubic)
        chart.invalidate()
    }

    // ═══════════════════════════════════════════════════════════
    //  3. BAR CHART — Vehicle Utilization
    // ═══════════════════════════════════════════════════════════

    /**
     * Style a [HorizontalBarChart] for vehicle utilization.
     */
    fun styleHorizontalBarChart(chart: HorizontalBarChart, context: Context) {
        chart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            setFitBars(true)
            setExtraOffsets(8f, 8f, 24f, 8f)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = ContextCompat.getColor(context, R.color.text_secondary)
                textSize = 10f
            }

            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(context, R.color.outline_variant)
                textColor = ContextCompat.getColor(context, R.color.text_secondary)
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = "${value.toInt()}%"
                }
            }

            axisRight.isEnabled = false

            legend.apply {
                isEnabled = false
            }
        }
    }

    /**
     * Set vehicle utilization data on a [HorizontalBarChart].
     */
    fun setUtilizationData(
        chart: HorizontalBarChart,
        context: Context,
        labels: List<String>,
        values: List<Float>  // 0..100 percent
    ) {
        if (labels.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }

        val dataSet = BarDataSet(entries, "Utilization").apply {
            colors = values.map { pct ->
                when {
                    pct >= 70f -> ContextCompat.getColor(context, R.color.analytics_util_high)
                    pct >= 40f -> ContextCompat.getColor(context, R.color.analytics_util_mid)
                    else -> ContextCompat.getColor(context, R.color.analytics_util_low)
                }
            }
            valueTextSize = 11f
            valueTextColor = ContextCompat.getColor(context, R.color.text_primary)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()}%"
            }
        }

        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.xAxis.labelCount = labels.size

        chart.data = BarData(dataSet).apply { barWidth = 0.6f }
        chart.animateY(800, Easing.EaseInOutQuad)
        chart.invalidate()
    }

    // ═══════════════════════════════════════════════════════════
    //  4. BAR CHART — Driver Performance
    // ═══════════════════════════════════════════════════════════

    /**
     * Style a vertical [BarChart] for driver scores.
     */
    fun styleBarChart(chart: BarChart, context: Context) {
        chart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            setFitBars(true)
            setExtraOffsets(8f, 8f, 8f, 8f)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = ContextCompat.getColor(context, R.color.text_secondary)
                textSize = 9f
                labelRotationAngle = -30f
            }

            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(context, R.color.outline_variant)
                textColor = ContextCompat.getColor(context, R.color.text_secondary)
                textSize = 10f
            }

            axisRight.isEnabled = false

            legend.isEnabled = false
        }
    }

    /**
     * Set driver performance scores on a [BarChart].
     */
    fun setDriverScoreData(
        chart: BarChart,
        context: Context,
        names: List<String>,
        scores: List<Float>
    ) {
        if (names.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        val entries = scores.mapIndexed { i, s -> BarEntry(i.toFloat(), s) }

        val dataSet = BarDataSet(entries, "Score").apply {
            colors = scores.map { score ->
                when {
                    score >= 80f -> ContextCompat.getColor(context, R.color.analytics_score_excellent)
                    score >= 60f -> ContextCompat.getColor(context, R.color.analytics_score_good)
                    score >= 40f -> ContextCompat.getColor(context, R.color.analytics_score_average)
                    else -> ContextCompat.getColor(context, R.color.analytics_score_risky)
                }
            }
            valueTextSize = 10f
            valueTextColor = ContextCompat.getColor(context, R.color.text_primary)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = value.toInt().toString()
            }
        }

        chart.xAxis.valueFormatter = IndexAxisValueFormatter(names)
        chart.xAxis.labelCount = names.size

        chart.data = BarData(dataSet).apply { barWidth = 0.65f }
        chart.animateY(800, Easing.EaseInOutQuad)
        chart.invalidate()
    }

    // ═══════════════════════════════════════════════════════════
    //  VALUE FORMATTERS
    // ═══════════════════════════════════════════════════════════

    private class PercentFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return String.format(Locale.US, "%.1f%%", value)
        }
    }

    private class CurrencyAxisFormatter : ValueFormatter() {
        private val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            currency = Currency.getInstance("INR")
            maximumFractionDigits = 0
        }

        override fun getFormattedValue(value: Float): String {
            return when {
                value >= 100_000f -> String.format(Locale.US, "₹%.1fL", value / 100_000f)
                value >= 1_000f -> String.format(Locale.US, "₹%.1fK", value / 1_000f)
                else -> fmt.format(value.toDouble())
            }
        }
    }
}
