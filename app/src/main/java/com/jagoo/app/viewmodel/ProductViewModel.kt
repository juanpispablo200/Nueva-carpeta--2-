package com.jagoo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.jagoo.app.data.Category
import com.jagoo.app.data.Dish
import com.jagoo.app.data.Restaurant
import com.jagoo.app.data.RestaurantData
import com.jagoo.app.data.repository.AddressItem
import com.jagoo.app.data.repository.LocalJagooRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocalJagooRepository(application)

    private val _categories = MutableStateFlow<List<Category>>(RestaurantData.categories)
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _restaurants = MutableStateFlow<List<Restaurant>>(repository.getRestaurants())
    val restaurants: StateFlow<List<Restaurant>> = _restaurants.asStateFlow()

    private val _dishes = MutableStateFlow<List<Dish>>(emptyList())
    val dishes: StateFlow<List<Dish>> = _dishes.asStateFlow()

    private val _selectedFilterChip = MutableStateFlow("Todos")
    val selectedFilterChip: StateFlow<String> = _selectedFilterChip.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _favoriteRestaurantIds = MutableStateFlow<Set<String>>(setOf("r1", "r3"))
    val favoriteRestaurantIds: StateFlow<Set<String>> = _favoriteRestaurantIds.asStateFlow()

    private val _addresses = MutableStateFlow<List<AddressItem>>(repository.getAddresses())
    val addresses: StateFlow<List<AddressItem>> = _addresses.asStateFlow()

    init {
        refreshData()
    }

    fun refreshData() {
        _restaurants.value = repository.getRestaurants()
        _addresses.value = repository.getAddresses()
    }

    fun selectFilterChip(chip: String) {
        _selectedFilterChip.value = chip
    }

    fun selectCategory(categoryName: String?) {
        _selectedCategory.value = categoryName
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(restaurantId: String) {
        _favoriteRestaurantIds.update { current ->
            if (current.contains(restaurantId)) current - restaurantId
            else current + restaurantId
        }
    }

    fun isFavorite(restaurantId: String): Boolean {
        return _favoriteRestaurantIds.value.contains(restaurantId)
    }

    fun getRestaurantById(restaurantId: String): Restaurant? {
        return _restaurants.value.find { it.id == restaurantId }
    }

    fun getDishesForRestaurant(restaurantId: String): List<Dish> {
        val loaded = repository.getDishesForRestaurant(restaurantId)
        _dishes.value = loaded
        return loaded
    }

    // --- MÉTODOS DEL PANEL ADMINISTRADOR Y DIRECCIONES ---

    fun updateRestaurantStatus(restaurantId: String, isOpen: Boolean) {
        repository.updateRestaurantStatus(restaurantId, isOpen)
        refreshData()
    }

    fun saveDish(dish: Dish) {
        repository.saveDish(dish)
        getDishesForRestaurant(dish.restaurantId)
    }

    fun deleteDish(dishId: String, restaurantId: String) {
        repository.deleteDish(dishId)
        getDishesForRestaurant(restaurantId)
    }

    fun toggleDishAvailability(dishId: String, restaurantId: String, isAvailable: Boolean) {
        repository.toggleDishAvailability(dishId, isAvailable)
        getDishesForRestaurant(restaurantId)
    }

    fun saveAddress(id: String, label: String, fullAddress: String, isPrimary: Boolean) {
        repository.saveAddress(id, label, fullAddress, isPrimary)
        _addresses.value = repository.getAddresses()
    }

    fun deleteAddress(id: String) {
        repository.deleteAddress(id)
        _addresses.value = repository.getAddresses()
    }

    fun setPrimaryAddress(id: String) {
        repository.setPrimaryAddress(id)
        _addresses.value = repository.getAddresses()
    }

    fun getPrimaryAddress(): String {
        return _addresses.value.find { it.isPrimary }?.fullAddress
            ?: "Av. República del Salvador, Quito"
    }

    fun getFilteredRestaurants(): List<Restaurant> {
        val query = _searchQuery.value.trim()
        val filter = _selectedFilterChip.value
        val cat = _selectedCategory.value

        var list = _restaurants.value

        if (cat != null && cat != "Todas" && cat != "Todos") {
            list = list.filter { it.category.contains(cat, ignoreCase = true) || cat.contains(it.category, ignoreCase = true) }
        }

        if (query.isNotBlank()) {
            list = list.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.category.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
            }
        }

        return when (filter) {
            "Ofertas" -> list.filter { it.isOffer }
            "Mejor Evaluados" -> list.filter { it.rating >= 4.7 }.sortedByDescending { it.rating }
            "< 30 min" -> list.filter {
                val minTime = it.deliveryTime.split("-").firstOrNull()?.trim()?.toIntOrNull() ?: 30
                minTime <= 30
            }
            "Envío Gratis" -> list.filter { it.deliveryFee == 0.0 }
            "Abiertos" -> list.filter { it.isOpen }
            "Populares" -> list.filter { it.isFeatured || it.rating >= 4.8 }
            "Favoritos" -> list.filter { _favoriteRestaurantIds.value.contains(it.id) }
            else -> list
        }
    }
}
