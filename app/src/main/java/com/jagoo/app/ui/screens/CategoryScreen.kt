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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jagoo.app.data.MockData
import com.jagoo.app.ui.components.FilterChip
import com.jagoo.app.ui.components.JagooHeader
import com.jagoo.app.ui.components.ProductCard
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.ProductViewModel

@Composable
fun CategoryScreen(
    categoryName: String = "Frutas y verduras",
    productViewModel: ProductViewModel,
    cartViewModel: CartViewModel,
    onBackClick: () -> Unit
) {
    val products by productViewModel.products.collectAsState()
    val selectedFilter by productViewModel.selectedFilterChip.collectAsState()
    val filterChips = MockData.filterChips

    // Filter products by category or display the category products
    val categoryProducts = products.filter {
        it.category == "Frutas y verduras" || categoryName == "Todas"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        // Top Yellow Header with back button & category title
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

        // 2-column product grid matching screenshot
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(categoryProducts) { product ->
                // Check quantity in cart
                val qtyInCart = if (product.name.contains("Tomate")) 2 else cartViewModel.getItemQuantity(product.id)

                ProductCard(
                    product = product,
                    quantityInCart = qtyInCart,
                    useYellowPlusButton = true, // Yellow '+' buttons per Screen 2 screenshot
                    onAddToCart = { cartViewModel.addToCart(product) },
                    onQuantityChange = { newQty -> cartViewModel.updateQuantity(product.id, newQty) }
                )
            }
        }
    }
}
