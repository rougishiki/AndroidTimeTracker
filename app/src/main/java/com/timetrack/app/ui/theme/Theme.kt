package com.timetrack.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Maps the neutral palette onto Material's roles — all of them.
 *
 * Overriding only the obvious roles is a trap. Anything left unset falls back to
 * Material3's **baseline palette, which is purple**, and those roles are not
 * obscure: `inverseSurface` paints every snackbar, `secondaryContainer` paints the
 * selected segment and every selected chip, `surfaceTint` tints every card at
 * rest. A "neutral" theme that skips them is not neutral, it is grey with purple
 * patches appearing at random — which reads as broken rather than as designed.
 *
 * Two other deliberate choices:
 * - `surfaceTint` is transparent, so cards are the colour they say they are
 *   instead of being washed with a tint for having an elevation.
 * - Every `surfaceContainer*` is plain `surface`, so dialogs and menus float as
 *   white sheets. Filled grey is reserved for `surfaceVariant`, which means one
 *   thing in this app: the live timer.
 */
private val Transparent = Color(0x00000000)

private val LightScheme = lightColorScheme(
    primary = LightInk,
    onPrimary = LightSurface,
    primaryContainer = LightFill,
    onPrimaryContainer = LightInk,
    inversePrimary = LightInk,

    secondary = LightInkSecondary,
    onSecondary = LightSurface,
    secondaryContainer = LightFill,
    onSecondaryContainer = LightInk,

    tertiary = LightInk,
    onTertiary = LightSurface,
    tertiaryContainer = LightFill,
    onTertiaryContainer = LightInk,

    background = LightBg,
    onBackground = LightInk,

    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightFill,
    onSurfaceVariant = LightInkSecondary,
    surfaceTint = Transparent,
    surfaceBright = LightSurface,
    surfaceDim = LightBg,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightFill,

    inverseSurface = LightInk,
    inverseOnSurface = LightSurface,

    error = LightDestructive,
    onError = LightSurface,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,

    outline = LightOutlineStrong,
    outlineVariant = LightOutline,
    scrim = Color(0xFF000000),
)

private val DarkScheme = darkColorScheme(
    primary = DarkInk,
    onPrimary = DarkBg,
    primaryContainer = DarkFill,
    onPrimaryContainer = DarkInk,
    inversePrimary = DarkInk,

    secondary = DarkInkSecondary,
    onSecondary = DarkBg,
    secondaryContainer = DarkFill,
    onSecondaryContainer = DarkInk,

    tertiary = DarkInk,
    onTertiary = DarkBg,
    tertiaryContainer = DarkFill,
    onTertiaryContainer = DarkInk,

    background = DarkBg,
    onBackground = DarkInk,

    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkFill,
    onSurfaceVariant = DarkInkSecondary,
    surfaceTint = Transparent,
    surfaceBright = DarkFill,
    surfaceDim = DarkBg,
    surfaceContainerLowest = DarkBg,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurface,
    surfaceContainerHighest = DarkFill,

    inverseSurface = DarkInk,
    inverseOnSurface = DarkBg,

    error = DarkDestructive,
    onError = DarkBg,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,

    outline = DarkOutlineStrong,
    outlineVariant = DarkOutline,
    scrim = Color(0xFF000000),
)

/**
 * The app's chrome colour: neutral, in both modes, always.
 *
 * Material You was enabled for one release and is off again on purpose. It
 * generates a tinted palette from the wallpaper, and a tinted palette is exactly
 * what this design removed — it competes with the task colours, which are the
 * only colours here that mean anything. Turning it back on is one line, and the
 * trade is a personalised chrome against a calm one.
 *
 * This has nothing to do with the colour a *task* is drawn in: those come from
 * `TimeTrackRepository.colorFor`, a fixed palette that also appears in the
 * charts, the notification and the exported JSON, and they never change.
 */
@Composable
fun TimeTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = TimeTrackTypography,
        content = content,
    )
}
