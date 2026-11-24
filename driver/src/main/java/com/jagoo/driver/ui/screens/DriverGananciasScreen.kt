package com.jagoo.driver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.driver.ui.theme.*
import com.jagoo.driver.viewmodel.DriverRepartidorViewModel

@Composable
fun DriverGananciasScreen(
    repartidorViewModel: DriverRepartidorViewModel,
    onBackClick: () -> Unit
) {
    val entregas  by repartidorViewModel.historialEntregas.collectAsState()
    val earnings  by repartidorViewModel.earnings.collectAsState()
    val loading   by repartidorViewModel.loading.collectAsState()

    // Selector de período
    var selectedPeriod by remember { mutableStateOf("all") }
    val periods = listOf("today" to "Hoy", "week" to "Semana", "month" to "Mes", "all" to "Total")

    // Cargar ganancias al entrar y cuando cambia el período
    LaunchedEffect(selectedPeriod) {
        repartidorViewModel.cargarGanancias(selectedPeriod)
    }

    // Usar datos del backend si disponibles, si no calcular local
    val totalEntregas  = earnings?.totalDeliveries ?: entregas.size.toLong()
    val tarifaEntrega  = earnings?.feePerDelivery  ?: 1.50
    val gananciaTotal  = earnings?.totalEarnings   ?: (entregas.size * 1.50)

    Column(
        modifier = Modifier.fillMaxSize().background(JagooBackground)
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = JagooGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Default.Payments, null, tint = JagooGreen, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Ganancias e Historial",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = JagooGreen,
                modifier = Modifier.weight(1f)
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = JagooGreen,
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = {
                    repartidorViewModel.cargarGanancias(selectedPeriod)
                    repartidorViewModel.cargarPedidos()
                }) {
                    Icon(Icons.Default.Refresh, "Actualizar", tint = JagooGreen, modifier = Modifier.size(20.dp))
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Selector de período
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    periods.forEach { (value, label) ->
                        FilterChip(
                            selected = selectedPeriod == value,
                            onClick = { selectedPeriod = value },
                            label = {
                                Text(
                                    label,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedPeriod == value) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JagooGreen,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Tarjeta resumen
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = JagooGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Ganancias",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$${String.format("%.2f", gananciaTotal).replace('.', ',')}",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp,
                            color = JagooYellow
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Entregas completadas",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    "$totalEntregas",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Tarifa por entrega",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    "$${String.format("%.2f", tarifaEntrega).replace('.', ',')}",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Historial de Entregas (${entregas.size})",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = JagooTextPrimary
                )
            }

            if (entregas.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = JagooGreen.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Aún no tienes entregas completadas",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 14.sp,
                                color = JagooTextMuted
                            )
                        }
                    }
                }
            } else {
                items(entregas) { pedido ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    pedido.restaurantName,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = JagooTextPrimary
                                )
                                Text(
                                    "Pedido #${pedido.id.take(8)}",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = JagooTextSecondary
                                )
                                if (!pedido.deliveryAddress.isNullOrBlank()) {
                                    Text(
                                        pedido.deliveryAddress,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 10.sp,
                                        color = JagooTextMuted,
                                        maxLines = 1
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "+$${String.format("%.2f", tarifaEntrega).replace('.', ',')}",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = JagooGreen
                                )
                                Text(
                                    "Comisión",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
