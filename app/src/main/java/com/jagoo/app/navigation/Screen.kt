package com.jagoo.app.navigation

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
    object Profile      : Screen("profile")
}
