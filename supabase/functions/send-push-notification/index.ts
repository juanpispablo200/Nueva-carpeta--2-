/**
 * send-push-notification — Supabase Edge Function
 * FCM HTTP v1 con OAuth2 (cuenta de servicio).
 *
 * Payload de entrada:
 *   { user_id, order_id?, type, title, body }
 *
 * Secrets requeridos (Supabase Dashboard → Edge Functions → Secrets):
 *   FIREBASE_PROJECT_ID    — ID del proyecto Firebase, ej: jagoo-app-12345
 *   FIREBASE_CLIENT_EMAIL  — email de la cuenta de servicio
 *   FIREBASE_PRIVATE_KEY   — clave privada PEM (con \n literales)
 *   SUPABASE_SERVICE_ROLE_KEY — para leer/escribir profiles y notification_log
 */

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { encode as encodeBase64Url } from 'https://deno.land/std@0.208.0/encoding/base64url.ts'

// ── OAuth2: obtener access token para FCM HTTP v1 ─────────────────────────────

async function getFirebaseAccessToken(): Promise<string> {
  const projectId   = Deno.env.get('FIREBASE_PROJECT_ID')   ?? ''
  const clientEmail = Deno.env.get('FIREBASE_CLIENT_EMAIL') ?? ''
  // Las claves privadas se almacenan con \n literales en los secrets de Supabase
  const privateKey  = (Deno.env.get('FIREBASE_PRIVATE_KEY') ?? '').replace(/\\n/g, '\n')

  if (!projectId || !clientEmail || !privateKey) {
    throw new Error('Faltan variables de entorno Firebase (PROJECT_ID, CLIENT_EMAIL, PRIVATE_KEY)')
  }

  const now = Math.floor(Date.now() / 1000)

  // Construir JWT para OAuth2
  const header  = { alg: 'RS256', typ: 'JWT' }
  const payload = {
    iss:   clientEmail,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud:   'https://oauth2.googleapis.com/token',
    iat:   now,
    exp:   now + 3600,
  }

  const enc = (obj: unknown) =>
    encodeBase64Url(new TextEncoder().encode(JSON.stringify(obj)))

  const signingInput = `${enc(header)}.${enc(payload)}`

  // Importar la clave privada RSA
  const keyData = privateKey
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s/g, '')

  const keyBuffer = Uint8Array.from(atob(keyData), c => c.charCodeAt(0))

  const cryptoKey = await crypto.subtle.importKey(
    'pkcs8',
    keyBuffer,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  )

  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    cryptoKey,
    new TextEncoder().encode(signingInput)
  )

  const jwt = `${signingInput}.${encodeBase64Url(new Uint8Array(signature))}`

  // Intercambiar JWT por access token
  const tokenRes = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion:  jwt,
    }),
  })

  if (!tokenRes.ok) {
    const err = await tokenRes.text()
    throw new Error(`OAuth2 token error: ${err}`)
  }

  const { access_token } = await tokenRes.json()
  return access_token as string
}

// ── Enviar notificación via FCM HTTP v1 ───────────────────────────────────────

async function sendFcmV1(
  fcmToken: string,
  title: string,
  body: string,
  data: Record<string, string>
): Promise<{ success: boolean; error?: string; tokenInvalid?: boolean }> {
  const projectId   = Deno.env.get('FIREBASE_PROJECT_ID')!
  const accessToken = await getFirebaseAccessToken()

  const fcmUrl = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`

  const message = {
    message: {
      token: fcmToken,
      notification: { title, body },
      data,
      android: {
        priority: 'HIGH',
        notification: { sound: 'default', channel_id: 'jagoo_order_status' },
      },
    },
  }

  const res = await fetch(fcmUrl, {
    method:  'POST',
    headers: {
      'Content-Type':  'application/json',
      'Authorization': `Bearer ${accessToken}`,
    },
    body: JSON.stringify(message),
  })

  if (res.ok) {
    return { success: true }
  }

  const errBody = await res.json()
  const errCode = errBody?.error?.details?.[0]?.errorCode ?? errBody?.error?.status ?? ''

  // Token inválido o no registrado → hay que borrarlo de la BD
  const tokenInvalid = errCode === 'UNREGISTERED' || errCode === 'INVALID_ARGUMENT'

  return {
    success:      false,
    error:        JSON.stringify(errBody),
    tokenInvalid,
  }
}

// ── Handler principal ─────────────────────────────────────────────────────────

interface PushPayload {
  user_id:   string
  order_id?: string
  type:      string
  title:     string
  body:      string
}

Deno.serve(async (req: Request) => {
  if (req.method !== 'POST') {
    return new Response('Method not allowed', { status: 405 })
  }

  let payload: PushPayload
  try {
    payload = await req.json()
  } catch {
    return new Response('Invalid JSON', { status: 400 })
  }

  const { user_id, order_id, type, title, body } = payload
  if (!user_id || !title || !body) {
    return new Response('Missing required fields', { status: 400 })
  }

  const supabase = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
  )

  // Leer token FCM del usuario
  const { data: profile } = await supabase
    .from('profiles')
    .select('fcm_token')
    .eq('id', user_id)
    .single()

  if (!profile?.fcm_token) {
    return new Response(
      JSON.stringify({ sent: false, reason: 'no_fcm_token' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )
  }

  // Enviar notificación
  const result = await sendFcmV1(
    profile.fcm_token,
    title,
    body,
    { type, title, body, order_id: order_id ?? '' }
  )

  // Si el token es inválido, borrarlo de la BD
  if (result.tokenInvalid) {
    await supabase
      .from('profiles')
      .update({ fcm_token: null })
      .eq('id', user_id)
    console.warn(`Token FCM inválido eliminado para user_id=${user_id}`)
  }

  // Registrar en notification_log
  await supabase.from('notification_log').insert({
    user_id,
    order_id:  order_id ?? null,
    type,
    title,
    body,
    success:   result.success,
    error_msg: result.error ?? null,
  })

  return new Response(
    JSON.stringify({ sent: result.success, error: result.error }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  )
})
