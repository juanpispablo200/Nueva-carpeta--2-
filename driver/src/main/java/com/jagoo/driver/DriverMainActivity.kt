package com.jagoo.driver

import android.Manifest
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
import androidx.navigation.compose.rememberNavController
import com.jagoo.driver.navigation.DriverNavGraph
import com.jagoo.driver.navigation.DriverScreen
import com.jagoo.driver.notifications.DriverNotificationManager
import com.jagoo.driver.ui.theme.JagooDriverTheme
import com.jagoo.driver.viewmodel.DriverAuthState
import com.jagoo.driver.viewmodel.DriverAuthViewModel
import com.jagoo.driver.viewmodel.DriverRepartidorViewModel

class DriverMainActivity : ComponentActivity() {

    private val authViewModel:        DriverAuthViewModel        by viewModels()
    private val repartidorViewModel:  DriverRepartidorViewModel  by viewModels()

    // Solicitar permisos en cadena: notificaciones → ubicación
    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* El usuario puede denegar — el servicio GPS se maneja gracefully */ }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Tras notificaciones, pedir ubicación
        requestLocationPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DriverNotificationManager.createNotificationChannel(this)
        requestPermissionsSequentially()

        setContent {
            JagooDriverTheme {
                val navController = rememberNavController()
                val authState by authViewModel.state.collectAsState()

                LaunchedEffect(authState) {
                    if (authState is DriverAuthState.Error &&
                        (authState as DriverAuthState.Error).message.contains("desactivada")) {
                        navController.navigate(DriverScreen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                val startDestination = when (authState) {
                    is DriverAuthState.Repartidor -> DriverScreen.Repartidor.route
                    else                          -> DriverScreen.Login.route
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DriverNavGraph(
                        navController       = navController,
                        authViewModel       = authViewModel,
                        repartidorViewModel = repartidorViewModel,
                        startDestination    = startDestination,
                        modifier            = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        authViewModel.validateSessionOnResume()
    }

    private fun requestPermissionsSequentially() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        requestLocationPermissions()
    }

    private fun requestLocationPermissions() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted) {
            locationPermLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}
