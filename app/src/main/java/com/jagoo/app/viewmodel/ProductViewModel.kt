package com.jagoo.app.viewmodel

import androidx.lifecycle.ViewModel
import com.jagoo.app.data.Category
import com.jagoo.app.data.MockData
import com.jagoo.app.data.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProductViewModel : ViewModel() {

    private val _categories = MutableStateFlow<List<Category>>(MockData.categories)
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _products = MutableStateFlow<List<Product>>(MockData.products)
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _selectedFilterChip = MutableStateFlow("Todos")
    val selectedFilterChip: StateFlow<String> = _selectedFilterChip.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun selectFilterChip(chip: String) {
        _selectedFilterChip.value = chip
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
