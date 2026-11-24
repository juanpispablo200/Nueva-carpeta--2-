package com.jagoo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El catálogo de ejemplo debe respetar las mismas reglas que el servidor. */
class CatalogConsistencyTest {

    private val validTypes = BusinessType.entries.map { it.key }.toSet()

    @Test fun every_restaurant_has_a_valid_business_type() {
        RestaurantData.restaurants.forEach {
            assertTrue("${it.id} tiene tipo inválido: ${it.businessType}", it.businessType in validTypes)
        }
    }

    @Test fun catalog_includes_pharmacy_and_liquor_store() {
        assertTrue(RestaurantData.restaurants.any { it.businessType == "farmacia" })
        assertTrue(RestaurantData.restaurants.any { it.businessType == "licoreria" })
    }

    @Test fun every_dish_belongs_to_an_existing_business() {
        val ids = RestaurantData.restaurants.map { it.id }.toSet()
        RestaurantData.dishes.forEach { assertTrue("${it.id} sin negocio ${it.restaurantId}", it.restaurantId in ids) }
    }

    @Test fun dish_and_restaurant_ids_are_unique() {
        assertEquals(RestaurantData.restaurants.size, RestaurantData.restaurants.map { it.id }.toSet().size)
        assertEquals(RestaurantData.dishes.size, RestaurantData.dishes.map { it.id }.toSet().size)
    }

    @Test fun every_liquor_store_product_is_age_restricted_and_nothing_else_is() {
        val byId = RestaurantData.restaurants.associateBy { it.id }
        RestaurantData.dishes.forEach {
            val liquor = byId[it.restaurantId]?.businessType == "licoreria"
            assertEquals("${it.id}: +18 solo en licorería", liquor, it.isAgeRestricted)
        }
    }

    @Test fun no_product_requires_prescription() {
        assertFalse(RestaurantData.dishes.any { it.requiresPrescription })
    }

    @Test fun unknown_business_type_falls_back_to_restaurant() {
        assertEquals(BusinessType.RESTAURANTE, BusinessType.fromKey("algo-raro"))
        assertEquals(BusinessType.RESTAURANTE, BusinessType.fromKey(null))
        assertEquals(BusinessType.LICORERIA, BusinessType.fromKey("licoreria"))
    }

    @Test fun order_state_defaults_are_cash_and_pending() {
        val o = ActiveOrder(
            id = "x", restaurantId = "r", restaurantName = "n", restaurantLogoUrl = "",
            items = emptyList(), subtotal = 1.0, deliveryFee = 0.0, total = 1.0,
            estimatedMinutes = 25, statusStep = OrderStatusStep.CONFIRMED, timelineItems = emptyList()
        )
        assertEquals("cash", o.paymentMethod)
        assertEquals("pending", o.paymentStatus)
        assertEquals("Esperando confirmación", OrderStatusText.label(o.copy(rawStatus = "pending")))
    }
}
