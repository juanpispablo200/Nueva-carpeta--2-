package com.jagoo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppOrderTest {

    private fun item(name: String, price: Double, qty: Int, opts: List<SelectedOption> = emptyList(), note: String = ""): CartItem {
        val dish = Dish(
            id = "d1", restaurantId = "r1", restaurantName = "Negocio",
            name = name, description = "", price = price, imageUrl = "", menuSection = "s"
        )
        return CartItem(dish, "r1", "Negocio", qty, opts, note)
    }

    @Test fun `mensaje incluye productos, subtotal y marca de origen`() {
        val msg = WhatsAppOrder.buildMessage("Brasa & Pan", listOf(item("Pollo", 5.0, 2)), 0, null)
        assertTrue(msg.contains("Brasa & Pan"))
        assertTrue(msg.contains("2 x Pollo"))
        assertTrue(msg.contains("Subtotal: \$10.00"))
        assertTrue(msg.contains("Jagoo"))
    }

    @Test fun `mensaje incluye opciones, nota, promo y direccion`() {
        val opt = SelectedOption("g", "Salsa", "o", "Ají", 0.5)
        val msg = WhatsAppOrder.buildMessage("N", listOf(item("Hamburguesa", 4.0, 1, listOf(opt), "sin cebolla")), 20, "Calle 1")
        assertTrue(msg.contains("(Ají)"))
        assertTrue(msg.contains("Nota: sin cebolla"))
        assertTrue(msg.contains("-20%"))
        assertTrue(msg.contains("Entregar en: Calle 1"))
    }

    @Test fun `url valida codifica el texto`() {
        val url = WhatsAppOrder.buildUrl("593991234567", "Hola mundo & más")!!
        assertTrue(url.startsWith("https://wa.me/593991234567?text="))
        assertTrue(url.contains("Hola%20mundo"))
        assertTrue(url.contains("%26"))
        assertTrue(!url.contains(" "))
    }

    @Test fun `telefono invalido no genera url`() {
        assertNull(WhatsAppOrder.buildUrl("12345", "x"))
        assertNull(WhatsAppOrder.buildUrl("", "x"))
        assertEquals("https://wa.me/593991234567?text=a", WhatsAppOrder.buildUrl("+593 99 123 4567", "a"))
    }
}
