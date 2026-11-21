package com.jagoo.driver.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jagoo.driver.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Recibe notificaciones push para el repartidor.
 *
 * Tipos:
 *   "new_order"    — nuevo pedido disponible en su zona
 *   "order_update" — cambio de estado de un pedido asignado
 */
class DriverFirebaseMessagingService : FirebaseMessagingService() {

    private val tag = "DriverFCM"

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(tag, "Mensaje FCM Driver: ${message.data}")

        val data    = message.data
        val title   = data["title"]    ?: message.notification?.title    ?: "Jagoo Driver"
        val body    = data["body"]     ?: message.notification?.body     ?: ""

        DriverNotificationManager.showNewOrderNotification(this, title, body)
    }

    override fun onNewToken(token: String) {
        Log.d(tag, "Nuevo token FCM Driver: ${token.take(20)}…")
        saveFcmToken(token)
    }

    private fun saveFcmToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@launch
                SupabaseClient.client
                    .from("profiles")
                    .update({ set("fcm_token", token) }) {
                        filter { eq("id", userId) }
                    }
                Log.d(tag, "Token FCM Driver guardado")
            } catch (e: Exception) {
                Log.w(tag, "No se pudo guardar el token FCM Driver: ${e.message}")
            }
        }
    }
}
