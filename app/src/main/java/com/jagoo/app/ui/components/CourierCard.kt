package com.jagoo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooTextSecondary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.PoppinsFontFamily

@Composable
fun CourierCard(
    name: String = "Carlos M.",
    vehicleInfo: String = "Moto · PBC-1234",
    onCallClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Courier avatar
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(JagooGreen.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Repartidor",
                        tint = JagooGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(JagooSpacing.sm + JagooSpacing.xs))  // 12dp total

                Column {
                    Text(
                        text = name,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = JagooTextPrimary
                    )
                    Text(
                        text = vehicleInfo,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = JagooTextSecondary  // Mejor contraste que Gray
                    )
                }
            }

            // Call button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(JagooYellow)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Llamar al repartidor") { onCallClick() }
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Llamar repartidor",
                    tint = JagooGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
