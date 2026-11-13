package com.jagoo.app.data

data class Product(
    val id: String,
    val name: String,
    val weightOrQuantity: String = "",
    val price: Double,
    val imageUrl: String,
    val category: String,
    val isOffer: Boolean = false,
    val offerBadge: String? = null
)

data class CartItem(
    val product: Product,
    val quantity: Int
)

data class Category(
    val id: String,
    val name: String,
    val iconName: String,
    val isSelected: Boolean = false
)

enum class OrderStatusStep {
    CONFIRMED,
    PREPARING,
    IN_TRANSIT,
    DELIVERED
}

data class OrderTimelineItem(
    val title: String,
    val time: String,
    val step: OrderStatusStep,
    val isCompleted: Boolean,
    val isCurrent: Boolean
)
