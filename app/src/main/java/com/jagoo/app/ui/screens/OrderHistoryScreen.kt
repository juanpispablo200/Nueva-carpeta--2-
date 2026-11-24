package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagoo.app.data.OrderStatusStep
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooRed
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooTextSecondary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.JagooHeaderBrush
import com.jagoo.app.ui.theme.PillShape
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.CartViewModel

@Composable
fun OrderHistoryScreen(
    cartViewModel: CartViewModel,
    onBackClick: () -> Unit,
    onReorder: (restaurantId: String) -> Unit = {}
) {
    val history        by cartViewModel.orderHistory.collectAsState()
    val historyLoading by cartViewModel.historyLoading.collectAsState()
    val historyError   by cartViewModel.historyError.collectAsState()

    LaunchedEffect(Unit) {
        cartViewModel.refreshOrderHistory()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JagooHeaderBrush)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar",
                    tint = JagooGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.History, "Historial",
                tint = JagooGreen, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Historial de Pedidos",
                fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 18.sp, color = JagooGreen)
        }

        // Estado de carga
        if (historyLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = JagooGreen)
            }
            return@Column
        }

        // Estado de error con retry
        if (historyError != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(JagooSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("⚠️", fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text(historyError!!, fontFamily = PoppinsFontFamily,
                    fontSize = 15.sp, color = JagooTextMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { cartViewModel.refreshOrderHistory() },
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = JagooGreen)
                ) {
                    Text("Reintentar", color = Color.White,
                        fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
                }
            }
            return@Column
        }

        // Estado vacío
        if (history.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(JagooSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(100.dp).clip(CircleShape)
                        .background(JagooYellow.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.Restaurant, "Sin historial",
                        tint = JagooGreen, modifier = Modifier.size(50.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Aún no tienes pedidos anteriores",
                    fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, color = JagooTextPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Los pedidos realizados aparecerán aquí.",
                    fontFamily = PoppinsFontFamily, fontSize = 13.sp,
                    color = JagooTextMuted, textAlign = TextAlign.Center)
            }
            return@Column
        }

        // Lista de pedidos
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(history, key = { it.id }) { order ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = order.restaurantLogoUrl,
                                    contentDescription = order.restaurantName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(order.restaurantName,
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp, color = JagooTextPrimary)
                                    Text("Pedido ${com.jagoo.app.data.orderNumber(order.id)}",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp, color = JagooTextSecondary)
                                }
                            }

                            // Badge con estado real del pedido
                            val (badgeText, badgeColor) = when (order.statusStep) {
                                OrderStatusStep.CONFIRMED  -> "Confirmado" to JagooGreen
                                OrderStatusStep.PREPARING  -> "En cocina" to Color(0xFFE65100)
                                OrderStatusStep.READY      -> "Listo" to Color(0xFF7B1FA2)
                                OrderStatusStep.IN_TRANSIT -> "En camino" to Color(0xFF1565C0)
                                OrderStatusStep.DELIVERED  -> "Entregado" to JagooGreen
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(badgeColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(badgeText,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp, color = badgeColor)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Total: $${String.format("%.2f", order.total).replace('.', ',')}",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp, color = JagooGreen
                                )
                                Text(
                                    "${order.items.size} plato${if (order.items.size != 1) "s" else ""}",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp, color = JagooTextMuted
                                )
                            }

                            // "Volver a Pedir" funcional — navega al restaurante
                            if (order.statusStep == OrderStatusStep.DELIVERED) {
                                Button(
                                    onClick = { onReorder(order.restaurantId) },
                                    shape = PillShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Volver a Pedir",
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp, color = JagooGreen)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
