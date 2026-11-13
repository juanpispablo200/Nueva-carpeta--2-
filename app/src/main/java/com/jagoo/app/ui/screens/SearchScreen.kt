package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.ProductViewModel

@Composable
fun SearchScreen(
    productViewModel: ProductViewModel,
    cartViewModel: CartViewModel,
    onNavigateToRestaurant: (String) -> Unit
) {
    val restaurants by productViewModel.restaurants.collectAsState()
    val dishes by productViewModel.dishes.collectAsState()
    val searchQuery by productViewModel.searchQuery.collectAsState()
    val selectedFilter by productViewModel.selectedFilterChip.collectAsState()

    val searchFilters = listOf("Todos", "Restaurantes", "Platos", "Mejor Evaluados", "< 30 min", "Ofertas")

    val matchingRestaurants = if (searchQuery.isBlank()) {
        restaurants
    } else {
        restaurants.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    val matchingDishes = if (searchQuery.isBlank()) {
        emptyList()
    } else {
        dishes.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true) ||
                    it.menuSection.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        JagooHeader(
            showSearchBar = true,
            searchQuery = searchQuery,
            placeholderText = "Busca restaurantes o platos...",
            onSearchQueryChange = { productViewModel.updateSearchQuery(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(searchFilters) { filter ->
                FilterChip(
                    text = filter,
                    isSelected = selectedFilter == filter,
                    onClick = { productViewModel.selectFilterChip(filter) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (matchingDishes.isNotEmpty() && selectedFilter != "Restaurantes") {
                item {
                    Text(
                        text = "Platos encontrados (${matchingDishes.size})",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = JagooGreen
                    )
                }

                items(matchingDishes) { dish ->
                    val qtyInCart = cartViewModel.getItemQuantity(dish.id)
                    DishCard(
                        dish = dish,
                        quantityInCart = qtyInCart,
                        onAddToCart = { cartViewModel.addToCart(dish) },
                        onQuantityChange = { newQty -> cartViewModel.updateQuantity(dish.id, newQty) },
                        onClickDish = { onNavigateToRestaurant(dish.restaurantId) }
                    )
                }
            }

            if (selectedFilter != "Platos") {
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "Restaurantes destacados" else "Restaurantes (${matchingRestaurants.size})",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = JagooTextPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(matchingRestaurants) { restaurant ->
                    RestaurantCard(
                        restaurant = restaurant,
                        onClick = { onNavigateToRestaurant(restaurant.id) }
                    )
                }
            }
        }
    }
}
