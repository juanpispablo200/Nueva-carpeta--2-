package com.jagoo.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderRulesTest {

    private val today = LocalDate.of(2025, 6, 15)

    // ── Edad ─────────────────────────────────────────────────────────────────
    @Test fun adult_exactly18_today_is_adult() =
        assertTrue(AgeRules.isAdult(LocalDate.of(2007, 6, 15), today))

    @Test fun one_day_before_18_is_minor() =
        assertFalse(AgeRules.isAdult(LocalDate.of(2007, 6, 16), today))

    @Test fun future_birth_is_not_adult() =
        assertFalse(AgeRules.isAdult(LocalDate.of(2030, 1, 1), today))

    @Test fun parseBirthDate_valid() =
        assertEquals(LocalDate.of(1990, 2, 28), AgeRules.parseBirthDate("28", "2", "1990", today))

    @Test fun parseBirthDate_rejects_impossible_dates() {
        assertNull(AgeRules.parseBirthDate("31", "2", "1990", today))
        assertNull(AgeRules.parseBirthDate("0", "5", "1990", today))
        assertNull(AgeRules.parseBirthDate("10", "13", "1990", today))
        assertNull(AgeRules.parseBirthDate("a", "5", "1990", today))
        assertNull(AgeRules.parseBirthDate("10", "5", "1800", today))
    }

    @Test fun parseBirthDate_rejects_future() =
        assertNull(AgeRules.parseBirthDate("1", "1", "2031", today))

    @Test fun leap_day_birth_is_valid() =
        assertNotNull(AgeRules.parseBirthDate("29", "2", "2000", today))

    // ── Efectivo ─────────────────────────────────────────────────────────────
    @Test fun change_is_computed_in_cents() =
        assertEquals(5.50, CashRules.change(14.50, 20.0)!!, 0.0001)

    @Test fun change_zero_when_exact() =
        assertEquals(0.0, CashRules.change(14.50, 14.50)!!, 0.0001)

    @Test fun change_null_when_not_enough() =
        assertNull(CashRules.change(14.50, 14.49))

    @Test fun change_has_no_floating_point_noise() =
        assertEquals(0.1, CashRules.change(0.2, 0.3)!!, 0.0)

    @Test fun parseAmount_accepts_comma_and_dot() {
        assertEquals(20.5, CashRules.parseAmount("20,5")!!, 0.0)
        assertEquals(20.0, CashRules.parseAmount(" 20 ")!!, 0.0)
    }

    @Test fun parseAmount_rejects_garbage_and_non_positive() {
        assertNull(CashRules.parseAmount(""))
        assertNull(CashRules.parseAmount("abc"))
        assertNull(CashRules.parseAmount("0"))
        assertNull(CashRules.parseAmount("-5"))
    }

    // ── Errores del servidor ─────────────────────────────────────────────────
    @Test fun server_code_is_translated() {
        val e = OrderErrors.fromThrowable(Exception("ERROR: EDAD_NO_VERIFICADA (P0001)"))
        assertEquals("EDAD_NO_VERIFICADA", e.code)
        assertTrue(e.message.contains("fecha de nacimiento"))
    }

    @Test fun minimum_order_amount_is_shown() {
        val e = OrderErrors.fromThrowable(Exception("PEDIDO_MINIMO:7.50"))
        assertEquals("PEDIDO_MINIMO", e.code)
        assertTrue(e.message.contains("7.50"))
    }

    @Test fun network_errors_are_recognized() {
        val e = OrderErrors.fromThrowable(java.net.UnknownHostException("Unable to resolve host"))
        assertEquals(OrderErrorKind.NETWORK, e.kind)
    }

    @Test fun unknown_error_has_generic_message_without_leaking_details() {
        val e = OrderErrors.fromThrowable(Exception("duplicate key value violates constraint orders_pkey"))
        assertEquals("UNKNOWN", e.code)
        assertFalse(e.message.contains("orders_pkey"))
    }

    // ── Transferencia: plazo ─────────────────────────────────────────────────
    @Test fun minutesLeft_rounds_up_and_floors_at_zero() {
        assertEquals(30, TransferRules.minutesLeft(30 * 60_000L, 0))
        assertEquals(1, TransferRules.minutesLeft(1_000L, 0))
        assertEquals(0, TransferRules.minutesLeft(1_000L, 2_000L))
        assertEquals(0, TransferRules.minutesLeft(null, 0))
    }

    // ── Texto de estados ─────────────────────────────────────────────────────
    private fun order(
        raw: String, method: String = "cash", pay: String = "pending",
        rx: Boolean = false, rxStatus: String = "none", cancel: String? = null
    ) = ActiveOrder(
        id = "x", restaurantId = "r", restaurantName = "n", restaurantLogoUrl = "",
        items = emptyList(), subtotal = 1.0, deliveryFee = 0.0, total = 1.0,
        estimatedMinutes = 25, statusStep = OrderStatusStep.CONFIRMED, timelineItems = emptyList(),
        rawStatus = raw, paymentMethod = method, paymentStatus = pay,
        prescriptionRequired = rx, prescriptionStatus = rxStatus, cancelReason = cancel
    )

    @Test fun transfer_without_proof_says_proof_is_missing() =
        assertEquals("Falta el comprobante", OrderStatusText.label(order("pending", "transfer", "pending")))

    @Test fun transfer_with_proof_says_verifying() =
        assertEquals("Pago en verificación", OrderStatusText.label(order("pending", "transfer", "verifying")))

    @Test fun prescription_pending_takes_priority() =
        assertEquals("Receta en revisión", OrderStatusText.label(order("pending", rx = true, rxStatus = "pending")))

    @Test fun timeout_cancellation_explains_why() {
        val o = order("cancelled", "transfer", cancel = "TRANSFER_TIMEOUT")
        assertEquals("Cancelado", OrderStatusText.label(o))
        assertTrue(OrderStatusText.description(o).contains("comprobante"))
    }

    @Test fun rejected_prescription_cancellation_explains_why() =
        assertTrue(OrderStatusText.description(order("cancelled", cancel = "RECETA_RECHAZADA")).contains("receta"))

    // ── Número de pedido ─────────────────────────────────────────────────────
    @Test fun orderNumber_is_short_and_stable() {
        assertEquals("JG-A1B2C3", orderNumber("a1b2c3d4-0000-0000-0000-000000000000"))
        assertEquals("JG-1234", orderNumber("JG-1234"))
    }
}
