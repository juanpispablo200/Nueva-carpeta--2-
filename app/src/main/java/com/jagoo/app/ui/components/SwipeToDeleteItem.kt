package com.jagoo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagoo.app.data.CartItem
import com.jagoo.app.ui.theme.CardShape
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooRed
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooTextSecondary
import com.jagoo.app.ui.theme.PoppinsFontFamily

@Composable
fun SwipeToDeleteItem(
    cartItem: CartItem,
    showDeleteButton: Boolean = false,
    onQuantityChange: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Imagen del plato
                AsyncImage(
                    model = cartItem.dish.imageUrl,
                    contentDescription = cartItem.dish.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CardShape)
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Nombre, opciones y precio
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = cartItem.dish.name,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = JagooTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (cartItem.selectedOptions.isNotEmpty()) {
                        Text(
                            text = cartItem.selectedOptions.joinToString { it.optionName },
                            fontFamily = PoppinsFontFamily,
                            fontSize = 10.sp,
                            color = JagooTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = cartItem.restaurantName,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 10.sp,
                            color = JagooTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "$${String.format("%.2f", cartItem.totalPrice).replace('.', ',')}",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = JagooGreen
                    )
                }

                // Selector de cantidad
                QuantitySelector(
                    quantity = cartItem.quantity,
                    onDecrease = { onQuantityChange(cartItem.quantity - 1) },
                    onIncrease = { onQuantityChange(cartItem.quantity + 1) }
                )
            }
        }

        // Botón de eliminar si se activa
        if (showDeleteButton) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .clip(CardShape)
                    .background(JagooRed)
                    .clickable { onDelete() }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Eliminar",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
