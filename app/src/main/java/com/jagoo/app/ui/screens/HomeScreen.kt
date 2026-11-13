package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
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
import com.jagoo.app.ui.components.ProductCard
import com.jagoo.app.ui.components.PromoBanner
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
    onNavigateToCategory: (String) -> Unit
) {
    val categories by productViewModel.categories.collectAsState()
    val products by productViewModel.products.collectAsState()
    val searchQuery by productViewModel.searchQuery.collectAsState()

    val offersList = products.filter { it.isOffer }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        // Header amarillo con buscador interactivo
        JagooHeader(
            showSearchBar = true,
            searchQuery = searchQuery,
            onSearchQueryChange = { productViewModel.updateSearchQuery(it) }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = JagooSpacing.md, vertical = 12.dp)
        ) {
            // Banner promocional
            PromoBanner()

            Spacer(modifier = Modifier.height(JagooSpacing.md))

            // Categorías con scroll horizontal (LazyRow)
            // Antes usaba Row + forEach — se recortaban con 5+ categorías
            LazyRow(
                contentPadding = PaddingValues(horizontal = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(JagooSpacing.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    CategoryItem(
                        category = category,
                        onClick = { onNavigateToCategory(category.name.replace("\n", " ")) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Encabezado "Ofertas del día"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ofertas del día",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = JagooTextPrimary
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Ver todas las ofertas",
                    tint = JagooTextPrimary,
                    modifier = Modifier.clickable { onNavigateToCategory("Ofertas") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Grid de 2 columnas para ofertas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                offersList.take(2).forEach { product ->
                    val qtyInCart = cartViewModel.getItemQuantity(product.id)

                    ProductCard(
                        product = product,
                        quantityInCart = qtyInCart,
                        useYellowPlusButton = false,
                        onAddToCart = { cartViewModel.addToCart(product) },
                        onQuantityChange = { newQty -> cartViewModel.updateQuantity(product.id, newQty) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(JagooSpacing.md))
        }
    }
}
