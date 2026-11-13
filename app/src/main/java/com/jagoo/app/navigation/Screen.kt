package com.jagoo.app.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Category : Screen("category/{name}") {
        fun createRoute(name: String) = "category/$name"
    }
    object RestaurantDetail : Screen("restaurant/{id}") {
        fun createRoute(id: String) = "restaurant/$id"
    }
    object Search : Screen("search")
    object Orders : Screen("orders")
    object Cart : Screen("cart")
    object Profile : Screen("profile")
}
