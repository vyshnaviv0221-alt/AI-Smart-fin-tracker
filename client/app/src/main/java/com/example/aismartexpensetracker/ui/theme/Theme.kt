package com.example.aismartexpensetracker.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AppColorScheme = lightColorScheme(
    primary = Indigo500,
    onPrimary = SurfaceWhite,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo900,
    secondary = Indigo700,
    onSecondary = SurfaceWhite,
    background = Canvas,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    surfaceVariant = Indigo50,
    onSurfaceVariant = InkMuted,
    error = Danger,
    onError = SurfaceWhite,
    errorContainer = DangerSoft,
    onErrorContainer = Danger,
    outline = Hairline,
    outlineVariant = Hairline
)

/**
 * True when the user has turned animations off system-wide (Settings >
 * Accessibility > Remove animations, or Developer options). This is Android's
 * equivalent of the web's prefers-reduced-motion.
 *
 * Reduced motion does not mean no feedback: components read this and swap the
 * spring for an instant change, so the state change stays legible without
 * vestibular movement.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun AISMARTEXPENSETRACKERTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // The app paints a light canvas with dark-on-light text throughout, so it
    // deliberately does not follow the system into dark mode -- Material's
    // Card would take a dark surface and render near-black on those light
    // pages. Dynamic colour is off for the same reason: it would swap the
    // brand palette for the user's wallpaper.
    val reducedMotion = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }.getOrDefault(false)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Light canvas, so status bar icons must be dark.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = AppColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
