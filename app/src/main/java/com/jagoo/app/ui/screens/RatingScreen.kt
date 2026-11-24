package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooRed
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.JagooHeaderBrush
import com.jagoo.app.ui.theme.PillShape
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.RatingViewModel

@Composable
fun RatingScreen(
    ratingViewModel: RatingViewModel,
    orderId:         String,
    restaurantName:  String,
    courierName:     String?,
    onDone:          () -> Unit
) {
    val restaurantStars   by ratingViewModel.restaurantStars.collectAsState()
    val courierStars      by ratingViewModel.courierStars.collectAsState()
    val restaurantComment by ratingViewModel.restaurantComment.collectAsState()
    val courierComment    by ratingViewModel.courierComment.collectAsState()
    val loading           by ratingViewModel.loading.collectAsState()
    val error             by ratingViewModel.error.collectAsState()
    val submitted         by ratingViewModel.submitted.collectAsState()

    if (submitted) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDone() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JagooHeaderBrush)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Star, "Calificar", tint = JagooGreen, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "¿Cómo estuvo tu pedido?",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = JagooGreen
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Calificación restaurante
            RatingCard(
                icon        = Icons.Default.Restaurant,
                title       = "Negocio",
                subtitle    = restaurantName,
                stars       = restaurantStars,
                onStarClick = { ratingViewModel.setRestaurantStars(it) },
                comment     = restaurantComment,
                onCommentChange = { ratingViewModel.setRestaurantComment(it) },
                placeholder = "¿Cómo estuvo la comida?"
            )

            // Calificación repartidor (opcional si hay courier)
            if (!courierName.isNullOrBlank()) {
                RatingCard(
                    icon        = Icons.Default.TwoWheeler,
                    title       = "Repartidor",
                    subtitle    = courierName,
                    stars       = courierStars,
                    onStarClick = { ratingViewModel.setCourierStars(it) },
                    comment     = courierComment,
                    onCommentChange = { ratingViewModel.setCourierComment(it) },
                    placeholder = "¿Cómo fue la entrega?"
                )
            }

            if (error != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = JagooRed.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(error!!, fontFamily = PoppinsFontFamily, fontSize = 13.sp,
                        color = JagooRed, modifier = Modifier.padding(12.dp))
                }
            }

            Button(
                onClick = { ratingViewModel.submit(orderId) },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = JagooGreen),
                enabled = !loading && restaurantStars > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Enviar calificación", fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            }

            androidx.compose.material3.TextButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Omitir por ahora", fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp, color = JagooTextMuted)
            }
        }
    }
}

@Composable
private fun RatingCard(
    icon:            androidx.compose.ui.graphics.vector.ImageVector,
    title:           String,
    subtitle:        String,
    stars:           Int,
    onStarClick:     (Int) -> Unit,
    comment:         String,
    onCommentChange: (String) -> Unit,
    placeholder:     String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, title, tint = JagooGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(title, fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, color = JagooTextPrimary)
                    Text(subtitle, fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp, color = JagooTextMuted)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Estrellas
            Row {
                (1..5).forEach { i ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(role = Role.Button) { onStarClick(i) }
                            .semantics { selected = i <= stars }
                    ) {
                        Icon(
                            imageVector = if (i <= stars) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (i == 1) "1 estrella" else "$i estrellas",
                            tint = if (i <= stars) JagooYellow else Color.Gray.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            if (stars > 0) {
                Text(
                    text = when (stars) {
                        1 -> "Muy malo 😞"
                        2 -> "Malo 😐"
                        3 -> "Regular 🙂"
                        4 -> "Bueno 😊"
                        5 -> "¡Excelente! 🎉"
                        else -> ""
                    },
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = JagooGreen,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = comment,
                onValueChange = onCommentChange,
                label = { Text("Comentario (opcional)") },
                placeholder = { Text(placeholder) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
