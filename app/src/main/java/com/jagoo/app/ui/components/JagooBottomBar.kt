package com.jagoo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.navigation.Screen
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooDivider
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.PoppinsFontFamily

data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home.route, "Inicio", Icons.Default.Home),
    BottomNavItem(Screen.Search.route, "Buscar", Icons.Default.Search),
    BottomNavItem(Screen.Orders.route, "Pedidos", Icons.Default.Receipt),
    BottomNavItem(Screen.Cart.route, "Carrito", Icons.Default.ShoppingCart),
    BottomNavItem(Screen.Profile.route, "Perfil", Icons.Default.Person)
)

@Composable
fun JagooBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .shadow(12.dp, androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), clip = false)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .fillMaxWidth()
            .background(JagooCardWhite)
    ) {
        // Separador superior: distingue visualmente la barra del contenido en fondos crema
        HorizontalDivider(
            thickness = 1.dp,
            color = JagooDivider
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onNavigate(item.route) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) JagooGreen else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) Color.White else Color.Gray,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.title,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.sp,
                        color = if (isSelected) JagooGreen else Color.Gray
                    )
                }
            }
        }
    }
}
