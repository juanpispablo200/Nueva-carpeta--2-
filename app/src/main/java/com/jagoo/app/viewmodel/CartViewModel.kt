package com.jagoo.app.viewmodel

import androidx.lifecycle.ViewModel
import com.jagoo.app.data.CartItem
import com.jagoo.app.data.Dish
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
}
