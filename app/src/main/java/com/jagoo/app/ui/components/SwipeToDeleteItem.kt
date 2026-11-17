package com.jagoo.app.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

private enum class SwipeDismissState { Closed, Open }

private val DELETE_WIDTH = 80.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeToDeleteItem(
    cartItem: CartItem,
    showDeleteButton: Boolean = false,
    onQuantityChange: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { DELETE_WIDTH.toPx() }

    val state = remember(cartItem.dish.id) {
        AnchoredDraggableState(
            initialValue = SwipeDismissState.Closed,
            anchors = DraggableAnchors {
                SwipeDismissState.Closed at 0f
                SwipeDismissState.Open at -deleteWidthPx
            },
            positionalThreshold = { total -> total * 0.4f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            snapAnimationSpec = tween(durationMillis = 220),
            decayAnimationSpec = androidx.compose.animation.core.exponentialDecay()
        )
    }

    val isOpen = state.targetValue == SwipeDismissState.Open

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(CardShape)
    ) {
        // Fondo rojo con botón Eliminar
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .width(DELETE_WIDTH)
                .align(Alignment.CenterEnd)
                .background(JagooRed)
                .clickable(
                    enabled = isOpen,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDelete() }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar ítem del carrito",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
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

        // Tarjeta deslizable
        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(x = state.offset.roundToInt(), y = 0) }
                .anchoredDraggable(state = state, orientation = Orientation.Horizontal)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AsyncImage(
                    model = cartItem.dish.imageUrl,
                    contentDescription = cartItem.dish.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CardShape)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
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

                QuantitySelector(
                    quantity = cartItem.quantity,
                    onDecrease = { onQuantityChange(cartItem.quantity - 1) },
                    onIncrease = { onQuantityChange(cartItem.quantity + 1) }
                )
            }
        }
    }
}
