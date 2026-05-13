package com.lifelog.app.ui.events.components

import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis

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
