package com.jagoo.app.data.repository

import android.content.Context
import com.jagoo.app.data.ActiveOrder
import com.jagoo.app.data.Dish
import com.jagoo.app.data.Restaurant
import com.jagoo.app.data.db.JagooDatabaseHelper

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
 * Puntos de extensión para migración futura a Supabase
 */
class SupabaseJagooRepository(
    private val localRepo: LocalJagooRepository
) : JagooRepository by localRepo {
    // Cuando se conecte Supabase Client (io.github.jan-tennert.supabase),
    // aquí se sobreescribirán los métodos con llamadas asíncronas REST/Realtime.
}
