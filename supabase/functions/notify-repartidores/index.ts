/**
 * Edge Function: notify-repartidores
 *
 * La llama el trigger de la base de datos (public.call_edge_function) cuando un
 * pedido pasa a 'ready'. Avisa por push a los repartidores activos y disponibles
 * reutilizando send-push-notification (FCM HTTP v1).
 *
 * Seguridad: solo acepta x-webhook-secret (o Bearer = service role key).
 * verify_jwt = false en supabase/config.toml.
 *
 * Secrets: WEBHOOK_SECRET, SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY
 * (y los de Firebase, que usa send-push-notification).
 */
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { isInternalCaller, unauthorized } from '../_shared/auth.ts'

const MAX_COURIERS = 50
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

Deno.serve(async (req: Request) => {
  if (req.method !== 'POST') return new Response('Method not allowed', { status: 405 })
  if (!(await isInternalCaller(req))) return unauthorized()

  try {
    const body = await req.json()
    const record = body?.record
    if (!record || record.status !== 'ready' || record.courier_id) {
      return json({ sent: 0, reason: 'not_ready_or_assigned' })
    }
    const orderId = String(record.id ?? '')
    if (!UUID.test(orderId)) return json({ error: 'order id inválido' }, 400)
    const name = String(record.restaurant_name ?? 'un negocio').slice(0, 60)

    const supabaseUrl = Deno.env.get('SUPABASE_URL')!
    const supabase = createClient(supabaseUrl, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, {
      auth: { persistSession: false, autoRefreshToken: false },
    })

    // Repartidores activos, disponibles y con token (modelo actual: profiles)
    const { data: couriers, error } = await supabase
      .from('profiles')
      .select('id')
      .eq('role', 'repartidor')
      .eq('is_available', true)
      .neq('is_active', false)
      .not('fcm_token', 'is', null)
      .limit(MAX_COURIERS)
    if (error) return json({ error: 'No se pudo leer repartidores' }, 500)
    if (!couriers?.length) return json({ sent: 0, reason: 'no_available_couriers' })

    const secret = Deno.env.get('WEBHOOK_SECRET') ?? ''
    const results = await Promise.allSettled(
      couriers.map((c: { id: string }) =>
        fetch(`${supabaseUrl}/functions/v1/send-push-notification`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'x-webhook-secret': secret },
          body: JSON.stringify({
            user_id: c.id,
            order_id: orderId,
            type: 'new_order',
            title: '🛵 Nuevo pedido disponible',
            body: `${name} tiene un pedido listo para recoger`,
          }),
        }),
      ),
    )
    const sent = results.filter((r) => r.status === 'fulfilled' && (r.value as Response).ok).length
    return json({ ok: true, sent, targeted: couriers.length })
  } catch (err) {
    console.error('Error en notify-repartidores:', err)
    return json({ error: 'Error interno' }, 500)
  }
})

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
