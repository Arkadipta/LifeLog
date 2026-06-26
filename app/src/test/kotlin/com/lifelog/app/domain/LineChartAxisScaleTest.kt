package com.lifelog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineChartAxisScaleTest {

    private fun compute(min: Double, max: Double, maxTicks: Int = 4) =
        LineChartAxisScale.compute(min, max, maxTicks)!!

    private fun assertNice(value: Double) {
        // A "nice" tick is an integer multiple of its own step; we only assert
        // that labels are not jittery doubles by checking they round cleanly to
        // a few decimals — the per-test step expectations cover exact values.
        val rounded = Math.round(value * 1_000.0) / 1_000.0
        assertEquals("tick $value should be clean", rounded, value, 1e-9)
    }

    @Test
    fun `narrow range is fit tightly, not anchored at zero`() {
        val scale = compute(79.5, 80.1)
        // Far from a 0–80 axis: the window hugs the data.
        assertTrue("min ${scale.min} should be just below data", scale.min in 79.0..79.5)
        assertTrue("max ${scale.max} should be just above data", scale.max in 80.1..81.0)
        assertTrue(scale.ticks.isNotEmpty())
        scale.ticks.forEach { assertNice(it) }
        // The tight range needs fractional labels.
        assertTrue(scale.decimals >= 1)
    }

    @Test
    fun `wide single-series range frames the data with round ticks`() {
        val scale = compute(77.0, 123.0)
        assertTrue(scale.min < 77.0)
        assertTrue(scale.max > 123.0)
        assertEquals(listOf(80.0, 100.0, 120.0), scale.ticks)
        assertEquals(0, scale.decimals)
    }

    @Test
    fun `multi-series global min and max share one axis`() {
        // Systolic 110–130 and diastolic 70–90 → caller passes the global extent.
        val scale = compute(70.0, 130.0)
        assertTrue("min ${scale.min}", scale.min in 64.0..66.0)
        assertTrue("max ${scale.max}", scale.max in 134.0..136.0)
        scale.ticks.forEach { assertNice(it) }
    }

    @Test
    fun `tick count never exceeds the cap`() {
        listOf(
            0.0 to 1.0,
            1.0 to 9.0,
            3.0 to 7.0,
            0.0 to 1000.0,
            -50.0 to 50.0,
            0.123 to 0.987,
        ).forEach { (lo, hi) ->
            val scale = compute(lo, hi, maxTicks = 4)
            assertTrue(
                "range $lo..$hi produced ${scale.ticks}",
                scale.ticks.size <= 4
            )
            assertTrue("ticks within range", scale.ticks.all { it in scale.min..scale.max })
        }
    }

    @Test
    fun `ranges crossing zero include a zero tick`() {
        val scale = compute(-10.0, 50.0)
        assertTrue("expected 0 among ${scale.ticks}", scale.ticks.any { kotlin.math.abs(it) < 1e-9 })
    }

    @Test
    fun `all-negative range stays negative`() {
        val scale = compute(-130.0, -70.0)
        assertTrue(scale.max <= 0.0)
        assertTrue(scale.ticks.all { it < 0.0 })
    }

    @Test
    fun `flat series yields a single centered tick`() {
        val scale = compute(80.0, 80.0)
        assertEquals(listOf(80.0), scale.ticks)
        assertTrue("80 should be centered", scale.min < 80.0 && scale.max > 80.0)
    }

    @Test
    fun `flat zero series is handled without dividing by zero`() {
        val scale = compute(0.0, 0.0)
        assertEquals(listOf(0.0), scale.ticks)
        assertTrue(scale.min < 0.0 && scale.max > 0.0)
    }

    @Test
    fun `non-finite input falls back to null`() {
        assertNull(LineChartAxisScale.compute(Double.NaN, 1.0))
        assertNull(LineChartAxisScale.compute(0.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `swapped min and max are tolerated`() {
        val scale = compute(123.0, 77.0)
        assertEquals(listOf(80.0, 100.0, 120.0), scale.ticks)
    }
}
