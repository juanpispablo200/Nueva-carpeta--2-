package com.jagoo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagoo.app.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderItemResumen(
    val id: String                                    = "",
    @SerialName("dish_name") val dishName: String    = "",
    val quantity: Int                                 = 1,
    @SerialName("unit_price") val unitPrice: Double   = 0.0
)

@Serializable
data class OrderResumen(
    val id: String                                            = "",
    @SerialName("restaurant_name") val restaurantName: String = "",
    @SerialName("restaurant_phone") val restaurantPhone: String? = "+593 99 111 2222",
    @SerialName("customer_name") val customerName: String?     = "Cliente Jagoo",
    @SerialName("customer_phone") val customerPhone: String?   = "+593 99 999 8888",
    @SerialName("delivery_address") val deliveryAddress: String? = null,
    val total: Double                                         = 0.0,
    val status: String                                        = "ready",
    @SerialName("created_at") val createdAt: String           = "",
    val items: List<OrderItemResumen>                         = emptyList()
)

class RepartidorViewModel(application: Application) : AndroidViewModel(application) {

    private val supabase = SupabaseClient.client
    private val auth     = supabase.auth
    private var realtimeChannel: RealtimeChannel? = null

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _pedidosDisponibles = MutableStateFlow<List<OrderResumen>>(emptyList())
    val pedidosDisponibles: StateFlow<List<OrderResumen>> = _pedidosDisponibles.asStateFlow()

    private val _misPedidos = MutableStateFlow<List<OrderResumen>>(emptyList())
    val misPedidos: StateFlow<List<OrderResumen>> = _misPedidos.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        cargarPedidos()
        suscribirRealtime()
    }

    // ── Suscripción Supabase Realtime a cambios en orders ──────────────────────
    private fun suscribirRealtime() {
        viewModelScope.launch {
            try {
                realtimeChannel = supabase.realtime.channel("repartidor_orders")
                val flow = realtimeChannel?.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "orders"
                }
                realtimeChannel?.subscribe()
                flow?.collect {
                    cargarPedidos()
                }
            } catch (_: Exception) {
                // Fallback silencioso si no hay WebSocket disponible
            }
        }
    }

    // ── Toggle disponible/no disponible ───────────────────────────────────────
    fun toggleDisponible() {
        viewModelScope.launch {
            val userId = auth.currentUserOrNull()?.id ?: return@launch
            val nuevo  = !_isAvailable.value
            try {
                supabase.from("repartidores")
                    .upsert(mapOf("id" to userId, "is_available" to nuevo))
                _isAvailable.value = nuevo
                if (nuevo) cargarPedidos()
            } catch (e: Exception) {
                _error.value = "No se pudo cambiar el estado de disponibilidad"
            }
        }
    }

    // ── Cargar pedidos en tiempo real (disponibles y asignados) ───────────────
    fun cargarPedidos() {
        viewModelScope.launch {
            _loading.value = true
            val userId = auth.currentUserOrNull()?.id
            try {
                // 1. Pedidos listos (ready) sin repartidor asignado
                val disponibles = supabase.from("orders")
                    .select { filter {
                        eq("status", "ready")
                        filter("repartidor_id", FilterOperator.IS, "null")
                    }}
                    .decodeList<OrderResumen>()
                _pedidosDisponibles.value = disponibles

                // 2. Pedidos asignados al repartidor actual (ready, picked_up, on_the_way)
                if (userId != null) {
                    val mios = supabase.from("orders")
                        .select { filter {
                            eq("repartidor_id", userId)
                            neq("status", "delivered")
                            neq("status", "cancelled")
                        }}
                        .decodeList<OrderResumen>()
                    _misPedidos.value = mios
                }
            } catch (e: Exception) {
                _error.value = "Error al obtener pedidos: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Aceptar pedido (asignación de repartidor) ─────────────────────────────
    fun aceptarPedido(orderId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val userId = auth.currentUserOrNull()?.id ?: run {
                onError("No autenticado"); return@launch
            }
            try {
                val libre = supabase.from("orders")
                    .select { filter {
                        eq("id", orderId)
                        filter("repartidor_id", FilterOperator.IS, "null")
                    }}
                    .decodeList<OrderResumen>()
                    .isNotEmpty()

                if (!libre) {
                    onError("Este pedido ya fue tomado por otro repartidor")
                    cargarPedidos()
                    return@launch
                }

                supabase.from("orders").update({
                    set("repartidor_id", userId)
                    set("status", "picked_up")
                }) { filter { eq("id", orderId) } }

                cargarPedidos()
                onSuccess()
            } catch (e: Exception) {
                onError(friendlyError(e))
            }
        }
    }

    // ── Avanzar estado (picked_up ➔ on_the_way ➔ delivered) ───────────────────
    fun cambiarEstado(orderId: String, nuevoStatus: String) {
        viewModelScope.launch {
            try {
                supabase.from("orders")
                    .update({ set("status", nuevoStatus) }) {
                        filter {
                            eq("id", orderId)
                            eq("repartidor_id", auth.currentUserOrNull()?.id ?: "")
                        }
                    }
                cargarPedidos()
            } catch (e: Exception) {
                _error.value = friendlyError(e)
            }
        }
    }

    fun clearError() { _error.value = null }

    private fun friendlyError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("Transición") || msg.contains("invalid status") || msg.contains("check constraint") ->
                "Transición de estado no permitida por la base de datos."
            msg.contains("policy") || msg.contains("row-level security") || msg.contains("RLS") ->
                "No tienes permisos para modificar este pedido."
            else -> "Error al actualizar estado. Intenta de nuevo."
        }
    }
}
