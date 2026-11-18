package com.jagoo.driver.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.jagoo.driver.ui.screens.DriverGananciasScreen
import com.jagoo.driver.ui.screens.DriverLoginScreen
import com.jagoo.driver.ui.screens.DriverRepartidorScreen
import com.jagoo.driver.viewmodel.DriverAuthState
import com.jagoo.driver.viewmodel.DriverAuthViewModel
import com.jagoo.driver.viewmodel.DriverRepartidorViewModel

@Composable
fun DriverNavGraph(
    navController: NavHostController,
    authViewModel: DriverAuthViewModel,
    repartidorViewModel: DriverRepartidorViewModel,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(DriverScreen.Login.route) {
            DriverLoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(DriverScreen.Repartidor.route) {
                        popUpTo(DriverScreen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(DriverScreen.Repartidor.route) {
            DriverRepartidorScreen(
                repartidorViewModel = repartidorViewModel,
                onNavigateToGanancias = { navController.navigate(DriverScreen.Ganancias.route) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(DriverScreen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(DriverScreen.Ganancias.route) {
            DriverGananciasScreen(
                repartidorViewModel = repartidorViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
