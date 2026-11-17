package com.jagoo.app.data.repository

import android.content.Context
import com.jagoo.app.data.ActiveOrder
import com.jagoo.app.data.CartItem
import com.jagoo.app.data.Dish
import com.jagoo.app.data.Restaurant
import com.jagoo.app.data.db.JagooDatabaseHelper
import com.jagoo.app.data.remote.SupabaseClient
import com.jagoo.app.data.remote.toAddressItem
import com.jagoo.app.data.remote.toActiveOrder
import com.jagoo.app.data.remote.toDish
import com.jagoo.app.data.remote.toRestaurant
import com.jagoo.app.data.remote.AddressDto
import com.jagoo.app.data.remote.DishDto
import com.jagoo.app.data.remote.OrderDto
import com.jagoo.app.data.remote.OrderItemDto
import com.jagoo.app.data.remote.RestaurantDto
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from

data class AddressItem(
    val id: String,
    val label: String,
    val fullAddress: String,
    val isPrimary: Boolean
)

interface JagooRepository {
    fun getRestaurants(): List<Restaurant>
    fun getDishesForRestaurant(restaurantId: String): List<Dish>
    fun saveDish(dish: Dish)
    fun deleteDish(dishId: String)
    fun toggleDishAvailability(dishId: String, isAvailable: Boolean)
    fun updateRestaurantStatus(restaurantId: String, isOpen: Boolean)
    fun saveOrder(order: ActiveOrder)
    fun getOrderHistory(): List<ActiveOrder>
    fun getAddresses(): List<AddressItem>
    fun saveAddress(id: String, label: String, fullAddress: String, isPrimary: Boolean)
    fun deleteAddress(id: String)
    fun setPrimaryAddress(id: String)
}

class LocalJagooRepository(context: Context) : JagooRepository {

    private val dbHelper = JagooDatabaseHelper(context)

    override fun getRestaurants(): List<Restaurant> {
        val list = dbHelper.getAllRestaurants()
        return if (list.isNotEmpty()) list else com.jagoo.app.data.RestaurantData.restaurants
    }

    override fun getDishesForRestaurant(restaurantId: String): List<Dish> {
        val list = dbHelper.getDishesForRestaurant(restaurantId)
        return if (list.isNotEmpty()) list else com.jagoo.app.data.RestaurantData.dishes.filter { it.restaurantId == restaurantId }
    }

    override fun saveDish(dish: Dish) {
        dbHelper.saveDish(dish)
    }

    override fun deleteDish(dishId: String) {
        dbHelper.deleteDish(dishId)
    }

    override fun toggleDishAvailability(dishId: String, isAvailable: Boolean) {
        dbHelper.toggleDishAvailability(dishId, isAvailable)
    }

    override fun updateRestaurantStatus(restaurantId: String, isOpen: Boolean) {
        dbHelper.updateRestaurantStatus(restaurantId, isOpen)
    }

    override fun saveOrder(order: ActiveOrder) {
        dbHelper.insertOrder(order)
    }

    override fun getOrderHistory(): List<ActiveOrder> {
        return dbHelper.getOrderHistory()
    }

    override fun getAddresses(): List<AddressItem> {
        val pairs = dbHelper.getAddresses()
        return pairs.map { (id, info) ->
            val parts = info.first.split(":")
            val label = parts.firstOrNull()?.trim() ?: "Dirección"
            val fullAddr = parts.getOrNull(1)?.trim() ?: info.first
            AddressItem(id = id, label = label, fullAddress = fullAddr, isPrimary = info.second)
        }
    }

    override fun saveAddress(id: String, label: String, fullAddress: String, isPrimary: Boolean) {
        dbHelper.saveAddress(id, label, fullAddress, isPrimary)
    }

    override fun deleteAddress(id: String) {
        dbHelper.deleteAddress(id)
    }

    override fun setPrimaryAddress(id: String) {
        dbHelper.setPrimaryAddress(id)
    }
}

/**
 * Implementación real de JagooRepository usando Supabase-kt 3.x
 *
 * API de Supabase-kt 3.x:
 *   supabase.from("tabla").select { filter { eq("col", value) } }
 *   supabase.from("tabla").insert(dto)
 *   supabase.from("tabla").update({ set("col", value) }) { filter { eq("id", x) } }
 *   supabase.from("tabla").delete { filter { eq("id", x) } }
 */
class SupabaseJagooRepository(
    private val localRepo: LocalJagooRepository
) : JagooRepository {

    private val supabase = com.jagoo.app.data.remote.SupabaseClient.client

    // ── Restaurantes ──────────────────────────────────────────

    override fun getRestaurants(): List<com.jagoo.app.data.Restaurant> =
        localRepo.getRestaurants()

    suspend fun getRestaurantsSuspend(): List<com.jagoo.app.data.Restaurant> = try {
        supabase.from("restaurants")
            .select()
            .decodeList<com.jagoo.app.data.remote.RestaurantDto>()
            .map { it.toRestaurant() }
    } catch (e: Exception) { localRepo.getRestaurants() }

    // ── Platos ────────────────────────────────────────────────

    override fun getDishesForRestaurant(restaurantId: String): List<com.jagoo.app.data.Dish> =
        localRepo.getDishesForRestaurant(restaurantId)

    suspend fun getDishesSuspend(restaurantId: String): List<com.jagoo.app.data.Dish> = try {
        supabase.from("dishes")
            .select { filter { eq("restaurant_id", restaurantId) } }
            .decodeList<com.jagoo.app.data.remote.DishDto>()
            .map { it.toDish() }
    } catch (e: Exception) { localRepo.getDishesForRestaurant(restaurantId) }

    override fun saveDish(dish: com.jagoo.app.data.Dish) = localRepo.saveDish(dish)

    suspend fun saveDishSuspend(dish: com.jagoo.app.data.Dish) {
        try {
            val dto = com.jagoo.app.data.remote.DishDto(
                id = dish.id, restaurantId = dish.restaurantId,
                restaurantName = dish.restaurantName, sectionName = dish.menuSection,
                name = dish.name, description = dish.description, price = dish.price,
                imageUrl = dish.imageUrl, isAvailable = dish.isAvailable,
                isPopular = dish.isPopular, isOffer = dish.isOffer, offerBadge = dish.offerBadge
            )
            supabase.from("dishes").upsert(dto)
        } catch (e: Exception) { localRepo.saveDish(dish) }
    }

    override fun deleteDish(dishId: String) = localRepo.deleteDish(dishId)

    suspend fun deleteDishSuspend(dishId: String) {
        try { supabase.from("dishes").delete { filter { eq("id", dishId) } } }
        catch (e: Exception) { localRepo.deleteDish(dishId) }
    }

    override fun toggleDishAvailability(dishId: String, isAvailable: Boolean) =
        localRepo.toggleDishAvailability(dishId, isAvailable)

    suspend fun toggleDishAvailabilitySuspend(dishId: String, isAvailable: Boolean) {
        try {
            supabase.from("dishes")
                .update({ set("is_available", isAvailable) }) { filter { eq("id", dishId) } }
        } catch (e: Exception) { localRepo.toggleDishAvailability(dishId, isAvailable) }
    }

    override fun updateRestaurantStatus(restaurantId: String, isOpen: Boolean) =
        localRepo.updateRestaurantStatus(restaurantId, isOpen)

    suspend fun updateRestaurantStatusSuspend(restaurantId: String, isOpen: Boolean) {
        try {
            supabase.from("restaurants")
                .update({ set("is_open", isOpen) }) { filter { eq("id", restaurantId) } }
        } catch (e: Exception) { localRepo.updateRestaurantStatus(restaurantId, isOpen) }
    }

    // ── Pedidos ───────────────────────────────────────────────

    override fun saveOrder(order: com.jagoo.app.data.ActiveOrder) = localRepo.saveOrder(order)

    suspend fun saveOrderSuspend(
        order: com.jagoo.app.data.ActiveOrder,
        cartItems: List<com.jagoo.app.data.CartItem>,
        deliveryAddress: String
    ) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        try {
            val dto = com.jagoo.app.data.remote.OrderDto(
                customerId = userId, restaurantId = order.restaurantId,
                restaurantName = order.restaurantName,
                restaurantLogoUrl = order.restaurantLogoUrl,
                subtotal = order.subtotal, deliveryFee = order.deliveryFee,
                total = order.total, status = "pending",
                deliveryAddress = deliveryAddress
            )
            val inserted = supabase.from("orders")
                .insert(dto) { select() }
                .decodeSingle<com.jagoo.app.data.remote.OrderDto>()

            val items = cartItems.map { item ->
                com.jagoo.app.data.remote.OrderItemDto(
                    orderId = inserted.id, dishId = item.dish.id,
                    dishName = item.dish.name, unitPrice = item.unitPrice,
                    quantity = item.quantity,
                    specialInstructions = item.specialInstructions
                )
            }
            supabase.from("order_items").insert(items)
            localRepo.saveOrder(order)
        } catch (e: Exception) { localRepo.saveOrder(order) }
    }

    override fun getOrderHistory(): List<com.jagoo.app.data.ActiveOrder> =
        localRepo.getOrderHistory()

    suspend fun getOrderHistorySuspend(): List<com.jagoo.app.data.ActiveOrder> {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: return localRepo.getOrderHistory()
        return try {
            supabase.from("orders")
                .select { filter { eq("customer_id", userId) } }
                .decodeList<com.jagoo.app.data.remote.OrderDto>()
                .map { it.toActiveOrder() }
        } catch (e: Exception) { localRepo.getOrderHistory() }
    }

    // ── Direcciones ───────────────────────────────────────────

    override fun getAddresses(): List<AddressItem> = localRepo.getAddresses()

    suspend fun getAddressesSuspend(): List<AddressItem> {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: return localRepo.getAddresses()
        return try {
            supabase.from("addresses")
                .select { filter { eq("user_id", userId) } }
                .decodeList<com.jagoo.app.data.remote.AddressDto>()
                .map { it.toAddressItem() }
        } catch (e: Exception) { localRepo.getAddresses() }
    }

    override fun saveAddress(id: String, label: String, fullAddress: String, isPrimary: Boolean) =
        localRepo.saveAddress(id, label, fullAddress, isPrimary)

    suspend fun saveAddressSuspend(id: String, label: String, fullAddress: String, isPrimary: Boolean) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        try {
            if (isPrimary) {
                supabase.from("addresses")
                    .update({ set("is_primary", false) }) { filter { eq("user_id", userId) } }
            }
            supabase.from("addresses").upsert(
                com.jagoo.app.data.remote.AddressDto(
                    id = id, userId = userId, label = label,
                    fullAddress = fullAddress, isPrimary = isPrimary
                )
            )
        } catch (e: Exception) { localRepo.saveAddress(id, label, fullAddress, isPrimary) }
    }

    override fun deleteAddress(id: String) = localRepo.deleteAddress(id)

    suspend fun deleteAddressSuspend(id: String) {
        try { supabase.from("addresses").delete { filter { eq("id", id) } } }
        catch (e: Exception) { localRepo.deleteAddress(id) }
    }

    override fun setPrimaryAddress(id: String) = localRepo.setPrimaryAddress(id)

    suspend fun setPrimaryAddressSuspend(id: String) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        try {
            supabase.from("addresses")
                .update({ set("is_primary", false) }) { filter { eq("user_id", userId) } }
            supabase.from("addresses")
                .update({ set("is_primary", true) }) { filter { eq("id", id) } }
        } catch (e: Exception) { localRepo.setPrimaryAddress(id) }
    }
}
