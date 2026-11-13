package com.jagoo.app.data.db

data class RoleEntity(
    val id: String,
    val name: String,
    val permissionsJson: String = "[]"
)

data class UserEntity(
    val id: String,
    val email: String,
    val fullName: String,
    val phone: String,
    val roleId: String,
    val restaurantId: String? = null
)

data class RestaurantEntity(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val logoUrl: String,
    val coverUrl: String,
    val brandColorHex: String,
    val rating: Double,
    val reviewCount: Int,
    val deliveryTime: String,
    val deliveryFee: Double,
    val minOrder: Double,
    val isOpen: Boolean = true,
    val isFeatured: Boolean = false,
    val isOffer: Boolean = false,
    val offerText: String? = null
)

data class MenuSectionEntity(
    val id: String,
    val restaurantId: String,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true
)

data class DishEntity(
    val id: String,
    val restaurantId: String,
    val sectionName: String,
    val name: String,
    val description: String,
    val price: Double,
    val imageUrl: String,
    val isAvailable: Boolean = true,
    val isPopular: Boolean = false,
    val isOffer: Boolean = false,
    val offerBadge: String? = null
)

data class OptionGroupEntity(
    val id: String,
    val dishId: String,
    val title: String,
    val isRequired: Boolean = false,
    val maxSelection: Int = 1
)

data class OptionEntity(
    val id: String,
    val groupId: String,
    val name: String,
    val price: Double = 0.0,
    val isAvailable: Boolean = true
)

data class OrderEntity(
    val id: String,
    val restaurantId: String,
    val restaurantName: String,
    val restaurantLogoUrl: String,
    val subtotal: Double,
    val deliveryFee: Double,
    val total: Double,
    val status: String,
    val createdAt: String
)

data class OrderItemEntity(
    val id: String,
    val orderId: String,
    val dishId: String,
    val dishName: String,
    val unitPrice: Double,
    val quantity: Int,
    val selectedOptionsJson: String = "[]"
)

data class AddressEntity(
    val id: String,
    val label: String, // Ej: "Casa", "Trabajo"
    val fullAddress: String,
    val isPrimary: Boolean = false
)
