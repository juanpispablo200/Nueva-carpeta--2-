package com.jagoo.app.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jagoo.app.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Recibe notificaciones push de Firebase Cloud Messaging.
 *
 * Payload esperado (data payload para garantía de entrega en background):
 *   data: {
 *     "type":     "order_status" | "payment_verified" | "promo",
 *     "title":    "Tu pedido está en camino",
 *     "body":     "El repartidor llegará en ~10 min",
 *     "order_id": "uuid-del-pedido"   (opcional)
 *   }
 */
class JagooFirebaseMessagingService : FirebaseMessagingService() {

    private val tag = "JagooFCM"

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(tag, "Mensaje recibido de FCM: ${message.data}")

        val data     = message.data
        val type     = data["type"]     ?: "order_status"
        val title    = data["title"]    ?: message.notification?.title    ?: "Jagoo"
        val body     = data["body"]     ?: message.notification?.body     ?: ""
        val orderId  = data["order_id"]

        when (type) {
            "order_status", "payment_verified" ->
                JagooNotificationManager.showOrderNotification(this, title, body, orderId)
            "promo" ->
                JagooNotificationManager.showOrderNotification(this, title, body)
            else ->
                JagooNotificationManager.showOrderNotification(this, title, body)
        }
    }

    /**
     * Se llama cuando FCM asigna o rota el token del dispositivo.
     * Guardamos el token en la tabla `profiles` para poder enviar
     * notificaciones dirigidas a este usuario.
     */
    override fun onNewToken(token: String) {
        Log.d(tag, "Nuevo token FCM: ${token.take(20)}…")
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
                Log.d(tag, "Token FCM guardado en profiles")
            } catch (e: Exception) {
                Log.w(tag, "No se pudo guardar el token FCM: ${e.message}")
            }
        }
    }
}
