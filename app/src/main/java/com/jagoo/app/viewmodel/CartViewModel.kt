package com.jagoo.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import com.jagoo.app.BuildConfig
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagoo.app.data.ActiveOrder
import com.jagoo.app.data.AgeRules
import com.jagoo.app.data.ImageCompressor
import com.jagoo.app.data.OrderError
import com.jagoo.app.data.OrderErrors
import com.jagoo.app.data.remote.DeliveryZoneDto
import com.jagoo.app.data.remote.buildTimeline
import com.jagoo.app.data.remote.parseIsoMillis
import com.jagoo.app.data.CartItem
import com.jagoo.app.data.Dish
import com.jagoo.app.data.OrderStatusStep
import com.jagoo.app.data.OrderTimelineItem
import com.jagoo.app.data.RestaurantData
import com.jagoo.app.data.SelectedOption
import com.jagoo.app.data.remote.SupabaseClient
import com.jagoo.app.data.repository.LocalJagooRepository
import com.jagoo.app.data.repository.SupabaseJagooRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.util.UUID

data class CartConflict(
    val newDish: Dish,
    val newRestaurantName: String,
    val currentRestaurantName: String,
    val selectedOptions: List<SelectedOption> = emptyList(),
    val specialInstructions: String = "",
    val quantity: Int = 1
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

    /**
     * Costo de envío estimado: el de la zona elegida; si no hay zonas
     * configuradas, la tarifa por defecto de ajustes. El total definitivo lo
     * calcula el servidor al crear el pedido.
     */
    val deliveryFee: Double
        get() = _selectedZone.value?.fee ?: _defaultFee.value

    // ── Zonas de envío y pedido mínimo (configurados por el admin) ────────────
    private val _zones = MutableStateFlow<List<DeliveryZoneDto>>(emptyList())
    val zones: StateFlow<List<DeliveryZoneDto>> = _zones.asStateFlow()

    private val _selectedZone = MutableStateFlow<DeliveryZoneDto?>(null)
    val selectedZone: StateFlow<DeliveryZoneDto?> = _selectedZone.asStateFlow()

    private val _defaultFee = MutableStateFlow(0.0)

    private val _minOrder = MutableStateFlow(0.0)
    val minOrder: StateFlow<Double> = _minOrder.asStateFlow()

    private val _configLoading = MutableStateFlow(false)
    val configLoading: StateFlow<Boolean> = _configLoading.asStateFlow()

    fun selectZone(zone: DeliveryZoneDto) { _selectedZone.value = zone }

    /** Carga zonas, tarifa por defecto y pedido mínimo al entrar al checkout. */
    fun loadCheckoutConfig() {
        viewModelScope.launch {
            _configLoading.value = true
            val supabase = SupabaseClient.client
            val restId = currentRestaurantId

            // Cada paso es independiente: si uno falla (p. ej. la tabla de zonas aún no
            // existe en el servidor), los demás se cargan igual y el envío nunca queda en $0 por error.

            // 1) Zonas de envío configuradas por el admin
            try {
                val zones = withTimeout(12_000L) {
                    supabase.from("delivery_zones")
                        .select { filter { eq("is_active", true) } }
                        .decodeList<DeliveryZoneDto>()
                        .sortedBy { it.sortOrder }
                }
                _zones.value = zones
                if (_selectedZone.value == null || zones.none { it.id == _selectedZone.value?.id }) {
                    _selectedZone.value = zones.singleOrNull()
                }
            } catch (e: Exception) {
                _zones.value = emptyList()
                _selectedZone.value = null
                if (BuildConfig.DEBUG) Log.w("Checkout", "delivery_zones no disponible: ${e.message?.take(160)}")
            }

            // 2) Tarifa propia del negocio y pedido mínimo (el servidor usa la misma tarifa si no hay zonas)
            if (restId != null) {
                try {
                    val row = withTimeout(12_000L) {
                        supabase.from("restaurants")
                            .select { filter { eq("id", restId) } }
                            .decodeSingle<RestaurantFeeRow>()
                    }
                    _defaultFee.value = row.deliveryFee
                    _minOrder.value = row.minOrder
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w("Checkout", "tarifa del negocio no disponible: ${e.message?.take(160)}")
                }

                // 3) Pedido mínimo efectivo (el del tipo de negocio manda sobre el del negocio)
                try {
                    _minOrder.value = withTimeout(12_000L) {
                        supabase.postgrest.rpc("get_effective_min_order", buildJsonObject {
                            put("p_restaurant_id", restId)
                        }).decodeAs<Double>()
                    }
                } catch (_: Exception) { /* se conserva el mínimo del negocio */ }
            }
            _configLoading.value = false
        }
    }

    fun getSubtotal() = _cartItems.value.sumOf { it.totalPrice }

    fun getTotal(): Double {
        val discount = _couponDiscount.value
        return (getSubtotal() + deliveryFee - discount).coerceAtLeast(0.0)
    }

    fun getItemQuantity(dishId: String) =
        _cartItems.value.filter { it.dish.id == dishId }.sumOf { it.quantity }

    // ── Carrito: agregar, quitar, vaciar ──────────────────────────────────────
    // ── Edad (licorería) ──────────────────────────────────────────────────────
    private val _ageStatus = MutableStateFlow(AgeStatus.UNKNOWN)
    val ageStatus: StateFlow<AgeStatus> = _ageStatus.asStateFlow()

    private val _ageGate = MutableStateFlow<AgeGateRequest?>(null)
    val ageGate: StateFlow<AgeGateRequest?> = _ageGate.asStateFlow()

    private val _ageSaving = MutableStateFlow(false)
    val ageSaving: StateFlow<Boolean> = _ageSaving.asStateFlow()

    private val _ageError = MutableStateFlow<String?>(null)
    val ageError: StateFlow<String?> = _ageError.asStateFlow()

    /** Agrega al carrito. Un solo negocio por pedido; los productos +18 piden la fecha de nacimiento. */
    fun addToCart(
        dish: Dish,
        selectedOptions: List<SelectedOption> = emptyList(),
        specialInstructions: String = "",
        quantity: Int = 1
    ) {
        if (dish.isAgeRestricted && _ageStatus.value != AgeStatus.ADULT) {
            requestAgeGate(AgeGateRequest(dish, selectedOptions, specialInstructions, quantity))
            return
        }
        addAfterAgeCheck(dish, selectedOptions, specialInstructions, quantity)
    }

    private fun addAfterAgeCheck(
        dish: Dish,
        selectedOptions: List<SelectedOption>,
        specialInstructions: String,
        quantity: Int = 1
    ) {
        val currentRestId = currentRestaurantId
        if (currentRestId != null && currentRestId != dish.restaurantId) {
            _cartConflict.value = CartConflict(
                newDish               = dish,
                newRestaurantName     = dish.restaurantName,
                currentRestaurantName = currentRestaurantName ?: "otro negocio",
                selectedOptions       = selectedOptions,
                specialInstructions   = specialInstructions,
                quantity              = quantity
            )
            return
        }
        addDishDirectly(dish, selectedOptions, specialInstructions, quantity)
    }

    private fun requestAgeGate(request: AgeGateRequest) {
        viewModelScope.launch {
            if (_ageStatus.value == AgeStatus.UNKNOWN) loadAgeStatus()
            when (_ageStatus.value) {
                AgeStatus.ADULT -> addAfterAgeCheck(request.dish, request.selectedOptions, request.specialInstructions, request.quantity)
                AgeStatus.MINOR -> _ageGate.value = request.copy(minor = true)
                else            -> _ageGate.value = request
            }
        }
    }

    private suspend fun loadAgeStatus() {
        try {
            val uid = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return
            val row = withTimeout(10_000L) {
                SupabaseClient.client.from("profiles")
                    .select { filter { eq("id", uid) } }
                    .decodeSingle<BirthDateRow>()
            }
            val birth = row.birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            _ageStatus.value = when {
                birth == null              -> AgeStatus.MISSING
                AgeRules.isAdult(birth)    -> AgeStatus.ADULT
                else                       -> AgeStatus.MINOR
            }
        } catch (_: Exception) {
            _ageStatus.value = AgeStatus.MISSING
        }
    }

    /** Guarda la fecha de nacimiento UNA vez (el servidor no deja cambiarla después). */
    fun submitBirthDate(day: String, month: String, year: String) {
        val request = _ageGate.value ?: return
        val birth = AgeRules.parseBirthDate(day, month, year)
        if (birth == null) {
            _ageError.value = "Escribe una fecha real: día, mes y año de nacimiento."
            return
        }
        viewModelScope.launch {
            _ageSaving.value = true
            _ageError.value = null
            try {
                val adult = withTimeout(12_000L) {
                    SupabaseClient.client.postgrest
                        .rpc("set_birth_date", buildJsonObject { put("p_birth", birth.toString()) })
                        .decodeAs<Boolean>()
                }
                _ageStatus.value = if (adult) AgeStatus.ADULT else AgeStatus.MINOR
                if (adult) {
                    _ageGate.value = null
                    addAfterAgeCheck(request.dish, request.selectedOptions, request.specialInstructions, request.quantity)
                } else {
                    _ageGate.value = request.copy(minor = true)
                }
            } catch (e: Exception) {
                _ageError.value = "No se pudo guardar la fecha. Revisa tu conexión e intenta de nuevo."
            } finally {
                _ageSaving.value = false
            }
        }
    }

    fun dismissAgeGate() {
        _ageGate.value = null
        _ageError.value = null
    }

    fun confirmClearCartAndAddPending() {
        val conflict = _cartConflict.value ?: return
        _cartItems.value = emptyList()
        addDishDirectly(conflict.newDish, conflict.selectedOptions, conflict.specialInstructions, conflict.quantity)
        _cartConflict.value = null
    }

    fun dismissConflictDialog() { _cartConflict.value = null }

    private fun addDishDirectly(
        dish: Dish,
        selectedOptions: List<SelectedOption>,
        specialInstructions: String,
        quantity: Int = 1
    ) {
        _cartItems.update { items ->
            val existingIndex = items.indexOfFirst {
                it.dish.id == dish.id && it.selectedOptions == selectedOptions
            }
            if (existingIndex >= 0) {
                items.mapIndexed { index, item ->
                    if (index == existingIndex) item.copy(quantity = item.quantity + quantity) else item
                }
            } else {
                items + CartItem(
                    dish                = dish,
                    restaurantId        = dish.restaurantId,
                    restaurantName      = dish.restaurantName,
                    quantity            = quantity,
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
                    .rpc("check_coupon", buildJsonObject {
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
                    msg.contains("DEMASIADOS_INTENTOS")                    -> "Demasiados intentos. Espera unos minutos e intenta de nuevo."
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

    // ── Receta (farmacia) ─────────────────────────────────────────────────────
    private val _prescriptionUri = MutableStateFlow<Uri?>(null)
    val prescriptionUri: StateFlow<Uri?> = _prescriptionUri.asStateFlow()

    fun setPrescriptionUri(uri: Uri?) { _prescriptionUri.value = uri }

    /** El carrito tiene productos que exigen receta (los de venta libre no). */
    val cartRequiresPrescription: Boolean
        get() = _cartItems.value.any { it.dish.requiresPrescription }

    val cartHasAgeRestricted: Boolean
        get() = _cartItems.value.any { it.dish.isAgeRestricted }

    // ── Crear pedido ──────────────────────────────────────────────────────────
    private val _orderState = MutableStateFlow<OrderPlacement>(OrderPlacement.Idle)
    val orderState: StateFlow<OrderPlacement> = _orderState.asStateFlow()

    fun resetOrderState() { _orderState.value = OrderPlacement.Idle }

    /**
     * Crea el pedido en el servidor y usa el uuid REAL que devuelve en toda la app.
     * paymentMethod: "cash" o "transfer". cashTendered: con cuánto paga (solo efectivo).
     */
    fun placeOrder(paymentMethod: String, cashTendered: Double?, ageConfirmed: Boolean = false) {
        if (_orderState.value is OrderPlacement.Loading) return
        val items = _cartItems.value
        val restId = currentRestaurantId
        if (items.isEmpty() || restId == null) {
            _orderState.value = OrderPlacement.Failed(OrderErrors.fromThrowable(IllegalStateException("CARRITO_VACIO")))
            return
        }
        val restName = currentRestaurantName ?: "Negocio"
        val restaurant = RestaurantData.restaurants.find { it.id == restId }
        val address = _deliveryAddress.value.ifBlank { "Sin dirección" }
        val coupon = if (_couponApplied.value) validatedCoupon?.code else null
        val zoneId = _selectedZone.value?.id

        viewModelScope.launch {
            _orderState.value = OrderPlacement.Loading
            var uploadedRxPath: String? = null
            val supabase = SupabaseClient.client
            try {
                // 1. Receta: se sube antes (la ruta debe empezar con <uid>/)
                if (items.any { it.dish.requiresPrescription }) {
                    val uri = _prescriptionUri.value
                        ?: throw IllegalStateException("RECETA_REQUERIDA")
                    val uid = supabase.auth.currentUserOrNull()?.id
                        ?: throw IllegalStateException("NO_AUTENTICADO")
                    val bytes = ImageCompressor.compressToJpeg(getApplication(), uri)
                    val path = "$uid/${UUID.randomUUID()}.jpg"
                    withTimeout(60_000L) { supabase.storage["prescriptions"].upload(path, bytes) }
                    uploadedRxPath = path
                }

                // 2. Pedido en el servidor
                val res = withTimeout(30_000L) {
                    supabaseRepo.createOrderSuspend(
                        restaurantId     = restId,
                        items            = items,
                        deliveryAddress  = address,
                        zoneId           = zoneId,
                        paymentMethod    = paymentMethod,
                        cashTendered     = if (paymentMethod == "cash") cashTendered else null,
                        couponCode       = coupon,
                        prescriptionPath = uploadedRxPath,
                        ageConfirmed     = ageConfirmed
                    )
                }

                val newOrder = ActiveOrder(
                    id                = res.orderId,
                    restaurantId      = restId,
                    restaurantName    = restName,
                    restaurantLogoUrl = restaurant?.logoUrl ?: "",
                    items             = items,
                    subtotal          = res.subtotal,
                    deliveryFee       = res.deliveryFee,
                    total             = res.total,
                    estimatedMinutes  = 25,
                    statusStep        = OrderStatusStep.CONFIRMED,
                    timelineItems     = buildTimeline("pending"),
                    courier           = null,
                    rawStatus         = "pending",
                    paymentMethod     = paymentMethod,
                    changeDue         = res.changeDue,
                    transferDeadlineMillis = parseIsoMillis(res.transferDeadline),
                    ageCheckRequired  = res.ageCheckRequired,
                    prescriptionRequired = res.prescriptionRequired,
                    prescriptionStatus   = if (res.prescriptionRequired) "pending" else "none"
                )
                _activeOrder.value = newOrder
                localRepo.saveOrder(newOrder)

                _cartItems.value       = emptyList()
                _couponApplied.value   = false
                _couponCode.value      = ""
                _couponDiscount.value  = 0.0
                _deliveryAddress.value = ""
                _prescriptionUri.value = null
                validatedCoupon        = null

                _orderState.value = OrderPlacement.Placed(newOrder)
                refreshOrderHistory()
            } catch (e: Exception) {
                // Si el pedido falló, no dejamos la receta huérfana en Storage
                uploadedRxPath?.let { path ->
                    try { supabase.storage["prescriptions"].delete(path) } catch (_: Exception) {}
                }
                val err = OrderErrors.fromThrowable(e)
                if (BuildConfig.DEBUG) Log.e("CrearPedido", "code=${err.code} tipo=${e::class.java.simpleName} detalle=${err.detail}")
                _orderState.value = OrderPlacement.Failed(err)
            }
        }
    }

    // ── Historial ─────────────────────────────────────────────────────────────
    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()

    private val _historyError = MutableStateFlow<String?>(null)
    val historyError: StateFlow<String?> = _historyError.asStateFlow()

    fun refreshOrderHistory() {
        viewModelScope.launch {
            _historyLoading.value = true
            _historyError.value   = null
            try {
                val result = supabaseRepo.getOrderHistorySuspend()
                _orderHistory.value = result
            } catch (e: Exception) {
                _historyError.value = "No se pudo cargar el historial. Verifica tu conexión."
                // Mantiene datos locales como fallback
            } finally {
                _historyLoading.value = false
            }
        }
    }

    // ── Transferencia: subir comprobante (obligatorio) ───────────────────────
    /**
     * Comprime la foto y la sube al bucket privado payment-proofs en <uid>/…;
     * luego registra el comprobante (RPC upload_payment_proof). El pedido pasa a
     * "verifying" y el admin lo revisa. Sin comprobante a tiempo, el servidor
     * cancela el pedido.
     */
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
                val uid = supabase.auth.currentUserOrNull()?.id
                    ?: throw IllegalStateException("NO_AUTENTICADO")

                val bytes = ImageCompressor.compressToJpeg(context, imageUri)
                val path = "$uid/${orderId}_${System.currentTimeMillis()}.jpg"
                withTimeout(60_000L) { supabase.storage["payment-proofs"].upload(path, bytes) }

                withTimeout(30_000L) {
                    supabase.postgrest.rpc("upload_payment_proof", buildJsonObject {
                        put("p_order_id", orderId)
                        put("p_image_url", path)          // ruta del archivo en el bucket privado
                        put("p_reference_number", referenceNumber)
                        put("p_amount", amount)
                    })
                }

                _activeOrder.value = _activeOrder.value?.takeIf { it.id == orderId }
                    ?.copy(paymentStatus = "verifying") ?: _activeOrder.value
                onSuccess()
            } catch (e: Exception) {
                onError(proofErrorMessage(e))
            }
        }
    }

    private fun proofErrorMessage(e: Exception): String {
        val raw = e.message.orEmpty()
        return when {
            raw.contains("PEDIDO_NO_PAGABLE") ||
            raw.contains("PEDIDO_CANCELADO")  -> "Este pedido ya fue cancelado. Haz uno nuevo."
            raw.contains("COMPROBANTE_YA_ENVIADO") -> "Ya enviaste un comprobante. Espera la verificación."
            e is kotlinx.coroutines.TimeoutCancellationException ||
            raw.contains("timeout", true) || raw.contains("unable to resolve", true) ->
                "Conexión lenta o sin internet. Intenta de nuevo."
            else -> "No se pudo enviar el comprobante. Intenta de nuevo."
        }
    }

}

/** Estado de la creación del pedido. */
sealed class OrderPlacement {
    object Idle : OrderPlacement()
    object Loading : OrderPlacement()
    data class Placed(val order: ActiveOrder) : OrderPlacement()
    data class Failed(val error: OrderError) : OrderPlacement()
}

enum class AgeStatus { UNKNOWN, MISSING, ADULT, MINOR }

/** Producto +18 que espera la confirmación de edad. */
data class AgeGateRequest(
    val dish: Dish,
    val selectedOptions: List<SelectedOption> = emptyList(),
    val specialInstructions: String = "",
    val quantity: Int = 1,
    val minor: Boolean = false
)

@Serializable
private data class BirthDateRow(@SerialName("birth_date") val birthDate: String? = null)

@Serializable
private data class RestaurantFeeRow(
    @SerialName("delivery_fee") val deliveryFee: Double = 0.0,
    @SerialName("min_order")    val minOrder: Double = 0.0
)
