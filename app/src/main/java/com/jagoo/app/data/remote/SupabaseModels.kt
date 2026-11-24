package com.jagoo.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modelos de Supabase.
 * Usan @Serializable de kotlinx.serialization para que Supabase-kt
 * pueda convertir automáticamente el JSON de la API a estas clases.
 */

@Serializable
data class ProfileDto(
    val id: String                                  = "",
    @SerialName("full_name")  val fullName: String  = "",
    val phone: String                               = "",
    val role: String                                = "customer"
)

@Serializable
data class RestaurantDto(
    val id: String                                       = "",
    val name: String                                     = "",
    val category: String                                 = "",
    val description: String                              = "",
    @SerialName("logo_url")     val logoUrl: String      = "",
    @SerialName("cover_url")    val coverUrl: String     = "",
    @SerialName("brand_color")  val brandColor: String   = "#FFD600",
    val rating: Double                                   = 0.0,
    @SerialName("review_count") val reviewCount: Int     = 0,
    @SerialName("delivery_time") val deliveryTime: String = "30-40 min",
    @SerialName("delivery_fee") val deliveryFee: Double  = 1.50,
    @SerialName("min_order")    val minOrder: Double     = 5.00,
    @SerialName("is_open")      val isOpen: Boolean      = true,
    @SerialName("is_featured")  val isFeatured: Boolean  = false,
    @SerialName("is_offer")     val isOffer: Boolean     = false,
    @SerialName("offer_text")   val offerText: String?   = null,
    @SerialName("owner_id")     val ownerId: String?     = null,
    @SerialName("business_type") val businessType: String = "restaurante",
    @SerialName("is_active")    val isActive: Boolean    = true
)

@Serializable
data class DishDto(
    val id: String                                           = "",
    @SerialName("restaurant_id")   val restaurantId: String = "",
    @SerialName("restaurant_name") val restaurantName: String = "",
    @SerialName("section_name")    val sectionName: String  = "",
    val name: String                                         = "",
    val description: String                                  = "",
    val price: Double                                        = 0.0,
    @SerialName("image_url")       val imageUrl: String      = "",
    @SerialName("is_available")    val isAvailable: Boolean  = true,
    @SerialName("is_popular")      val isPopular: Boolean    = false,
    @SerialName("is_offer")        val isOffer: Boolean      = false,
    @SerialName("offer_badge")     val offerBadge: String?   = null,
    // option_groups viene como JSON de Supabase; se parsea en el mapper
    @SerialName("option_groups")   val optionGroupsJson: String = "[]",
    @SerialName("is_age_restricted")     val isAgeRestricted: Boolean = false,
    @SerialName("requires_prescription") val requiresPrescription: Boolean = false
)

@Serializable
data class AddressDto(
    val id: String                                     = "",
    @SerialName("user_id")     val userId: String      = "",
    val label: String                                  = "Casa",
    @SerialName("full_address") val fullAddress: String = "",
    @SerialName("is_primary")  val isPrimary: Boolean  = false
)

@Serializable
data class OrderDto(
    val id: String                                            = "",
    @SerialName("customer_id")        val customerId: String = "",
    @SerialName("restaurant_id")      val restaurantId: String = "",
    @SerialName("restaurant_name")    val restaurantName: String = "",
    @SerialName("restaurant_logo_url") val restaurantLogoUrl: String = "",
    val subtotal: Double                                      = 0.0,
    @SerialName("delivery_fee")       val deliveryFee: Double = 0.0,
    val total: Double                                         = 0.0,
    val status: String                                        = "pending",
    @SerialName("delivery_address")   val deliveryAddress: String? = null,
    @SerialName("courier_id")         val courierId: String? = null,
    @SerialName("courier_name")       val courierName: String? = null,
    @SerialName("courier_vehicle")    val courierVehicle: String? = null,
    @SerialName("courier_plate")      val courierPlate: String? = null,
    @SerialName("created_at")         val createdAt: String   = "",
    @SerialName("payment_method")     val paymentMethod: String? = null,
    @SerialName("payment_status")     val paymentStatus: String? = null,
    @SerialName("change_due")         val changeDue: Double? = null,
    @SerialName("transfer_deadline")  val transferDeadline: String? = null,
    @SerialName("cancel_reason")      val cancelReason: String? = null,
    @SerialName("age_check_required") val ageCheckRequired: Boolean = false,
    @SerialName("prescription_required") val prescriptionRequired: Boolean = false,
    @SerialName("prescription_status")   val prescriptionStatus: String? = null
)

/** Zona de envío (tabla delivery_zones). */
@Serializable
data class DeliveryZoneDto(
    val id: String       = "",
    val name: String     = "",
    val fee: Double      = 0.0,
    @SerialName("is_active")  val isActive: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int    = 0
)

/** Respuesta de la RPC create_order. */
@Serializable
data class CreateOrderResponse(
    @SerialName("order_id")          val orderId: String = "",
    val subtotal: Double             = 0.0,
    @SerialName("delivery_fee")      val deliveryFee: Double = 0.0,
    val discount: Double             = 0.0,
    val total: Double                = 0.0,
    @SerialName("change_due")        val changeDue: Double? = null,
    @SerialName("transfer_deadline") val transferDeadline: String? = null,
    @SerialName("age_check_required")    val ageCheckRequired: Boolean = false,
    @SerialName("prescription_required") val prescriptionRequired: Boolean = false
)

@Serializable
data class OrderItemDto(
    val id: String                                                = "",
    @SerialName("order_id")              val orderId: String     = "",
    @SerialName("dish_id")               val dishId: String      = "",
    @SerialName("dish_name")             val dishName: String    = "",
    @SerialName("unit_price")            val unitPrice: Double   = 0.0,
    val quantity: Int                                             = 1,
    @SerialName("selected_options_json") val selectedOptionsJson: String = "[]",
    @SerialName("special_instructions")  val specialInstructions: String = ""
)

@Serializable
data class SelectedOptionDto(
    val optionGroupId: String    = "",
    val optionGroupTitle: String = "",
    val optionId: String         = "",
    val optionName: String       = "",
    val optionPrice: Double      = 0.0
)
