package com.jagoo.app.data

import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period

/**
 * Reglas puras (sin Android) usadas por el pedido. Se prueban con tests JVM.
 * El servidor repite todas las validaciones: esto solo da respuesta rápida en la app.
 */

object AgeRules {
    const val ADULT_AGE = 18

    fun isAdult(birth: LocalDate, today: LocalDate = LocalDate.now()): Boolean =
        !birth.isAfter(today) && Period.between(birth, today).years >= ADULT_AGE

    /** Construye la fecha o devuelve null si día/mes/año no forman una fecha real y pasada. */
    fun parseBirthDate(day: String, month: String, year: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val d = day.trim().toIntOrNull() ?: return null
        val m = month.trim().toIntOrNull() ?: return null
        val y = year.trim().toIntOrNull() ?: return null
        if (y < 1900) return null
        return try {
            LocalDate.of(y, m, d).takeIf { !it.isAfter(today) }
        } catch (_: DateTimeException) {
            null
        }
    }
}

object CashRules {
    /** Cambio a devolver; null si lo entregado no cubre el total. */
    fun change(total: Double, tendered: Double): Double? {
        if (tendered.isNaN() || total.isNaN()) return null
        val cents = Math.round((tendered - total) * 100)
        return if (cents < 0) null else cents / 100.0
    }

    /** Lee un monto escrito por el usuario ("20", "20.5", "20,50"). */
    fun parseAmount(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
}

/** Errores del servidor (códigos estables de create_order) traducidos para el cliente. */
enum class OrderErrorKind { NETWORK, FIELD, GENERIC }

data class OrderError(val code: String, val message: String, val kind: OrderErrorKind = OrderErrorKind.GENERIC)

object OrderErrors {
    private val messages = mapOf(
        "NO_AUTENTICADO" to "Tu sesión expiró. Inicia sesión de nuevo.",
        "CUENTA_NO_VALIDA" to "Tu cuenta no puede hacer pedidos.",
        "METODO_PAGO_INVALIDO" to "Elige efectivo o transferencia.",
        "NEGOCIO_NO_DISPONIBLE" to "Este negocio no está disponible ahora.",
        "NEGOCIO_CERRADO" to "Este negocio está cerrado en este momento.",
        "CARRITO_VACIO" to "Tu carrito está vacío.",
        "PRODUCTO_NO_DISPONIBLE" to "Algún producto ya no está disponible. Revisa tu carrito.",
        "CANTIDAD_INVALIDA" to "Revisa las cantidades de tu carrito.",
        "PRECIO_INVALIDO" to "Los precios cambiaron. Vuelve a abrir el negocio para actualizarlos.",
        "ZONA_REQUERIDA" to "Elige tu zona de entrega.",
        "ZONA_INVALIDA" to "La zona elegida ya no está disponible. Elige otra.",
        "EDAD_NO_VERIFICADA" to "Confirma tu fecha de nacimiento para pedir productos para mayores de 18.",
        "MENOR_DE_EDAD" to "Debes ser mayor de 18 años para pedir estos productos.",
        "RECETA_REQUERIDA" to "Sube una foto de tu receta para pedir estos productos.",
        "EFECTIVO_INSUFICIENTE" to "El monto con el que pagas no cubre el total del pedido.",
        "REPARTO_INVALIDO" to "No pudimos procesar el pedido. Intenta más tarde."
    )

    fun fromThrowable(t: Throwable): OrderError {
        val raw = t.message.orEmpty()

        // PEDIDO_MINIMO:5.00
        Regex("PEDIDO_MINIMO:\\s*([0-9]+(?:\\.[0-9]+)?)").find(raw)?.let {
            return OrderError("PEDIDO_MINIMO", "El pedido mínimo de este negocio es $${it.groupValues[1]}.", OrderErrorKind.FIELD)
        }
        for ((code, text) in messages) {
            if (raw.contains(code)) return OrderError(code, text, OrderErrorKind.FIELD)
        }

        val lower = raw.lowercase()
        val network = t is java.io.IOException ||
            lower.contains("unable to resolve") || lower.contains("timeout") ||
            lower.contains("timed out") || lower.contains("connect") || lower.contains("network")
        return if (network) {
            OrderError("NETWORK", "Sin conexión estable. Revisa tu internet e intenta de nuevo.", OrderErrorKind.NETWORK)
        } else {
            OrderError("UNKNOWN", "No se pudo crear el pedido. Intenta de nuevo.")
        }
    }
}

object TransferRules {
    /** Minutos que faltan para el plazo (0 si venció o no hay plazo). */
    fun minutesLeft(deadlineMillis: Long?, nowMillis: Long): Long {
        if (deadlineMillis == null) return 0
        val diff = deadlineMillis - nowMillis
        return if (diff <= 0) 0 else (diff + 59_999) / 60_000
    }
}
