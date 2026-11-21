package com.jagoo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagoo.app.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RatingViewModel(application: Application) : AndroidViewModel(application) {

    private val _restaurantStars   = MutableStateFlow(0)
    val restaurantStars: StateFlow<Int> = _restaurantStars.asStateFlow()

    private val _courierStars      = MutableStateFlow(0)
    val courierStars: StateFlow<Int> = _courierStars.asStateFlow()

    private val _restaurantComment = MutableStateFlow("")
    val restaurantComment: StateFlow<String> = _restaurantComment.asStateFlow()

    private val _courierComment    = MutableStateFlow("")
    val courierComment: StateFlow<String> = _courierComment.asStateFlow()

    private val _loading           = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error             = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _submitted         = MutableStateFlow(false)
    val submitted: StateFlow<Boolean> = _submitted.asStateFlow()

    fun setRestaurantStars(stars: Int)      { _restaurantStars.value   = stars }
    fun setCourierStars(stars: Int)         { _courierStars.value      = stars }
    fun setRestaurantComment(text: String)  { _restaurantComment.value = text  }
    fun setCourierComment(text: String)     { _courierComment.value    = text  }

    fun submit(orderId: String) {
        if (_restaurantStars.value == 0) {
            _error.value = "Selecciona al menos una estrella para el restaurante"
            return
        }
        _loading.value = true
        _error.value   = null

        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("p_order_id",           orderId)
                    put("p_restaurant_stars",   _restaurantStars.value)
                    if (_courierStars.value > 0)
                        put("p_courier_stars",  _courierStars.value)
                    if (_restaurantComment.value.isNotBlank())
                        put("p_restaurant_comment", _restaurantComment.value.trim())
                    if (_courierComment.value.isNotBlank())
                        put("p_courier_comment", _courierComment.value.trim())
                }
                SupabaseClient.client.postgrest.rpc("submit_rating", params)
                _submitted.value = true
            } catch (e: Exception) {
                _error.value = when {
                    e.message?.contains("already") == true ||
                    e.message?.contains("unique")  == true -> "Ya calificaste este pedido."
                    else -> "Error al enviar calificación. Intenta de nuevo."
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun reset() {
        _restaurantStars.value   = 0
        _courierStars.value      = 0
        _restaurantComment.value = ""
        _courierComment.value    = ""
        _error.value             = null
        _submitted.value         = false
    }
}
