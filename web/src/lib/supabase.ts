import { createClient } from '@supabase/supabase-js'

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL as string
const supabaseKey = import.meta.env.VITE_SUPABASE_ANON_KEY as string

// ── Detectar si las claves están configuradas ─────────────────────────────────
export const isConfigured =
  !!supabaseUrl &&
  supabaseUrl !== 'PEGA_TU_URL_AQUI' &&
  !!supabaseKey &&
  supabaseKey !== 'PEGA_TU_ANON_KEY_AQUI'

// Crear cliente solo si las claves son válidas.
// Si no están, usamos una URL ficticia para que el import no explote,
// pero el cliente lanzará error en cada llamada (que capturamos).
export const supabase = createClient(
  isConfigured ? supabaseUrl  : 'https://placeholder.supabase.co',
  isConfigured ? supabaseKey  : 'placeholder-key'
)

export type UserRole = 'customer' | 'restaurant' | 'admin'

export interface Profile {
  id:        string
  full_name: string
  phone:     string
  role:      UserRole
}

export async function getMyProfile(): Promise<Profile | null> {
  if (!isConfigured) return null
  try {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return null
    const { data, error } = await supabase
      .from('profiles')
      .select('id, full_name, phone, role')
      .eq('id', user.id)
      .single()
    if (error) return null
    return data as Profile
  } catch {
    return null
  }
}
