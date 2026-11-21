/**
 * process-kushki-payment — Supabase Edge Function
 *
 * Recibe el token de Kushki del cliente Android y ejecuta el cobro
 * en el servidor usando la clave privada de Kushki.
 * El cliente NUNCA envía datos de tarjeta directamente.
 *
 * Payload:
 *   { "order_id": "uuid", "token": "kushki_token_xxx", "amount": 12.50 }
 *
 * Secrets requeridos:
 *   KUSHKI_PRIVATE_KEY  — Clave privada de Kushki (nunca en el cliente)
 *   SUPABASE_SERVICE_ROLE_KEY — para llamar register_kushki_charge
 */

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const KUSHKI_CHARGES_URL = 'https://api-uat.kushkipagos.com/v1/charges'  // sandbox
// Producción: 'https://api.kushkipagos.com/v1/charges'

Deno.serve(async (req: Request) => {
  if (req.method !== 'POST') {
    return new Response('Method not allowed', { status: 405 })
  }

  // Verificar que viene del cliente autenticado (JWT de Supabase)
  const authHeader = req.headers.get('Authorization')
  if (!authHeader?.startsWith('Bearer ')) {
    return new Response('Unauthorized', { status: 401 })
  }

  let body: { order_id: string; token: string; amount: number }
  try {
    body = await req.json()
  } catch {
    return new Response('Invalid JSON', { status: 400 })
  }

  const { order_id, token, amount } = body
  if (!order_id || !token || !amount) {
    return new Response('Missing fields: order_id, token, amount', { status: 400 })
  }

  const kushkiKey = Deno.env.get('KUSHKI_PRIVATE_KEY') ?? ''
  if (!kushkiKey) {
    return new Response('KUSHKI_PRIVATE_KEY not configured', { status: 500 })
  }

  // Cobrar en Kushki
  let kushkiStatus = 'error'
  let kushkiResponse: unknown = null

  try {
    const chargeRes = await fetch(KUSHKI_CHARGES_URL, {
      method: 'POST',
      headers: {
        'Content-Type':  'application/json',
        'Private-Merchant-Id': kushkiKey,
      },
      body: JSON.stringify({
        token,
        amount: {
          subtotalIva: 0,
          subtotalIva0: amount,
          iva: 0,
          currency: 'USD',
        },
        metadata: { orderId: order_id },
      }),
    })

    kushkiResponse = await chargeRes.json()
    kushkiStatus = chargeRes.ok ? 'approved' : 'declined'
    console.log('Kushki response:', kushkiResponse)
  } catch (err) {
    console.error('Kushki error:', err)
    kushkiStatus = 'error'
    kushkiResponse = { error: String(err) }
  }

  // Registrar en BD via service_role
  const supabase = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
  )

  const { error: dbError } = await supabase.rpc('register_kushki_charge', {
    p_order_id:        order_id,
    p_token:           token,
    p_amount:          amount,
    p_status:          kushkiStatus,
    p_kushki_response: kushkiResponse,
  })

  if (dbError) {
    console.error('DB error:', dbError)
  }

  return new Response(
    JSON.stringify({
      success: kushkiStatus === 'approved',
      status:  kushkiStatus,
    }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  )
})
