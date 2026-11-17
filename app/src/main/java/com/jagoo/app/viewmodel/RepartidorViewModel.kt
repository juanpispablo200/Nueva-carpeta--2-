package com.jagoo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagoo.app.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderResumen(
    val id: String                                       = "",
    @SerialName("restaurant_name") val restaurantName: String = "",
    @SerialName("delivery_address") val deliveryAddress: String? = null,
    val total: Double                                    = 0.0,
    val status: String                                   = "ready",
    @SerialName("created_at") val createdAt: String      = ""
)

class RepartidorViewModel(application: Application) : AndroidViewModel(application) {

    private val supabase = SupabaseClient.client
    private val auth     = supabase.auth

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

    init { cargarPedidos() }

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
                _error.value = "No se pudo cambiar el estado"
            }
        }
    }

    // ── Cargar pedidos disponibles (status = ready, sin repartidor) ───────────
    fun cargarPedidos() {
        viewModelScope.launch {
            _loading.value = true
            val userId = auth.currentUserOrNull()?.id
            try {
                // Pedidos listos sin repartidor asignado
                val disponibles = supabase.from("orders")
                    .select { filter {
                        eq("status", "ready")
                        filter("repartidor_id", FilterOperator.IS, "null")
                    }}
                    .decodeList<OrderResumen>()
                _pedidosDisponibles.value = disponibles

                // Mis pedidos activos
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
                _error.value = "Error al cargar pedidos: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Aceptar pedido (asignación atómica via RPC) ───────────────────────────
    fun aceptarPedido(orderId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val userId = auth.currentUserOrNull()?.id ?: run {
                onError("No autenticado"); return@launch
            }
            try {
                val ok = supabase.from("orders")
                    .select { filter {
                        eq("id", orderId)
                        eq("status", "ready")
                        filter("repartidor_id", FilterOperator.IS, "null")
                    }}
                    .decodeList<OrderResumen>()
                    .isNotEmpty()

                if (!ok) { onError("Este pedido ya fue tomado por otro repartidor"); return@launch }

                supabase.from("orders").update({
                    set("repartidor_id", userId)
                    set("status", "picked_up")
                }) { filter { eq("id", orderId) } }

                cargarPedidos()
                onSuccess()
            } catch (e: Exception) {
                onError("Error al aceptar: ${e.message}")
            }
        }
    }

    // ── Cambiar estado de un pedido propio ────────────────────────────────────
    fun cambiarEstado(orderId: String, nuevoStatus: String) {
        viewModelScope.launch {
            try {
                supabase.from("orders")
                    .update({ set("status", nuevoStatus) }) { filter { eq("id", orderId) } }
                cargarPedidos()
            } catch (e: Exception) {
                _error.value = "Error al actualizar estado"
            }
        }
    }

    fun clearError() { _error.value = null }
}
