package com.jagoo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagoo.app.data.remote.PromotionDto
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.JagooYellowBadge
import com.jagoo.app.ui.theme.PoppinsFontFamily

/**
 * Banner del home con una promoción REAL y vigente, elegida al azar entre las que
 * devuelve el servidor. Sin promociones no se dibuja nada (nunca se inventa una oferta).
 */
@Composable
fun PromoBanner(
    promotions: List<PromotionDto>,
    onClick: (restaurantId: String) -> Unit
) {
    if (promotions.isEmpty()) return
    val promo = remember(promotions) { promotions.random() }
    val vence = formatEndDate(promo.endsAt)
    val descripcion = "Promoción de ${promo.restaurantName}: ${promo.title}, " +
        "${promo.discountPercent} por ciento de descuento" +
        (vence?.let { ", vence el $it" } ?: "") + ". Toca para ver el negocio."

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = JagooGreen),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = "Ver ${promo.restaurantName}") { onClick(promo.restaurantId) }
            .semantics(mergeDescendants = true) { contentDescription = descripcion }
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
                    text = promo.title,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = androidx.compose.ui.graphics.Color.White
                )
                Text(
                    text = "-${promo.discountPercent}%",
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
                        .padding(horizontal = JagooSpacing.sm + JagooSpacing.xs, vertical = 4.dp)
                ) {
                    Text(
                        text = buildString {
                            append(promo.restaurantName)
                            vence?.let { append(" · hasta el ").append(it) }
                        },
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = JagooGreen
                    )
                }
            }
            val imagen = promo.coverUrl?.takeIf { it.isNotBlank() } ?: promo.logoUrl?.takeIf { it.isNotBlank() }
            if (imagen != null) {
                Spacer(modifier = Modifier.width(JagooSpacing.sm))
                AsyncImage(
                    model = imagen,
                    contentDescription = null,   // decorativa: ya está descrita en el banner
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }
        }
    }
}

/** "dd/MM" a partir de un instante ISO-8601; null si no se puede leer. */
internal fun formatEndDate(iso: String): String? = try {
    val d = java.time.OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.of("America/Guayaquil"))
    "%02d/%02d".format(d.dayOfMonth, d.monthValue)
} catch (_: Exception) { null }
