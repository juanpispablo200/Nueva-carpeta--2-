package com.jagoo.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jagoo.app.ui.screens.AddressesScreen
import com.jagoo.app.ui.screens.AdminDashboardScreen
import com.jagoo.app.ui.screens.AdminMenuScreen
import com.jagoo.app.ui.screens.CartScreen
import com.jagoo.app.ui.screens.CategoryScreen
import com.jagoo.app.ui.screens.HomeScreen
import com.jagoo.app.ui.screens.OrderHistoryScreen
import com.jagoo.app.ui.screens.OrderTrackingScreen
import com.jagoo.app.ui.screens.ProfileScreen
import com.jagoo.app.ui.screens.RestaurantDetailScreen
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
                onNavigateToRestaurant = { restaurantId ->
                    navController.navigate(Screen.RestaurantDetail.createRoute(restaurantId))
                },
                onNavigateToCategory = { categoryName ->
                    navController.navigate(Screen.Category.createRoute(categoryName))
                }
            )
        }

        composable(
            route = Screen.Category.route,
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: "Hamburguesas"
            CategoryScreen(
                categoryName = name,
                productViewModel = productViewModel,
                cartViewModel = cartViewModel,
                onNavigateToRestaurant = { restaurantId ->
                    navController.navigate(Screen.RestaurantDetail.createRoute(restaurantId))
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.RestaurantDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val restaurantId = backStackEntry.arguments?.getString("id") ?: "r1"
            RestaurantDetailScreen(
                restaurantId = restaurantId,
                productViewModel = productViewModel,
                cartViewModel = cartViewModel,
                onNavigateToCart = {
                    navController.navigate(Screen.Cart.route)
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.AdminDashboard.route) {
            AdminDashboardScreen(
                productViewModel = productViewModel,
                onNavigateToMenuAdmin = { restaurantId ->
                    navController.navigate(Screen.AdminMenu.createRoute(restaurantId))
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AdminMenu.route,
            arguments = listOf(navArgument("restaurantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val restaurantId = backStackEntry.arguments?.getString("restaurantId") ?: "r1"
            AdminMenuScreen(
                restaurantId = restaurantId,
                productViewModel = productViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.OrderHistory.route) {
            OrderHistoryScreen(
                cartViewModel = cartViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Addresses.route) {
            AddressesScreen(
                productViewModel = productViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                productViewModel = productViewModel,
                cartViewModel = cartViewModel,
                onNavigateToRestaurant = { restaurantId ->
                    navController.navigate(Screen.RestaurantDetail.createRoute(restaurantId))
                }
            )
        }

        composable(Screen.Orders.route) {
            OrderTrackingScreen(
                cartViewModel = cartViewModel,
                onExploreRestaurants = {
                    navController.navigate(Screen.Home.route)
                }
            )
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
            ProfileScreen(
                productViewModel = productViewModel,
                onNavigateToAddresses = {
                    navController.navigate(Screen.Addresses.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.OrderHistory.route)
                },
                onNavigateToAdmin = {
                    navController.navigate(Screen.AdminDashboard.route)
                }
            )
        }
    }
}
