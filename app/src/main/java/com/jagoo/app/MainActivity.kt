package com.jagoo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jagoo.app.navigation.NavGraph
import com.jagoo.app.navigation.Screen
import com.jagoo.app.ui.components.JagooBottomBar
import com.jagoo.app.ui.theme.JagooTheme
import com.jagoo.app.viewmodel.AuthState
import com.jagoo.app.viewmodel.AuthViewModel
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.ProductViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel:    AuthViewModel    by viewModels()
    private val productViewModel: ProductViewModel by viewModels()
    private val cartViewModel:    CartViewModel    by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            JagooTheme {
                val navController = rememberNavController()
                val authState by authViewModel.state.collectAsState()

                // Determinar pantalla de inicio:
                //   - Si ya hay sesión activa (Customer) → Home
                //   - Cualquier otro caso              → Login
                val startDestination = when (authState) {
                    is AuthState.Customer -> Screen.Home.route
                    else                  -> Screen.Login.route
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Rutas donde NO se muestra la barra de navegación inferior
                val routesWithoutBottomBar = setOf(
                    Screen.Login.route,
                    Screen.Register.route,
                    Screen.RestaurantDetail.route,
                    Screen.Category.route,
                    Screen.OrderHistory.route,
                    Screen.Addresses.route
                )

                val showBottomBar = currentRoute != null &&
                    routesWithoutBottomBar.none { currentRoute.startsWith(it.substringBefore("{")) }

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            JagooBottomBar(
                                currentRoute = currentRoute,
                                onNavigate   = { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    NavGraph(
                        navController    = navController,
                        authViewModel    = authViewModel,
                        productViewModel = productViewModel,
                        cartViewModel    = cartViewModel,
                        startDestination = startDestination,
                        modifier         = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
