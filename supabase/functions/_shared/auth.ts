/**
 * Autenticación de llamadas internas (base de datos o cron) a Edge Functions.
 *
 * La base de datos manda la cabecera `x-webhook-secret` con el secreto guardado
 * en Supabase Vault; la función lo compara con la variable WEBHOOK_SECRET.
 * También se acepta `Authorization: Bearer <service_role_key>` (otras funciones).
 */

const enc = new TextEncoder()

async function sha256(s: string): Promise<Uint8Array> {
  return new Uint8Array(await crypto.subtle.digest('SHA-256', enc.encode(s)))
}

/** Comparación en tiempo constante (compara hashes, así tampoco filtra el largo). */
export async function safeEqual(a: string, b: string): Promise<boolean> {
  const [x, y] = await Promise.all([sha256(a), sha256(b)])
  let diff = 0
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i]
  return diff === 0
}

export async function isInternalCaller(req: Request): Promise<boolean> {
  const secret = Deno.env.get('WEBHOOK_SECRET') ?? ''
  const header = req.headers.get('x-webhook-secret') ?? ''
  if (secret && header && (await safeEqual(header, secret))) return true

  const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
  const bearer = (req.headers.get('Authorization') ?? '').replace(/^Bearer\s+/i, '')
  if (serviceKey && bearer && (await safeEqual(bearer, serviceKey))) return true

  return false
}

export function unauthorized(): Response {
  return new Response(JSON.stringify({ error: 'No autorizado' }), {
    status: 401,
    headers: { 'Content-Type': 'application/json' },
  })
}
