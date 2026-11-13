package com.jagoo.app.data

object MockData {
    val categories = listOf(
        Category(id = "1", name = "Frutas y\nverduras", iconName = "apple"),
        Category(id = "2", name = "Carnes", iconName = "meat"),
        Category(id = "3", name = "Lácteos", iconName = "dairy"),
        Category(id = "4", name = "Abarrotes", iconName = "groceries"),
        Category(id = "5", name = "Bebidas", iconName = "drinks")
    )

    val filterChips = listOf(
        "Todos", "Ofertas", "Orgánico", "Local", "Precio ↑"
    )

    val products = listOf(
        Product(
            id = "p1",
            name = "Banano orito",
            weightOrQuantity = "1 kg",
            price = 1.20,
            imageUrl = "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=500",
            category = "Frutas y verduras",
            isOffer = true,
            offerBadge = "Oferta"
        ),
        Product(
            id = "p2",
            name = "Queso fresco",
            weightOrQuantity = "500 g",
            price = 3.80,
            imageUrl = "https://images.unsplash.com/photo-1552767059-ce182ead8c1b?w=500",
            category = "Lácteos",
            isOffer = true,
            offerBadge = "Oferta"
        ),
        Product(
            id = "p3",
            name = "Tomate riñón",
            weightOrQuantity = "1 lb",
            price = 0.85,
            imageUrl = "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=500",
            category = "Frutas y verduras",
            isOffer = false
        ),
        Product(
            id = "p4",
            name = "Aguacate",
            weightOrQuantity = "",
            price = 1.50,
            imageUrl = "https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?w=500",
            category = "Frutas y verduras",
            isOffer = false
        ),
        Product(
            id = "p5",
            name = "Mora de castilla",
            weightOrQuantity = "500 g",
            price = 2.40,
            imageUrl = "https://images.unsplash.com/photo-1438283173091-5dbf5c5a3236?w=500",
            category = "Frutas y verduras",
            isOffer = false
        ),
        Product(
            id = "p6",
            name = "Naranja",
            weightOrQuantity = "12 u",
            price = 2.00,
            imageUrl = "https://images.unsplash.com/photo-1611080626919-7cf5a9dbab5b?w=500",
            category = "Frutas y verduras",
            isOffer = false
        ),
        Product(
            id = "p7",
            name = "Aguacate Hass",
            weightOrQuantity = "1 kg",
            price = 5.80,
            imageUrl = "https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?w=500",
            category = "Frutas y verduras",
            isOffer = false
        ),
        Product(
            id = "p8",
            name = "Leche entera",
            weightOrQuantity = "1 L",
            price = 1.10,
            imageUrl = "https://images.unsplash.com/photo-1563636619-e9143da7973b?w=500",
            category = "Lácteos",
            isOffer = false
        )
    )

    val initialCartItems = listOf(
        CartItem(product = products[6], quantity = 1), // Aguacate Hass 1 kg, $5.80
        CartItem(product = products[7], quantity = 1)  // Leche entera 1 L, $1.10
    )

    val orderTimelineItems = listOf(
        OrderTimelineItem(
            title = "Confirmado",
            time = "14:02",
            step = OrderStatusStep.CONFIRMED,
            isCompleted = true,
            isCurrent = false
        ),
        OrderTimelineItem(
            title = "Preparando",
            time = "14:08",
            step = OrderStatusStep.PREPARING,
            isCompleted = true,
            isCurrent = false
        ),
        OrderTimelineItem(
            title = "En camino",
            time = "14:21",
            step = OrderStatusStep.IN_TRANSIT,
            isCompleted = false,
            isCurrent = true
        ),
        OrderTimelineItem(
            title = "Entregado",
            time = "est. 14:39",
            step = OrderStatusStep.DELIVERED,
            isCompleted = false,
            isCurrent = false
        )
    )
}
