package com.jagoo.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

private val LightColorScheme = lightColorScheme(
    primary = JagooYellow,
    onPrimary = JagooTextPrimary,
    primaryContainer = JagooYellowBadge,
    onPrimaryContainer = JagooTextPrimary,
    secondary = JagooGreen,
    onSecondary = JagooCardWhite,
    secondaryContainer = JagooGreenLight,
    onSecondaryContainer = JagooGreen,
    background = JagooBackground,
    onBackground = JagooTextPrimary,
    surface = JagooCardWhite,
    onSurface = JagooTextPrimary,
    error = JagooRed,
    onError = JagooCardWhite
)

@Composable
fun JagooTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = JagooYellow.toArgb()
            // Forzar íconos oscuros en la barra de estado (legibles sobre fondo amarillo)
            WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
