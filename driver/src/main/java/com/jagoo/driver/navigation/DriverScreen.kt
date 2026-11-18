package com.jagoo.driver.navigation

sealed class DriverScreen(val route: String) {
    object Login     : DriverScreen("login")
    object Repartidor : DriverScreen("repartidor")
    object Ganancias  : DriverScreen("ganancias")
}
