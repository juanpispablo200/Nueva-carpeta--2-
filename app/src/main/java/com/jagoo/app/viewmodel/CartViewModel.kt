package com.jagoo.app.viewmodel

import androidx.lifecycle.ViewModel
import com.jagoo.app.data.ActiveOrder
import com.jagoo.app.data.CartItem
import com.jagoo.app.data.Dish
import com.jagoo.app.data.OrderStatusStep
import com.jagoo.app.data.OrderTimelineItem
import com.jagoo.app.data.RestaurantData
import com.jagoo.app.data.SelectedOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CartConflict(
    val newDish: Dish,
    val newRestaurantName: String,
    val currentRestaurantName: String,
    val selectedOptions: List<SelectedOption> = emptyList(),
    val specialInstructions: String = ""
)

class CartViewModel : ViewModel() {

    private val _cartItems = MutableStateFlow<List<CartItem>>(RestaurantData.initialCartItems)
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _couponCode = MutableStateFlow("")
    val couponCode: StateFlow<String> = _couponCode.asStateFlow()

    private val _couponApplied = MutableStateFlow(false)
    val couponApplied: StateFlow<Boolean> = _couponApplied.asStateFlow()

    private val _cartConflict = MutableStateFlow<CartConflict?>(null)
    val cartConflict: StateFlow<CartConflict?> = _cartConflict.asStateFlow()

    // Pedido activo confirmado (se puebla al hacer "Confirmar Pedido")
    private val _activeOrder = MutableStateFlow<ActiveOrder?>(null)
    val activeOrder: StateFlow<ActiveOrder?> = _activeOrder.asStateFlow()

    val currentRestaurantId: String?
        get() = _cartItems.value.firstOrNull()?.restaurantId

    val currentRestaurantName: String?
        get() = _cartItems.value.firstOrNull()?.restaurantName

    val deliveryFee: Double
        get() {
            val restId = currentRestaurantId ?: return 1.50
            return RestaurantData.restaurants.find { it.id == restId }?.deliveryFee ?: 1.50
        }

    fun getSubtotal(): Double {
        return _cartItems.value.sumOf { it.totalPrice }
    }

    fun getTotal(): Double {
        val subtotal = getSubtotal()
        val discount = if (_couponApplied.value) 2.0 else 0.0
        return (subtotal + deliveryFee - discount).coerceAtLeast(0.0)
    }

    fun getItemQuantity(dishId: String): Int {
        return _cartItems.value.filter { it.dish.id == dishId }.sumOf { it.quantity }
    }

    fun addToCart(
        dish: Dish,
        selectedOptions: List<SelectedOption> = emptyList(),
        specialInstructions: String = ""
    ) {
        val currentRestId = currentRestaurantId
        if (currentRestId != null && currentRestId != dish.restaurantId) {
            // Conflicto de restaurante: intentar agregar plato de otro restaurante
            _cartConflict.value = CartConflict(
                newDish = dish,
                newRestaurantName = dish.restaurantName,
                currentRestaurantName = currentRestaurantName ?: "otro restaurante",
                selectedOptions = selectedOptions,
                specialInstructions = specialInstructions
            )
            return
        }

        // Agregar normalmente
        addDishDirectly(dish, selectedOptions, specialInstructions)
    }

    fun confirmClearCartAndAddPending() {
        val conflict = _cartConflict.value ?: return
        _cartItems.value = emptyList()
        addDishDirectly(conflict.newDish, conflict.selectedOptions, conflict.specialInstructions)
        _cartConflict.value = null
    }

    fun dismissConflictDialog() {
        _cartConflict.value = null
    }

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
                    if (index == existingIndex) item.copy(quantity = item.quantity + 1)
                    else item
                }
            } else {
                items + CartItem(
                    dish = dish,
                    restaurantId = dish.restaurantId,
                    restaurantName = dish.restaurantName,
                    quantity = 1,
                    selectedOptions = selectedOptions,
                    specialInstructions = specialInstructions
                )
            }
        }
    }

    fun updateQuantity(dishId: String, newQuantity: Int) {
        _cartItems.update { items ->
            if (newQuantity <= 0) {
                items.filter { it.dish.id != dishId }
            } else {
                val index = items.indexOfFirst { it.dish.id == dishId }
                if (index >= 0) {
                    items.mapIndexed { i, item ->
                        if (i == index) item.copy(quantity = newQuantity)
                        else item
                    }
                } else items
            }
        }
    }

    fun removeItem(dishId: String) {
        _cartItems.update { items ->
            items.filter { it.dish.id != dishId }
        }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
    }

    fun setCouponCode(code: String) {
        _couponCode.value = code
    }

    fun applyCoupon() {
        if (_couponCode.value.isNotBlank()) {
            _couponApplied.value = true
        }
    }

    /**
     * Convierte el carrito actual en un ActiveOrder y lo coloca en [activeOrder].
     * Después limpia el carrito para que quede vacío.
     */
    fun confirmOrder() {
        val items = _cartItems.value
        if (items.isEmpty()) return

        val restId = currentRestaurantId ?: return
        val restName = currentRestaurantName ?: "Restaurante"
        val restLogo = RestaurantData.restaurants.find { it.id == restId }?.logoUrl ?: ""
        val subtotal = getSubtotal()
        val fee = deliveryFee
        val discount = if (_couponApplied.value) 2.0 else 0.0
        val total = (subtotal + fee - discount).coerceAtLeast(0.0)

        val orderId = "JG-${(1000..9999).random()}"

        val timeline = listOf(
            OrderTimelineItem(
                title = "Pedido confirmado",
                time = java.time.LocalTime.now()
                    .let { "%02d:%02d".format(it.hour, it.minute) },
                step = OrderStatusStep.CONFIRMED,
                isCompleted = true,
                isCurrent = false
            ),
            OrderTimelineItem(
                title = "En preparación en cocina",
                time = "",
                step = OrderStatusStep.PREPARING,
                isCompleted = false,
                isCurrent = true
            ),
            OrderTimelineItem(
                title = "Repartidor en camino",
                time = "",
                step = OrderStatusStep.IN_TRANSIT,
                isCompleted = false,
                isCurrent = false
            ),
            OrderTimelineItem(
                title = "Entrega estimada",
                time = "~30 min",
                step = OrderStatusStep.DELIVERED,
                isCompleted = false,
                isCurrent = false
            )
        )

        _activeOrder.value = ActiveOrder(
            id = orderId,
            restaurantId = restId,
            restaurantName = restName,
            restaurantLogoUrl = restLogo,
            items = items,
            subtotal = subtotal,
            deliveryFee = fee,
            total = total,
            estimatedMinutes = 25,
            statusStep = OrderStatusStep.PREPARING,
            timelineItems = timeline,
            courierName = "Asignando...",
            courierVehicle = "Moto"
        )

        _cartItems.value = emptyList()
        _couponApplied.value = false
        _couponCode.value = ""
    }
}
