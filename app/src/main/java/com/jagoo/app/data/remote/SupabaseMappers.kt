package com.jagoo.app.data.remote

import com.jagoo.app.data.ActiveOrder
import com.jagoo.app.data.CourierInfo
import com.jagoo.app.data.Dish
import com.jagoo.app.data.DishOption
import com.jagoo.app.data.DishOptionGroup
import com.jagoo.app.data.OrderStatusStep
import com.jagoo.app.data.OrderTimelineItem
import com.jagoo.app.data.Restaurant
import com.jagoo.app.data.RestaurantData
import com.jagoo.app.data.repository.AddressItem
import org.json.JSONArray

fun RestaurantDto.toRestaurant(): Restaurant = Restaurant(
    id            = id, name = name, category = category, description = description,
    logoUrl       = logoUrl, coverUrl = coverUrl, brandColorHex = brandColor,
    rating        = rating, reviewCount = reviewCount, deliveryTime = deliveryTime,
    deliveryFee   = deliveryFee, minOrder = minOrder, isOpen = isOpen,
    isFeatured    = isFeatured, isOffer = isOffer, offerText = offerText,
    businessType  = businessType, isActive = isActive
)

fun DishDto.toDish(): Dish = Dish(
    id = id, restaurantId = restaurantId, restaurantName = restaurantName,
    menuSection = sectionName, name = name, description = description, price = price,
    imageUrl = imageUrl, isAvailable = isAvailable, isPopular = isPopular,
    isOffer = isOffer, offerBadge = offerBadge, optionGroups = parseOptionGroups(optionGroupsJson),
    isAgeRestricted = isAgeRestricted, requiresPrescription = requiresPrescription
)

fun parseOptionGroups(json: String): List<DishOptionGroup> {
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val group = arr.getJSONObject(i)
            val optionsArr = group.getJSONArray("options")
            DishOptionGroup(
                id = group.getString("id"), title = group.getString("title"),
                isRequired = group.optBoolean("isRequired", false),
                maxSelection = group.optInt("maxSelection", 1),
                options = (0 until optionsArr.length()).map { j ->
                    val opt = optionsArr.getJSONObject(j)
                    DishOption(id = opt.getString("id"), name = opt.getString("name"), price = opt.optDouble("price", 0.0))
                }
            )
        }
    } catch (e: Exception) { emptyList() }
}

fun AddressDto.toAddressItem(): AddressItem = AddressItem(
    id = id, label = label, fullAddress = fullAddress, isPrimary = isPrimary
)

fun OrderDto.toActiveOrder(): ActiveOrder {
    val step = when (status) {
        "pending", "accepted"      -> OrderStatusStep.CONFIRMED
        "preparing"                -> OrderStatusStep.PREPARING
        "ready"                    -> OrderStatusStep.READY
        "picked_up", "on_the_way"  -> OrderStatusStep.IN_TRANSIT
        "delivered"                -> OrderStatusStep.DELIVERED
        else                       -> OrderStatusStep.CONFIRMED
    }
    val restaurant = RestaurantData.restaurants.find { it.id == restaurantId }
    return ActiveOrder(
        id                = id,
        restaurantId      = restaurantId,
        restaurantName    = restaurantName,
        restaurantLogoUrl = restaurantLogoUrl.ifBlank { restaurant?.logoUrl ?: "" },
        items             = emptyList(),
        subtotal          = subtotal,
        deliveryFee       = deliveryFee,
        total             = total,
        estimatedMinutes  = 25,
        statusStep        = step,
        timelineItems     = buildTimeline(status),
        courier           = courierName?.let { CourierInfo(name = it, vehicle = courierVehicle ?: "", plate = courierPlate ?: "") },
        rawStatus         = status,
        paymentMethod     = paymentMethod,
        paymentStatus     = paymentStatus,
        changeDue         = changeDue,
        transferDeadlineMillis = parseIsoMillis(transferDeadline),
        cancelReason      = cancelReason,
        ageCheckRequired  = ageCheckRequired,
        prescriptionRequired = prescriptionRequired,
        prescriptionStatus   = prescriptionStatus
    )
}

/** Fecha ISO-8601 de Postgres (con zona) → milisegundos; null si no se puede leer. */
fun parseIsoMillis(value: String?): Long? =
    if (value.isNullOrBlank()) null
    else try { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() } catch (_: Exception) { null }

fun buildTimeline(status: String): List<OrderTimelineItem> {
    data class Step(val key: String, val label: String, val step: OrderStatusStep)
    val steps = listOf(
        Step("pending",    "Pedido confirmado",        OrderStatusStep.CONFIRMED),
        Step("preparing",  "En preparación",           OrderStatusStep.PREPARING),
        Step("ready",      "Listo para recoger",       OrderStatusStep.READY),
        Step("on_the_way", "Repartidor en camino",     OrderStatusStep.IN_TRANSIT),
        Step("delivered",  "Entregado",                OrderStatusStep.DELIVERED)
    )
    val order = listOf("pending","accepted","preparing","ready","picked_up","on_the_way","delivered")
    val currentIndex = order.indexOf(status).coerceAtLeast(0)
    return steps.map { s ->
        val si = order.indexOf(s.key)
        OrderTimelineItem(title = s.label, time = "", step = s.step,
            isCompleted = si < currentIndex, isCurrent = si == currentIndex)
    }
}
