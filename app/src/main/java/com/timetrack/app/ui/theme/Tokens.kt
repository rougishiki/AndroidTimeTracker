package com.timetrack.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing on an 8pt grid, with the gaps deliberately unequal.
 *
 * A single uniform gap is what makes a screen read as a flat stack of blocks:
 * when everything is 16dp apart, nothing is grouped. The contrast between [sm]
 * inside a group and [xl] between groups is what creates structure without
 * needing a card around every section.
 */
object Space {
    /** Between a label and the thing it labels. */
    val xs = 4.dp

    /** Between rows of the same list. */
    val sm = 8.dp

    /** Between a label and its value on one line. */
    val md = 12.dp

    /** Between elements of one block. */
    val lg = 20.dp

    /** Between blocks. This is the one that does the grouping. */
    val xl = 32.dp

    val pageH = 20.dp
    val pageV = 24.dp
}

/**
 * Three radii, each with one job: containers, controls, and buttons (which use
 * the Material3 pill and are not listed here). Rounding everything to the same
 * value is another way of flattening the hierarchy.
 */
object Radius {
    val container = 16.dp
    val control = 12.dp
    val bar = 3.dp

    /** The leading status stripe on a running row. */
    val stripe = 1.5.dp
}
