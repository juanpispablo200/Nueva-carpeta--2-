/**
 * Edge Function: notify-repartidores
 *
 * Se invoca con un Database Webhook cuando orders.status cambia a 'ready'.
 * Busca todos los repartidores disponibles y les envía una notificación FCM.
 *
 * Variables de entorno requeridas (configurar en Supabase Dashboard → Edge Functions → Secrets):
 *   FCM_SERVER_KEY  — clave del servidor Firebase Cloud Messaging (FCM v1 o legacy)
 *   SUPABASE_URL    — URL del proyecto
 *   SUPABASE_SERVICE_ROLE_KEY — service role key (para leer sin RLS)
 */

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

serve(async (req) => {
  try {
    const body = await req.json()

    // El webhook envía { type, table, record, old_record }
    const record = body.record
    if (!record || record.status !== "ready") {
      return new Response("No es estado 'ready', ignorado", { status: 200 })
    }

    const orderId         = record.id
    const restaurantName  = record.restaurant_name ?? "un restaurante"

    // Cliente con service role para leer sin RLS
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    )

    // 1. Buscar repartidores disponibles
    const { data: repartidores, error: errR } = await supabase
      .from("repartidores")
      .select("id")
      .eq("is_available", true)

    if (errR || !repartidores || repartidores.length === 0) {
      return new Response("Sin repartidores disponibles", { status: 200 })
    }

    const repartidorIds = repartidores.map((r: { id: string }) => r.id)

    // 2. Obtener sus device tokens
    const { data: tokens, error: errT } = await supabase
      .from("device_tokens")
      .select("token")
      .in("user_id", repartidorIds)
      .eq("platform", "android")

    if (errT || !tokens || tokens.length === 0) {
      return new Response("Sin tokens FCM", { status: 200 })
    }

    const fcmTokens = tokens.map((t: { token: string }) => t.token)

    // 3. Enviar notificación FCM (legacy HTTP v1)
    const fcmKey = Deno.env.get("FCM_SERVER_KEY")!
    const fcmBody = {
      registration_ids: fcmTokens,
      notification: {
        title: "🛵 Nuevo pedido disponible",
        body:  `${restaurantName} tiene un pedido listo para recoger`,
        sound: "default",
      },
      data: {
        type:     "new_order",
        order_id: orderId,
      },
      priority: "high",
    }

    const fcmRes = await fetch("https://fcm.googleapis.com/fcm/send", {
      method:  "POST",
      headers: {
        "Content-Type":  "application/json",
        "Authorization": `key=${fcmKey}`,
      },
      body: JSON.stringify(fcmBody),
    })

    const fcmJson = await fcmRes.json()
    console.log("FCM response:", JSON.stringify(fcmJson))

    return new Response(
      JSON.stringify({ ok: true, sent: fcmTokens.length }),
      { headers: { "Content-Type": "application/json" } }
    )
  } catch (err) {
    console.error("Error en notify-repartidores:", err)
    return new Response(JSON.stringify({ error: String(err) }), { status: 500 })
  }
})
