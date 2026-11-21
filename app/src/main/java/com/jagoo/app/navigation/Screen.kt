package com.jagoo.app.navigation

fun String.encodeRouteParam(): String = java.net.URLEncoder.encode(this, "UTF-8")

sealed class Screen(val route: String) {
    // ── Auth ──────────────────────────────────────────────────
    object Login    : Screen("login")
    object Register : Screen("register")

    // ── App principal ─────────────────────────────────────────
    object Home : Screen("home")
    object Category : Screen("category/{name}") {
        fun createRoute(name: String) = "category/$name"
    }
    object RestaurantDetail : Screen("restaurant/{id}") {
        fun createRoute(id: String) = "restaurant/$id"
    }
    object OrderHistory : Screen("orders/history")
    object Addresses    : Screen("profile/addresses")
    object Search       : Screen("search")
    object Orders       : Screen("orders")
    object Cart         : Screen("cart")
    object Checkout     : Screen("checkout")
    object PaymentInstructions : Screen("payment/{orderId}/{total}") {
        fun createRoute(orderId: String, total: Double) = "payment/$orderId/$total"
    }
    object KushkiPayment : Screen("payment/card/{orderId}/{total}") {
        fun createRoute(orderId: String, total: Double) = "payment/card/$orderId/$total"
    }
    object Rating : Screen("rating/{orderId}/{restaurantName}/{courierName}") {
        fun createRoute(orderId: String, restaurantName: String, courierName: String) =
            "rating/${orderId}/${restaurantName.encodeRouteParam()}/${courierName.encodeRouteParam()}"
    }
    object Profile      : Screen("profile")
}
