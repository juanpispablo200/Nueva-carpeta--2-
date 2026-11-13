package com.jagoo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.data.Category
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooIconBgYellow
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.PoppinsFontFamily

@Composable
fun CategoryItem(
    category: Category,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (category.iconName) {
        "apple" -> Icons.Default.Restaurant
        "meat" -> Icons.Default.Restaurant
        "dairy" -> Icons.Default.WaterDrop
        "groceries" -> Icons.Default.ShoppingBag
        "drinks" -> Icons.Default.WaterDrop
        else -> Icons.Default.Storefront
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(68.dp)
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(JagooIconBgYellow)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = category.name,
                tint = JagooGreen,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = category.name,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = JagooTextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp,
            maxLines = 2
        )
    }
}
