package com.jagoo.driver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagoo.driver.data.remote.DriverOrderDto
import com.jagoo.driver.data.remote.SupabaseClient
import com.jagoo.driver.notifications.DriverNotificationManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DriverRepartidorViewModel(application: Application) : AndroidViewModel(application) {

    private val supabase = SupabaseClient.client
    private val auth     = supabase.auth
    private var realtimeChannel: RealtimeChannel? = null

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _pedidosDisponibles = MutableStateFlow<List<DriverOrderDto>>(emptyList())
    val pedidosDisponibles: StateFlow<List<DriverOrderDto>> = _pedidosDisponibles.asStateFlow()

    private val _misPedidos = MutableStateFlow<List<DriverOrderDto>>(emptyList())
    val misPedidos: StateFlow<List<DriverOrderDto>> = _misPedidos.asStateFlow()

    private val _historialEntregas = MutableStateFlow<List<DriverOrderDto>>(emptyList())
    val historialEntregas: StateFlow<List<DriverOrderDto>> = _historialEntregas.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        cargarEstadoInicial()
        cargarPedidos()
        suscribirRealtime()
    }

    private fun cargarEstadoInicial() {
        viewModelScope.launch {
            val userId = auth.currentUserOrNull()?.id ?: return@launch
            try {
                val profileMap = supabase.from("profiles")
                    .select { filter { eq("id", userId) } }
                    .decodeSingle<Map<String, Boolean>>()
                _isAvailable.value = profileMap["is_available"] ?: false
            } catch (_: Exception) {}
        }
    }

    private fun suscribirRealtime() {
        viewModelScope.launch {
            try {
                realtimeChannel = supabase.realtime.channel("driver_orders_channel")
                val flow = realtimeChannel?.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "orders"
                }
                realtimeChannel?.subscribe()
                flow?.collect { action ->
                    cargarPedidos()
                    if (action is PostgresAction.Insert || action is PostgresAction.Update) {
                        if (_isAvailable.value) {
                            DriverNotificationManager.showNewOrderNotification(
                                getApplication(),
                                "🚴 ¡Nuevo Pedido Disponible!",
                                "Hay una nueva entrega lista en Jagoo Driver"
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun toggleDisponible() {
        viewModelScope.launch {
            val userId = auth.currentUserOrNull()?.id ?: return@launch
            val nuevo  = !_isAvailable.value
            try {
                supabase.from("profiles")
                    .update({ set("is_available", nuevo) }) { filter { eq("id", userId) } }
                _isAvailable.value = nuevo
                if (nuevo) cargarPedidos()
            } catch (e: Exception) {
                _error.value = "Error al actualizar disponibilidad"
            }
        }
    }

    fun cargarPedidos() {
        viewModelScope.launch {
            _loading.value = true
            val userId = auth.currentUserOrNull()?.id
            try {
                // 1. Pedidos en ready sin courier_id
                val disponibles = supabase.from("orders")
                    .select { filter {
                        eq("status", "ready")
                        filter("courier_id", FilterOperator.IS, "null")
                    }}
                    .decodeList<DriverOrderDto>()
                _pedidosDisponibles.value = disponibles

                // 2. Mis pedidos en curso
                if (userId != null) {
                    val mios = supabase.from("orders")
                        .select { filter {
                            eq("courier_id", userId)
                            neq("status", "delivered")
                            neq("status", "cancelled")
                        }}
                        .decodeList<DriverOrderDto>()
                    _misPedidos.value = mios

                    // 3. Historial de entregas completadas
                    val completados = supabase.from("orders")
                        .select { filter {
                            eq("courier_id", userId)
                            eq("status", "delivered")
                        }}
                        .decodeList<DriverOrderDto>()
                    _historialEntregas.value = completados
                }
            } catch (e: Exception) {
                _error.value = "Error al cargar pedidos: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun aceptarPedido(orderId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject { put("p_order_id", orderId) }
                supabase.postgrest.rpc("accept_order", params)
                cargarPedidos()
                onSuccess()
            } catch (e: Exception) {
                onError("Pedido ya tomado por otro repartidor")
                cargarPedidos()
            }
        }
    }

    fun cambiarEstado(orderId: String, nuevoStatus: String) {
        viewModelScope.launch {
            try {
                supabase.from("orders")
                    .update({ set("status", nuevoStatus) }) {
                        filter {
                            eq("id", orderId)
                            eq("courier_id", auth.currentUserOrNull()?.id ?: "")
                        }
                    }
                cargarPedidos()
            } catch (e: Exception) {
                _error.value = "Transición de estado no permitida"
            }
        }
    }

    fun clearError() { _error.value = null }
}
