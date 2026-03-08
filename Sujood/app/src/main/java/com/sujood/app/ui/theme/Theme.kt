package com.sujood.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Custom dark color scheme for Sujood app.
 * Uses the Calm-inspired palette with deep navy background and soft accents.
 */
private val SujoodDarkColorScheme = darkColorScheme(
    // Primary colors
    primary = SoftPurple,
    onPrimary = TextPrimary,
    primaryContainer = SoftIndigo,
    onPrimaryContainer = TextPrimary,

    // Secondary colors
    secondary = WarmAmber,
    onSecondary = DeepNavy,
    secondaryContainer = WarmAmber.copy(alpha = 0.3f),
    onSecondaryContainer = TextPrimary,

    // Tertiary colors
    tertiary = LavenderGlow,
    onTertiary = TextPrimary,
    tertiaryContainer = LavenderGlow.copy(alpha = 0.3f),
    onTertiaryContainer = TextPrimary,

    // Background colors
    background = DeepNavy,
    onBackground = TextPrimary,

    // Surface colors
    surface = MidnightBlue,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondary,

    // Error colors
    error = WarningOrange,
    onError = TextPrimary,
    errorContainer = WarningOrange.copy(alpha = 0.3f),
    onErrorContainer = TextPrimary,

    // Outline and inverse colors
    outline = TextTertiary,
    outlineVariant = GlassBorder,
    inverseSurface = TextPrimary,
    inverseOnSurface = DeepNavy,
    inversePrimary = SoftPurple,

    // Scrim
    scrim = DeepNavy.copy(alpha = 0.8f)
)

/**
 * Sujood app theme.
 * Always uses dark theme for the calm, spiritual aesthetic.
 */
@Composable
fun SujoodTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = SujoodDarkColorScheme
    
    // Get the current view to apply system UI changes
    val view = LocalView.current
    
    // Apply system bar colors
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DeepNavy.toArgb()
            window.navigationBarColor = DeepNavy.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
