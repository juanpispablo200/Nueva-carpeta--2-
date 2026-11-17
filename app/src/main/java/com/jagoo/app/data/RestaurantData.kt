package com.jagoo.app.data

object RestaurantData {

    val categories = listOf(
        Category(id = "1", name = "Hamburguesas", iconEmoji = "🍔", iconName = "burger", colorHex = "#FFE082"),
        Category(id = "2", name = "Pizza & Pastas", iconEmoji = "🍕", iconName = "pizza", colorHex = "#FFCC80"),
        Category(id = "3", name = "Café & Postres", iconEmoji = "☕", iconName = "coffee", colorHex = "#D7CCC8"),
        Category(id = "4", name = "Comida Típica", iconEmoji = "🍲", iconName = "tipica", colorHex = "#C8E6C9"),
        Category(id = "5", name = "Sushi & Ramen", iconEmoji = "🍣", iconName = "sushi", colorHex = "#FFCDD2"),
        Category(id = "6", name = "Pollo & Parrilla", iconEmoji = "🍗", iconName = "chicken", colorHex = "#FFE0B2"),
        Category(id = "7", name = "Saludable", iconEmoji = "🥗", iconName = "healthy", colorHex = "#DCEDC8")
    )

    val filterChips = listOf(
        "Todos", "Populares", "Ofertas", "Envío Gratis", "Mejor Evaluados", "Abiertos"
    )

    val restaurants = listOf(
        Restaurant(
            id = "r1",
            name = "Brasa & Pan",
            category = "Hamburguesas",
            description = "Hamburguesas artesanales 100% carne de res a la parrilla de carbón.",
            logoUrl = "https://images.unsplash.com/photo-1550547660-d9450f859349?w=300",
            coverUrl = "https://images.unsplash.com/photo-1586190848861-99aa4a171e90?w=800",
            rating = 4.8,
            reviewCount = 340,
            deliveryTime = "25-35 min",
            deliveryFee = 1.50,
            minOrder = 6.00,
            isOpen = true,
            brandColorHex = "#D32F2F",
            isFeatured = true,
            isOffer = true,
            offerText = "20% OFF en combos"
        ),
        Restaurant(
            id = "r2",
            name = "Casa Nonna",
            category = "Pizza & Pastas",
            description = "Pastas frescas hechas a mano y pizzas en horno de piedra estilo italiano.",
            logoUrl = "https://images.unsplash.com/photo-1579684947550-22e945225d9a?w=300",
            coverUrl = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=800",
            rating = 4.7,
            reviewCount = 215,
            deliveryTime = "30-40 min",
            deliveryFee = 2.00,
            minOrder = 8.00,
            isOpen = true,
            brandColorHex = "#388E3C",
            isFeatured = true,
            isOffer = false
        ),
        Restaurant(
            id = "r3",
            name = "Café Tertulia",
            category = "Café & Postres",
            description = "Especialistas en cafés de origen ecuatoriano, repostería y desayunos.",
            logoUrl = "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=300",
            coverUrl = "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=800",
            rating = 4.9,
            reviewCount = 412,
            deliveryTime = "15-25 min",
            deliveryFee = 1.25,
            minOrder = 4.00,
            isOpen = true,
            brandColorHex = "#6D4C41",
            isFeatured = false,
            isOffer = true,
            offerText = "Envío Gratis desde $12"
        ),
        Restaurant(
            id = "r4",
            name = "Sabor Lojano",
            category = "Comida Típica",
            description = "Auténticos sabores del sur del Ecuador: cecina, repe y tamales lojanos.",
            logoUrl = "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=300",
            coverUrl = "https://images.unsplash.com/photo-1544025162-d76694265947?w=800",
            rating = 4.6,
            reviewCount = 180,
            deliveryTime = "25-35 min",
            deliveryFee = 1.75,
            minOrder = 5.00,
            isOpen = true,
            brandColorHex = "#F57C00",
            isFeatured = true,
            isOffer = false
        ),
        Restaurant(
            id = "r5",
            name = "Sushi Kenzo",
            category = "Sushi & Ramen",
            description = "Rolls gourmet, nigiris frescos y ramen reconfortante de preparación al instante.",
            logoUrl = "https://images.unsplash.com/photo-1611143669185-af224c5e3252?w=300",
            coverUrl = "https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=800",
            rating = 4.8,
            reviewCount = 290,
            deliveryTime = "35-45 min",
            deliveryFee = 2.25,
            minOrder = 10.00,
            isOpen = true,
            brandColorHex = "#C62828",
            isFeatured = false,
            isOffer = true,
            offerText = "Roll gratis > $20"
        ),
        Restaurant(
            id = "r6",
            name = "Pollos Don Chicho",
            category = "Pollo & Parrilla",
            description = "El mejor pollo broaster crocante y alitas barbecue con receta familiar secreta.",
            logoUrl = "https://images.unsplash.com/photo-1598515214211-89d3c73ae83b?w=300",
            coverUrl = "https://images.unsplash.com/photo-1626645738196-c2a7c87a8f58?w=800",
            rating = 4.5,
            reviewCount = 520,
            deliveryTime = "20-30 min",
            deliveryFee = 1.50,
            minOrder = 5.00,
            isOpen = true,
            brandColorHex = "#E65100",
            isFeatured = false,
            isOffer = false
        ),
        Restaurant(
            id = "r7",
            name = "Green Bowl",
            category = "Saludable",
            description = "Bowls nutritivos, ensaladas de estación y smoothies 100% naturales.",
            logoUrl = "https://images.unsplash.com/photo-1540420773420-3366772f4999?w=300",
            coverUrl = "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=800",
            rating = 4.7,
            reviewCount = 145,
            deliveryTime = "20-30 min",
            deliveryFee = 1.50,
            minOrder = 6.00,
            isOpen = true,
            brandColorHex = "#2E7D32",
            isFeatured = false,
            isOffer = false
        )
    )

    val dishes = listOf(
        // --- Brasa & Pan (r1) ---
        Dish(
            id = "d1",
            restaurantId = "r1",
            restaurantName = "Brasa & Pan",
            name = "Clásica Brasa",
            description = "Carne a la parrilla (150g), queso cheddar fundido, lechuga fresca, tomate y salsa artesanal de la casa.",
            price = 6.50,
            imageUrl = "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=500",
            menuSection = "Hamburguesas",
            isPopular = true,
            isOffer = false,
            optionGroups = listOf(
                DishOptionGroup(
                    id = "og1",
                    title = "Término de la carne",
                    isRequired = true,
                    maxSelection = 1,
                    options = listOf(
                        DishOption("opt1", "Término Medio", 0.0),
                        DishOption("opt2", "Bien Cocida", 0.0),
                        DishOption("opt3", "Tres Cuartos", 0.0)
                    )
                ),
                DishOptionGroup(
                    id = "og2",
                    title = "Extras recomendados",
                    isRequired = false,
                    maxSelection = 3,
                    options = listOf(
                        DishOption("opt4", "Extra Queso Cheddar", 1.00),
                        DishOption("opt5", "Tocino Crujiente", 1.50),
                        DishOption("opt6", "Huevo Frito", 0.80)
                    )
                )
            )
        ),
        Dish(
            id = "d2",
            restaurantId = "r1",
            restaurantName = "Brasa & Pan",
            name = "Doble Queso & Tocino",
            description = "Doble carne vacuna (300g), doble capa de cheddar, tocino ahumado crujiente y cebolla caramelizada.",
            price = 8.90,
            imageUrl = "https://images.unsplash.com/photo-1586190848861-99aa4a171e90?w=500",
            menuSection = "Hamburguesas",
            isPopular = true,
            isOffer = true,
            offerBadge = "Popular"
        ),
        Dish(
            id = "d3",
            restaurantId = "r1",
            restaurantName = "Brasa & Pan",
            name = "BBQ Crispy Burger",
            description = "Carne de res, aros de cebolla empanizados, queso americano fundido y generosa salsa BBQ ahumada.",
            price = 7.90,
            imageUrl = "https://images.unsplash.com/photo-1550547660-d9450f859349?w=500",
            menuSection = "Hamburguesas",
            isPopular = false,
            isOffer = false
        ),
        Dish(
            id = "d4",
            restaurantId = "r1",
            restaurantName = "Brasa & Pan",
            name = "Papas Rústicas sazonadas",
            description = "Papas gruesas cortadas a mano con pimentón ahumado, sal de mar y dip de mayonesa de ajo.",
            price = 3.20,
            imageUrl = "https://images.unsplash.com/photo-1573080496219-bb080dd4f877?w=500",
            menuSection = "Acompañantes",
            isPopular = true,
            isOffer = false
        ),
        Dish(
            id = "d5",
            restaurantId = "r1",
            restaurantName = "Brasa & Pan",
            name = "Malteada de Vainilla & Oreo",
            description = "Helado cremoso de vainilla batido con trozos de galleta OREO y crema batida encima.",
            price = 4.00,
            imageUrl = "https://images.unsplash.com/photo-1572490122747-3968b75cc699?w=500",
            menuSection = "Bebidas & Postres",
            isPopular = false,
            isOffer = false
        ),

        // --- Casa Nonna (r2) ---
        Dish(
            id = "d6",
            restaurantId = "r2",
            restaurantName = "Casa Nonna",
            name = "Fetuccini Alfredo con Pollo",
            description = "Pasta fresca feta en casa salteada en salsa cremosa Alfredo de mantequilla, crema y queso Parmigiano.",
            price = 9.50,
            imageUrl = "https://images.unsplash.com/photo-1645112411341-6c4fd023714a?w=500",
            menuSection = "Pastas",
            isPopular = true,
            isOffer = false
        ),
        Dish(
            id = "d7",
            restaurantId = "r2",
            restaurantName = "Casa Nonna",
            name = "Lasaña Bolognesa Gratinada",
            description = "Capas de pasta artesanal intercaladas con abundante salsa Bolognesa de res, bechamel y mozzarella.",
            price = 10.50,
            imageUrl = "https://images.unsplash.com/photo-1574894709920-11b28e7367e3?w=500",
            menuSection = "Pastas",
            isPopular = true,
            isOffer = false
        ),
        Dish(
            id = "d8",
            restaurantId = "r2",
            restaurantName = "Casa Nonna",
            name = "Pizza Margarita Italiana",
            description = "Masa madre de 48h de fermentación, salsa de tomate San Marzano, mozzarella fior di latte y albahaca.",
            price = 11.00,
            imageUrl = "https://images.unsplash.com/photo-1604382354936-07c5d9983bd3?w=500",
            menuSection = "Pizzas",
            isPopular = true,
            isOffer = true,
            offerBadge = "Chef"
        ),
        Dish(
            id = "d9",
            restaurantId = "r2",
            restaurantName = "Casa Nonna",
            name = "Tiramisú Tradicional",
            description = "Bizcocho savoiardi humedecido en café espresso con licor Amaretto, capas de mascarpone y cacao.",
            price = 4.50,
            imageUrl = "https://images.unsplash.com/photo-1571877227200-a0d98ea607e9?w=500",
            menuSection = "Postres",
            isPopular = false,
            isOffer = false
        ),

        // --- Café Tertulia (r3) ---
        Dish(
            id = "d10",
            restaurantId = "r3",
            restaurantName = "Café Tertulia",
            name = "Capuchino Especial Tertulia",
            description = "Espresso doble de grano lojano tostado medio, leche cremada sedosa y toque de cacao en polvo.",
            price = 3.00,
            imageUrl = "https://images.unsplash.com/photo-1534778101976-62847782c213?w=500",
            menuSection = "Cafetería",
            isPopular = true,
            isOffer = false
        ),
        Dish(
            id = "d11",
            restaurantId = "r3",
            restaurantName = "Café Tertulia",
            name = "Latte de Vainilla Natural",
            description = "Espresso balanceado con leche al vapor perfumada con esencia pura de vainilla.",
            price = 3.50,
            imageUrl = "https://images.unsplash.com/photo-1461023058943-07fcbe16d735?w=500",
            menuSection = "Cafetería",
            isPopular = false,
            isOffer = false
        ),
        Dish(
            id = "d12",
            restaurantId = "r3",
            restaurantName = "Café Tertulia",
            name = "Cold Brew Nitro",
            description = "Café infusionado en frío durante 16 horas con cascada nitrogenada para una textura suave y dulce.",
            price = 3.80,
            imageUrl = "https://images.unsplash.com/photo-1517701604599-bb29b565090c?w=500",
            menuSection = "Bebidas Frías",
            isPopular = true,
            isOffer = false
        ),
        Dish(
            id = "d13",
            restaurantId = "r3",
            restaurantName = "Café Tertulia",
            name = "Croissant de Mantequilla",
            description = "Croissant hojaldrado francés horneado cada mañana con mantequilla de vaca seleccionada.",
            price = 2.20,
            imageUrl = "https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=500",
            menuSection = "Panadería",
            isPopular = false,
            isOffer = false
        ),
        Dish(
            id = "d14",
            restaurantId = "r3",
            restaurantName = "Café Tertulia",
            name = "Cheesecake de Frutos Rojos",
            description = "Tarta cremosa sobre galleta horneada, decorada con salsa casera de mora, frutilla y arándanos.",
            price = 4.20,
            imageUrl = "https://images.unsplash.com/photo-1533134242443-d4fd215305ad?w=500",
            menuSection = "Postres",
            isPopular = true,
            isOffer = true,
            offerBadge = "Top Postre"
        ),

        // --- Sabor Lojano (r4) ---
        Dish(
            id = "d15",
            restaurantId = "r4",
            restaurantName = "Sabor Lojano",
            name = "Repe Lojano Tradicional",
            description = "Sopa emblemática de plátano verde rallado, quesillo fresco de la sierra y cilantro tierno.",
            price = 4.00,
            imageUrl = "https://images.unsplash.com/photo-1547592166-23ac45744acd?w=500",
            menuSection = "Sopas & Entradas",
            isPopular = true,
            isOffer = false
        ),
        Dish(
            id = "d16",
            restaurantId = "r4",
            restaurantName = "Sabor Lojano",
            name = "Cecina Lojana con Yuca",
            description = "Carne de cerdo marinado seco al humo, acompañada de yucas al vapor, encurtido de cebolla y aguacate.",
            price = 8.50,
            imageUrl = "https://images.unsplash.com/photo-1544025162-d76694265947?w=500",
            menuSection = "Platos Fuertes",
            isPopular = true,
            isOffer = false
        ),
        Dish(
            id = "d17",
            restaurantId = "r4",
            restaurantName = "Sabor Lojano",
            name = "Tamal Lojano de Pollo",
            description = "Masa de maíz criollo rellena de guiso de pollo, arvejas y huevo duro, envuelta en hoja de achira.",
            price = 2.80,
            imageUrl = "https://images.unsplash.com/photo-1626777552726-4a6b54c97e46?w=500",
            menuSection = "Entradas",
            isPopular = false,
            isOffer = false
        ),
        Dish(
            id = "d18",
            restaurantId = "r4",
            restaurantName = "Sabor Lojano",
            name = "Hornado Lojano Festivo",
            description = "Jugoso cerdo asado al horno de leña, servido con llapingachos, mote caliente y agrio casero.",
            price = 9.00,
            imageUrl = "https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=500",
            menuSection = "Platos Fuertes",
            isPopular = true,
            isOffer = true,
            offerBadge = "Típico"
        ),

        // --- Sushi Kenzo (r5) ---
        Dish(
            id = "d19",
            restaurantId = "r5",
            restaurantName = "Sushi Kenzo",
            name = "Kenzo Special Roll (10 bocados)",
            description = "Relleno de camarón empanizado y palta, cubierto con láminas de salmón fresco y salsa acevichada picante.",
            price = 11.50,
            imageUrl = "https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=500",
            menuSection = "Rolls Especiales",
            isPopular = true,
            isOffer = true,
            offerBadge = "Favorito"
        ),
        Dish(
            id = "d20",
            restaurantId = "r5",
            restaurantName = "Sushi Kenzo",
            name = "Combo Nigiris Variados (8 Pzs)",
            description = "Selección del chef con nigiris de salmón, atún rojo, langostino y pesca blanca del día.",
            price = 12.00,
            imageUrl = "https://images.unsplash.com/photo-1611143669185-af224c5e3252?w=500",
            menuSection = "Nigiris & Sashimi",
            isPopular = false,
            isOffer = false
        ),
        Dish(
            id = "d21",
            restaurantId = "r5",
            restaurantName = "Sushi Kenzo",
            name = "Tonkotsu Ramen de Cerdo",
            description = "Fideos frescos en caldo denso de cerdo cocido 12h, con panceta chashu, huevo marinado y alga nori.",
            price = 10.50,
            imageUrl = "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=500",
            menuSection = "Ramen",
            isPopular = true,
            isOffer = false
        ),

        // --- Pollos Don Chicho (r6) ---
        Dish(
            id = "d22",
            restaurantId = "r6",
            restaurantName = "Pollos Don Chicho",
            name = "Combo Broaster 4 Presas",
            description = "4 presas de pollo crujiente doradas a la perfección, acompañadas de papas fritas y ensalada coleslaw.",
            price = 7.50,
            imageUrl = "https://images.unsplash.com/photo-1626645738196-c2a7c87a8f58?w=500",
            menuSection = "Pollo Broaster",
            isPopular = true,
            isOffer = false
        ),
        Dish(
            id = "d23",
            restaurantId = "r6",
            restaurantName = "Pollos Don Chicho",
            name = "Alitas BBQ o Búfalo (12 Pzs)",
            description = "Alitas jugosas a la parrilla bañadas en salsa a elección (salsa BBQ ahumada o Búfalo picante).",
            price = 8.90,
            imageUrl = "https://images.unsplash.com/photo-1527477396000-e27163b481c2?w=500",
            menuSection = "Alitas",
            isPopular = true,
            isOffer = true,
            offerBadge = "Combo Alitas"
        ),

        // --- Green Bowl (r7) ---
        Dish(
            id = "d24",
            restaurantId = "r7",
            restaurantName = "Green Bowl",
            name = "Bowl de Quinoa & Salmón Teriyaki",
            description = "Quinoa orgánica, salmón fresco a la plancha, palta, edamame, rodajas de pepino y vinagreta de sésamo.",
            price = 9.80,
            imageUrl = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500",
            menuSection = "Bowls Nutritivos",
            isPopular = true,
            isOffer = false
        ),
        Dish(
            id = "d25",
            restaurantId = "r7",
            restaurantName = "Green Bowl",
            name = "Smoothie Acai Berry Energy",
            description = "Batido denso de acai orgánico, plátano, arándanos, leche de coco y semillas de chía arriba.",
            price = 4.50,
            imageUrl = "https://images.unsplash.com/photo-1553530666-ba11a7da3888?w=500",
            menuSection = "Smoothies",
            isPopular = false,
            isOffer = false
        )
    )

    // Items iniciales del carrito (desde un único restaurante: Brasa & Pan)
    val initialCartItems = listOf(
        CartItem(
            dish = dishes[0], // Clásica Brasa ($6.50)
            restaurantId = "r1",
            restaurantName = "Brasa & Pan",
            quantity = 1,
            selectedOptions = listOf(
                SelectedOption("og1", "Término de la carne", "opt1", "Término Medio", 0.0),
                SelectedOption("og2", "Extras recomendados", "opt5", "Tocino Crujiente", 1.50)
            )
        ),
        CartItem(
            dish = dishes[3], // Papas Rústicas ($3.20)
            restaurantId = "r1",
            restaurantName = "Brasa & Pan",
            quantity = 1
        )
    )

    val sampleTimelineItems = listOf(
        OrderTimelineItem(
            title = "Confirmado por el restaurante",
            time = "14:02",
            step = OrderStatusStep.CONFIRMED,
            isCompleted = true,
            isCurrent = false
        ),
        OrderTimelineItem(
            title = "En preparación en cocina",
            time = "14:08",
            step = OrderStatusStep.PREPARING,
            isCompleted = true,
            isCurrent = false
        ),
        OrderTimelineItem(
            title = "Repartidor en camino",
            time = "14:21",
            step = OrderStatusStep.IN_TRANSIT,
            isCompleted = false,
            isCurrent = true
        ),
        OrderTimelineItem(
            title = "Entrega estimada",
            time = "est. 14:38",
            step = OrderStatusStep.DELIVERED,
            isCompleted = false,
            isCurrent = false
        )
    )

    val sampleActiveOrder = ActiveOrder(
        id = "JG-9042",
        restaurantId = "r1",
        restaurantName = "Brasa & Pan",
        restaurantLogoUrl = "https://images.unsplash.com/photo-1550547660-d9450f859349?w=300",
        items = emptyList(),
        subtotal = 11.20,
        deliveryFee = 1.50,
        total = 12.70,
        estimatedMinutes = 18,
        statusStep = OrderStatusStep.IN_TRANSIT,
        timelineItems = sampleTimelineItems,
        courier = null
    )
}
