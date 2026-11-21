package com.jagoo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jagoo.app.navigation.NavGraph
import com.jagoo.app.navigation.Screen
import com.jagoo.app.notifications.JagooNotificationManager
import com.jagoo.app.ui.components.JagooBottomBar
import com.jagoo.app.ui.theme.JagooTheme
import com.jagoo.app.viewmodel.AuthState
import com.jagoo.app.viewmodel.AuthViewModel
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.KushkiViewModel
import com.jagoo.app.viewmodel.ProductViewModel
import com.jagoo.app.viewmodel.RatingViewModel
import com.jagoo.app.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel:     AuthViewModel     by viewModels()
    private val productViewModel:  ProductViewModel  by viewModels()
    private val cartViewModel:     CartViewModel     by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val kushkiViewModel:   KushkiViewModel   by viewModels()
    private val ratingViewModel:   RatingViewModel   by viewModels()

    // Solicitar permiso de notificaciones en Android 13+
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Silencioso — el usuario puede denegarlas */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JagooNotificationManager.createChannels(this)
        requestNotificationPermissionIfNeeded()

        // Deep link desde notificación: jagoo://order/{orderId}
        val deepLinkOrderId = intent?.data?.let { uri ->
            if (uri.scheme == "jagoo" && uri.host == "order") uri.lastPathSegment else null
        }

        setContent {
            JagooTheme {
                val navController = rememberNavController()
                val authState by authViewModel.state.collectAsState()

                LaunchedEffect(authState) {
                    if (authState is AuthState.Error &&
                        (authState as AuthState.Error).message.contains("desactivada")) {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                val startDestination = when (authState) {
                    is AuthState.Customer -> Screen.Home.route
                    else                  -> Screen.Login.route
                }

                // Si hay deep link de pedido, navegar directo al tracking
                LaunchedEffect(authState, deepLinkOrderId) {
                    if (authState is AuthState.Customer && deepLinkOrderId != null) {
                        navController.navigate(Screen.Orders.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    }
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val routesWithoutBottomBar = setOf(
                    Screen.Login.route,
                    Screen.Register.route,
                    Screen.RestaurantDetail.route,
                    Screen.Category.route,
                    Screen.OrderHistory.route,
                    Screen.Addresses.route,
                    Screen.PaymentInstructions.route,
                    Screen.KushkiPayment.route,
                    Screen.Rating.route,
                    Screen.Checkout.route
                )

                val showBottomBar = currentRoute != null &&
                    routesWithoutBottomBar.none {
                        currentRoute.startsWith(it.substringBefore("{"))
                    }

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
                        navController     = navController,
                        authViewModel     = authViewModel,
                        productViewModel  = productViewModel,
                        cartViewModel     = cartViewModel,
                        settingsViewModel = settingsViewModel,
                        kushkiViewModel   = kushkiViewModel,
                        ratingViewModel   = ratingViewModel,
                        startDestination  = startDestination,
                        modifier          = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        authViewModel.validateSessionOnResume()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
