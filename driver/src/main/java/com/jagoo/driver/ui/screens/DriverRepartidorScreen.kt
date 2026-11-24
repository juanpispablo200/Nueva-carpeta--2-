package com.jagoo.driver.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.driver.data.remote.DriverOrderDto
import com.jagoo.driver.util.PhoneRules
import com.jagoo.driver.ui.theme.*
import com.jagoo.driver.viewmodel.DriverRepartidorViewModel
import kotlinx.coroutines.delay

@Composable
fun DriverRepartidorScreen(
    repartidorViewModel: DriverRepartidorViewModel,
    onNavigateToGanancias: () -> Unit,
    onLogout: () -> Unit
) {
    val isAvailable     by repartidorViewModel.isAvailable.collectAsState()
    val disponibles     by repartidorViewModel.pedidosDisponibles.collectAsState()
    val misPedidos      by repartidorViewModel.misPedidos.collectAsState()
    val loading         by repartidorViewModel.loading.collectAsState()
    val error           by repartidorViewModel.error.collectAsState()

    var pedidoOferta    by remember { mutableStateOf<DriverOrderDto?>(null) }
    var pedidoDetalle   by remember { mutableStateOf<DriverOrderDto?>(null) }
    var errorDialog     by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(error) {
        if (error != null) { errorDialog = error; repartidorViewModel.clearError() }
    }

    // Modal de Nueva Oferta con contador de 30s
    LaunchedEffect(disponibles, isAvailable) {
        if (isAvailable && disponibles.isNotEmpty() && misPedidos.isEmpty()) {
            pedidoOferta = disponibles.first()
        }
    }

    errorDialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            title = { Text("Aviso de Entrega", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold) },
            text  = { Text(msg, fontFamily = PoppinsFontFamily) },
            confirmButton = { TextButton(onClick = { errorDialog = null }) { Text("OK", color = JagooGreen) } }
        )
    }

    // Modal Nueva Oferta (Contador de 30 segundos)
    pedidoOferta?.let { oferta ->
        NuevaOfertaModal(
            pedido = oferta,
            onAceptar = {
                repartidorViewModel.aceptarPedido(oferta.id,
                    onSuccess = { pedidoOferta = null },
                    onError   = { msg -> errorDialog = msg; pedidoOferta = null }
                )
            },
            onRechazar = { pedidoOferta = null }
        )
    }

    // Pantalla de Detalle de Entrega Activa
    pedidoDetalle?.let { pedido ->
        DriverDetalleScreen(
            pedido = pedido,
            isMyOrder = misPedidos.any { it.id == pedido.id },
            onAceptar = {
                repartidorViewModel.aceptarPedido(pedido.id,
                    onSuccess = { pedidoDetalle = null },
                    onError   = { msg -> errorDialog = msg; pedidoDetalle = null }
                )
            },
            onCambiarEstado = { nuevoStatus ->
                repartidorViewModel.cambiarEstado(pedido.id, nuevoStatus)
            },
            onConfirmarEdad = {
                repartidorViewModel.confirmarEdad(pedido.id)
                pedidoDetalle = null
            },
            onBack = { pedidoDetalle = null }
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
                .background(JagooHeaderBrush)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TwoWheeler, "Driver", tint = JagooGreen, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Jagoo Driver", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = JagooGreen)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isAvailable) "Disponible" else "No disponible",
                        fontFamily = PoppinsFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = if (isAvailable) JagooGreen else Color.Gray
                    )
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = isAvailable,
                        onCheckedChange = { repartidorViewModel.toggleDisponible() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JagooGreen),
                        modifier = Modifier.height(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onNavigateToGanancias, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Payments, "Ganancias", tint = JagooGreen)
                    }
                    IconButton(onClick = onLogout, modifier = Modifier.size(48.dp)) {
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
            if (misPedidos.isNotEmpty()) {
                item {
                    Text("Entregas Activas (${misPedidos.size})", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = JagooTextPrimary)
                }
                items(misPedidos) { pedido ->
                    DriverPedidoCard(pedido = pedido, isMyOrder = true, onClick = { pedidoDetalle = pedido })
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            item {
                Text("Ofertas Disponibles (${disponibles.size})", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = JagooTextPrimary)
            }

            if (disponibles.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, null, tint = JagooGreen.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No hay pedidos pendientes en tu zona", fontFamily = PoppinsFontFamily, fontSize = 14.sp, color = JagooTextMuted)
                        }
                    }
                }
            } else {
                items(disponibles) { pedido ->
                    DriverPedidoCard(pedido = pedido, isMyOrder = false, onClick = { pedidoDetalle = pedido })
                }
            }

            item {
                TextButton(onClick = { repartidorViewModel.cargarPedidos() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null, tint = JagooGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Actualizar lista", fontFamily = PoppinsFontFamily, color = JagooGreen, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun NuevaOfertaModal(
    pedido: DriverOrderDto,
    onAceptar: () -> Unit,
    onRechazar: () -> Unit
) {
    var timeLeft by remember { mutableIntStateOf(30) }

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000L)
            timeLeft--
        }
        onRechazar()
    }

    AlertDialog(
        onDismissRequest = onRechazar,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, null, tint = JagooGreen)
                Spacer(Modifier.width(8.dp))
                Text("¡Nueva Oferta de Entrega!", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Negocio: ${pedido.restaurantName}", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                pedido.zoneName?.let {
                    Text("Zona: $it", fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = JagooTextSecondary)
                }
                Text(gananciaTexto(pedido), fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = JagooGreen)
                Text("La direcci?n se muestra al aceptar el pedido.", fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = JagooTextSecondary)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { timeLeft / 30f },
                    modifier = Modifier.fillMaxWidth(),
                    color = JagooGreen,
                    trackColor = Color.LightGray.copy(alpha = 0.4f)
                )
                Text("Tiempo restante: $timeLeft s", fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = onAceptar,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = JagooYellow)
            ) {
                Text("Aceptar ($timeLeft s)", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, color = JagooGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onRechazar) {
                Text("Ignorar", fontFamily = PoppinsFontFamily, color = Color.Gray)
            }
        }
    )
}

@Composable
private fun DriverPedidoCard(pedido: DriverOrderDto, isMyOrder: Boolean, onClick: () -> Unit) {
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
                Text(pedido.restaurantName, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = JagooTextPrimary)
                (if (isMyOrder) pedido.deliveryAddress else pedido.zoneName?.let { "Zona: $it" })?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = JagooTextSecondary, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isMyOrder) JagooGreen.copy(alpha = 0.1f) else JagooYellow.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isMyOrder) statusLabel(pedido.status) else "Oferta disponible",
                        fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 10.sp, color = if (isMyOrder) JagooGreen else JagooTextPrimary
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (isMyOrder) "$${String.format("%.2f", pedido.total).replace('.', ',')}" else gananciaTexto(pedido),
                    fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = JagooGreen
                )
                Spacer(Modifier.height(4.dp))
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DriverDetalleScreen(
    pedido: DriverOrderDto,
    isMyOrder: Boolean,
    onAceptar: () -> Unit,
    onCambiarEstado: (String) -> Unit,
    onConfirmarEdad: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current

    fun abrirMapa(direccion: String) {
        try {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(direccion)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(direccion)}")))
            }
        } catch (_: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(direccion)}")))
        }
    }

    fun llamarTelefono(numero: String) {
        val dial = PhoneRules.dialable(numero) ?: return   // nunca se marca un n?mero falso
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dial")))
        } catch (_: Exception) {}
    }

    Column(modifier = Modifier.fillMaxSize().background(JagooBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(JagooYellow).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = JagooGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Detalle del pedido #${pedido.id}", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = JagooGreen)
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            InfoRow(Icons.Default.Restaurant, "Negocio", pedido.restaurantName)
                            PhoneRules.dialable(pedido.restaurantPhone)?.let { phone ->
                                IconButton(onClick = { llamarTelefono(phone) }, modifier = Modifier.size(48.dp).clip(CircleShape).background(JagooYellow.copy(alpha = 0.4f))) {
                                    Icon(Icons.Default.Phone, "Llamar restaurante", tint = JagooGreen, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            if (isMyOrder) InfoRow(Icons.Default.Person, "Cliente", pedido.customerName?.takeIf { it.isNotBlank() } ?: "Cliente (sin nombre registrado)")
                            PhoneRules.dialable(pedido.customerPhone)?.takeIf { isMyOrder }?.let { phone ->
                                IconButton(onClick = { llamarTelefono(phone) }, modifier = Modifier.size(48.dp).clip(CircleShape).background(JagooYellow.copy(alpha = 0.4f))) {
                                    Icon(Icons.Default.Phone, "Llamar cliente", tint = JagooGreen, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                        pedido.deliveryAddress?.let { address ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                InfoRow(Icons.Default.LocationOn, "Entregar en", address)
                                Button(
                                    onClick = { abrirMapa(address) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = JagooGreen),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Default.Map, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Mapa", fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                        if (isMyOrder) InfoRow(Icons.Default.AttachMoney, "Total", "$${String.format("%.2f", pedido.total).replace('.', ',')}")
                        else InfoRow(Icons.Default.AttachMoney, "Ganancia", gananciaTexto(pedido).removePrefix("Ganas "))
                        InfoRow(Icons.Default.Info, "Estado", statusLabel(pedido.status))
                        if (pedido.items.isNotEmpty()) {
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                            Text("Productos", fontFamily = PoppinsFontFamily, fontSize = 10.sp, color = JagooTextSecondary)
                            pedido.items.forEach { it2 ->
                                Text("${it2.quantity} x ${it2.dishName}", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = JagooTextPrimary)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                if (!isMyOrder && (pedido.status == "ready")) {
                    Button(
                        onClick = onAceptar,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = JagooGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Aceptar pedido", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = JagooGreen)
                    }
                } else if (isMyOrder) {
                    if (pedido.ageCheckRequired) {
                        val verified = pedido.ageVerifiedAt != null
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (verified) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)),
                            modifier = Modifier.fillMaxWidth().semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = if (verified) "Edad verificada" else "Alerta: verificar la cédula del cliente al entregar"
                            }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    if (verified) "✅ Cédula verificada" else "⚠️ Verificar cédula al entregar",
                                    fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp
                                )
                                if (!verified) {
                                    Text("Este pedido tiene productos solo para mayores de 18. No lo entregues sin ver la cédula.", fontFamily = PoppinsFontFamily, fontSize = 12.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = onConfirmarEdad,
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = JagooGreen),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    ) { Text("Ya verifiqué la cédula", color = Color.White, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (pedido.paymentMethod == "cash") {
                        Text(
                            "💵 Cobrar en efectivo: $${String.format("%.2f", pedido.total)}" +
                                (pedido.changeDue?.let { " · Cambio a devolver: $${String.format("%.2f", it)}" } ?: ""),
                            fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = JagooGreen
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    val (nextStatus, nextLabel) = nextAction(pedido.status)
                    if (nextStatus != null) {
                        Button(
                            onClick = { onCambiarEstado(nextStatus) },
                            enabled = !(nextStatus == "delivered" && pedido.ageCheckRequired && pedido.ageVerifiedAt == null),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text(nextLabel, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = JagooGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, label, tint = JagooGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, fontFamily = PoppinsFontFamily, fontSize = 10.sp, color = JagooTextSecondary)
            Text(value, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = JagooTextPrimary)
        }
    }
}

private fun gananciaTexto(p: DriverOrderDto): String =
    p.driverAmount?.let { "Ganas $" + String.format("%.2f", it).replace('.', ',') } ?: "Ganancia por definir"

private fun statusLabel(status: String) = when (status) {
    "pending"    -> "Pendiente"
    "accepted"   -> "Aceptado"
    "preparing"  -> "En preparación"
    "ready"      -> "Listo para recoger"
    "picked_up"  -> "Recogido"
    "on_the_way" -> "En camino"
    "delivered"  -> "Entregado"
    "cancelled"  -> "Cancelado"
    else         -> status
}

private fun nextAction(status: String): Pair<String?, String> = when (status) {
    "ready"      -> "picked_up"  to "Marcar como recogido 📦"
    "picked_up"  -> "on_the_way" to "Marcar en camino 🚴"
    "on_the_way" -> "delivered"  to "Marcar como entregado ✅"
    else         -> null to ""
}
