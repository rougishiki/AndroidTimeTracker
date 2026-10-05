package com.timetrack.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One type scale, six sizes that matter, everything in tabular figures.
 *
 * Two decisions worth stating:
 *
 * **Numbers use `tnum`, not a monospace family.** Tabular figures stop the clock
 * from jittering as digits change width, which is the only reason monospace was
 * ever used here — but they keep the text looking like text instead of like code.
 *
 * **The big clock is Light, not Bold.** Large numerals read as precise when they
 * are thin and as heavy-handed when they are bold. Bold is for small text that
 * has to survive a busy background.
 *
 * `titleSmall` is the section label: small, semi-bold, and always used with a
 * secondary colour. Hierarchy comes from that contrast rather than from putting
 * every block on its own card.
 */
private fun numeric(
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    letterSpacing: Float = 0f,
) = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.sp,
    // Tabular figures everywhere: any number on screen should have a stable
    // width, and applying it globally is simpler than remembering which ones.
    fontFeatureSettings = "tnum",
)

private val LIGHT = FontWeight.Light
private val NORMAL = FontWeight.Normal
private val MEDIUM = FontWeight.Medium
private val SEMI = FontWeight.SemiBold

val TimeTrackTypography = Typography(
    displayLarge = numeric(56f, 58f, LIGHT),
    displayMedium = numeric(44f, 46f, LIGHT),
    displaySmall = numeric(36f, 40f, LIGHT),
    headlineLarge = numeric(40f, 44f, LIGHT),
    headlineMedium = numeric(34f, 38f, MEDIUM),
    headlineSmall = numeric(26f, 30f, MEDIUM),
    titleLarge = numeric(20f, 26f, MEDIUM),
    titleMedium = numeric(17f, 22f, MEDIUM),
    titleSmall = numeric(13f, 16f, SEMI, letterSpacing = 0.3f),
    bodyLarge = numeric(16f, 23f, NORMAL),
    bodyMedium = numeric(15f, 22f, NORMAL),
    bodySmall = numeric(12.5f, 17f, NORMAL),
    labelLarge = numeric(15f, 20f, MEDIUM),
    labelMedium = numeric(12.5f, 16f, MEDIUM),
    labelSmall = numeric(11.5f, 15f, MEDIUM),
)
