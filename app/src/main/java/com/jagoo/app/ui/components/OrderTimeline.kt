package com.jagoo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.HorizontalDivider  // Reemplaza el Divider deprecado de Material3 1.1+
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.data.MockData
import com.jagoo.app.data.OrderTimelineItem
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.PoppinsFontFamily

@Composable
fun OrderTimeline(
    items: List<OrderTimelineItem> = MockData.orderTimelineItems
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Gráfico horizontal: Tienda → Repartidor → Casa
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(JagooGreen.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = "Tienda",
                        tint = JagooGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Jagoo",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = JagooTextPrimary
                )
            }

            // Línea de conexión activa (ya recorrida)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(JagooGreen)
                    .padding(horizontal = 8.dp)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(JagooYellow)
                ) {
                    Icon(
                        imageVector = Icons.Default.TwoWheeler,
                        contentDescription = "Repartidor",
                        tint = JagooGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Línea de conexión pendiente (aún no recorrida)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(Color.LightGray)
                    .padding(horizontal = 8.dp)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Tu casa",
                        tint = JagooGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tu casa",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = JagooTextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Lista vertical de pasos del pedido
        Column(
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            items.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    // Indicador de estado del paso
                    when {
                        item.isCompleted -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(JagooGreen)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        item.isCurrent -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(JagooYellow)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(JagooGreen)
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color.LightGray, CircleShape)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = if (item.isCurrent) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            color = when {
                                item.isCurrent -> JagooTextPrimary
                                item.isCompleted -> JagooTextPrimary
                                else -> JagooTextMuted
                            }
                        )
                        Text(
                            text = " · ${item.time}",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = if (item.isCurrent) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            color = if (item.isCurrent) JagooTextPrimary else JagooTextMuted
                        )
                    }
                }
            }
        }
    }
}
