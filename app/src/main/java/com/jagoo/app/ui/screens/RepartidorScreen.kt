package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jagoo.app.ui.theme.*
import com.jagoo.app.viewmodel.OrderResumen
import com.jagoo.app.viewmodel.RepartidorViewModel

/**
 * RepartidorScreen — pantalla principal del repartidor.
 * Muestra: toggle disponible, pedidos disponibles, mis pedidos activos.
 */
@Composable
fun RepartidorScreen(
    repartidorViewModel: RepartidorViewModel = viewModel(),
    onLogout: () -> Unit = {}
) {
    val isAvailable     by repartidorViewModel.isAvailable.collectAsState()
    val disponibles     by repartidorViewModel.pedidosDisponibles.collectAsState()
    val misPedidos      by repartidorViewModel.misPedidos.collectAsState()
    val loading         by repartidorViewModel.loading.collectAsState()
    val error           by repartidorViewModel.error.collectAsState()

    var pedidoDetalle   by remember { mutableStateOf<OrderResumen?>(null) }
    var errorDialog     by remember { mutableStateOf<String?>(null) }

    // Mostrar error en Snackbar
    LaunchedEffect(error) {
        if (error != null) { errorDialog = error; repartidorViewModel.clearError() }
    }

    // Diálogo de error
    errorDialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            title = { Text("Aviso", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold) },
            text  = { Text(msg, fontFamily = PoppinsFontFamily) },
            confirmButton = { TextButton(onClick = { errorDialog = null }) { Text("OK", color = JagooGreen) } }
        )
    }

    // Pantalla detalle
    pedidoDetalle?.let { pedido ->
        RepartidorDetalleScreen(
            pedido          = pedido,
            isMyOrder       = misPedidos.any { it.id == pedido.id },
            onAceptar       = {
                repartidorViewModel.aceptarPedido(pedido.id,
                    onSuccess = { pedidoDetalle = null },
                    onError   = { errorDialog = it }
                )
            },
            onCambiarEstado = { nuevoStatus ->
                repartidorViewModel.cambiarEstado(pedido.id, nuevoStatus)
                pedidoDetalle = null
            },
            onBack          = { pedidoDetalle = null }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(JagooBackground)
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HeaderBottomShape)
                .background(JagooYellow)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TwoWheeler, "Repartidor", tint = JagooGreen, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Panel Repartidor", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, color = JagooGreen)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isAvailable) "Disponible" else "No disponible",
                        fontFamily = PoppinsFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = if (isAvailable) JagooGreen else Color.Gray)
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = isAvailable,
                        onCheckedChange = { repartidorViewModel.toggleDisponible() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JagooGreen),
                        modifier = Modifier.height(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onLogout, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Cerrar sesión", tint = JagooGreen)
                    }
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = JagooGreen)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {

            // ── Mis pedidos activos ─────────────────────────────────────────
            if (misPedidos.isNotEmpty()) {
                item {
                    Text("Mis pedidos activos (${misPedidos.size})",
                        fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = JagooTextPrimary)
                }
                items(misPedidos) { pedido ->
                    PedidoCard(
                        pedido      = pedido,
                        isMyOrder   = true,
                        onClick     = { pedidoDetalle = pedido }
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            // ── Pedidos disponibles ─────────────────────────────────────────
            item {
                Text("Pedidos disponibles (${disponibles.size})",
                    fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = JagooTextPrimary)
            }

            if (disponibles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, null, tint = JagooGreen.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No hay pedidos listos ahora",
                                fontFamily = PoppinsFontFamily, fontSize = 14.sp, color = JagooTextMuted)
                        }
                    }
                }
            } else {
                items(disponibles) { pedido ->
                    PedidoCard(pedido = pedido, isMyOrder = false, onClick = { pedidoDetalle = pedido })
                }
            }

            item {
                TextButton(onClick = { repartidorViewModel.cargarPedidos() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null, tint = JagooGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Actualizar", fontFamily = PoppinsFontFamily, color = JagooGreen, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PedidoCard(pedido: OrderResumen, isMyOrder: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(pedido.restaurantName, fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = JagooTextPrimary)
                pedido.deliveryAddress?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, fontFamily = PoppinsFontFamily, fontSize = 11.sp,
                        color = JagooTextSecondary, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isMyOrder) JagooGreen.copy(alpha = 0.1f) else JagooYellow.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isMyOrder) statusLabel(pedido.status) else "Listo para recoger",
                        fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (isMyOrder) JagooGreen else JagooTextPrimary
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$${String.format("%.2f", pedido.total).replace('.', ',')}",
                    fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = JagooGreen)
                Spacer(Modifier.height(4.dp))
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RepartidorDetalleScreen(
    pedido: OrderResumen,
    isMyOrder: Boolean,
    onAceptar: () -> Unit,
    onCambiarEstado: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(JagooBackground)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(JagooYellow)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = JagooGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Detalle del pedido", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 17.sp, color = JagooGreen)
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow(Icons.Default.Restaurant, "Restaurante", pedido.restaurantName)
                        pedido.deliveryAddress?.let { InfoRow(Icons.Default.LocationOn, "Entregar en", it) }
                        InfoRow(Icons.Default.AttachMoney, "Total", "$${String.format("%.2f", pedido.total).replace('.', ',')}")
                        InfoRow(Icons.Default.Info, "Estado", statusLabel(pedido.status))
                    }
                }
            }

            // Acciones según estado
            item {
                if (!isMyOrder && pedido.status == "ready") {
                    Button(
                        onClick = onAceptar,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = JagooGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Aceptar pedido", fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = JagooGreen)
                    }
                } else if (isMyOrder) {
                    val (nextStatus, nextLabel) = nextAction(pedido.status)
                    if (nextStatus != null) {
                        Button(
                            onClick = { onCambiarEstado(nextStatus) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text(nextLabel, fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = JagooGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, tint = JagooGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, fontFamily = PoppinsFontFamily, fontSize = 10.sp, color = JagooTextSecondary)
            Text(value, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, color = JagooTextPrimary)
        }
    }
}

private fun statusLabel(status: String) = when (status) {
    "ready"      -> "Listo para recoger"
    "picked_up"  -> "Recogido"
    "on_the_way" -> "En camino"
    "delivered"  -> "Entregado"
    else         -> status
}

private fun nextAction(status: String): Pair<String?, String> = when (status) {
    "picked_up"  -> "on_the_way" to "Marcar en camino"
    "on_the_way" -> "delivered"  to "Marcar como entregado"
    else         -> null to ""
}
