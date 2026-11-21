package com.jagoo.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagoo.app.data.ActiveOrder
import com.jagoo.app.data.CartItem
import com.jagoo.app.data.Dish
import com.jagoo.app.data.OrderStatusStep
import com.jagoo.app.data.OrderTimelineItem
import com.jagoo.app.data.RestaurantData
import com.jagoo.app.data.SelectedOption
import com.jagoo.app.data.remote.SupabaseClient
import com.jagoo.app.data.repository.LocalJagooRepository
import com.jagoo.app.data.repository.SupabaseJagooRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CartConflict(
    val newDish: Dish,
    val newRestaurantName: String,
    val currentRestaurantName: String,
    val selectedOptions: List<SelectedOption> = emptyList(),
    val specialInstructions: String = ""
)

/** Respuesta de la RPC validate_coupon */
@Serializable
data class CouponValidation(
    val id:        String  = "",
    val code:      String  = "",
    val type:      String  = "fixed",
    val value:     Double  = 0.0,
    @SerialName("min_order") val minOrder: Double = 0.0,
    val discount:  Double  = 0.0
)

class CartViewModel(application: Application) : AndroidViewModel(application) {

    private val localRepo    = LocalJagooRepository(application)
    private val supabaseRepo = SupabaseJagooRepository(localRepo)

    // ── Carrito ───────────────────────────────────────────────────────────────
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _couponCode    = MutableStateFlow("")
    val couponCode: StateFlow<String> = _couponCode.asStateFlow()

    private val _couponApplied = MutableStateFlow(false)
    val couponApplied: StateFlow<Boolean> = _couponApplied.asStateFlow()

    private val _couponDiscount = MutableStateFlow(0.0)
    val couponDiscount: StateFlow<Double> = _couponDiscount.asStateFlow()

    private val _couponError = MutableStateFlow<String?>(null)
    val couponError: StateFlow<String?> = _couponError.asStateFlow()

    private val _couponLoading = MutableStateFlow(false)
    val couponLoading: StateFlow<Boolean> = _couponLoading.asStateFlow()

    // Guardamos la validación para usarla al confirmar
    private var validatedCoupon: CouponValidation? = null

    private val _cartConflict  = MutableStateFlow<CartConflict?>(null)
    val cartConflict: StateFlow<CartConflict?> = _cartConflict.asStateFlow()

    // ── Pedido activo ─────────────────────────────────────────────────────────
    private val _activeOrder = MutableStateFlow<ActiveOrder?>(null)
    val activeOrder: StateFlow<ActiveOrder?> = _activeOrder.asStateFlow()

    // ── Historial de pedidos ──────────────────────────────────────────────────
    private val _orderHistory = MutableStateFlow<List<ActiveOrder>>(emptyList())
    val orderHistory: StateFlow<List<ActiveOrder>> = _orderHistory.asStateFlow()

    // ── Dirección de entrega seleccionada ─────────────────────────────────────
    // Empieza vacía; el CheckoutScreen la puebla desde la dirección primaria
    private val _deliveryAddress = MutableStateFlow("")
    val deliveryAddress: StateFlow<String> = _deliveryAddress.asStateFlow()

    fun setDeliveryAddress(address: String) { _deliveryAddress.value = address }

    init {
        // Cargar historial al iniciar (desde local como fallback inicial)
        _orderHistory.value = localRepo.getOrderHistory()
    }

    // ── Propiedades derivadas ─────────────────────────────────────────────────
    val currentRestaurantId: String?
        get() = _cartItems.value.firstOrNull()?.restaurantId

    val currentRestaurantName: String?
        get() = _cartItems.value.firstOrNull()?.restaurantName

    val deliveryFee: Double
        get() {
            val restId = currentRestaurantId ?: return 1.50
            return RestaurantData.restaurants.find { it.id == restId }?.deliveryFee ?: 1.50
        }

    fun getSubtotal() = _cartItems.value.sumOf { it.totalPrice }

    fun getTotal(): Double {
        val discount = _couponDiscount.value
        return (getSubtotal() + deliveryFee - discount).coerceAtLeast(0.0)
    }

    fun getItemQuantity(dishId: String) =
        _cartItems.value.filter { it.dish.id == dishId }.sumOf { it.quantity }

    // ── Carrito: agregar, quitar, vaciar ──────────────────────────────────────
    fun addToCart(
        dish: Dish,
        selectedOptions: List<SelectedOption> = emptyList(),
        specialInstructions: String = ""
    ) {
        val currentRestId = currentRestaurantId
        if (currentRestId != null && currentRestId != dish.restaurantId) {
            _cartConflict.value = CartConflict(
                newDish               = dish,
                newRestaurantName     = dish.restaurantName,
                currentRestaurantName = currentRestaurantName ?: "otro restaurante",
                selectedOptions       = selectedOptions,
                specialInstructions   = specialInstructions
            )
            return
        }
        addDishDirectly(dish, selectedOptions, specialInstructions)
    }

    fun confirmClearCartAndAddPending() {
        val conflict = _cartConflict.value ?: return
        _cartItems.value = emptyList()
        addDishDirectly(conflict.newDish, conflict.selectedOptions, conflict.specialInstructions)
        _cartConflict.value = null
    }

    fun dismissConflictDialog() { _cartConflict.value = null }

    private fun addDishDirectly(
        dish: Dish,
        selectedOptions: List<SelectedOption>,
        specialInstructions: String
    ) {
        _cartItems.update { items ->
            val existingIndex = items.indexOfFirst {
                it.dish.id == dish.id && it.selectedOptions == selectedOptions
            }
            if (existingIndex >= 0) {
                items.mapIndexed { index, item ->
                    if (index == existingIndex) item.copy(quantity = item.quantity + 1) else item
                }
            } else {
                items + CartItem(
                    dish                = dish,
                    restaurantId        = dish.restaurantId,
                    restaurantName      = dish.restaurantName,
                    quantity            = 1,
                    selectedOptions     = selectedOptions,
                    specialInstructions = specialInstructions
                )
            }
        }
    }

    fun updateQuantity(dishId: String, newQuantity: Int) {
        _cartItems.update { items ->
            if (newQuantity <= 0) items.filter { it.dish.id != dishId }
            else {
                val index = items.indexOfFirst { it.dish.id == dishId }
                if (index >= 0) items.mapIndexed { i, item ->
                    if (i == index) item.copy(quantity = newQuantity) else item
                } else items
            }
        }
    }

    fun removeItem(dishId: String) {
        _cartItems.update { it.filter { item -> item.dish.id != dishId } }
    }

    fun clearCart() { _cartItems.value = emptyList() }

    // ── Cupón — validación real contra Supabase ───────────────────────────────
    fun setCouponCode(code: String) {
        _couponCode.value = code
        _couponError.value = null
    }

    fun applyCoupon() {
        val code = _couponCode.value.trim()
        if (code.isBlank()) { _couponError.value = "Ingresa un código de cupón"; return }
        _couponLoading.value = true
        _couponError.value = null
        viewModelScope.launch {
            try {
                val supabase = SupabaseClient.client
                val results = supabase.postgrest
                    .rpc("validate_coupon", buildJsonObject {
                        put("p_code", code)
                        put("p_order_total", getSubtotal() + deliveryFee)
                    })
                    .decodeList<CouponValidation>()

                val result = results.firstOrNull()
                if (result != null && result.discount > 0) {
                    validatedCoupon    = result
                    _couponDiscount.value = result.discount
                    _couponApplied.value  = true
                } else {
                    _couponError.value = "Cupón no válido"
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Error al validar el cupón"
                _couponError.value = when {
                    msg.contains("no válido") || msg.contains("expirado") -> "Cupón no válido o expirado"
                    msg.contains("mínimo")                                 -> msg
                    else                                                   -> "Error al validar el cupón"
                }
            } finally {
                _couponLoading.value = false
            }
        }
    }

    fun removeCoupon() {
        validatedCoupon        = null
        _couponApplied.value   = false
        _couponDiscount.value  = 0.0
        _couponCode.value      = ""
        _couponError.value     = null
    }

    // ── Confirmar pedido — guarda en Supabase + local ─────────────────────────
    fun confirmOrder() {
        val items = _cartItems.value
        if (items.isEmpty()) return

        val restId   = currentRestaurantId ?: return
        val restName = currentRestaurantName ?: "Restaurante"
        val restLogo = RestaurantData.restaurants.find { it.id == restId }?.logoUrl ?: ""
        val subtotal = getSubtotal()
        val fee      = deliveryFee
        val discount = _couponDiscount.value
        val total    = getTotal()
        val orderId  = "JG-${(1000..9999).random()}"
        val address  = _deliveryAddress.value.ifBlank { "Sin dirección" }

        val timeline = listOf(
            OrderTimelineItem(
                title       = "Pedido confirmado",
                time        = java.time.LocalTime.now()
                    .let { "%02d:%02d".format(it.hour, it.minute) },
                step        = OrderStatusStep.CONFIRMED,
                isCompleted = true,
                isCurrent   = false
            ),
            OrderTimelineItem(
                title       = "En preparación en cocina",
                time        = "",
                step        = OrderStatusStep.PREPARING,
                isCompleted = false,
                isCurrent   = true
            ),
            OrderTimelineItem(
                title       = "Repartidor en camino",
                time        = "",
                step        = OrderStatusStep.IN_TRANSIT,
                isCompleted = false,
                isCurrent   = false
            ),
            OrderTimelineItem(
                title       = "Entrega estimada",
                time        = "~30 min",
                step        = OrderStatusStep.DELIVERED,
                isCompleted = false,
                isCurrent   = false
            )
        )

        val newOrder = ActiveOrder(
            id                = orderId,
            restaurantId      = restId,
            restaurantName    = restName,
            restaurantLogoUrl = restLogo,
            items             = items,
            subtotal          = subtotal,
            deliveryFee       = fee,
            total             = total,
            estimatedMinutes  = 25,
            statusStep        = OrderStatusStep.PREPARING,
            timelineItems     = timeline,
            courier           = null   // se asigna cuando el restaurante ponga status=ready
        )

        _activeOrder.value = newOrder

        // Guardar en Supabase (en segundo plano) y también localmente
        viewModelScope.launch {
            supabaseRepo.saveOrderSuspend(
                order           = newOrder,
                cartItems       = items,
                deliveryAddress = address
            )
            // Si había cupón aplicado, incrementar su uso en backend
            if (_couponApplied.value && validatedCoupon != null) {
                try {
                    val supabase = SupabaseClient.client
                    supabase.postgrest.rpc("apply_coupon", buildJsonObject {
                        put("p_code", validatedCoupon!!.code)
                        put("p_order_id", orderId)
                    })
                } catch (_: Exception) { /* No bloquear el pedido si falla */ }
            }
            // Refrescar historial desde Supabase
            refreshOrderHistory()
        }

        _cartItems.value       = emptyList()
        _couponApplied.value   = false
        _couponCode.value      = ""
        _couponDiscount.value  = 0.0
        _deliveryAddress.value = ""
        validatedCoupon        = null
    }

    // ── Historial ─────────────────────────────────────────────────────────────
    fun refreshOrderHistory() {
        viewModelScope.launch {
            _orderHistory.value = supabaseRepo.getOrderHistorySuspend()
        }
    }

    // ── Pagos — subir comprobante a Supabase Storage ─────────────────────────
    fun uploadPaymentProof(
        orderId: String,
        imageUri: Uri,
        referenceNumber: String?,
        amount: Double,
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val supabase = SupabaseClient.client

                // 1. Leer bytes de la imagen desde la Uri del dispositivo
                val bytes = context.contentResolver
                    .openInputStream(imageUri)
                    ?.use { it.readBytes() }
                    ?: throw Exception("No se pudo leer la imagen")

                // 2. Subir a Supabase Storage bucket "payment-proofs"
                val fileName = "proof_${orderId}_${System.currentTimeMillis()}.jpg"
                supabase.storage["payment-proofs"].upload(
                    path = fileName,
                    data = bytes
                )

                // 3. Obtener URL pública del archivo
                val imageUrl = supabase.storage["payment-proofs"].publicUrl(fileName)

                // 4. Registrar el comprobante en la BD via RPC
                supabase.postgrest.rpc("upload_payment_proof", buildJsonObject {
                    put("p_order_id", orderId)
                    put("p_image_url", imageUrl)
                    if (referenceNumber != null) put("p_reference_number", referenceNumber)
                    put("p_amount", amount)
                })

                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Error al subir el comprobante")
            }
        }
    }

}
