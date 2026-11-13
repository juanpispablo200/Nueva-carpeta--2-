package com.jagoo.app.viewmodel

import androidx.lifecycle.ViewModel
import com.jagoo.app.data.CartItem
import com.jagoo.app.data.MockData
import com.jagoo.app.data.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CartViewModel : ViewModel() {

    private val _cartItems = MutableStateFlow<List<CartItem>>(MockData.initialCartItems)
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _couponCode = MutableStateFlow("")
    val couponCode: StateFlow<String> = _couponCode.asStateFlow()

    private val _couponApplied = MutableStateFlow(false)
    val couponApplied: StateFlow<Boolean> = _couponApplied.asStateFlow()

    val deliveryFee = 1.50

    fun getSubtotal(): Double {
        return _cartItems.value.sumOf { it.product.price * it.quantity }
    }

    fun getTotal(): Double {
        val subtotal = getSubtotal()
        val discount = if (_couponApplied.value) 2.0 else 0.0
        return (subtotal + deliveryFee - discount).coerceAtLeast(0.0)
    }

    fun getItemQuantity(productId: String): Int {
        return _cartItems.value.find { it.product.id == productId }?.quantity ?: 0
    }

    fun addToCart(product: Product) {
        _cartItems.update { items ->
            val existing = items.find { it.product.id == product.id }
            if (existing != null) {
                items.map {
                    if (it.product.id == product.id) it.copy(quantity = it.quantity + 1)
                    else it
                }
            } else {
                items + CartItem(product = product, quantity = 1)
            }
        }
    }

    fun updateQuantity(productId: String, newQuantity: Int) {
        _cartItems.update { items ->
            if (newQuantity <= 0) {
                items.filter { it.product.id != productId }
            } else {
                items.map {
                    if (it.product.id == productId) it.copy(quantity = newQuantity)
                    else it
                }
            }
        }
    }

    fun removeItem(productId: String) {
        _cartItems.update { items ->
            items.filter { it.product.id != productId }
        }
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
