package com.jagoo.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val PillShape = CircleShape
val HeaderBottomShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
val CardShape = RoundedCornerShape(18.dp)
val CouponShape = RoundedCornerShape(16.dp)
val ProductCardShape = RoundedCornerShape(18.dp)
val ChipShape = RoundedCornerShape(20.dp)
