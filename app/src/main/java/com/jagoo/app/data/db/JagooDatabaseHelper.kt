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
        const val DATABASE_VERSION = 1
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
                offer_text TEXT
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
                offer_badge TEXT
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
                created_at TEXT NOT NULL
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
        db.execSQL("DROP TABLE IF EXISTS addresses")
        db.execSQL("DROP TABLE IF EXISTS order_items")
        db.execSQL("DROP TABLE IF EXISTS orders")
        db.execSQL("DROP TABLE IF EXISTS options")
        db.execSQL("DROP TABLE IF EXISTS option_groups")
        db.execSQL("DROP TABLE IF EXISTS dishes")
        db.execSQL("DROP TABLE IF EXISTS menu_sections")
        db.execSQL("DROP TABLE IF EXISTS restaurants")
        db.execSQL("DROP TABLE IF EXISTS users")
        db.execSQL("DROP TABLE IF EXISTS roles")
        onCreate(db)
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
            }
            db.insert("restaurants", null, cv)
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
            }
            db.insert("dishes", null, cv)

            // Seed Option Groups for dish
            d.optionGroups.forEach { og ->
                val ogCv = ContentValues().apply {
                    put("id", og.id)
                    put("dish_id", d.id)
                    put("title", og.title)
                    put("is_required", if (og.isRequired) 1 else 0)
                    put("max_selection", og.maxSelection)
                }
                db.insert("option_groups", null, ogCv)

                og.options.forEach { opt ->
                    val optCv = ContentValues().apply {
                        put("id", opt.id)
                        put("group_id", og.id)
                        put("name", opt.name)
                        put("price", opt.price)
                        put("is_available", 1)
                    }
                    db.insert("options", null, optCv)
                }
            }
        }

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
                        offerText = cursor.getString(cursor.getColumnIndexOrThrow("offer_text"))
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
                        offerBadge = cursor.getString(cursor.getColumnIndexOrThrow("offer_badge"))
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
                        id = orderId,
                        restaurantId = restId,
                        restaurantName = restName,
                        restaurantLogoUrl = logoUrl,
                        items = emptyList(),
                        subtotal = subtotal,
                        deliveryFee = fee,
                        total = total,
                        estimatedMinutes = 25,
                        statusStep = step,
                        timelineItems = RestaurantData.sampleTimelineItems,
                        courierName = "Carlos M.",
                        courierVehicle = "Moto · PBC-1234"
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
