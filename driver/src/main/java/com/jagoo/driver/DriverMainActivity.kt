package com.jagoo.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.jagoo.driver.navigation.DriverNavGraph
import com.jagoo.driver.navigation.DriverScreen
import com.jagoo.driver.notifications.DriverNotificationManager
import com.jagoo.driver.ui.theme.JagooDriverTheme
import com.jagoo.driver.viewmodel.DriverAuthState
import com.jagoo.driver.viewmodel.DriverAuthViewModel
import com.jagoo.driver.viewmodel.DriverRepartidorViewModel

class DriverMainActivity : ComponentActivity() {

    private val authViewModel: DriverAuthViewModel by viewModels()
    private val repartidorViewModel: DriverRepartidorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar canal de notificaciones push de alta prioridad para repartidores
        DriverNotificationManager.createNotificationChannel(this)

        setContent {
            JagooDriverTheme {
                val navController = rememberNavController()
                val authState by authViewModel.state.collectAsState()

                LaunchedEffect(authState) {
                    if (authState is DriverAuthState.Error && (authState as DriverAuthState.Error).message.contains("desactivada")) {
                        navController.navigate(DriverScreen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                val startDestination = when (authState) {
                    is DriverAuthState.Repartidor -> DriverScreen.Repartidor.route
                    else                          -> DriverScreen.Login.route
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    DriverNavGraph(
                        navController = navController,
                        authViewModel = authViewModel,
                        repartidorViewModel = repartidorViewModel,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-validar rol de repartidor e is_active al volver a primer plano
        authViewModel.validateSessionOnResume()
    }
}
