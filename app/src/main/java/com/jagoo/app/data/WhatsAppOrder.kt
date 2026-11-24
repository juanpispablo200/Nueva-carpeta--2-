package com.jagoo.app.data

import java.net.URLEncoder
import java.util.Locale

/**
 * Pedido alternativo por WhatsApp: arma el mensaje prellenado con el carrito.
 * El pedido hecho así NO pasa por Jagoo (sin seguimiento, sin pago en la app).
 * El servidor no entrega el número de licorerías ni farmacias.
 */
object WhatsAppOrder {

    private fun money(v: Double) = String.format(Locale.US, "%.2f", v)

    fun buildMessage(
        restaurantName: String,
        items: List<CartItem>,
        promoPercent: Int = 0,
        deliveryAddress: String? = null
    ): String {
        val subtotal = items.sumOf { it.totalPrice }
        val sb = StringBuilder()
        sb.append("Hola, quiero hacer un pedido en ").append(restaurantName).append(":\n\n")
        for (it in items) {
            sb.append("• ").append(it.quantity).append(" x ").append(it.dish.name)
            val extras = it.selectedOptions.joinToString(", ") { o -> o.optionName }
            if (extras.isNotBlank()) sb.append(" (").append(extras).append(")")
            sb.append(" - $").append(money(it.totalPrice)).append('\n')
            if (it.specialInstructions.isNotBlank()) {
                sb.append("   Nota: ").append(it.specialInstructions.trim()).append('\n')
            }
        }
        sb.append("\nSubtotal: $").append(money(subtotal))
        if (promoPercent > 0) sb.append("\nPromoción vigente: -").append(promoPercent).append("%")
        if (!deliveryAddress.isNullOrBlank()) sb.append("\n\nEntregar en: ").append(deliveryAddress.trim())
        sb.append("\n\n(Mensaje enviado desde la app Jagoo)")
        return sb.toString()
    }

    /** Enlace wa.me. `phone` debe venir solo con dígitos y código de país (ej. 593991234567). */
    fun buildUrl(phone: String, message: String): String? {
        val digits = phone.filter { it.isDigit() }
        if (digits.length !in 11..15) return null
        return "https://wa.me/$digits?text=" + URLEncoder.encode(message, "UTF-8").replace("+", "%20")
    }
}
