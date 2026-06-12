package com.lifelog.app.ui.events.components

import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.HorizontalDimensions
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis

/**
 * X-axis item placer that draws a label and tick exactly at the given x values
 * (bucket indices chosen by ChartTickGenerator), instead of deriving positions
 * from the data, so sparse series still get a fully labeled axis.
 */
internal class ExplicitTickItemPlacer(private val xValues: List<Double>) :
    HorizontalAxis.ItemPlacer {

    override fun getShiftExtremeLines(context: CartesianDrawingContext): Boolean = false

    override fun getFirstLabelValue(
        context: CartesianMeasuringContext,
        maxLabelWidth: Float,
    ): Double? = xValues.firstOrNull()

    override fun getLastLabelValue(
        context: CartesianMeasuringContext,
        maxLabelWidth: Float,
    ): Double? = xValues.lastOrNull()

    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = xValues.filter { it in visibleXRange }

    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasuringContext,
        horizontalDimensions: HorizontalDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
    ): List<Double> = xValues

    override fun getHeightMeasurementLabelValues(
        context: CartesianMeasuringContext,
        horizontalDimensions: HorizontalDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = xValues

    override fun getStartHorizontalAxisInset(
        context: CartesianMeasuringContext,
        horizontalDimensions: HorizontalDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = (tickThickness / 2 - horizontalDimensions.unscalableStartPadding)
        .coerceAtLeast(0f)

    override fun getEndHorizontalAxisInset(
        context: CartesianMeasuringContext,
        horizontalDimensions: HorizontalDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = (tickThickness / 2 - horizontalDimensions.unscalableEndPadding)
        .coerceAtLeast(0f)
}

/**
 * Y-axis item placer that caps labels at 4 for normal data, but falls back to a single
 * tick at the constant value when the y-range is zero (flat / single-value series).
 * This prevents both overlapping duplicate labels and invisible axes.
 */
internal object RobustCountItemPlacer : VerticalAxis.ItemPlacer {

    private val delegate = VerticalAxis.ItemPlacer.count(count = { 4 })

    override fun getShiftTopLines(context: CartesianDrawingContext): Boolean =
        delegate.getShiftTopLines(context)

    override fun getLabelValues(
        context: CartesianDrawingContext,
        axisHeight: Float,
        maxLabelHeight: Float,
        position: Axis.Position.Vertical,
    ): List<Double> {
        val yRange = context.ranges.getYRange(position)
        return if (yRange.length == 0.0) listOf(yRange.minY)
        else delegate.getLabelValues(context, axisHeight, maxLabelHeight, position)
    }

    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasuringContext,
        axisHeight: Float,
        maxLabelHeight: Float,
        position: Axis.Position.Vertical,
    ): List<Double> {
        val yRange = context.ranges.getYRange(position)
        return if (yRange.length == 0.0) listOf(yRange.minY)
        else delegate.getWidthMeasurementLabelValues(context, axisHeight, maxLabelHeight, position)
    }

    override fun getHeightMeasurementLabelValues(
        context: CartesianMeasuringContext,
        position: Axis.Position.Vertical,
    ): List<Double> = delegate.getHeightMeasurementLabelValues(context, position)

    override fun getLineValues(
        context: CartesianDrawingContext,
        axisHeight: Float,
        maxLabelHeight: Float,
        position: Axis.Position.Vertical,
    ): List<Double>? = delegate.getLineValues(context, axisHeight, maxLabelHeight, position)

    override fun getTopVerticalAxisInset(
        context: CartesianMeasuringContext,
        verticalLabelPosition: VerticalAxis.VerticalLabelPosition,
        maxLabelHeight: Float,
        maxLineThickness: Float,
    ): Float = delegate.getTopVerticalAxisInset(
        context, verticalLabelPosition, maxLabelHeight, maxLineThickness
    )

    override fun getBottomVerticalAxisInset(
        context: CartesianMeasuringContext,
        verticalLabelPosition: VerticalAxis.VerticalLabelPosition,
        maxLabelHeight: Float,
        maxLineThickness: Float,
    ): Float = delegate.getBottomVerticalAxisInset(
        context, verticalLabelPosition, maxLabelHeight, maxLineThickness
    )
}
