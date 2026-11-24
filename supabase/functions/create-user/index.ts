/**
 * create-user — Edge Function segura para crear usuarios desde el panel admin.
 *
 * Usa auth.admin.createUser con la service role key (solo servidor).
 * La sesión del admin NO cambia. El nombre viene del formulario, nunca del admin.
 *
 * Payload:
 *   { email, password, full_name, phone?, role }
 *   role: 'repartidor' | 'restaurant' | 'admin' | 'customer'
 *
 * Secrets requeridos (ya disponibles automáticamente en Edge Functions):
 *   SUPABASE_URL
 *   SUPABASE_SERVICE_ROLE_KEY
 */

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const ALLOWED_ROLES = ['customer', 'restaurant', 'admin', 'repartidor']

// El panel web (otro origen) llama a esta función desde el navegador: sin CORS la bloquea.
const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: CORS })
  if (req.method !== 'POST') {
    return json({ error: 'Método no permitido' }, 405)
  }

  // Verificar Content-Type
  const ct = req.headers.get('Content-Type') ?? ''
  if (!ct.includes('application/json')) {
    return json({ error: 'Content-Type debe ser application/json' }, 415)
  }

  // Verificar que el llamante está autenticado
  const authHeader = req.headers.get('Authorization')
  if (!authHeader?.startsWith('Bearer ')) {
    return json({ error: 'No autorizado' }, 401)
  }

  const userToken = authHeader.replace('Bearer ', '')

  // Cliente con la sesión del admin (para verificar su rol)
  const supabaseUser = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_ANON_KEY')!,
    { global: { headers: { Authorization: authHeader } } }
  )

  // Verificar que quien llama es admin
  const { data: { user: caller }, error: callerErr } = await supabaseUser.auth.getUser(userToken)
  if (callerErr || !caller) {
    return json({ error: 'No autorizado' }, 401)
  }

  const { data: callerProfile } = await supabaseUser
    .from('profiles')
    .select('role')
    .eq('id', caller.id)
    .single()

  if (callerProfile?.role !== 'admin') {
    return json({ error: 'Solo los administradores pueden crear usuarios' }, 403)
  }

  // Leer y validar el payload
  let body: { email: string; password: string; full_name: string; phone?: string; role: string; vehicle_type?: string; plate?: string }
  try {
    body = await req.json()
  } catch {
    return json({ error: 'JSON inválido' }, 400)
  }

  const { email, password, full_name, phone, role, vehicle_type, plate } = body
  // Datos del vehículo (solo repartidores): se muestran al cliente en el seguimiento
  const vehicle = role === 'repartidor' && ['moto', 'bici', 'carro'].includes(vehicle_type ?? '') ? vehicle_type : null
  const plateClean = role === 'repartidor' ? (plate ?? '').toUpperCase().replace(/[^A-Z0-9-]/g, '').slice(0, 12) || null : null

  if (!email?.trim())     return json({ error: 'El email es obligatorio' }, 400)
  if (!password)          return json({ error: 'La contraseña es obligatoria' }, 400)
  if (password.length < 6) return json({ error: 'La contraseña debe tener al menos 6 caracteres' }, 400)
  if (!full_name?.trim()) return json({ error: 'El nombre completo es obligatorio' }, 400)
  if (!ALLOWED_ROLES.includes(role)) return json({ error: `Rol inválido: ${role}` }, 400)

  // Cliente admin (service role) — NO afecta la sesión del admin
  const supabaseAdmin = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
  )

  // Crear el usuario nuevo con el nombre del formulario
  const { data: newUser, error: createErr } = await supabaseAdmin.auth.admin.createUser({
    email:          email.trim().toLowerCase(),
    password,
    email_confirm:  true,  // confirmar automáticamente
    user_metadata:  {
      full_name: full_name.trim(),
      phone:     phone?.trim() ?? '',
    },
  })

  if (createErr) {
    const msg = createErr.message.toLowerCase()
    if (msg.includes('already registered') || msg.includes('already been registered')) {
      return json({ error: 'Este email ya está registrado' }, 409)
    }
    return json({ error: `Error al crear usuario: ${createErr.message}` }, 500)
  }

  if (!newUser.user) {
    return json({ error: 'No se pudo crear el usuario' }, 500)
  }

  // Crear/actualizar el perfil con el nombre correcto del NUEVO usuario
  const { error: profileErr } = await supabaseAdmin
    .from('profiles')
    .upsert({
      id:        newUser.user.id,
      full_name: full_name.trim(),   // ← del formulario, NO del admin
      phone:     phone?.trim() ?? '',
      role,
      is_active: true,
      ...(vehicle ? { vehicle_type: vehicle } : {}),
      ...(plateClean ? { plate: plateClean } : {}),
    }, { onConflict: 'id' })

  if (profileErr) {
    // El usuario ya fue creado, el perfil falló — igual devolvemos éxito parcial
    console.error('Error creando perfil:', profileErr.message)
  }

  return json({
    success:  true,
    user_id:  newUser.user.id,
    email:    newUser.user.email,
    full_name: full_name.trim(),
    role,
  }, 200)
})

function json(data: unknown, status: number) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...CORS, 'Content-Type': 'application/json' },
  })
}
