package com.jagoo.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jagoo.app.ui.screens.CartScreen
import com.jagoo.app.ui.screens.CategoryScreen
import com.jagoo.app.ui.screens.HomeScreen
import com.jagoo.app.ui.screens.OrderTrackingScreen
import com.jagoo.app.ui.screens.ProfileScreen
import com.jagoo.app.ui.screens.SearchScreen
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.ProductViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    productViewModel: ProductViewModel,
    cartViewModel: CartViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                productViewModel = productViewModel,
                cartViewModel = cartViewModel,
                onNavigateToCategory = { categoryName ->
                    navController.navigate(Screen.Category.createRoute(categoryName))
                }
            )
        }

        composable(
            route = Screen.Category.route,
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: "Frutas y verduras"
            CategoryScreen(
                categoryName = name,
                productViewModel = productViewModel,
                cartViewModel = cartViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                productViewModel = productViewModel,
                cartViewModel = cartViewModel
            )
        }

        composable(Screen.Orders.route) {
            OrderTrackingScreen()
        }

        composable(Screen.Cart.route) {
            CartScreen(
                cartViewModel = cartViewModel,
                onContinueClick = {
                    navController.navigate(Screen.Orders.route)
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen()
        }
    }
}
