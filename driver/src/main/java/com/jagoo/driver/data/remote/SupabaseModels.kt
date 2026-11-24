package com.jagoo.driver.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriverProfileDto(
    val id: String                                  = "",
    @SerialName("full_name")  val fullName: String  = "",
    val phone: String                               = "",
    val role: String                                = "repartidor",
    @SerialName("is_active")  val isActive: Boolean = true,
    @SerialName("is_available") val isAvailable: Boolean = false
)

@Serializable
data class DriverOrderItemDto(
    val id: String                                    = "",
    @SerialName("dish_name") val dishName: String    = "",
    val quantity: Int                                 = 1,
    @SerialName("unit_price") val unitPrice: Double   = 0.0
)

@Serializable
data class DriverOrderDto(
    val id: String                                            = "",
    @SerialName("restaurant_name") val restaurantName: String = "",
    // Contacto: no existe en la tabla orders; lo completa get_order_contact. Sin valores falsos.
    @SerialName("restaurant_phone") val restaurantPhone: String? = null,
    @SerialName("customer_name") val customerName: String?     = null,
    @SerialName("customer_phone") val customerPhone: String?   = null,
    // Lista de pedidos libres (list_available_orders): sin dirección ni total
    @SerialName("driver_amount") val driverAmount: Double?     = null,
    @SerialName("delivery_zone_name") val zoneName: String?    = null,
    @SerialName("delivery_address") val deliveryAddress: String? = null,
    val total: Double                                         = 0.0,
    val status: String                                        = "ready",
    @SerialName("courier_id") val courierId: String?          = null,
    @SerialName("created_at") val createdAt: String           = "",
    val items: List<DriverOrderItemDto>                        = emptyList(),
    @SerialName("payment_method") val paymentMethod: String   = "cash",
    @SerialName("cash_tendered") val cashTendered: Double?    = null,
    @SerialName("change_due") val changeDue: Double?          = null,
    @SerialName("age_check_required") val ageCheckRequired: Boolean = false,
    @SerialName("age_verified_at") val ageVerifiedAt: String? = null
)
