package com.jagoo.driver.location

import com.jagoo.driver.BuildConfig

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.jagoo.driver.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Foreground Service que actualiza la ubicación del repartidor en Supabase
 * cada 5 segundos mientras tiene un pedido activo.
 *
 * Se inicia desde DriverRepartidorViewModel cuando el repartidor acepta un pedido
 * y se detiene cuando marca el pedido como entregado.
 */
class DriverLocationService : Service() {

    private val tag = "DriverLocationService"
    private val channelId = "jagoo_driver_location"
    private val notifId = 9001
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        startForeground(notifId, buildNotification())
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        job.cancel()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            if (BuildConfig.DEBUG) Log.w(tag, "Operación fallida")
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5_000L  // cada 5 segundos
        )
            .setMinUpdateIntervalMillis(3_000L)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                publishLocation(loc.latitude, loc.longitude)
            }
        }

        fusedClient.requestLocationUpdates(
            request,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    private fun publishLocation(lat: Double, lng: Double) {
        scope.launch {
            try {
                SupabaseClient.client.postgrest.rpc(
                    "update_driver_location",
                    buildJsonObject {
                        put("p_lat", lat)
                        put("p_lng", lng)
                    }
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(tag, "Operación fallida")
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Tracking de entrega",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Jagoo Driver está compartiendo tu ubicación" }

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Jagoo Driver")
            .setContentText("Compartiendo ubicación con el cliente")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}