package com.jagoo.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.jagoo.app.data.Product
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooGreenLight
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooTextSecondary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.JagooYellowBadge
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.ui.theme.ProductCardShape

@Composable
fun ProductCard(
    product: Product,
    quantityInCart: Int = 0,
    useYellowPlusButton: Boolean = false,
    onAddToCart: () -> Unit = {},
    onQuantityChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Estado interno para la animación del botón +
    var isPressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "button_scale"
    )

    Card(
        shape = ProductCardShape,
        colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .heightIn(min = 200.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(JagooSpacing.sm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // animateContentSize: la transición entre el botón + y el QuantitySelector
                    // ocurre con un resorte suave en vez de un corte abrupto
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Sección superior: Badge de oferta + Imagen del producto
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    if (product.isOffer && product.offerBadge != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .clip(CircleShape)
                                .background(JagooYellowBadge)
                                .padding(horizontal = JagooSpacing.sm, vertical = 3.dp)
                        ) {
                            Text(
                                text = product.offerBadge,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.sp,
                                color = JagooTextPrimary
                            )
                        }
                    }

                    // Imagen con estado de carga y error
                    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = if (product.isOffer) 26.dp else JagooSpacing.xs,
                                start = JagooSpacing.xs,
                                end = JagooSpacing.xs,
                                bottom = JagooSpacing.xs
                            )
                    ) {
                        // Fondo de carga (skeleton suave)
                        if (imageState is AsyncImagePainter.State.Loading ||
                            imageState is AsyncImagePainter.State.Empty
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .background(JagooGreenLight)
                            )
                        }

                        // Icono de error si la imagen falla
                        if (imageState is AsyncImagePainter.State.Error) {
                            Icon(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = "Sin imagen",
                                tint = JagooTextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        AsyncImage(
                            model = product.imageUrl,
                            contentDescription = product.name,
                            contentScale = ContentScale.Fit,
                            onState = { imageState = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(JagooSpacing.sm))

                // Nombre del producto
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (product.weightOrQuantity.isNotEmpty())
                            "${product.name} ${product.weightOrQuantity}"
                        else product.name,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = JagooTextSecondary,
                        maxLines = 3,
                        lineHeight = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(JagooSpacing.sm))

                // Sección inferior: Precio + Botón/Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("$%.2f", product.price).replace('.', ','),
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = JagooGreen
                    )

                    if (quantityInCart > 0) {
                        // Selector de cantidad (animado por animateContentSize del padre)
                        QuantitySelector(
                            quantity = quantityInCart,
                            onDecrease = { onQuantityChange(quantityInCart - 1) },
                            onIncrease = { onQuantityChange(quantityInCart + 1) },
                            isCompact = true
                        )
                    } else {
                        // Botón + con animación de escala (rebote al presionar)
                        val buttonBg = if (useYellowPlusButton) JagooYellow else JagooGreen
                        val iconTint = if (useYellowPlusButton) JagooTextPrimary else Color.White

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .scale(buttonScale)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(buttonBg)
                                .clickable {
                                    isPressed = true
                                    onAddToCart()
                                    // Resetear el estado después del click para que
                                    // la animación se pueda disparar de nuevo
                                    isPressed = false
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Agregar al carrito",
                                tint = iconTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
