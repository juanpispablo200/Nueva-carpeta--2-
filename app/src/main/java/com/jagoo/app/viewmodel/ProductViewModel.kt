package com.jagoo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagoo.app.data.Category
import com.jagoo.app.data.Dish
import com.jagoo.app.data.Restaurant
import com.jagoo.app.data.RestaurantData
import com.jagoo.app.data.repository.AddressItem
import com.jagoo.app.data.repository.LocalJagooRepository
import com.jagoo.app.data.repository.SupabaseJagooRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val localRepo    = LocalJagooRepository(application)
    private val supabaseRepo = SupabaseJagooRepository(localRepo)

    private val _categories = MutableStateFlow<List<Category>>(RestaurantData.categories)
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _restaurants = MutableStateFlow<List<Restaurant>>(RestaurantData.restaurants)
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

    private val _addresses = MutableStateFlow<List<AddressItem>>(localRepo.getAddresses())
    val addresses: StateFlow<List<AddressItem>> = _addresses.asStateFlow()

    // ── Al iniciar: carga datos desde Supabase, cae a mock si falla ──────────
    init {
        loadRestaurantsFromSupabase()
        loadAddressesFromSupabase()
    }

    private fun loadRestaurantsFromSupabase() {
        viewModelScope.launch {
            val result = supabaseRepo.getRestaurantsSuspend()
            if (result.isNotEmpty()) _restaurants.value = result
        }
    }

    private fun loadAddressesFromSupabase() {
        viewModelScope.launch {
            val result = supabaseRepo.getAddressesSuspend()
            if (result.isNotEmpty()) _addresses.value = result
        }
    }

    fun refreshData() {
        loadRestaurantsFromSupabase()
        loadAddressesFromSupabase()
    }

    // ── Filtros y búsqueda ────────────────────────────────────────────────────
    fun selectFilterChip(chip: String)      { _selectedFilterChip.value = chip }
    fun selectCategory(name: String?)       { _selectedCategory.value = name }
    fun updateSearchQuery(query: String)    { _searchQuery.value = query }

    // ── Favoritos ─────────────────────────────────────────────────────────────
    fun toggleFavorite(restaurantId: String) {
        _favoriteRestaurantIds.update { current ->
            if (current.contains(restaurantId)) current - restaurantId
            else current + restaurantId
        }
    }
    fun isFavorite(restaurantId: String) = _favoriteRestaurantIds.value.contains(restaurantId)

    // ── Restaurantes ──────────────────────────────────────────────────────────
    fun getRestaurantById(restaurantId: String): Restaurant? =
        _restaurants.value.find { it.id == restaurantId }

    // ── Platos — carga desde Supabase con fallback a mock ────────────────────
    fun getDishesForRestaurant(restaurantId: String): List<Dish> {
        viewModelScope.launch {
            val result = supabaseRepo.getDishesSuspend(restaurantId)
            _dishes.value = result
        }
        // Retornar inmediatamente del mock mientras llega Supabase
        val cached = _dishes.value.filter { it.restaurantId == restaurantId }
        return cached.ifEmpty { localRepo.getDishesForRestaurant(restaurantId) }
    }

    // ── Estado del restaurante (admin) ────────────────────────────────────────
    fun updateRestaurantStatus(restaurantId: String, isOpen: Boolean) {
        viewModelScope.launch {
            supabaseRepo.updateRestaurantStatusSuspend(restaurantId, isOpen)
            loadRestaurantsFromSupabase()
        }
    }

    // ── CRUD platos (admin) ───────────────────────────────────────────────────
    fun saveDish(dish: Dish) {
        viewModelScope.launch {
            supabaseRepo.saveDishSuspend(dish)
            getDishesForRestaurant(dish.restaurantId)
        }
    }

    fun deleteDish(dishId: String, restaurantId: String) {
        viewModelScope.launch {
            supabaseRepo.deleteDishSuspend(dishId)
            getDishesForRestaurant(restaurantId)
        }
    }

    fun toggleDishAvailability(dishId: String, restaurantId: String, isAvailable: Boolean) {
        viewModelScope.launch {
            supabaseRepo.toggleDishAvailabilitySuspend(dishId, isAvailable)
            getDishesForRestaurant(restaurantId)
        }
    }

    // ── CRUD direcciones ──────────────────────────────────────────────────────
    fun saveAddress(id: String, label: String, fullAddress: String, isPrimary: Boolean) {
        viewModelScope.launch {
            supabaseRepo.saveAddressSuspend(id, label, fullAddress, isPrimary)
            loadAddressesFromSupabase()
        }
    }

    fun deleteAddress(id: String) {
        viewModelScope.launch {
            supabaseRepo.deleteAddressSuspend(id)
            loadAddressesFromSupabase()
        }
    }

    fun setPrimaryAddress(id: String) {
        viewModelScope.launch {
            supabaseRepo.setPrimaryAddressSuspend(id)
            loadAddressesFromSupabase()
        }
    }

    fun getPrimaryAddress(): String =
        _addresses.value.find { it.isPrimary }?.fullAddress
            ?: "Av. República del Salvador, Quito"

    // ── Filtrar restaurantes ───────────────────────────────────────────────────
    fun getFilteredRestaurants(): List<Restaurant> {
        val query  = _searchQuery.value.trim()
        val filter = _selectedFilterChip.value
        val cat    = _selectedCategory.value

        var list = _restaurants.value

        if (cat != null && cat != "Todas" && cat != "Todos") {
            list = list.filter {
                it.category.contains(cat, ignoreCase = true) ||
                cat.contains(it.category, ignoreCase = true)
            }
        }

        if (query.isNotBlank()) {
            list = list.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
            }
        }

        return when (filter) {
            "Ofertas"         -> list.filter { it.isOffer }
            "Mejor Evaluados" -> list.filter { it.rating >= 4.7 }.sortedByDescending { it.rating }
            "< 30 min"        -> list.filter {
                it.deliveryTime.split("-").firstOrNull()?.trim()?.toIntOrNull() ?: 30 <= 30
            }
            "Envío Gratis"    -> list.filter { it.deliveryFee == 0.0 }
            "Abiertos"        -> list.filter { it.isOpen }
            "Populares"       -> list.filter { it.isFeatured || it.rating >= 4.8 }
            "Favoritos"       -> list.filter { _favoriteRestaurantIds.value.contains(it.id) }
            else              -> list
        }
    }
}
