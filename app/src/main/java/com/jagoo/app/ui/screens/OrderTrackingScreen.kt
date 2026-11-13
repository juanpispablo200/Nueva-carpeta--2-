package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagoo.app.data.ActiveOrder
import com.jagoo.app.data.OrderStatusStep
import com.jagoo.app.data.RestaurantData
import com.jagoo.app.ui.components.CourierCard
import com.jagoo.app.ui.components.JagooHeader
import com.jagoo.app.ui.components.OrderTimeline
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.JagooYellowBadge
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.CartViewModel

@Composable
fun OrderTrackingScreen(
    cartViewModel: CartViewModel
) {
    val activeOrder by cartViewModel.activeOrder.collectAsState()

    // Usar el pedido real si existe, si no mostrar el mock de demostración
    val order: ActiveOrder = activeOrder ?: RestaurantData.sampleActiveOrder

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        JagooHeader()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = JagooSpacing.md, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tarjeta del restaurante del pedido
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(JagooYellow)
                    ) {
                        AsyncImage(
                            model = order.restaurantLogoUrl,
                            contentDescription = order.restaurantName,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = order.restaurantName,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = JagooTextPrimary
                        )
                        Text(
                            text = "Pedido #${order.id} · ${order.items.size} ítem${if (order.items.size != 1) "s" else ""}",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Status Section: tiempo estimado + estado actual
            val statusLabel = when (order.statusStep) {
                OrderStatusStep.CONFIRMED -> "Confirmado"
                OrderStatusStep.PREPARING -> "Preparando"
                OrderStatusStep.IN_TRANSIT -> "En camino"
                OrderStatusStep.DELIVERED -> "Entregado"
            }

            val statusDescription = when (order.statusStep) {
                OrderStatusStep.CONFIRMED -> "El restaurante confirmó tu pedido"
                OrderStatusStep.PREPARING -> "El restaurante está preparando tu pedido"
                OrderStatusStep.IN_TRANSIT -> "El repartidor va hacia tu ubicación"
                OrderStatusStep.DELIVERED -> "Tu pedido fue entregado"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(JagooSpacing.sm)
            ) {
                // Tiempo estimado
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(90.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(JagooYellow)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${order.estimatedMinutes}",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = JagooGreen,
                            lineHeight = 32.sp
                        )
                        Text(
                            text = "min",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = JagooGreen
                        )
                    }
                }

                // Estado actual
                val cardColor = if (order.statusStep == OrderStatusStep.DELIVERED)
                    JagooGreen.copy(alpha = 0.85f) else JagooGreen

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (order.statusStep == OrderStatusStep.DELIVERED)
                                    Icons.Default.CheckCircle else Icons.Default.TwoWheeler,
                                contentDescription = statusLabel,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(JagooYellowBadge)
                                    .padding(horizontal = JagooSpacing.sm, vertical = 2.dp)
                            ) {
                                Text(
                                    text = statusLabel,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = JagooGreen
                                )
                            }
                        }

                        Column {
                            Text(
                                text = statusDescription,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            if (order.statusStep == OrderStatusStep.IN_TRANSIT ||
                                order.statusStep == OrderStatusStep.PREPARING
                            ) {
                                Text(
                                    text = "Llegada aprox. en ${order.estimatedMinutes} min",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Timeline del pedido
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    OrderTimeline(items = order.timelineItems)
                }
            }

            // Tarjeta del repartidor
            CourierCard(
                name = order.courierName,
                vehicleInfo = order.courierVehicle
            )

            // Resumen de la orden
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = "Detalle del pedido",
                            tint = JagooGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(JagooSpacing.sm))
                        Text(
                            text = "${order.items.size} plato${if (order.items.size != 1) "s" else ""}" +
                                    " · $${String.format("%.2f", order.total).replace('.', ',')} · Efectivo",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = JagooTextPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ver detalle",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = JagooGreen
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Ver detalle del pedido",
                            tint = JagooGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
