package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.ui.components.JagooHeader
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooTextSecondary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.ProductViewModel

@Composable
fun ProfileScreen(
    productViewModel: ProductViewModel,
    onNavigateToAddresses: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToAdmin: () -> Unit = {}
) {
    val favIds by productViewModel.favoriteRestaurantIds.collectAsState()
    val primaryAddr = productViewModel.getPrimaryAddress()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        JagooHeader()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tarjeta de datos del usuario
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(JagooYellow)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Perfil",
                            tint = JagooGreen,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Juan Pablo",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = JagooTextPrimary
                        )
                        Text(
                            text = "+593 99 123 4567",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            color = JagooTextSecondary
                        )
                    }
                }
            }

            // Opciones del Menú de Perfil
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    ProfileMenuItem(
                        icon = Icons.Default.AdminPanelSettings,
                        title = "Panel Administrador",
                        subtitle = "Gestionar menús de restaurantes",
                        onClick = onNavigateToAdmin
                    )
                    ProfileMenuItem(
                        icon = Icons.Default.LocationOn,
                        title = "Mis Direcciones",
                        subtitle = primaryAddr,
                        onClick = onNavigateToAddresses
                    )
                    ProfileMenuItem(
                        icon = Icons.Default.History,
                        title = "Historial de Pedidos",
                        subtitle = "Ver pedidos anteriores",
                        onClick = onNavigateToHistory
                    )
                    ProfileMenuItem(
                        icon = Icons.Default.Favorite,
                        title = "Restaurantes Favoritos",
                        subtitle = "${favIds.size} restaurantes guardados",
                        onClick = {}
                    )
                    ProfileMenuItem(
                        icon = Icons.Default.CreditCard,
                        title = "Métodos de Pago",
                        subtitle = "Efectivo / Tarjeta crédito",
                        onClick = {}
                    )
                    ProfileMenuItem(
                        icon = Icons.Default.Notifications,
                        title = "Notificaciones",
                        subtitle = "Promociones y estado",
                        onClick = {}
                    )
                    ProfileMenuItem(
                        icon = Icons.Default.HelpOutline,
                        title = "Centro de Ayuda",
                        subtitle = "Preguntas frecuentes y soporte",
                        onClick = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(JagooGreen.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = JagooGreen,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = JagooTextPrimary
                )
                Text(
                    text = subtitle,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    color = JagooTextSecondary
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}
