package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.jagoo.app.data.Dish
import com.jagoo.app.ui.components.DishCard
import com.jagoo.app.ui.components.DishDetailDialog
import com.jagoo.app.ui.components.FilterChip
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooRed
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooTextSecondary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.JagooYellowBadge
import com.jagoo.app.ui.theme.PillShape
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.ProductViewModel

@Composable
fun RestaurantDetailScreen(
    restaurantId: String,
    productViewModel: ProductViewModel,
    cartViewModel: CartViewModel,
    onNavigateToCart: () -> Unit = {},
    onBackClick: () -> Unit
) {
    val restaurant = productViewModel.getRestaurantById(restaurantId)
    val dishes = productViewModel.getDishesForRestaurant(restaurantId)
    val favoriteIds by productViewModel.favoriteRestaurantIds.collectAsState()
    val cartItems by cartViewModel.cartItems.collectAsState()

    val isFav = favoriteIds.contains(restaurantId)

    // Ítems del restaurante actual en el carrito
    val currentRestCartItems = cartItems.filter { it.restaurantId == restaurantId }
    val cartItemCount = currentRestCartItems.sumOf { it.quantity }
    val cartSubtotal = currentRestCartItems.sumOf { it.totalPrice }

    var selectedSection by remember { mutableStateOf("Todos") }
    var selectedDishForDetail by remember { mutableStateOf<Dish?>(null) }
    var dishQuery by remember { mutableStateOf("") }

    if (restaurant == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text("Negocio no encontrado")
        }
        return
    }

    val menuSections = listOf("Todos", "Populares") + dishes.map { it.menuSection }.distinct()

    val sectionDishes = when (selectedSection) {
        "Todos" -> dishes
        "Populares" -> dishes.filter { it.isPopular }
        else -> dishes.filter { it.menuSection == selectedSection }
    }


    // Búsqueda dentro del negocio (nombre o descripción), sin distinguir tildes ni mayúsculas
    val filteredDishes = if (dishQuery.isBlank()) sectionDishes else sectionDishes.filter {
        it.name.contains(dishQuery.trim(), ignoreCase = true) ||
        it.description.contains(dishQuery.trim(), ignoreCase = true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Portada superior con botón de regreso y botón favorito
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                AsyncImage(
                    model = restaurant.coverUrl,
                    contentDescription = restaurant.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                )

                // Botón regresar
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Regresar",
                        tint = JagooGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Botón Favorito
                IconButton(
                    onClick = { productViewModel.toggleFavorite(restaurantId) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (isFav) JagooRed else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Logo circular superpuesto
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(start = 16.dp, bottom = 12.dp)
                        .size(64.dp)
                        .align(Alignment.BottomStart)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(3.dp)
                ) {
                    AsyncImage(
                        model = restaurant.logoUrl,
                        contentDescription = restaurant.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            }

            // Info del Restaurante
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JagooCardWhite)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = restaurant.name,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = JagooTextPrimary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(JagooYellowBadge)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFF57F17),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${restaurant.rating} (${restaurant.reviewCount})",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = JagooTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = restaurant.description,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    color = JagooTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Tiempo",
                            tint = JagooGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = restaurant.deliveryTime,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = JagooTextPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = "Envío",
                            tint = JagooGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (restaurant.deliveryFee == 0.0) "Envío gratis" else "$${String.format("%.2f", restaurant.deliveryFee).replace('.', ',')}",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (restaurant.deliveryFee == 0.0) Color(0xFF2E7D32) else JagooTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Buscador dentro de este negocio
            OutlinedTextField(
                value = dishQuery,
                onValueChange = { dishQuery = it },
                label = { Text("Buscar en este negocio") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = JagooGreen) },
                trailingIcon = {
                    if (dishQuery.isNotEmpty()) {
                        IconButton(onClick = { dishQuery = "" }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda", tint = JagooGreen)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))
            // Selector de secciones del menú en scroll horizontal
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(menuSections) { section ->
                    FilterChip(
                        text = section,
                        isSelected = selectedSection == section,
                        onClick = { selectedSection = section }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lista de platos filtrados
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = if (cartItemCount > 0) 80.dp else 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredDishes) { dish ->
                    val qtyInCart = cartViewModel.getItemQuantity(dish.id)

                    DishCard(
                        dish = dish,
                        quantityInCart = qtyInCart,
                        onAddToCart = {
                            if (dish.optionGroups.isNotEmpty()) {
                                selectedDishForDetail = dish
                            } else {
                                cartViewModel.addToCart(dish)
                            }
                        },
                        onQuantityChange = { newQty ->
                            cartViewModel.updateQuantity(dish.id, newQty)
                        },
                        onClickDish = {
                            selectedDishForDetail = dish
                        }
                    )
                }
            }
        }

        // Barra Flotante Inferior de Ver Carrito si hay productos en el carrito
        if (cartItemCount > 0) {
            Surface(
                shadowElevation = 8.dp,
                color = Color.Transparent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onNavigateToCart,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(JagooGreen)
                            ) {
                                Text(
                                    text = "$cartItemCount",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Ver mi carrito",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = JagooGreen
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$${String.format("%.2f", cartSubtotal).replace('.', ',')}",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = JagooGreen
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Ver Carrito",
                                tint = JagooGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogo de detalle del plato
    selectedDishForDetail?.let { dish ->
        DishDetailDialog(
            dish = dish,
            onDismiss = { selectedDishForDetail = null },
            onAddToCart = { selectedDish, options, notes, qty ->
                cartViewModel.addToCart(selectedDish, options, notes, qty)
            }
        )
    }
}
