package com.timetrack.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Maps the neutral palette onto Material's roles.
 *
 * The `surfaceContainer*` roles are set explicitly because Material3 components
 * (notably `Card`) default to them rather than to `surface`; leaving them alone
 * would let a tinted container back in through the side door.
 */
private val LightScheme = lightColorScheme(
    primary = LightInk,
    onPrimary = LightSurface,
    primaryContainer = LightFill,
    onPrimaryContainer = LightInk,
    secondary = LightInkSecondary,
    onSecondary = LightSurface,
    background = LightBg,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightFill,
    onSurfaceVariant = LightInkSecondary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightFill,
    surfaceContainerHigh = LightFill,
    surfaceContainerHighest = LightFill,
    outline = LightOutlineStrong,
    outlineVariant = LightOutline,
    error = LightDestructive,
    onError = LightSurface,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
)

private val DarkScheme = darkColorScheme(
    primary = DarkInk,
    onPrimary = DarkBg,
    primaryContainer = DarkFill,
    onPrimaryContainer = DarkInk,
    secondary = DarkInkSecondary,
    onSecondary = DarkBg,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkFill,
    onSurfaceVariant = DarkInkSecondary,
    surfaceContainerLowest = DarkBg,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkFill,
    surfaceContainerHigh = DarkFill,
    surfaceContainerHighest = DarkFill,
    outline = DarkOutlineStrong,
    outlineVariant = DarkOutline,
    error = DarkDestructive,
    onError = DarkBg,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
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
