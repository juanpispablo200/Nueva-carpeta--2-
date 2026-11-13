package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.ui.components.JagooHeader
import com.jagoo.app.ui.components.ProductCard
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.ProductViewModel

@Composable
fun SearchScreen(
    productViewModel: ProductViewModel,
    cartViewModel: CartViewModel
) {
    val products by productViewModel.products.collectAsState()
    val searchQuery by productViewModel.searchQuery.collectAsState()

    val filteredProducts = if (searchQuery.isBlank()) products else {
        products.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        // Search Header
        JagooHeader(
            showSearchBar = true,
            searchQuery = searchQuery,
            onSearchQueryChange = { productViewModel.updateSearchQuery(it) }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = if (searchQuery.isBlank()) "Todos los productos" else "Resultados de búsqueda",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = JagooGreen
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredProducts) { product ->
                    val qtyInCart = cartViewModel.getItemQuantity(product.id)
                    ProductCard(
                        product = product,
                        quantityInCart = qtyInCart,
                        useYellowPlusButton = true,
                        onAddToCart = { cartViewModel.addToCart(product) },
                        onQuantityChange = { newQty -> cartViewModel.updateQuantity(product.id, newQty) }
                    )
                }
            }
        }
    }
}
