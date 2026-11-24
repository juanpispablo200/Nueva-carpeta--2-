package com.jagoo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooGreenLight
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.PoppinsFontFamily

@Composable
fun QuantitySelector(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    isCompact: Boolean = false
) {
    // Animación de escala para el botón + del selector
    var increasePressed by remember { mutableStateOf(false) }
    var decreasePressed by remember { mutableStateOf(false) }

    val increaseScale by animateFloatAsState(
        targetValue = if (increasePressed) 0.78f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "increase_scale"
    )
    val decreaseScale by animateFloatAsState(
        targetValue = if (decreasePressed) 0.78f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "decrease_scale"
    )

    // El fondo del botón − vira a rojo suave cuando quantity == 1 (siguiente tap elimina el item)
    val decreaseBg by animateColorAsState(
        targetValue = if (quantity <= 1) Color(0xFFFFEBEE) else Color.Transparent,
        label = "decrease_bg"
    )
    val decreaseTint by animateColorAsState(
        targetValue = if (quantity <= 1) Color(0xFFD32F2F) else JagooGreen,
        label = "decrease_tint"
    )

    // La píldora visual mide 28/32dp; cada botón tiene un área táctil de 48x48dp.
    val pillHeight = if (isCompact) 28.dp else 32.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .height(48.dp)
            .drawBehind {
                val h = pillHeight.toPx()
                drawRoundRect(
                    color = JagooGreenLight,
                    topLeft = Offset(0f, (size.height - h) / 2f),
                    size = Size(size.width, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f)
                )
            }
    ) {
        // Botón decrementar (con feedback de color cuando va a eliminar)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) {
                    decreasePressed = true
                    onDecrease()
                    decreasePressed = false
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(decreaseScale)
                    .size(if (isCompact) 20.dp else 24.dp)
                    .clip(CircleShape)
                    .background(decreaseBg)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = if (quantity <= 1) "Quitar del carrito" else "Disminuir cantidad",
                    tint = decreaseTint,
                    modifier = Modifier.size(if (isCompact) 14.dp else 16.dp)
                )
            }
        }

        Text(
            text = "$quantity",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = if (isCompact) 12.sp else 14.sp,
            color = JagooTextPrimary,
            modifier = Modifier.padding(horizontal = JagooSpacing.xs)
        )

        // Botón incrementar
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) {
                    increasePressed = true
                    onIncrease()
                    increasePressed = false
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(increaseScale)
                    .size(if (isCompact) 20.dp else 24.dp)
                    .clip(CircleShape)
                    .background(JagooGreen)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Aumentar cantidad",
                    tint = Color.White,
                    modifier = Modifier.size(if (isCompact) 14.dp else 16.dp)
                )
            }
        }
    }
}
