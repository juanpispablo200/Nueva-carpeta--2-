package com.jagoo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.data.OrderStatusStep
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.PoppinsFontFamily
import kotlinx.coroutines.delay

/**
 * Banner animado que aparece 3 segundos cuando el estado del pedido cambia.
 * Se muestra superpuesto en la parte superior de la pantalla de tracking.
 */
@Composable
fun OrderStatusBanner(
    statusStep: OrderStatusStep?,
    modifier: Modifier = Modifier
) {
    var visible     by remember { mutableStateOf(false) }
    var lastStep    by remember { mutableStateOf<OrderStatusStep?>(null) }
    var bannerText  by remember { mutableStateOf("") }

    LaunchedEffect(statusStep) {
        if (statusStep != null && statusStep != lastStep) {
            if (lastStep != null) {
                bannerText = when (statusStep) {
                    OrderStatusStep.CONFIRMED  -> "✅ Pedido confirmado"
                    OrderStatusStep.PREPARING  -> "👨‍🍳 En preparación"
                    OrderStatusStep.READY      -> "✅ Listo para recoger"
                    OrderStatusStep.IN_TRANSIT -> "🛵 Repartidor en camino"
                    OrderStatusStep.DELIVERED  -> "🎉 ¡Pedido entregado!"
                }
                visible = true
                delay(3000)
                visible = false
            }
            lastStep = statusStep
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn() + slideInVertically { -it },
            exit    = fadeOut() + slideOutVertically { -it }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(JagooGreen)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector     = if (statusStep == OrderStatusStep.IN_TRANSIT)
                        Icons.Default.TwoWheeler else Icons.Default.CheckCircle,
                    contentDescription = "Estado",
                    tint            = Color.White,
                    modifier        = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text       = bannerText,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    color      = Color.White
                )
            }
        }
    }
}
