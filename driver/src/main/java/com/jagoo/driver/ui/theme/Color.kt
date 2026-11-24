package com.jagoo.driver.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val JagooYellow = Color(0xFFFFD600)
val JagooYellowLight = Color(0xFFFFF176)
val JagooYellowBadge = Color(0xFFFFE082)

val JagooGreen = Color(0xFF0B5D2E)
val JagooGreenLight = Color(0xFFE8F5E9)

val JagooBackground = Color(0xFFFBF8EC)
val JagooCardWhite = Color(0xFFFFFFFF)

val JagooRed = Color(0xFFD32F2F)

val JagooTextPrimary = Color(0xFF1C211E)
val JagooTextSecondary = Color(0xFF666666)
val JagooTextMuted = Color(0xFF888888)

val HeaderBottomShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
val PillShape = CircleShape

/** Degradado cálido de los encabezados: más profundidad que un amarillo plano. */
val JagooHeaderBrush: androidx.compose.ui.graphics.Brush = androidx.compose.ui.graphics.Brush.verticalGradient(
    listOf(Color(0xFFFFE566), JagooYellow, Color(0xFFFFC107))
)
