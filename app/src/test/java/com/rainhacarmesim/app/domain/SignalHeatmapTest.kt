package com.rainhacarmesim.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SignalHeatmapTest {

    @Test
    fun gridAveragesReadingsInTheSameCell() {
        val grid = SignalGrid(cellSize = 0.5f)
        grid.add(0.1f, 0.1f, -60)
        grid.add(0.4f, 0.3f, -70)
        grid.add(-0.2f, 0.1f, -80) // célula vizinha (x negativo)
        val samples = grid.samples().sortedBy { it.x }
        assertEquals(2, samples.size)
        assertEquals(-80f, samples[0].rssi, 0.001f)
        assertEquals(-65f, samples[1].rssi, 0.001f)
        assertEquals(2, samples[1].count)
        assertEquals(0.25f, samples[1].x, 0.001f)
    }

    @Test
    fun interpolationMatchesSampleAndIgnoresFarAway() {
        val samples = listOf(SignalSample(0f, 0f, -60f, 1), SignalSample(2f, 0f, -80f, 1))
        assertEquals(-60f, SignalHeatmap.interpolate(samples, 0f, 0f)!!.first, 0.5f)
        assertEquals(-70f, SignalHeatmap.interpolate(samples, 1f, 0f)!!.first, 0.001f)
        assertNull(SignalHeatmap.interpolate(samples, 20f, 20f))
    }

    @Test
    fun colorScaleGoesFromRedToGreen() {
        assertEquals(0xFFFF3B3B.toInt(), SignalHeatmap.colorFor(-100f))
        assertEquals(0xFF3DDC84.toInt(), SignalHeatmap.colorFor(-30f))
        assertEquals(0xFFFFD60A.toInt(), SignalHeatmap.colorFor(-70f))
    }

    @Test
    fun renderLeavesAreasWithoutDataTransparent() {
        val samples = listOf(SignalSample(0f, 0f, -55f, 1))
        val bounds = MapBounds(-10f, -10f, 10f, 10f)
        val px = SignalHeatmap.render(samples, bounds, 20, 20)
        assertEquals(0, px[0] ushr 24) // canto, a 14 m: sem dado
        assertTrue((px[10 * 20 + 10] ushr 24) > 0) // centro: com dado
    }

    @Test
    fun boundsKeepMinimumSpanAndAspect() {
        val b = SignalHeatmap.boundsFor(listOf(MapPoint(0f, 0f)), aspect = 2f)
        assertEquals(2f, b.width / b.height, 0.001f)
        assertTrue(b.height >= 8f)
        assertEquals(0f, b.centerX, 0.001f)
        val wide = SignalHeatmap.boundsFor(listOf(MapPoint(-10f, 0f), MapPoint(10f, 0f)), aspect = 1f)
        assertTrue(wide.minX <= -10f && wide.maxX >= 10f)
        assertEquals(wide.width, wide.height, 0.001f)
    }

    @Test
    fun summaryReportsWorstAverageBest() {
        val s = SignalHeatmap.summary(listOf(SignalSample(0f, 0f, -80f, 1), SignalSample(1f, 0f, -60f, 1)))
        assertNotNull(s)
        assertEquals(-80f, s!!.first, 0.001f)
        assertEquals(-70f, s.second, 0.001f)
        assertEquals(-60f, s.third, 0.001f)
        assertNull(SignalHeatmap.summary(emptyList()))
    }

    @Test
    fun stepDetectorCountsWalkingAndIgnoresStillness() {
        val d = StepDetector()
        var steps = 0
        // 1 s parado: só gravidade.
        for (t in 0 until 50) if (d.onSample(0f, 0f, 9.81f, t * 20L)) steps++
        assertEquals(0, steps)
        // 5 s andando a 2 passos/s: oscilação vertical de ±3 m/s², amostrada a 50 Hz.
        for (i in 0 until 250) {
            val t = 1_000L + i * 20L
            val a = 9.81f + 3f * sin(2 * PI * 2.0 * (i * 0.02)).toFloat()
            if (d.onSample(0f, 0f, a, t)) steps++
        }
        assertTrue("passos detectados: $steps", steps in 8..11)
    }
}
