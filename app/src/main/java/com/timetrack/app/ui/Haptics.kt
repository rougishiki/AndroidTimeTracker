package com.timetrack.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The app's confirmation tap, in one place so it feels the same everywhere.
 *
 * Used for actions that change recorded data — starting, stopping, ticking,
 * deleting. Tracking is an act of trust ("did that actually start?"), and a
 * short buzz is the cheapest way to answer it without the user looking.
 *
 * Only one intensity on purpose. Anything finer is guesswork the user cannot
 * reliably perceive, and a tap that fires with no visible change is worse than
 * no tap at all.
 */
@Composable
fun rememberFirmTap(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.LongPress) } }
}
