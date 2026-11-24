package com.jagoo.app.data

data class Restaurant(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val logoUrl: String,
    val coverUrl: String,
    val rating: Double,
    val reviewCount: Int = 120,
    val deliveryTime: String,
    val deliveryFee: Double,
    val minOrder: Double,
    val isOpen: Boolean = true,
    val brandColorHex: String = "#FFD600",
    val isFeatured: Boolean = false,
    val isOffer: Boolean = false,
    val offerText: String? = null,
    val businessType: String = BusinessType.RESTAURANTE.key,
    val isActive: Boolean = true
)

data class DishOption(
    val id: String,
    val name: String,
    val price: Double = 0.0
)

data class DishOptionGroup(
    val id: String,
    val title: String,
    val isRequired: Boolean = false,
    val maxSelection: Int = 1,
    val options: List<DishOption>
)

data class Dish(
    val id: String,
    val restaurantId: String,
    val restaurantName: String = "",
    val name: String,
    val description: String,
    val price: Double,
    val imageUrl: String,
    val menuSection: String,
    val optionGroups: List<DishOptionGroup> = emptyList(),
    val isAvailable: Boolean = true,
    val isPopular: Boolean = false,
    val isOffer: Boolean = false,
    val offerBadge: String? = null,
    /** Producto solo para mayores de 18 (licorería). */
    val isAgeRestricted: Boolean = false,
    /** Producto de farmacia que exige receta; si es false, es de venta libre. */
    val requiresPrescription: Boolean = false
)

data class SelectedOption(
    val optionGroupId: String,
    val optionGroupTitle: String,
    val optionId: String,
    val optionName: String,
    val optionPrice: Double
)

data class CartItem(
    val dish: Dish,
    val restaurantId: String,
    val restaurantName: String,
    val quantity: Int,
    val selectedOptions: List<SelectedOption> = emptyList(),
    val specialInstructions: String = ""
) {
    val unitPrice: Double
        get() = dish.price + selectedOptions.sumOf { it.optionPrice }

    val totalPrice: Double
        get() = unitPrice * quantity
}

data class Category(
    val id: String,
    val name: String,
    val iconEmoji: String,
    val iconName: String = "",
    val colorHex: String = "#FFF59D",
    val isSelected: Boolean = false
)

enum class OrderStatusStep {
    CONFIRMED,   // pending / accepted
    PREPARING,   // preparing
    READY,       // ready — listo para recoger
    IN_TRANSIT,  // picked_up / on_the_way
    DELIVERED
}

data class OrderTimelineItem(
    val title: String,
    val time: String,
    val step: OrderStatusStep,
    val isCompleted: Boolean,
    val isCurrent: Boolean
)

data class CourierInfo(
    val name: String,
    val vehicle: String,
    val plate: String
)

data class ActiveOrder(
    val id: String,
    val restaurantId: String,
    val restaurantName: String,
    val restaurantLogoUrl: String,
    val items: List<CartItem>,
    val subtotal: Double,
    val deliveryFee: Double,
    val total: Double,
    val estimatedMinutes: Int,
    val statusStep: OrderStatusStep,
    val timelineItems: List<OrderTimelineItem>,
    val courier: CourierInfo? = null,         // null hasta que se asigne repartidor
    val rawStatus: String = "pending",
    val paymentMethod: String = "cash",
    val paymentStatus: String = "pending",
    val changeDue: Double? = null,
    val transferDeadlineMillis: Long? = null,
    val cancelReason: String? = null,
    val ageCheckRequired: Boolean = false,
    val prescriptionRequired: Boolean = false,
    val prescriptionStatus: String = "none"
)

/** Tipos de negocio (valores del enum business_type en la base de datos). */
enum class BusinessType(val key: String, val label: String, val emoji: String, val colorHex: String) {
    RESTAURANTE("restaurante", "Restaurantes", "🍽️", "#FFE082"),
    TIENDA("tienda", "Tiendas", "🛒", "#C8E6C9"),
    FARMACIA("farmacia", "Medicamentos", "💊", "#BBDEFB"),
    LICORERIA("licoreria", "Licorerías", "🍷", "#E1BEE7");

    companion object {
        fun fromKey(key: String?): BusinessType =
            entries.firstOrNull { it.key == key } ?: RESTAURANTE
    }
}

/** Número corto y legible del pedido (el id real es un uuid). */
fun orderNumber(id: String): String =
    if (id.startsWith("JG-")) id else "JG-" + id.replace("-", "").take(6).uppercase()
