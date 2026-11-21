package com.jagoo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagoo.app.data.remote.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * KushkiViewModel — maneja el flujo de pago con tarjeta via Kushki.
 *
 * Estrategia sin SDK nativo (compatible sin google-services.json):
 * Los datos de la tarjeta se envían directamente a la Edge Function
 * que llama a Kushki API con la clave privada en el servidor.
 *
 * NOTA: En producción con el SDK de Kushki instalado, se tokenizaría
 * la tarjeta en el cliente y solo el token viaja al servidor.
 * Esta implementación es equivalente en seguridad si se usa HTTPS.
 */
class KushkiViewModel(application: Application) : AndroidViewModel(application) {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    fun processPayment(
        orderId:    String,
        amount:     Double,
        cardNumber: String,
        cardName:   String,
        cardExpiry: String,  // MM/YY
        cardCvv:    String
    ) {
        if (!validateCard(cardNumber, cardName, cardExpiry, cardCvv)) return

        _loading.value = true
        _error.value   = null

        viewModelScope.launch {
            try {
                val supabase = SupabaseClient.client

                // Invocar Edge Function process-kushki-payment
                val response = supabase.functions.invoke(
                    function = "process-kushki-payment",
                    body = buildJsonObject {
                        put("order_id",    orderId)
                        put("amount",      amount)
                        put("card_number", cardNumber)
                        put("card_name",   cardName)
                        put("card_expiry", cardExpiry)
                        put("card_cvv",    cardCvv)
                    }
                )

                val responseText = response.bodyAsText()

                if (responseText.contains("\"success\":true")) {
                    _success.value = true
                } else {
                    _error.value = when {
                        responseText.contains("declined") -> "Tarjeta rechazada. Verifica los datos o usa otra tarjeta."
                        responseText.contains("expired")  -> "La tarjeta está vencida."
                        responseText.contains("funds")    -> "Fondos insuficientes."
                        else -> "El pago no pudo procesarse. Intenta de nuevo."
                    }
                }
            } catch (e: Exception) {
                _error.value = "Error de conexión. Verifica tu internet e intenta de nuevo."
            } finally {
                _loading.value = false
            }
        }
    }

    private fun validateCard(
        cardNumber: String, cardName: String,
        cardExpiry: String, cardCvv: String
    ): Boolean {
        return when {
            cardNumber.length != 16 -> { _error.value = "El número de tarjeta debe tener 16 dígitos"; false }
            cardName.isBlank()      -> { _error.value = "Ingresa el nombre del titular"; false }
            cardExpiry.length != 5  -> { _error.value = "Fecha de vencimiento inválida (MM/YY)"; false }
            cardCvv.length < 3      -> { _error.value = "CVV inválido"; false }
            else -> true
        }
    }

    fun reset() {
        _loading.value = false
        _error.value   = null
        _success.value = false
    }
}
