package com.jagoo.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jagoo.app.data.ActiveOrder
import com.jagoo.app.data.CartItem
import com.jagoo.app.data.Dish
import com.jagoo.app.data.DishOption
import com.jagoo.app.data.DishOptionGroup
import com.jagoo.app.data.OrderStatusStep
import com.jagoo.app.data.OrderTimelineItem
import com.jagoo.app.data.Restaurant
import com.jagoo.app.data.RestaurantData
import com.jagoo.app.data.SelectedOption

class JagooDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "jagoo_delivery.db"
        const val DATABASE_VERSION = 2
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. Roles
        db.execSQL(
            """
            CREATE TABLE roles (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                permissions_json TEXT
            );
            """.trimIndent()
        )

        // 2. Users
        db.execSQL(
            """
            CREATE TABLE users (
                id TEXT PRIMARY KEY,
                email TEXT NOT NULL,
                full_name TEXT NOT NULL,
                phone TEXT,
                role_id TEXT NOT NULL,
                restaurant_id TEXT
            );
            """.trimIndent()
        )

        // 3. Restaurants
        db.execSQL(
            """
            CREATE TABLE restaurants (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                description TEXT,
                logo_url TEXT,
                cover_url TEXT,
                brand_color TEXT,
                rating REAL,
                review_count INTEGER,
                delivery_time TEXT,
                delivery_fee REAL,
                min_order REAL,
                is_open INTEGER DEFAULT 1,
                is_featured INTEGER DEFAULT 0,
                is_offer INTEGER DEFAULT 0,
                offer_text TEXT,
                business_type TEXT NOT NULL DEFAULT 'restaurante'
                    CHECK (business_type IN ('restaurante','tienda','farmacia','licoreria')),
                permit_number TEXT,
                is_active INTEGER NOT NULL DEFAULT 1
            );
            """.trimIndent()
        )

        // 4. Menu Sections
        db.execSQL(
            """
            CREATE TABLE menu_sections (
                id TEXT PRIMARY KEY,
                restaurant_id TEXT NOT NULL,
                name TEXT NOT NULL,
                sort_order INTEGER DEFAULT 0,
                is_active INTEGER DEFAULT 1
            );
            """.trimIndent()
        )

        // 5. Dishes
        db.execSQL(
            """
            CREATE TABLE dishes (
                id TEXT PRIMARY KEY,
                restaurant_id TEXT NOT NULL,
                restaurant_name TEXT,
                section_name TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                price REAL NOT NULL,
                image_url TEXT,
                is_available INTEGER DEFAULT 1,
                is_popular INTEGER DEFAULT 0,
                is_offer INTEGER DEFAULT 0,
                offer_badge TEXT,
                is_age_restricted INTEGER NOT NULL DEFAULT 0,
                requires_prescription INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent()
        )

        // 6. Option Groups
        db.execSQL(
            """
            CREATE TABLE option_groups (
                id TEXT PRIMARY KEY,
                dish_id TEXT NOT NULL,
                title TEXT NOT NULL,
                is_required INTEGER DEFAULT 0,
                max_selection INTEGER DEFAULT 1
            );
            """.trimIndent()
        )

        // 7. Options
        db.execSQL(
            """
            CREATE TABLE options (
                id TEXT PRIMARY KEY,
                group_id TEXT NOT NULL,
                name TEXT NOT NULL,
                price REAL DEFAULT 0.0,
                is_available INTEGER DEFAULT 1
            );
            """.trimIndent()
        )

        // 8. Orders
        db.execSQL(
            """
            CREATE TABLE orders (
                id TEXT PRIMARY KEY,
                restaurant_id TEXT NOT NULL,
                restaurant_name TEXT NOT NULL,
                restaurant_logo_url TEXT,
                subtotal REAL NOT NULL,
                delivery_fee REAL NOT NULL,
                total REAL NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                payment_method TEXT NOT NULL DEFAULT 'cash'
                    CHECK (payment_method IN ('cash','transfer')),
                payment_status TEXT NOT NULL DEFAULT 'pending',
                change_due REAL,
                age_check_required INTEGER NOT NULL DEFAULT 0,
                cancel_reason TEXT
            );
            """.trimIndent()
        )

        // 9. Order Items
        db.execSQL(
            """
            CREATE TABLE order_items (
                id TEXT PRIMARY KEY,
                order_id TEXT NOT NULL,
                dish_id TEXT NOT NULL,
                dish_name TEXT NOT NULL,
                unit_price REAL NOT NULL,
                quantity INTEGER NOT NULL,
                selected_options_json TEXT
            );
            """.trimIndent()
        )

        // 10. Addresses
        db.execSQL(
            """
            CREATE TABLE addresses (
                id TEXT PRIMARY KEY,
                label TEXT NOT NULL,
                full_address TEXT NOT NULL,
                is_primary INTEGER DEFAULT 0
            );
            """.trimIndent()
        )

        // Cargar Semilla Inicial de Datos (Seed)
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migracion incremental: se agregan columnas, nunca se borran tablas ni datos del usuario.
        if (oldVersion < 2) migrateToV2(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // El esquema nuevo es compatible hacia atras (solo agrega columnas con valor por defecto).
    }

    /**
     * v2: tipo de negocio generico (restaurante, tienda, farmacia, licoreria), marcas +18 en
     * productos y datos de pago en pedidos. Los nombres son iguales a los de Supabase.
     */
    private fun migrateToV2(db: SQLiteDatabase) {
        addColumnIfMissing(db, "restaurants", "business_type",
            "TEXT NOT NULL DEFAULT 'restaurante' CHECK (business_type IN ('restaurante','tienda','farmacia','licoreria'))")
        addColumnIfMissing(db, "restaurants", "permit_number", "TEXT")
        addColumnIfMissing(db, "restaurants", "is_active", "INTEGER NOT NULL DEFAULT 1")

        addColumnIfMissing(db, "dishes", "is_age_restricted", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(db, "dishes", "requires_prescription", "INTEGER NOT NULL DEFAULT 0")

        addColumnIfMissing(db, "orders", "payment_method",
            "TEXT NOT NULL DEFAULT 'cash' CHECK (payment_method IN ('cash','transfer'))")
        addColumnIfMissing(db, "orders", "payment_status", "TEXT NOT NULL DEFAULT 'pending'")
        addColumnIfMissing(db, "orders", "change_due", "REAL")
        addColumnIfMissing(db, "orders", "age_check_required", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(db, "orders", "cancel_reason", "TEXT")

        // Negocios y productos nuevos del catalogo de ejemplo (farmacia y licoreria), sin pisar nada existente
        seedCatalog(db)
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, definition: String) {
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx) == column) return
            }
        }
        db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }
    private fun seedInitialData(db: SQLiteDatabase) {
        // Seed Roles
        db.execSQL("INSERT INTO roles (id, name) VALUES ('r_admin', 'RESTAURANT_OWNER'), ('r_customer', 'CUSTOMER');")

        // Seed Default Users (para los 7 restaurantes + cliente)
        val users = listOf(
            ContentValues().apply {
                put("id", "u_admin_brasa")
                put("email", "brasa@jagoo.com")
                put("full_name", "Gerente Brasa & Pan")
                put("phone", "+593 99 111 2222")
                put("role_id", "r_admin")
                put("restaurant_id", "r1")
            },
            ContentValues().apply {
                put("id", "u_juan")
                put("email", "juanpablo@jagoo.com")
                put("full_name", "Juan Pablo")
                put("phone", "+593 99 123 4567")
                put("role_id", "r_customer")
            }
        )
        users.forEach { db.insert("users", null, it) }

        seedCatalog(db)

        // Seed Addresses
        val addresses = listOf(
            ContentValues().apply {
                put("id", "addr_1")
                put("label", "Casa")
                put("full_address", "Av. República del Salvador, Quito")
                put("is_primary", 1)
            },
            ContentValues().apply {
                put("id", "addr_2")
                put("label", "Trabajo")
                put("full_address", "Av. Amazonas y Eloy Alfaro, Quito")
                put("is_primary", 0)
            }
        )
        addresses.forEach { db.insert("addresses", null, it) }
    }

    // --- MÉTODOS DE LECTURA Y ESCRITURA DE RESTAURANTES Y PLATOS ---

    /** Inserta negocios, productos y opciones de ejemplo. CONFLICT_IGNORE: lo que ya existe no se toca. */
    private fun seedCatalog(db: SQLiteDatabase) {
        // Seed Restaurants
        RestaurantData.restaurants.forEach { r ->
            val cv = ContentValues().apply {
                put("id", r.id)
                put("name", r.name)
                put("category", r.category)
                put("description", r.description)
                put("logo_url", r.logoUrl)
                put("cover_url", r.coverUrl)
                put("brand_color", r.brandColorHex)
                put("rating", r.rating)
                put("review_count", r.reviewCount)
                put("delivery_time", r.deliveryTime)
                put("delivery_fee", r.deliveryFee)
                put("min_order", r.minOrder)
                put("is_open", if (r.isOpen) 1 else 0)
                put("is_featured", if (r.isFeatured) 1 else 0)
                put("is_offer", if (r.isOffer) 1 else 0)
                put("offer_text", r.offerText)
                put("business_type", r.businessType)
                put("is_active", if (r.isActive) 1 else 0)
            }
            db.insertWithOnConflict("restaurants", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }

        // Seed Menu Sections & Dishes
        RestaurantData.dishes.forEach { d ->
            val cv = ContentValues().apply {
                put("id", d.id)
                put("restaurant_id", d.restaurantId)
                put("restaurant_name", d.restaurantName)
                put("section_name", d.menuSection)
                put("name", d.name)
                put("description", d.description)
                put("price", d.price)
                put("image_url", d.imageUrl)
                put("is_available", if (d.isAvailable) 1 else 0)
                put("is_popular", if (d.isPopular) 1 else 0)
                put("is_offer", if (d.isOffer) 1 else 0)
                put("offer_badge", d.offerBadge)
                put("is_age_restricted", if (d.isAgeRestricted) 1 else 0)
                put("requires_prescription", if (d.requiresPrescription) 1 else 0)
            }
            db.insertWithOnConflict("dishes", null, cv, SQLiteDatabase.CONFLICT_IGNORE)

            // Seed Option Groups for dish
            d.optionGroups.forEach { og ->
                val ogCv = ContentValues().apply {
                    put("id", og.id)
                    put("dish_id", d.id)
                    put("title", og.title)
                    put("is_required", if (og.isRequired) 1 else 0)
                    put("max_selection", og.maxSelection)
                }
                db.insertWithOnConflict("option_groups", null, ogCv, SQLiteDatabase.CONFLICT_IGNORE)

                og.options.forEach { opt ->
                    val optCv = ContentValues().apply {
                        put("id", opt.id)
                        put("group_id", og.id)
                        put("name", opt.name)
                        put("price", opt.price)
                        put("is_available", 1)
                    }
                    db.insertWithOnConflict("options", null, optCv, SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
        }
    }

    fun getAllRestaurants(): List<Restaurant> {
        val list = mutableListOf<Restaurant>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM restaurants", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(
                    Restaurant(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
                        description = cursor.getString(cursor.getColumnIndexOrThrow("description")) ?: "",
                        logoUrl = cursor.getString(cursor.getColumnIndexOrThrow("logo_url")) ?: "",
                        coverUrl = cursor.getString(cursor.getColumnIndexOrThrow("cover_url")) ?: "",
                        rating = cursor.getDouble(cursor.getColumnIndexOrThrow("rating")),
                        reviewCount = cursor.getInt(cursor.getColumnIndexOrThrow("review_count")),
                        deliveryTime = cursor.getString(cursor.getColumnIndexOrThrow("delivery_time")) ?: "20-30 min",
                        deliveryFee = cursor.getDouble(cursor.getColumnIndexOrThrow("delivery_fee")),
                        minOrder = cursor.getDouble(cursor.getColumnIndexOrThrow("min_order")),
                        isOpen = cursor.getInt(cursor.getColumnIndexOrThrow("is_open")) == 1,
                        brandColorHex = cursor.getString(cursor.getColumnIndexOrThrow("brand_color")) ?: "#FFD600",
                        isFeatured = cursor.getInt(cursor.getColumnIndexOrThrow("is_featured")) == 1,
                        isOffer = cursor.getInt(cursor.getColumnIndexOrThrow("is_offer")) == 1,
                        offerText = cursor.getString(cursor.getColumnIndexOrThrow("offer_text")),
                        businessType = cursor.getString(cursor.getColumnIndexOrThrow("business_type")) ?: "restaurante",
                        isActive = cursor.getInt(cursor.getColumnIndexOrThrow("is_active")) == 1
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun updateRestaurantStatus(restaurantId: String, isOpen: Boolean) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("is_open", if (isOpen) 1 else 0)
        }
        db.update("restaurants", cv, "id = ?", arrayOf(restaurantId))
    }

    fun getDishesForRestaurant(restaurantId: String): List<Dish> {
        val list = mutableListOf<Dish>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM dishes WHERE restaurant_id = ?", arrayOf(restaurantId))
        if (cursor.moveToFirst()) {
            do {
                val dishId = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                val optionGroups = getOptionGroupsForDish(dishId)

                list.add(
                    Dish(
                        id = dishId,
                        restaurantId = cursor.getString(cursor.getColumnIndexOrThrow("restaurant_id")),
                        restaurantName = cursor.getString(cursor.getColumnIndexOrThrow("restaurant_name")) ?: "",
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        description = cursor.getString(cursor.getColumnIndexOrThrow("description")) ?: "",
                        price = cursor.getDouble(cursor.getColumnIndexOrThrow("price")),
                        imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("image_url")) ?: "",
                        menuSection = cursor.getString(cursor.getColumnIndexOrThrow("section_name")),
                        optionGroups = optionGroups,
                        isAvailable = cursor.getInt(cursor.getColumnIndexOrThrow("is_available")) == 1,
                        isPopular = cursor.getInt(cursor.getColumnIndexOrThrow("is_popular")) == 1,
                        isOffer = cursor.getInt(cursor.getColumnIndexOrThrow("is_offer")) == 1,
                        offerBadge = cursor.getString(cursor.getColumnIndexOrThrow("offer_badge")),
                        isAgeRestricted = cursor.getInt(cursor.getColumnIndexOrThrow("is_age_restricted")) == 1,
                        requiresPrescription = cursor.getInt(cursor.getColumnIndexOrThrow("requires_prescription")) == 1
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    private fun getOptionGroupsForDish(dishId: String): List<DishOptionGroup> {
        val groups = mutableListOf<DishOptionGroup>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM option_groups WHERE dish_id = ?", arrayOf(dishId))
        if (cursor.moveToFirst()) {
            do {
                val groupId = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                val options = getOptionsForGroup(groupId)

                groups.add(
                    DishOptionGroup(
                        id = groupId,
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        isRequired = cursor.getInt(cursor.getColumnIndexOrThrow("is_required")) == 1,
                        maxSelection = cursor.getInt(cursor.getColumnIndexOrThrow("max_selection")),
                        options = options
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return groups
    }

    private fun getOptionsForGroup(groupId: String): List<DishOption> {
        val list = mutableListOf<DishOption>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM options WHERE group_id = ?", arrayOf(groupId))
        if (cursor.moveToFirst()) {
            do {
                list.add(
                    DishOption(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        price = cursor.getDouble(cursor.getColumnIndexOrThrow("price"))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun saveDish(dish: Dish) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", dish.id)
            put("restaurant_id", dish.restaurantId)
            put("restaurant_name", dish.restaurantName)
            put("section_name", dish.menuSection)
            put("name", dish.name)
            put("description", dish.description)
            put("price", dish.price)
            put("image_url", dish.imageUrl)
            put("is_available", if (dish.isAvailable) 1 else 0)
            put("is_popular", if (dish.isPopular) 1 else 0)
            put("is_offer", if (dish.isOffer) 1 else 0)
            put("offer_badge", dish.offerBadge)
            put("is_age_restricted", if (dish.isAgeRestricted) 1 else 0)
            put("requires_prescription", if (dish.requiresPrescription) 1 else 0)
        }
        db.insertWithOnConflict("dishes", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteDish(dishId: String) {
        val db = writableDatabase
        db.delete("dishes", "id = ?", arrayOf(dishId))
    }

    fun toggleDishAvailability(dishId: String, isAvailable: Boolean) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("is_available", if (isAvailable) 1 else 0)
        }
        db.update("dishes", cv, "id = ?", arrayOf(dishId))
    }

    // --- MÉTODOS DE PEDIDOS (ORDERS) ---

    fun insertOrder(order: ActiveOrder) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", order.id)
            put("restaurant_id", order.restaurantId)
            put("restaurant_name", order.restaurantName)
            put("restaurant_logo_url", order.restaurantLogoUrl)
            put("subtotal", order.subtotal)
            put("delivery_fee", order.deliveryFee)
            put("total", order.total)
            put("status", order.statusStep.name)
            put("created_at", System.currentTimeMillis().toString())
            // Solo valores validos (el CHECK de la tabla acepta 'cash' y 'transfer')
            put("payment_method", if (order.paymentMethod == "transfer") "transfer" else "cash")
            put("payment_status", order.paymentStatus)
            put("change_due", order.changeDue)
            put("age_check_required", if (order.ageCheckRequired) 1 else 0)
            put("cancel_reason", order.cancelReason)
        }
        db.insertWithOnConflict("orders", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getOrderHistory(): List<ActiveOrder> {
        val list = mutableListOf<ActiveOrder>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM orders ORDER BY created_at DESC", null)
        if (cursor.moveToFirst()) {
            do {
                val orderId = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                val restId = cursor.getString(cursor.getColumnIndexOrThrow("restaurant_id"))
                val restName = cursor.getString(cursor.getColumnIndexOrThrow("restaurant_name"))
                val logoUrl = cursor.getString(cursor.getColumnIndexOrThrow("restaurant_logo_url")) ?: ""
                val subtotal = cursor.getDouble(cursor.getColumnIndexOrThrow("subtotal"))
                val fee = cursor.getDouble(cursor.getColumnIndexOrThrow("delivery_fee"))
                val total = cursor.getDouble(cursor.getColumnIndexOrThrow("total"))
                val statusStr = cursor.getString(cursor.getColumnIndexOrThrow("status"))

                val step = try {
                    OrderStatusStep.valueOf(statusStr)
                } catch (e: Exception) {
                    OrderStatusStep.CONFIRMED
                }

                list.add(
                    ActiveOrder(
                        id                = orderId,
                        restaurantId      = restId,
                        restaurantName    = restName,
                        restaurantLogoUrl = logoUrl,
                        items             = emptyList(),
                        subtotal          = subtotal,
                        deliveryFee       = fee,
                        total             = total,
                        estimatedMinutes  = 25,
                        statusStep        = step,
                        timelineItems     = RestaurantData.sampleTimelineItems,
                        courier           = null,
                        paymentMethod     = cursor.getString(cursor.getColumnIndexOrThrow("payment_method")) ?: "cash",
                        paymentStatus     = cursor.getString(cursor.getColumnIndexOrThrow("payment_status")) ?: "pending",
                        changeDue         = cursor.getColumnIndexOrThrow("change_due").let { if (cursor.isNull(it)) null else cursor.getDouble(it) },
                        ageCheckRequired  = cursor.getInt(cursor.getColumnIndexOrThrow("age_check_required")) == 1,
                        cancelReason      = cursor.getString(cursor.getColumnIndexOrThrow("cancel_reason"))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    // --- MÉTODOS DE DIRECCIONES ---

    fun getAddresses(): List<Pair<String, Pair<String, Boolean>>> {
        val list = mutableListOf<Pair<String, Pair<String, Boolean>>>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM addresses", null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                val label = cursor.getString(cursor.getColumnIndexOrThrow("label"))
                val fullAddress = cursor.getString(cursor.getColumnIndexOrThrow("full_address"))
                val isPrimary = cursor.getInt(cursor.getColumnIndexOrThrow("is_primary")) == 1
                list.add(Pair(id, Pair("$label: $fullAddress", isPrimary)))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun saveAddress(id: String, label: String, fullAddress: String, isPrimary: Boolean) {
        val db = writableDatabase
        if (isPrimary) {
            db.execSQL("UPDATE addresses SET is_primary = 0")
        }
        val cv = ContentValues().apply {
            put("id", id)
            put("label", label)
            put("full_address", fullAddress)
            put("is_primary", if (isPrimary) 1 else 0)
        }
        db.insertWithOnConflict("addresses", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteAddress(id: String) {
        val db = writableDatabase
        db.delete("addresses", "id = ?", arrayOf(id))
    }

    fun setPrimaryAddress(id: String) {
        val db = writableDatabase
        db.execSQL("UPDATE addresses SET is_primary = 0")
        db.execSQL("UPDATE addresses SET is_primary = 1 WHERE id = '$id'")
    }
}
