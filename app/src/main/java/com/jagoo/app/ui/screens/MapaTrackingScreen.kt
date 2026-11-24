package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.jagoo.app.data.remote.SupabaseClient
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.CartViewModel
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class DriverLocationDto(
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("location_updated_at") val updatedAt: String? = null,
    @SerialName("courier_name") val courierName: String? = null
)

/**
 * MapaTrackingScreen — muestra el mapa con la posición del repartidor en tiempo real.
 * Se embebe dentro de OrderTrackingScreen cuando el pedido está en estado "on_the_way".
 */
@Composable
fun MapaTrackingScreen(
    cartViewModel: CartViewModel,
    modifier: Modifier = Modifier
) {
    val activeOrder by cartViewModel.activeOrder.collectAsState()
    val orderId = activeOrder?.id

    var driverLatLng by remember { mutableStateOf<LatLng?>(null) }
    var courierName  by remember { mutableStateOf("") }
    var loading      by remember { mutableStateOf(true) }

    val cameraState = rememberCameraPositionState()

    // Cargar ubicación inicial y suscribirse a cambios via Realtime
    LaunchedEffect(orderId) {
        if (orderId == null) return@LaunchedEffect
        val supabase = SupabaseClient.client

        // Carga inicial via RPC
        try {
            val result = supabase.postgrest
                .rpc("get_driver_location", buildJsonObject {
                    put("p_order_id", orderId)
                })
                .decodeList<DriverLocationDto>()

            result.firstOrNull()?.let { loc ->
                if (loc.lat != null && loc.lng != null) {
                    driverLatLng = LatLng(loc.lat, loc.lng)
                    courierName  = loc.courierName ?: ""
                    cameraState.position = CameraPosition.fromLatLngZoom(driverLatLng!!, 15f)
                }
            }
        } catch (_: Exception) {}
        loading = false
        // Suscribirse a cambios en profiles del repartidor via Realtime
        // Escucha cambios en la tabla profiles filtrando por el courier del pedido
        val channel = supabase.realtime.channel("driver-location-$orderId")
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "profiles"
        }.onEach { update ->
            val lat = update.record["current_lat"]?.toString()?.toDoubleOrNull()
            val lng = update.record["current_lng"]?.toString()?.toDoubleOrNull()
            if (lat != null && lng != null) {
                val newPos = LatLng(lat, lng)
                driverLatLng = newPos
                // Seguir suavemente al repartidor
                cameraState.position = CameraPosition.fromLatLngZoom(newPos, 15f)
            }
        }.launchIn(this)

        channel.subscribe()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Mini card encima del mapa
        Card(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = JagooGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TwoWheeler,
                        contentDescription = "Repartidor",
                        tint = JagooYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(0.dp).also { })
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (courierName.isNotBlank())
                            "🛵 ${courierName} está en camino"
                        else
                            "🛵 Repartidor en camino",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Mapa
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            if (loading) {
                Box(Modifier.fillMaxSize().background(Color.LightGray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JagooGreen, modifier = Modifier.size(32.dp))
                }
            } else if (driverLatLng == null) {
                Box(Modifier.fillMaxSize().background(Color.LightGray.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🛵", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Esperando que el repartidor\nactive su GPS",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            color = JagooTextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraState,
                    properties = MapProperties(isMyLocationEnabled = false),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        scrollGesturesEnabled = true,
                        zoomGesturesEnabled = true
                    )
                ) {
                    driverLatLng?.let { pos ->
                        Marker(
                            state = MarkerState(position = pos),
                            title = courierName.ifBlank { "Repartidor" },
                            snippet = "En camino a tu dirección",
                            icon = BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_GREEN
                            )
                        )
                    }
                }
            }
        }
    }
}
