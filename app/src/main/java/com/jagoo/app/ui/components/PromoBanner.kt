package com.jagoo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.JagooYellowBadge
import com.jagoo.app.ui.theme.PoppinsFontFamily

@Composable
fun PromoBanner() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = JagooGreen),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),  // Sombra más pronunciada
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = JagooSpacing.md, vertical = JagooSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Frutas frescas",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = androidx.compose.ui.graphics.Color.White
                )

                Text(
                    text = "-25%",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    color = JagooYellow,
                    lineHeight = 36.sp
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(JagooYellowBadge)
                        .padding(horizontal = JagooSpacing.sm + JagooSpacing.xs, vertical = 3.dp)  // 10dp horizontal
                ) {
                    Text(
                        text = "Envío gratis desde $25",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = JagooGreen
                    )
                }
            }

            Spacer(modifier = Modifier.width(JagooSpacing.sm))

            AsyncImage(
                model = "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=500",
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(110.dp)
            )
        }
    }
}
