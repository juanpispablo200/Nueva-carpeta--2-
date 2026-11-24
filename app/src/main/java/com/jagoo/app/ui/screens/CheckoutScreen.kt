package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooRed
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.JagooHeaderBrush
import com.jagoo.app.ui.theme.PillShape
import com.jagoo.app.ui.theme.PoppinsFontFamily
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.jagoo.app.BuildConfig
import com.jagoo.app.data.CashRules
import com.jagoo.app.ui.components.ErrorCard
import com.jagoo.app.ui.components.NoticeCard
import com.jagoo.app.viewmodel.OrderPlacement
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.ProductViewModel

/**
 * CheckoutScreen — Pantalla de confirmación antes de confirmar el pedido.
 * Resume: dirección de entrega, método de pago, cupones, total.
 * El usuario confirma aquí y recién entonces se crea el pedido en BD.
 */
@Composable
fun CheckoutScreen(
    cartViewModel: CartViewModel,
    productViewModel: ProductViewModel,
    onBackClick: () -> Unit,
    onOrderPlaced: (orderId: String, total: Double, isTransfer: Boolean) -> Unit
) {
    val cartItems by cartViewModel.cartItems.collectAsState()
    val subtotal  = cartViewModel.getSubtotal()
    val fee       = cartViewModel.deliveryFee

    val couponCode     by cartViewModel.couponCode.collectAsState()
    val couponApplied  by cartViewModel.couponApplied.collectAsState()
    val couponDiscount by cartViewModel.couponDiscount.collectAsState()
    val couponError    by cartViewModel.couponError.collectAsState()
    val couponLoading  by cartViewModel.couponLoading.collectAsState()

    val promoState by cartViewModel.promo.collectAsState()
    val promoDiscount = cartViewModel.promoDiscount()
    val total = cartViewModel.getTotal()

    val addresses      by productViewModel.addresses.collectAsState()
    val primaryAddress = addresses.find { it.isPrimary }

    // Cargar dirección primaria al entrar
    LaunchedEffect(primaryAddress) {
        if (primaryAddress != null) {
            cartViewModel.setDeliveryAddress(primaryAddress.fullAddress)
        } else {
            cartViewModel.setDeliveryAddress("")
        }
    }

    // Método de pago: efectivo o transferencia
    var paymentMethod by rememberSaveable { mutableStateOf("cash") }
    var cashText by rememberSaveable { mutableStateOf("") }
    var ageConfirmed by rememberSaveable { mutableStateOf(false) }
    val tendered = CashRules.parseAmount(cashText)
    val change = tendered?.let { CashRules.change(total, it) }
    val cashError = paymentMethod == "cash" && cashText.isNotBlank() && change == null

    val orderState     by cartViewModel.orderState.collectAsState()
    val zones          by cartViewModel.zones.collectAsState()
    val selectedZone   by cartViewModel.selectedZone.collectAsState()
    val minOrder       by cartViewModel.minOrder.collectAsState()
    val prescriptionUri by cartViewModel.prescriptionUri.collectAsState()

    val needsPrescription = cartViewModel.cartRequiresPrescription
    val hasAgeRestricted  = cartViewModel.cartHasAgeRestricted
    val belowMinimum      = subtotal < minOrder
    val zoneMissing       = zones.isNotEmpty() && selectedZone == null
    val loading           = orderState is OrderPlacement.Loading

    val prescriptionPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) cartViewModel.setPrescriptionUri(uri) }

    LaunchedEffect(Unit) {
        cartViewModel.resetOrderState()
        cartViewModel.loadCheckoutConfig()
    }

    // Pedido creado: se usa el id REAL que devolvió el servidor
    LaunchedEffect(orderState) {
        val state = orderState
        if (state is OrderPlacement.Placed) {
            onOrderPlaced(state.order.id, state.order.total, state.order.paymentMethod == "transfer")
            cartViewModel.resetOrderState()
        }
    }

    val canConfirm = primaryAddress != null && cartItems.isNotEmpty() && !loading &&
        !belowMinimum && !zoneMissing &&
        (!hasAgeRestricted || ageConfirmed) &&
        (paymentMethod == "transfer" || (tendered != null && change != null)) &&
        (!needsPrescription || prescriptionUri != null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JagooHeaderBrush)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = JagooGreen,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                imageVector = Icons.Default.Receipt,
                contentDescription = "Checkout",
                tint = JagooGreen,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Confirmar Pedido",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = JagooGreen
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Dirección de entrega ──────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Dirección",
                            tint = JagooGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dirección de entrega",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = JagooTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (primaryAddress != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = primaryAddress.label,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = JagooTextPrimary
                                )
                                Text(
                                    text = primaryAddress.fullAddress,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.sp,
                                    color = JagooTextMuted
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Cambiar",
                                tint = JagooGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "No tienes una dirección configurada. Ve a Perfil → Mis Direcciones.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            color = JagooRed
                        )
                    }
                }
            }

            // ── Zona de entrega (si el admin configuró zonas) ─────────────────
            if (zones.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp).selectableGroup()) {
                        Text(
                            text = "Zona de entrega",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = JagooTextPrimary
                        )
                        zones.forEach { zone ->
                            ChoiceRow(
                                title = zone.name,
                                subtitle = "Envío $${String.format("%.2f", zone.fee)}",
                                selected = selectedZone?.id == zone.id,
                                onSelect = { cartViewModel.selectZone(zone) }
                            )
                        }
                        if (zoneMissing) {
                            Text(
                                text = "Elige tu zona para ver el costo de envío.",
                                fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = JagooRed
                            )
                        }
                    }
                }
            }

            // ── Método de pago ────────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp).selectableGroup()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Payment,
                            contentDescription = null,
                            tint = JagooGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Método de pago",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = JagooTextPrimary
                        )
                    }

                    ChoiceRow(
                        title = "Efectivo al recibir",
                        subtitle = "Pagas al repartidor cuando llega tu pedido",
                        selected = paymentMethod == "cash",
                        onSelect = { paymentMethod = "cash" }
                    )
                    ChoiceRow(
                        title = "Transferencia bancaria",
                        subtitle = "Subes el comprobante y lo verificamos. Tienes 30 minutos para subirlo.",
                        selected = paymentMethod == "transfer",
                        onSelect = { paymentMethod = "transfer"; cashText = "" }
                    )

                    if (paymentMethod == "cash") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cashText,
                            onValueChange = { cashText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(8) },
                            label = { Text("¿Con cuánto pagas?") },
                            placeholder = { Text("Ej: 20.00") },
                            prefix = { Text("$") },
                            isError = cashError,
                            supportingText = {
                                when {
                                    cashError -> Text("Debe ser al menos $${String.format("%.2f", total)} (el total del pedido).")
                                    change != null -> Text("Tu cambio será $${String.format("%.2f", change)}.")
                                    else -> Text("Escribe el billete con el que vas a pagar para que llevemos cambio.")
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ── Avisos: edad y receta ─────────────────────────────────────────
            if (hasAgeRestricted) {
                NoticeCard(
                    label = "Aviso de edad",
                    message = "Este pedido incluye productos solo para mayores de 18 años. El repartidor te pedirá la cédula al entregar."
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(
                            value = ageConfirmed,
                            role = Role.Checkbox,
                            onValueChange = { ageConfirmed = it }
                        )
                        .semantics { contentDescription = "Confirmo que soy mayor de 18 años y mostraré mi cédula al recibir el pedido" }
                ) {
                    Checkbox(checked = ageConfirmed, onCheckedChange = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Confirmo que soy mayor de 18 años y mostraré mi cédula al recibir el pedido.",
                        fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = JagooTextPrimary
                    )
                }
                if (!ageConfirmed) {
                    Text(
                        text = "Marca la casilla para poder confirmar el pedido.",
                        fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = JagooRed,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
            if (needsPrescription) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Receta médica",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = JagooTextPrimary
                        )
                        NoticeCard(
                            label = "Aviso de receta",
                            message = "Tu pedido tiene productos que requieren receta. La farmacia la revisará antes de aceptar el pedido. Si no la aprueba, el pedido se cancela."
                        )
                        OutlinedButton(
                            onClick = { prescriptionPicker.launch("image/*") },
                            shape = PillShape,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = if (prescriptionUri == null) "Subir foto de la receta" else "Cambiar foto de la receta",
                                fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, color = JagooGreen
                            )
                        }
                        Text(
                            text = if (prescriptionUri == null) "Falta la foto de la receta."
                                   else "Receta seleccionada.",
                            fontFamily = PoppinsFontFamily, fontSize = 12.sp,
                            color = if (prescriptionUri == null) JagooRed else JagooGreen,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            }

            // ── Cupón de descuento ────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "¿Tienes un cupón?",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = JagooTextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (couponApplied) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(JagooGreen.copy(alpha = 0.1f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Aplicado",
                                tint = JagooGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cupón aplicado: $couponCode",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = JagooGreen
                                )
                                Text(
                                    text = "Descuento: -$${String.format("%.2f", couponDiscount)}",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.sp,
                                    color = JagooGreen
                                )
                            }
                            TextButton(onClick = { cartViewModel.removeCoupon() }) {
                                Text("Quitar", color = JagooRed, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = couponCode,
                                onValueChange = { cartViewModel.setCouponCode(it) },
                                label = { Text("Código") },
                                placeholder = { Text("BIENVENIDO") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                enabled = !couponLoading
                            )
                            Button(
                                onClick = { cartViewModel.applyCoupon() },
                                shape = PillShape,
                                colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
                                enabled = !couponLoading && couponCode.isNotBlank(),
                                modifier = Modifier
                                    .height(56.dp)
                                    .align(Alignment.CenterVertically)
                            ) {
                                if (couponLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = JagooGreen,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Aplicar", color = JagooGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (couponError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = couponError!!,
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                color = JagooRed
                            )
                        }
                    }
                }
            }

            // ── Resumen del pedido ────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Resumen del pedido",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = JagooTextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Subtotal", fontSize = 13.sp, color = JagooTextMuted)
                        Text(
                            "$${String.format("%.2f", subtotal)}",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            color = JagooTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Envío", fontSize = 13.sp, color = JagooTextMuted)
                        Text(
                            "$${String.format("%.2f", fee)}",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            color = JagooTextPrimary
                        )
                    }

                    if (promoDiscount > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("PromociÃ³n (-${promoState?.discountPercent}%)", fontSize = 13.sp, color = JagooGreen)
                            Text(
                                "-$${String.format("%.2f", promoDiscount)}",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = JagooGreen
                            )
                        }
                    }
                    if (couponApplied && couponDiscount > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Descuento ($couponCode)", fontSize = 13.sp, color = JagooGreen)
                            Text(
                                "-$${String.format("%.2f", couponDiscount)}",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = JagooGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Total",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = JagooTextPrimary
                        )
                        Text(
                            "$${String.format("%.2f", total)}",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = JagooGreen
                        )
                    }
                }
            }

            // ── Confirmar ─────────────────────────────────────────────────────
            (orderState as? OrderPlacement.Failed)?.let {
                ErrorCard(message = it.error.message + if (BuildConfig.DEBUG && it.error.detail != null) "\n\n[debug] ${it.error.code}: ${it.error.detail}" else "")
            }

            if (belowMinimum) {
                ErrorCard(message = "El pedido mínimo de este negocio es $${String.format("%.2f", minOrder)}. Agrega más productos.")
            }

            Button(
                onClick = {
                    if (!canConfirm) return@Button
                    cartViewModel.placeOrder(paymentMethod, if (paymentMethod == "cash") tendered else null, ageConfirmed)
                },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = JagooGreen),
                enabled = canConfirm,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Creando pedido…", color = Color.White, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        text = when {
                            primaryAddress == null -> "Configura una dirección primero"
                            paymentMethod == "transfer" -> "Confirmar y pagar por transferencia"
                            else -> "Confirmar pedido"
                        },
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/** Opción seleccionable (radio) con área táctil de al menos 48dp. */
@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = JagooTextPrimary)
            Text(subtitle, fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = JagooTextMuted)
        }
    }
}
