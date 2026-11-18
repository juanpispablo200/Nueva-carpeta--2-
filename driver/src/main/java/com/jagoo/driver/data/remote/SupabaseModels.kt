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
    @SerialName("restaurant_phone") val restaurantPhone: String? = "+593 99 111 2222",
    @SerialName("customer_name") val customerName: String?     = "Cliente Jagoo",
    @SerialName("customer_phone") val customerPhone: String?   = "+593 99 999 8888",
    @SerialName("delivery_address") val deliveryAddress: String? = null,
    val total: Double                                         = 0.0,
    val status: String                                        = "ready",
    @SerialName("courier_id") val courierId: String?          = null,
    @SerialName("created_at") val createdAt: String           = "",
    val items: List<DriverOrderItemDto>                        = emptyList()
)
