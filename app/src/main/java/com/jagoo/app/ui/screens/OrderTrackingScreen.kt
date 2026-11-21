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
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagoo.app.data.ActiveOrder
import com.jagoo.app.data.OrderStatusStep
import com.jagoo.app.data.OrderTimelineItem
import com.jagoo.app.data.remote.OrderDto
import com.jagoo.app.data.remote.SupabaseClient
import com.jagoo.app.data.remote.buildTimeline
import com.jagoo.app.ui.components.CourierCard
import com.jagoo.app.ui.components.JagooHeader
import com.jagoo.app.ui.components.OrderStatusBanner
import com.jagoo.app.ui.components.OrderTimeline
import com.jagoo.app.ui.screens.MapaTrackingScreen
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.JagooYellowBadge
import com.jagoo.app.ui.theme.PillShape
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.CartViewModel
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
fun OrderTrackingScreen(
    cartViewModel: CartViewModel,
    onExploreRestaurants: () -> Unit = {},
    onOrderDelivered: (orderId: String, restaurantName: String, courierName: String?) -> Unit = { _, _, _ -> }
) {
    val activeOrder by cartViewModel.activeOrder.collectAsState()

    // ── Realtime: actualizar el estado del pedido en vivo ─────────────────────
    // Cuando Supabase cambia el status del pedido, la pantalla se actualiza sola
    var liveOrder by remember { mutableStateOf<ActiveOrder?>(null) }

    LaunchedEffect(activeOrder?.id) {
        val orderId = activeOrder?.id ?: return@LaunchedEffect
        val supabase = SupabaseClient.client

        // Escuchar cambios en la tabla orders para este pedido
        val channel = supabase.realtime.channel("order-$orderId")

        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table  = "orders"
        }.onEach { update ->
            // Solo procesar si es el pedido activo
            val recordId = update.record["id"]?.toString()?.trim('"') ?: return@onEach
            if (recordId != orderId) return@onEach
            // Decodificar el nuevo estado
            try {
                val newStatus = update.record["status"]
                    ?.toString()
                    ?.trim('"')
                    ?: return@onEach

                val current = liveOrder ?: activeOrder ?: return@onEach

                val newStep = when (newStatus) {
                    "pending", "accepted" -> OrderStatusStep.CONFIRMED
                    "preparing"           -> OrderStatusStep.PREPARING
                    "ready"               -> OrderStatusStep.READY
                    "picked_up",
                    "on_the_way"          -> OrderStatusStep.IN_TRANSIT
                    "delivered"           -> OrderStatusStep.DELIVERED
                    else                  -> current.statusStep
                }

                // Leer datos del repartidor si están disponibles en el record
                val courierName    = update.record["courier_name"]?.toString()?.trim('"')
                val courierVehicle = update.record["courier_vehicle"]?.toString()?.trim('"')
                val courierPlate   = update.record["courier_plate"]?.toString()?.trim('"')
                val courierInfo    = if (!courierName.isNullOrBlank())
                    com.jagoo.app.data.CourierInfo(
                        name    = courierName,
                        vehicle = courierVehicle ?: "",
                        plate   = courierPlate   ?: ""
                    ) else current.courier

                liveOrder = current.copy(
                    statusStep    = newStep,
                    timelineItems = buildTimeline(newStatus),
                    courier       = courierInfo
                )

                // Disparar calificación cuando se entrega el pedido
                if (newStep == OrderStatusStep.DELIVERED) {
                    onOrderDelivered(
                        orderId,
                        current.restaurantName,
                        courierInfo?.name
                    )
                }
            } catch (_: Exception) {}
        }.launchIn(this)

        channel.subscribe()
    }

    // Usar liveOrder si existe (tiene el status más reciente), si no usar activeOrder
    val displayOrder = liveOrder ?: activeOrder

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        JagooHeader()

        // Banner animado de cambio de estado
        OrderStatusBanner(statusStep = displayOrder?.statusStep)

        if (displayOrder == null) {
            // ── Estado vacío: sin pedido activo ──────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(JagooSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(JagooYellow.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "Sin pedido",
                        tint = JagooGreen,
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "No tienes ningún pedido activo",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = JagooTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Cuando realices un pedido, podrás seguir su estado en tiempo real aquí.",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = JagooTextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onExploreRestaurants,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "Ver Restaurantes",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = JagooGreen
                    )
                }
            }
        } else {
            val order = displayOrder

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = JagooSpacing.md, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Tarjeta del restaurante ───────────────────────────────────
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
                                text = "Pedido #${order.id} · ${order.items.size} platos",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // ── Hero: tiempo + estado ─────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    horizontalArrangement = Arrangement.spacedBy(JagooSpacing.sm)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(90.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(JagooYellow)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

                    // Estado en texto según statusStep
                    val statusLabel = when (order.statusStep) {
                        OrderStatusStep.CONFIRMED  -> "Confirmado"
                        OrderStatusStep.PREPARING  -> "En preparación"
                        OrderStatusStep.READY      -> "Listo para recoger"
                        OrderStatusStep.IN_TRANSIT -> "En camino"
                        OrderStatusStep.DELIVERED  -> "Entregado"
                    }
                    val statusDesc = when (order.statusStep) {
                        OrderStatusStep.CONFIRMED  -> "El restaurante confirmó tu pedido"
                        OrderStatusStep.PREPARING  -> "Tu pedido se está preparando"
                        OrderStatusStep.READY      -> "Esperando que el repartidor recoja"
                        OrderStatusStep.IN_TRANSIT -> "El repartidor está en camino"
                        OrderStatusStep.DELIVERED  -> "¡Tu pedido fue entregado!"
                    }

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = JagooGreen),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TwoWheeler,
                                    contentDescription = "Estado",
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
                                    text = statusDesc,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Llegada aprox. en ~${order.estimatedMinutes} min",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // ── Timeline ──────────────────────────────────────────────────
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

                // ── Mapa en tiempo real (solo cuando está en camino) ──────────
                if (order.statusStep == OrderStatusStep.IN_TRANSIT) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MapaTrackingScreen(
                            cartViewModel = cartViewModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ── Repartidor ────────────────────────────────────────────────
                if (order.courier != null) {
                    CourierCard(
                        name        = order.courier.name.ifBlank { "Repartidor asignado" },
                        vehicleInfo = listOfNotNull(
                            order.courier.vehicle.ifBlank { null },
                            order.courier.plate.ifBlank { null }
                        ).joinToString(" · ").ifBlank { "Jagoo Delivery" }
                    )
                }

                // ── Resumen del pedido ────────────────────────────────────────
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
                                contentDescription = "Detalle",
                                tint = JagooGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(JagooSpacing.sm))
                            Text(
                                text = "${order.items.size} platos · $${
                                    String.format("%.2f", order.total).replace('.', ',')
                                } · Efectivo",
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
                                contentDescription = null,
                                tint = JagooGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
