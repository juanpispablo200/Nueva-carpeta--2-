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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.ui.components.CategoryItem
import com.jagoo.app.ui.components.JagooHeader
import com.jagoo.app.ui.components.PromoBanner
import com.jagoo.app.ui.components.RestaurantCard
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.ProductViewModel

@Composable
fun HomeScreen(
    productViewModel: ProductViewModel,
    cartViewModel: CartViewModel,
    onNavigateToRestaurant: (String) -> Unit,
    onNavigateToCategory: (String) -> Unit
) {
    val categories by productViewModel.categories.collectAsState()
    val restaurants by productViewModel.restaurants.collectAsState()
    val searchQuery by productViewModel.searchQuery.collectAsState()
    val selectedCategory by productViewModel.selectedCategory.collectAsState()
    val addresses by productViewModel.addresses.collectAsState()

    val primaryAddress = addresses.find { it.isPrimary }?.fullAddress ?: "Av. República del Salvador, Quito"
    val offerRestaurants = restaurants.filter { it.isOffer }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        // Header amarillo con buscador "¿Qué se te antoja hoy?" y dirección principal
        JagooHeader(
            showSearchBar = true,
            searchQuery = searchQuery,
            deliveryAddress = primaryAddress,
            placeholderText = "¿Qué se te antoja hoy?",
            onSearchQueryChange = { productViewModel.updateSearchQuery(it) }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            // Banner promocional
            Box(modifier = Modifier.padding(horizontal = JagooSpacing.md)) {
                PromoBanner()
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Categorías de comida (Hamburguesas, Pizza, Café, Sushi, etc.)
            Text(
                text = "Categorías",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = JagooTextPrimary,
                modifier = Modifier.padding(horizontal = JagooSpacing.md)
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = JagooSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(JagooSpacing.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    CategoryItem(
                        category = category,
                        isSelected = selectedCategory == category.name,
                        onClick = {
                            productViewModel.selectCategory(category.name)
                            onNavigateToCategory(category.name)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Seccion "Ofertas del día"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JagooSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ofertas del día 🏷️",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = JagooTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = JagooSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(offerRestaurants) { restaurant ->
                    RestaurantCard(
                        restaurant = restaurant,
                        onClick = { onNavigateToRestaurant(restaurant.id) },
                        modifier = Modifier.width(280.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sección "Restaurantes cerca de ti"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JagooSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Restaurantes cerca de ti",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = JagooTextPrimary
                )

                Text(
                    text = "Ver todos",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = JagooGreen,
                    modifier = Modifier.clickable { onNavigateToCategory("Todos") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JagooSpacing.md),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                restaurants.forEach { restaurant ->
                    RestaurantCard(
                        restaurant = restaurant,
                        onClick = { onNavigateToRestaurant(restaurant.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(JagooSpacing.md))
        }
    }
}
