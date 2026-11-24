package com.jagoo.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jagoo.app.ui.screens.AddressesScreen
import com.jagoo.app.ui.screens.CartScreen
import com.jagoo.app.ui.screens.CategoryScreen
import com.jagoo.app.ui.screens.CheckoutScreen
import com.jagoo.app.ui.screens.HomeScreen
import com.jagoo.app.ui.screens.LoginScreen
import com.jagoo.app.ui.screens.OrderHistoryScreen
import com.jagoo.app.ui.screens.OrderTrackingScreen
import com.jagoo.app.ui.screens.PaymentInstructionsScreen
import com.jagoo.app.ui.screens.ProfileScreen
import com.jagoo.app.ui.screens.RatingScreen
import com.jagoo.app.ui.screens.RegisterScreen
import com.jagoo.app.ui.screens.RestaurantDetailScreen
import com.jagoo.app.ui.screens.SearchScreen
import com.jagoo.app.viewmodel.AuthViewModel
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.ProductViewModel
import com.jagoo.app.viewmodel.RatingViewModel
import com.jagoo.app.viewmodel.SettingsViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    productViewModel: ProductViewModel,
    cartViewModel: CartViewModel,
    settingsViewModel: SettingsViewModel,
    ratingViewModel: RatingViewModel,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier
    ) {

        // ── Auth ──────────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                authViewModel        = authViewModel,
                onLoginSuccess       = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                authViewModel     = authViewModel,
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        // ── Pantallas principales de Cliente ──────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                productViewModel       = productViewModel,
                cartViewModel          = cartViewModel,
                onNavigateToRestaurant = { id ->
                    navController.navigate(Screen.RestaurantDetail.createRoute(id))
                },
                onNavigateToCategory   = { name ->
                    navController.navigate(Screen.Category.createRoute(name))
                }
            )
        }

        composable(
            route     = Screen.Category.route,
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { back ->
            val name = back.arguments?.getString("name") ?: "Hamburguesas"
            CategoryScreen(
                categoryName           = name,
                productViewModel       = productViewModel,
                cartViewModel          = cartViewModel,
                onNavigateToRestaurant = { id ->
                    navController.navigate(Screen.RestaurantDetail.createRoute(id))
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route     = Screen.RestaurantDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { back ->
            val id = back.arguments?.getString("id") ?: "r1"
            RestaurantDetailScreen(
                restaurantId     = id,
                productViewModel = productViewModel,
                cartViewModel    = cartViewModel,
                onNavigateToCart = { navController.navigate(Screen.Cart.route) },
                onBackClick      = { navController.popBackStack() }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                productViewModel       = productViewModel,
                cartViewModel          = cartViewModel,
                onNavigateToRestaurant = { id ->
                    navController.navigate(Screen.RestaurantDetail.createRoute(id))
                }
            )
        }

        composable(Screen.Cart.route) {
            CartScreen(
                cartViewModel   = cartViewModel,
                onContinueClick = { navController.navigate(Screen.Checkout.route) }
            )
        }

        composable(Screen.Checkout.route) {
            CheckoutScreen(
                cartViewModel    = cartViewModel,
                productViewModel = productViewModel,
                onBackClick      = { navController.popBackStack() },
                onOrderPlaced    = { orderId, total, isTransfer ->
                    val target = if (isTransfer) Screen.PaymentInstructions.createRoute(orderId, total)
                                 else Screen.Orders.route
                    navController.navigate(target) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }
        composable(
            route     = Screen.PaymentInstructions.route,
            arguments = listOf(
                navArgument("orderId") { type = NavType.StringType },
                navArgument("total")   { type = NavType.StringType }
            )
        ) { back ->
            val orderId = back.arguments?.getString("orderId") ?: ""
            val total   = back.arguments?.getString("total")?.toDoubleOrNull() ?: 0.0
            PaymentInstructionsScreen(
                cartViewModel     = cartViewModel,
                settingsViewModel = settingsViewModel,
                orderId           = orderId,
                totalAmount       = total,
                onPaymentUploaded = {
                    navController.navigate(Screen.Orders.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.Orders.route) {
            OrderTrackingScreen(
                cartViewModel        = cartViewModel,
                onExploreRestaurants = { navController.navigate(Screen.Home.route) },
                onOrderDelivered     = { orderId, restaurantName, courierName ->
                    navController.navigate(
                        Screen.Rating.createRoute(orderId, restaurantName, courierName ?: "")
                    )
                }
            )
        }

        composable(
            route     = Screen.Rating.route,
            arguments = listOf(
                navArgument("orderId")        { type = NavType.StringType },
                navArgument("restaurantName") { type = NavType.StringType },
                navArgument("courierName")    { type = NavType.StringType }
            )
        ) { back ->
            val orderId        = back.arguments?.getString("orderId")        ?: ""
            val restaurantName = back.arguments?.getString("restaurantName")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            val courierName    = back.arguments?.getString("courierName")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?.takeIf { it.isNotBlank() }

            ratingViewModel.reset()
            RatingScreen(
                ratingViewModel = ratingViewModel,
                orderId         = orderId,
                restaurantName  = restaurantName,
                courierName     = courierName,
                onDone          = { navController.navigate(Screen.Home.route) { popUpTo(0) } }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                productViewModel      = productViewModel,
                authViewModel         = authViewModel,
                onNavigateToAddresses = { navController.navigate(Screen.Addresses.route) },
                onNavigateToHistory   = { navController.navigate(Screen.OrderHistory.route) },
                onLogout              = {
                    authViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onAccountDeleted      = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.OrderHistory.route) {
            OrderHistoryScreen(
                cartViewModel = cartViewModel,
                onBackClick   = { navController.popBackStack() },
                onReorder     = { restaurantId ->
                    navController.navigate(Screen.RestaurantDetail.createRoute(restaurantId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.Addresses.route) {
            AddressesScreen(
                productViewModel = productViewModel,
                onBackClick      = { navController.popBackStack() }
            )
        }
    }
}
