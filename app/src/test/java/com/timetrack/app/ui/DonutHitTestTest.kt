package com.timetrack.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Hit testing for the donut.
 *
 * The ring is drawn in a square box with a stroke: the painted band runs from
 * `centreRadius - stroke/2` to `centreRadius + stroke/2`. These tests pin the
 * three answers that matter — which sector, the hole, and outside.
 */
class DonutHitTestTest {

    // A 200x200 canvas with a 20px stroke: diameter 180, centreline radius 90,
    // so the band is 80..100 around the centre (100, 100).
    private val size = 200f
    private val stroke = 20f

    private fun indexAt(x: Float, y: Float, sweeps: List<Float>) =
        sliceIndexAt(x, y, size, size, stroke, sweeps)

    @Test
    fun `a tap on the right half belongs to the first slice`() {
        assertEquals(0, indexAt(190f, 100f, listOf(180f, 180f)))
    }

    @Test
    fun `a tap on the left half belongs to the second slice`() {
        assertEquals(1, indexAt(10f, 100f, listOf(180f, 180f)))
    }

    @Test
    fun `a three way split maps each sector to its own slice`() {
        val thirds = listOf(120f, 120f, 120f)
        // 3 o'clock, 6 o'clock, 9 o'clock — 90, 180 and 270 degrees from the start.
        assertEquals(0, indexAt(190f, 100f, thirds))
        assertEquals(1, indexAt(100f, 190f, thirds))
        assertEquals(2, indexAt(10f, 100f, thirds))
    }

    @Test
    fun `a tap just clockwise of twelve o'clock is the first slice`() {
        val sweeps = listOf(90f, 180f, 90f)
        // On the centreline, a few degrees past the start.
        assertEquals(0, indexAt(105f, 100f - 89.86f, sweeps))
    }

    @Test
    fun `a tap just anticlockwise of twelve o'clock is the last slice`() {
        val sweeps = listOf(90f, 180f, 90f)
        // The same distance the other way: 355 degrees from the start.
        assertEquals(2, indexAt(95f, 100f - 89.86f, sweeps))
    }

    @Test
    fun `the hole in the middle is not part of any slice`() {
        assertEquals(-1, indexAt(100f, 100f, listOf(180f, 180f)))
        // Just inside the inner edge, which is 80 from the centre.
        assertEquals(-1, indexAt(100f, 100f - 79f, listOf(180f, 180f)))
    }

    @Test
    fun `a tap outside the ring is not part of any slice`() {
        // The outer edge is 100 from the centre; the corner is far beyond it.
        assertEquals(-1, indexAt(5f, 5f, listOf(180f, 180f)))
        assertEquals(-1, indexAt(100f, 100f - 101f, listOf(180f, 180f)))
    }

    @Test
    fun `an empty sweep list selects nothing`() {
        assertEquals(-1, indexAt(190f, 100f, emptyList()))
    }

    @Test
    fun `a tiny first slice is still reachable`() {
        // One degree of arc, then the rest: a tap at the top must find the sliver.
        assertEquals(0, indexAt(100f, 10f, listOf(1f, 359f)))
    }
}
