/**
 * purge-storage-queue — Edge Function: borra de Storage los archivos que la
 * retención de datos dejó en storage_deletion_queue.
 *
 * Solo acepta x-webhook-secret (o la service_role key); la llama pg_cron vía
 * public.call_edge_function. verify_jwt = false en config.toml. No recibe
 * parámetros ni rutas del exterior: lee la cola de la base de datos.
 */

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { isInternalCaller } from '../_shared/auth.ts'

const BATCH = 200

Deno.serve(async (req: Request) => {
  if (req.method !== 'POST') return json({ error: 'Método no permitido' }, 405)

  if (!(await isInternalCaller(req))) return json({ error: 'No autorizado' }, 401)

  const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
  const admin = createClient(Deno.env.get('SUPABASE_URL')!, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  })

  const { data: rows, error } = await admin
    .from('storage_deletion_queue')
    .select('id,bucket,path,attempts')
    .is('deleted_at', null)
    .lt('attempts', 5)
    .order('queued_at', { ascending: true })
    .limit(BATCH)
  if (error) return json({ error: 'No se pudo leer la cola' }, 500)

  // Agrupar por bucket
  const byBucket = new Map<string, { id: number; path: string; attempts: number }[]>()
  for (const r of rows ?? []) {
    const list = byBucket.get(r.bucket) ?? []
    list.push(r)
    byBucket.set(r.bucket, list)
  }

  let deleted = 0
  let failed = 0
  for (const [bucket, items] of byBucket) {
    const { error: rmErr } = await admin.storage.from(bucket).remove(items.map((i) => i.path))
    const ids = items.map((i) => i.id)
    if (rmErr) {
      failed += items.length
      for (const i of items) {
        await admin.from('storage_deletion_queue').update({ attempts: i.attempts + 1 }).eq('id', i.id)
      }
    } else {
      deleted += items.length
      await admin.from('storage_deletion_queue').update({ deleted_at: new Date().toISOString() }).in('id', ids)
    }
  }

  // Solo conteos: nunca se registran rutas ni contenido
  return json({ deleted, failed }, 200)
})

function json(data: unknown, status: number) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
