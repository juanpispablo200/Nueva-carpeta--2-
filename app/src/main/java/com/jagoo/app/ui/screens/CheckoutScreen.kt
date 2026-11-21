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
import com.jagoo.app.ui.theme.PillShape
import com.jagoo.app.ui.theme.PoppinsFontFamily
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
    onOrderConfirmed: (orderId: String, total: Double) -> Unit,
    onPayWithCard: (orderId: String, total: Double) -> Unit = { _, _ -> }
) {
    val cartItems by cartViewModel.cartItems.collectAsState()
    val subtotal  = cartViewModel.getSubtotal()
    val fee       = cartViewModel.deliveryFee

    val couponCode     by cartViewModel.couponCode.collectAsState()
    val couponApplied  by cartViewModel.couponApplied.collectAsState()
    val couponDiscount by cartViewModel.couponDiscount.collectAsState()
    val couponError    by cartViewModel.couponError.collectAsState()
    val couponLoading  by cartViewModel.couponLoading.collectAsState()

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

    // Método de pago hardcodeado por ahora (Fase 3)
    val paymentMethod = "Efectivo al recibir"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JagooYellow)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(36.dp)
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
                .padding(16.dp),
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

            // ── Método de pago ────────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Payment,
                            contentDescription = "Pago",
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

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = paymentMethod,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            color = JagooTextMuted,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Seleccionado",
                            tint = JagooGreen,
                            modifier = Modifier.size(18.dp)
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

            // ── Botones de pago ───────────────────────────────────────────────
            Button(
                onClick = {
                    if (primaryAddress == null) return@Button
                    cartViewModel.confirmOrder()
                    val activeOrder = cartViewModel.activeOrder.value
                    if (activeOrder != null) {
                        onOrderConfirmed(activeOrder.id, activeOrder.total)
                    }
                },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = JagooGreen),
                enabled = primaryAddress != null && cartItems.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    text = if (primaryAddress == null) "Configura una dirección primero"
                           else "Pagar por Transferencia",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            Button(
                onClick = {
                    if (primaryAddress == null) return@Button
                    cartViewModel.confirmOrder()
                    val activeOrder = cartViewModel.activeOrder.value
                    if (activeOrder != null) {
                        onPayWithCard(activeOrder.id, activeOrder.total)
                    }
                },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
                enabled = primaryAddress != null && cartItems.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    text = "Pagar con Tarjeta (Kushki)",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = JagooGreen
                )
            }
        }
    }
}
