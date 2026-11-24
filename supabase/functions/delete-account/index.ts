/**
 * delete-account — Edge Function: el cliente elimina SU PROPIA cuenta.
 *
 * Seguridad
 *   - El usuario se identifica solo por su JWT. No se acepta ningún user_id en el body,
 *     así nadie puede borrar una cuenta ajena.
 *   - La service_role key vive únicamente aquí (variable de entorno del servidor).
 *   - La lógica de negocio (rol cliente, sin pedidos en curso, anonimización) la
 *     aplica la función SQL delete_customer_account, de forma atómica.
 *
 * Archivos que borra: comprobantes (bucket payment-proofs) y recetas (bucket
 * prescriptions) del usuario.
 *
 * Secrets: SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY (ya disponibles en Edge Functions).
 */

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const PROOFS_BUCKET = 'payment-proofs'
const RX_BUCKET = 'prescriptions'

Deno.serve(async (req: Request) => {
  if (req.method !== 'POST') {
    return json({ error: 'Método no permitido' }, 405)
  }

  const authHeader = req.headers.get('Authorization') ?? ''
  if (!authHeader.startsWith('Bearer ')) {
    return json({ error: 'Debes iniciar sesión para eliminar tu cuenta.' }, 401)
  }

  const admin = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
    { auth: { persistSession: false, autoRefreshToken: false } }
  )

  // 1. Identificar al usuario por su token
  const { data: userData, error: userErr } = await admin.auth.getUser(authHeader.slice(7))
  if (userErr || !userData?.user) {
    return json({ error: 'Tu sesión expiró. Inicia sesión de nuevo e inténtalo otra vez.' }, 401)
  }
  const userId = userData.user.id

  // 2. Rutas de archivos: hay que leerlas ANTES de anonimizar.
  //    Los archivos nuevos viven en <uid>/...; los antiguos se guardaron como URL.
  const { data: proofs } = await admin
    .from('payment_proofs')
    .select('image_url')
    .eq('customer_id', userId)
  const proofPaths = (proofs ?? [])
    .map((p: { image_url: string | null }) => toObjectPath(p.image_url, PROOFS_BUCKET, userId))
    .filter((p: string | null): p is string => !!p)

  const { data: rxOrders } = await admin
    .from('orders')
    .select('prescription_path')
    .eq('customer_id', userId)
    .not('prescription_path', 'is', null)
  const rxPaths = (rxOrders ?? [])
    .map((o: { prescription_path: string | null }) => o.prescription_path)
    .filter((p: string | null): p is string => !!p && p.startsWith(userId + '/'))

  // 3. Validar, anonimizar y borrar la cuenta (una sola transacción en la BD)
  const { error: rpcErr } = await admin.rpc('delete_customer_account', { p_user_id: userId })
  if (rpcErr) {
    const code = rpcErr.message ?? ''
    if (code.includes('PEDIDOS_EN_CURSO')) {
      return json({ error: 'Tienes pedidos en curso. Podrás eliminar tu cuenta cuando terminen.' }, 409)
    }
    if (code.includes('ROL_NO_PERMITIDO')) {
      return json({ error: 'Esta cuenta no es de cliente. Pide la baja al administrador de Jagoo.' }, 403)
    }
    if (code.includes('USUARIO_NO_ENCONTRADO')) {
      return json({ error: 'No encontramos tu cuenta.' }, 404)
    }
    console.error('delete_customer_account falló:', code)
    return json({ error: 'No se pudo eliminar la cuenta. Inténtalo de nuevo más tarde.' }, 500)
  }

  // 4. Borrar los archivos (la cuenta ya no existe; si falla, solo se registra).
  //    También se limpia la carpeta del usuario por si quedó algo sin referencia.
  const proofsOk = await removeFiles(admin, PROOFS_BUCKET, proofPaths, userId)
  const rxOk = await removeFiles(admin, RX_BUCKET, rxPaths, userId)

  return json({ success: true, storage_cleaned: proofsOk && rxOk }, 200)
})

/** Ruta del objeto: si ya es <uid>/..., se usa tal cual; si es una URL antigua, se extrae. */
function toObjectPath(value: string | null, bucket: string, userId: string): string | null {
  if (!value || value === '[eliminado]') return null
  if (value.startsWith(userId + '/')) return value
  const marker = '/' + bucket + '/'
  const i = value.indexOf(marker)
  if (i < 0) return null
  const raw = value.slice(i + marker.length).split('?')[0]
  try {
    return decodeURIComponent(raw)
  } catch {
    return raw
  }
}

// deno-lint-ignore no-explicit-any
async function removeFiles(admin: any, bucket: string, paths: string[], userId: string): Promise<boolean> {
  const all = new Set<string>(paths)
  const { data: listed } = await admin.storage.from(bucket).list(userId, { limit: 1000 })
  for (const f of listed ?? []) {
    if (f?.name) all.add(userId + '/' + f.name)
  }
  if (all.size === 0) return true
  const { error } = await admin.storage.from(bucket).remove([...all])
  if (error) {
    console.error('No se pudieron borrar ' + all.size + ' archivo(s) de ' + bucket)
    return false
  }
  return true
}

function json(data: unknown, status: number) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
