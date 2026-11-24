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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.ui.components.JagooHeader
import com.jagoo.app.ui.components.SwipeToDeleteItem
import com.jagoo.app.ui.theme.CouponShape
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooDivider
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooRed
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.PillShape
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.CartViewModel

@Composable
fun CartScreen(
    cartViewModel: CartViewModel,
    onContinueClick: () -> Unit
) {
    val cartItems by cartViewModel.cartItems.collectAsState()

    val restaurantName = cartViewModel.currentRestaurantName ?: "Negocio"
    val subtotal = cartViewModel.getSubtotal()
    val total = cartViewModel.getTotal()
    val deliveryFee = cartViewModel.deliveryFee

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        JagooHeader()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = JagooSpacing.md, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Tu Carrito",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = JagooGreen,
                        modifier = Modifier.padding(vertical = JagooSpacing.sm)
                    )

                    if (cartItems.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(JagooGreen.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restaurant,
                                contentDescription = "Negocio",
                                tint = JagooGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = restaurantName,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = JagooGreen
                            )
                        }
                    }
                }

                if (cartItems.isEmpty()) {
                    EmptyCartState(onStartShopping = onContinueClick)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(JagooSpacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(cartItems) { index, cartItem ->
                            val isDemoDelete = index == 1

                            SwipeToDeleteItem(
                                cartItem = cartItem,
                                showDeleteButton = isDemoDelete,
                                onQuantityChange = { newQty ->
                                    cartViewModel.updateQuantity(cartItem.dish.id, newQty)
                                },
                                onDelete = {
                                    cartViewModel.removeItem(cartItem.dish.id)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(JagooSpacing.md))

                    // Nota: los cupones se aplican en el checkout
                    Card(
                        shape = CouponShape,
                        colors = CardDefaults.cardColors(containerColor = JagooYellow.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalOffer,
                                contentDescription = "Cupón",
                                tint = JagooGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Aplicarás tu cupón en el siguiente paso",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                color = JagooGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(JagooSpacing.lg))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(JagooSpacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Subtotal",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = JagooTextPrimary
                            )
                            Text(
                                text = "$${String.format("%.2f", subtotal).replace('.', ',')}",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = JagooTextPrimary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Envío del negocio",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = JagooTextPrimary
                            )
                            Text(
                                text = "$${String.format("%.2f", deliveryFee).replace('.', ',')}",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = JagooTextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(JagooSpacing.xs))
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = JagooDivider
                        )
                        Spacer(modifier = Modifier.height(JagooSpacing.xs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total a pagar",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = JagooGreen
                            )
                            Text(
                                text = "$${String.format("%.2f", total).replace('.', ',')}",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = JagooGreen
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(JagooSpacing.md))

            Button(
                onClick = onContinueClick,
                enabled = cartItems.isNotEmpty(),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JagooYellow,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (cartItems.isEmpty()) "Carrito vacío" else "Continuar al Pago",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (cartItems.isEmpty()) JagooTextMuted else JagooGreen
                )
            }
        }
    }

}

@Composable
private fun EmptyCartState(
    onStartShopping: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JagooSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(JagooYellow.copy(alpha = 0.2f))
        ) {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = "Carrito vacío",
                tint = JagooGreen.copy(alpha = 0.6f),
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(JagooSpacing.lg))

        Text(
            text = "Tu carrito está vacío",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = JagooTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(JagooSpacing.sm))

        Text(
            text = "Explora tus negocios favoritos\ny agrega tus productos preferidos",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = JagooTextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(JagooSpacing.xl))

        Button(
            onClick = onStartShopping,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(48.dp)
        ) {
            Text(
                text = "Explorar negocios",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = JagooGreen
            )
        }
    }
}
