package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.ui.components.DishCard
import com.jagoo.app.ui.components.FilterChip
import com.jagoo.app.ui.components.JagooHeader
import com.jagoo.app.ui.components.RestaurantCard
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.ProductViewModel
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    productViewModel: ProductViewModel,
    cartViewModel: CartViewModel,
    onNavigateToRestaurant: (String) -> Unit
) {
    val restaurants   by productViewModel.visibleRestaurants.collectAsState()
    val dishes        by productViewModel.dishes.collectAsState()
    val searchQuery   by productViewModel.searchQuery.collectAsState()
    val selectedFilter by productViewModel.selectedFilterChip.collectAsState()

    // Debounce local: esperar 300ms después de que el usuario deja de escribir
    var debouncedQuery by remember { mutableStateOf(searchQuery) }
    var isDebouncing   by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        isDebouncing = searchQuery.isNotBlank()
        delay(300)
        debouncedQuery = searchQuery
        isDebouncing = false
    }

    val searchFilters = listOf("Todos", "Negocios", "Platos", "Mejor Evaluados", "< 30 min", "Ofertas")

    val matchingRestaurants = if (debouncedQuery.isBlank()) {
        productViewModel.getFilteredRestaurants()
    } else {
        restaurants.filter {
            it.name.contains(debouncedQuery, ignoreCase = true) ||
            it.category.contains(debouncedQuery, ignoreCase = true) ||
            it.description.contains(debouncedQuery, ignoreCase = true)
        }
    }

    val matchingDishes = if (debouncedQuery.isBlank()) {
        emptyList()
    } else {
        dishes.filter {
            it.name.contains(debouncedQuery, ignoreCase = true) ||
            it.description.contains(debouncedQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        JagooHeader(
            showSearchBar       = true,
            searchQuery         = searchQuery,
            placeholderText     = "Busca negocios o productos...",
            onSearchQueryChange = { productViewModel.updateSearchQuery(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filtros
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.fillMaxWidth()
        ) {
            items(searchFilters) { filter ->
                FilterChip(
                    text       = filter,
                    isSelected = selectedFilter == filter,
                    onClick    = { productViewModel.selectFilterChip(filter) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Spinner mientras debounce
        if (isDebouncing) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = JagooGreen, modifier = Modifier.padding(4.dp))
            }
            return@Column
        }

        // Sin resultados
        if (debouncedQuery.isNotBlank() && matchingRestaurants.isEmpty() && matchingDishes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😕", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text       = "Sin resultados para \"$debouncedQuery\"",
                        fontFamily = PoppinsFontFamily,
                        fontSize   = 15.sp,
                        color      = JagooTextMuted
                    )
                    Text(
                        text       = "Intenta con otro nombre o categoría",
                        fontFamily = PoppinsFontFamily,
                        fontSize   = 13.sp,
                        color      = JagooTextMuted
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement   = Arrangement.spacedBy(14.dp),
            modifier              = Modifier.fillMaxSize()
        ) {
            // Platos encontrados
            if (matchingDishes.isNotEmpty() && selectedFilter != "Negocios") {
                item {
                    Text(
                        text       = "Platos (${matchingDishes.size})",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = JagooGreen
                    )
                }
                items(matchingDishes) { dish ->
                    val qty = cartViewModel.getItemQuantity(dish.id)
                    DishCard(
                        dish             = dish,
                        quantityInCart   = qty,
                        onAddToCart      = { cartViewModel.addToCart(dish) },
                        onQuantityChange = { newQty -> cartViewModel.updateQuantity(dish.id, newQty) },
                        onClickDish      = { onNavigateToRestaurant(dish.restaurantId) }
                    )
                }
            }

            // Restaurantes
            if (selectedFilter != "Platos") {
                item {
                    Text(
                        text = if (debouncedQuery.isBlank())
                            "Negocios destacados"
                        else
                            "Negocios (${matchingRestaurants.size})",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = JagooTextPrimary,
                        modifier   = Modifier.padding(top = 8.dp)
                    )
                }
                items(matchingRestaurants) { restaurant ->
                    RestaurantCard(
                        restaurant = restaurant,
                        onClick    = { onNavigateToRestaurant(restaurant.id) }
                    )
                }
            }
        }
    }
}
