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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.jagoo.app.ui.components.CartDialogs
import com.jagoo.app.viewmodel.AuthViewModel
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.ProductViewModel
import com.jagoo.app.viewmodel.RatingViewModel
import com.jagoo.app.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel:     AuthViewModel     by viewModels()
    private val productViewModel:  ProductViewModel  by viewModels()
    private val cartViewModel:     CartViewModel     by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val ratingViewModel:   RatingViewModel   by viewModels()

    // Solicitar permiso de notificaciones en Android 13+
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Silencioso — el usuario puede denegarlas */ }

    private var pendingOrderId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JagooNotificationManager.createChannels(this)
        requestNotificationPermissionIfNeeded()

        pendingOrderId = extractOrderId(intent)

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

                // Si hay pedido pendiente (toque en notificación o deep link), ir al tracking
                LaunchedEffect(authState, pendingOrderId) {
                    if (authState is AuthState.Customer && pendingOrderId != null) {
                        navController.navigate(Screen.Orders.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                        pendingOrderId = null
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
                        ratingViewModel   = ratingViewModel,
                        startDestination  = startDestination,
                        modifier          = Modifier.padding(innerPadding)
                    )
                    // Avisos de "un pedido por negocio" y de edad (+18) desde cualquier pantalla
                    CartDialogs(cartViewModel)
                }
            }
        }
    }

    // Llega cuando la app ya está abierta y el usuario toca la notificación
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOrderId = extractOrderId(intent)
    }

    // Soporta el extra de la notificación push y el deep link jagoo://order/{orderId}
    private fun extractOrderId(intent: Intent?): String? {
        intent ?: return null
        intent.getStringExtra("navigate_to_order")?.takeIf { it.isNotBlank() }?.let { return it }
        intent.getStringExtra("order_id")?.takeIf { it.isNotBlank() }?.let { return it }
        val uri = intent.data ?: return null
        return if (uri.scheme == "jagoo" && uri.host == "order") uri.lastPathSegment else null
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
