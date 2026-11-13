package com.jagoo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
            .height(84.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Cart Card
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
                // Image
                AsyncImage(
                    model = cartItem.product.imageUrl,
                    contentDescription = cartItem.product.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(54.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Name & Subtitle
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (cartItem.product.weightOrQuantity.isNotEmpty())
                            "${cartItem.product.name} ${cartItem.product.weightOrQuantity}"
                        else cartItem.product.name,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = JagooTextPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format("$%.2f", cartItem.product.price).replace('.', ','),
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = JagooGreen
                    )
                }

                // Quantity selector
                QuantitySelector(
                    quantity = cartItem.quantity,
                    onDecrease = { onQuantityChange(cartItem.quantity - 1) },
                    onIncrease = { onQuantityChange(cartItem.quantity + 1) }
                )
            }
        }

        // Delete Red Action Button (if shown or swiped as in screen 3 example item 2)
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
