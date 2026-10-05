package com.timetrack.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The interface palette is deliberately achromatic.
 *
 * The only colours this app is allowed to show are the user's own ten task
 * colours and one red for destructive actions. Everything else is a neutral
 * grey, which is the whole point: a task's colour is data — it appears in the
 * charts, in the notification and in the exported JSON — and it cannot do that
 * job while competing with a tinted background on every screen.
 *
 * An earlier version tinted large containers with the theme's light primary,
 * which made every module look equally important and buried the data colours.
 * Filling a container is now reserved for one thing: showing state.
 */

// Light
val LightBg = Color(0xFFFAFAFB)
val LightSurface = Color(0xFFFFFFFF)
val LightFill = Color(0xFFF2F3F5)
val LightOutline = Color(0xFFE3E5E9)
val LightOutlineStrong = Color(0xFFC9CDD4)
val LightInk = Color(0xFF16181C)
val LightInkSecondary = Color(0xFF5B6169)
val LightInkTertiary = Color(0xFF8A9099)
val LightDestructive = Color(0xFFC4342F)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410E0B)

// Dark
val DarkBg = Color(0xFF0E1013)
val DarkSurface = Color(0xFF17191D)
val DarkFill = Color(0xFF1F2227)
val DarkOutline = Color(0xFF2A2E34)
val DarkOutlineStrong = Color(0xFF3A3F46)
val DarkInk = Color(0xFFF2F3F5)
val DarkInkSecondary = Color(0xFFA0A6AE)
val DarkInkTertiary = Color(0xFF6E747C)
val DarkDestructive = Color(0xFFFF6B66)
val DarkErrorContainer = Color(0xFF4A1B18)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
