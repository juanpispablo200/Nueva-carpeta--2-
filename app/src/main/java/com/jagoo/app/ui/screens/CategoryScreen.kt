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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jagoo.app.data.RestaurantData
import com.jagoo.app.ui.components.FilterChip
import com.jagoo.app.ui.components.JagooHeader
import com.jagoo.app.ui.components.RestaurantCard
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.ProductViewModel

@Composable
fun CategoryScreen(
    categoryName: String = "Hamburguesas",
    productViewModel: ProductViewModel,
    cartViewModel: CartViewModel,
    onNavigateToRestaurant: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val restaurants by productViewModel.visibleRestaurants.collectAsState()
    val selectedFilter by productViewModel.selectedFilterChip.collectAsState()
    val filterChips = RestaurantData.filterChips

    val categoryRestaurants = if (categoryName == "Todas" || categoryName == "Todos") {
        restaurants
    } else {
        restaurants.filter { it.category.contains(categoryName, ignoreCase = true) || categoryName.contains(it.category, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        JagooHeader(
            showBackButton = true,
            categoryTitle = categoryName,
            onBackClick = onBackClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Chips Row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filterChips) { chip ->
                FilterChip(
                    text = chip,
                    isSelected = selectedFilter == chip,
                    onClick = { productViewModel.selectFilterChip(chip) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Lista de restaurantes
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(categoryRestaurants) { restaurant ->
                RestaurantCard(
                    restaurant = restaurant,
                    onClick = { onNavigateToRestaurant(restaurant.id) }
                )
            }
        }
    }
}
