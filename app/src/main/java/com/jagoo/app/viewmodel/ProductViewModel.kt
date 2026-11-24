package com.jagoo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagoo.app.data.Category
import com.jagoo.app.data.Dish
import com.jagoo.app.data.Restaurant
import com.jagoo.app.data.RestaurantData
import com.jagoo.app.data.remote.SupabaseClient
import com.jagoo.app.data.remote.toRestaurant
import com.jagoo.app.data.repository.AddressItem
import com.jagoo.app.data.repository.LocalJagooRepository
import com.jagoo.app.data.repository.SupabaseJagooRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

    // Tipo de negocio (restaurante, tienda, farmacia, licoreria); null = todos
    private val _selectedType = MutableStateFlow<String?>(null)
    val selectedType: StateFlow<String?> = _selectedType.asStateFlow()

    /** Negocios activos del tipo elegido. */
    val visibleRestaurants: StateFlow<List<Restaurant>> =
        kotlinx.coroutines.flow.combine(_restaurants, _selectedType) { list, type ->
            list.filter { it.isActive && (type == null || it.businessType == type) }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, RestaurantData.restaurants)

    fun selectType(key: String?) {
        _selectedType.value = if (_selectedType.value == key) null else key
        _selectedCategory.value = null
    }
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _favoriteRestaurantIds = MutableStateFlow<Set<String>>(setOf("r1", "r3"))
    val favoriteRestaurantIds: StateFlow<Set<String>> = _favoriteRestaurantIds.asStateFlow()

    private val _favoritesLoading = MutableStateFlow(false)
    val favoritesLoading: StateFlow<Boolean> = _favoritesLoading.asStateFlow()

    // Estado de carga global — para mostrar errores con retry
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Promociones vigentes de los negocios (banner del home). Vacío = no se muestra banner.
    private val _promotions = MutableStateFlow<List<com.jagoo.app.data.remote.PromotionDto>>(emptyList())
    val promotions: StateFlow<List<com.jagoo.app.data.remote.PromotionDto>> = _promotions.asStateFlow()

    private val _addresses = MutableStateFlow<List<AddressItem>>(localRepo.getAddresses())
    val addresses: StateFlow<List<AddressItem>> = _addresses.asStateFlow()

    // ── Al iniciar: carga datos desde Supabase, cae a mock si falla ──────────
    init {
        loadRestaurantsFromSupabase()
        loadAddressesFromSupabase()
        loadFavoritesFromSupabase()
        loadPromotions()
        setupSearchDebounce()
    }

    @OptIn(FlowPreview::class)
    private fun setupSearchDebounce() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { /* El filtrado es reactivo vía getFilteredRestaurants() */ }
        }
    }

    /** true cuando la lista viene del servidor (no del ejemplo local). */
    private var loadedFromServer = false

    private val cachePrefs by lazy { getApplication<Application>().getSharedPreferences("jagoo_cache", android.content.Context.MODE_PRIVATE) }
    private val cacheJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun saveRestaurantsCache(dtos: List<com.jagoo.app.data.remote.RestaurantDto>) {
        try {
            cachePrefs.edit().putString("restaurants", cacheJson.encodeToString(dtos)).apply()
        } catch (_: Exception) { /* la caché es opcional */ }
    }

    private fun loadRestaurantsCache(): List<Restaurant>? = try {
        cachePrefs.getString("restaurants", null)
            ?.let { cacheJson.decodeFromString<List<com.jagoo.app.data.remote.RestaurantDto>>(it) }
            ?.takeIf { it.isNotEmpty() }
            ?.map { it.toRestaurant() }
    } catch (_: Exception) { null }

    private fun loadRestaurantsFromSupabase() {
        viewModelScope.launch {
            _isLoading.value = true
            _loadError.value = null
            try {
                // Con internet lento no se queda cargando para siempre
                val result = kotlinx.coroutines.withTimeout(15_000L) { supabaseRepo.getRestaurantsOrThrow() }
                _restaurants.value = result
                loadedFromServer = true
            } catch (e: Exception) {
                _loadError.value =
                    if (loadedFromServer) "Conexión lenta o sin internet. Mostrando la última lista que cargó."
                    else "Sin conexión. Mostrando datos de ejemplo."
                // Se conserva la última lista buena (o el ejemplo local si nunca cargó)
            } finally {
                _isLoading.value = false
            }
        }
    }
    fun retryLoad() {
        loadRestaurantsFromSupabase()
        loadAddressesFromSupabase()
        loadFavoritesFromSupabase()
        loadPromotions()
    }

    fun clearError() { _loadError.value = null }

    private fun loadAddressesFromSupabase() {
        viewModelScope.launch {
            val result = supabaseRepo.getAddressesSuspend()
            if (result.isNotEmpty()) _addresses.value = result
        }
    }

    fun refreshData() {
        loadRestaurantsFromSupabase()
        loadAddressesFromSupabase()
        loadPromotions()
    }

    /** Promociones reales y vigentes (list_active_promotions). Si falla, no se muestra banner. */
    fun loadPromotions() {
        viewModelScope.launch {
            try {
                _promotions.value = kotlinx.coroutines.withTimeout(10_000L) {
                    SupabaseClient.client.postgrest
                        .rpc("list_active_promotions")
                        .decodeList<com.jagoo.app.data.remote.PromotionDto>()
                }
            } catch (_: Exception) {
                _promotions.value = emptyList()
            }
        }
    }

    // ── Filtros y búsqueda ────────────────────────────────────────────────────
    fun selectFilterChip(chip: String)      { _selectedFilterChip.value = chip }
    fun selectCategory(name: String?)       { _selectedCategory.value = name }
    fun updateSearchQuery(query: String)    { _searchQuery.value = query }

    // ── Favoritos persistentes en Supabase ───────────────────────────────────
    private fun loadFavoritesFromSupabase() {
        viewModelScope.launch {
            try {
                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@launch
                val rows = SupabaseClient.client
                    .from("favorites")
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<FavoriteRow>()
                _favoriteRestaurantIds.value = rows.map { it.restaurantId }.toSet()
            } catch (_: Exception) { /* Mantiene favoritos en memoria si falla */ }
        }
    }

    fun toggleFavorite(restaurantId: String) {
        viewModelScope.launch {
            _favoritesLoading.value = true
            try {
                val isFav = SupabaseClient.client.postgrest
                    .rpc("toggle_favorite", buildJsonObject { put("p_restaurant_id", restaurantId) })
                    .decodeAs<Boolean>()
                _favoriteRestaurantIds.update { current ->
                    if (isFav) current + restaurantId else current - restaurantId
                }
            } catch (_: Exception) {
                // Fallback optimista si Supabase falla
                _favoriteRestaurantIds.update { current ->
                    if (current.contains(restaurantId)) current - restaurantId
                    else current + restaurantId
                }
            } finally {
                _favoritesLoading.value = false
            }
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

        var list = visibleRestaurants.value

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

@kotlinx.serialization.Serializable
private data class FavoriteRow(
    @kotlinx.serialization.SerialName("restaurant_id") val restaurantId: String
)
