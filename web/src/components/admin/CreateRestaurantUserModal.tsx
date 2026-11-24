import { useState } from 'react'
import { X, UserPlus, AlertCircle } from 'lucide-react'
import { supabase } from '../../lib/supabase'

interface Props {
  restaurantId:   string
  restaurantName: string
  onClose:        () => void
  onSuccess:      () => void
}

export default function CreateRestaurantUserModal({ restaurantId, restaurantName, onClose, onSuccess }: Props) {
  const [fullName, setFullName] = useState('')
  const [email,    setEmail]    = useState('')
  const [password, setPassword] = useState('')
  const [phone,    setPhone]    = useState('')
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState('')

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (password.length < 6) { setError('La contraseña debe tener al menos 6 caracteres'); return }
    setError('')
    setLoading(true)

    try {
      // Token del admin actual — su sesión NO cambia
      const { data: { session } } = await supabase.auth.getSession()
      if (!session) throw new Error('No hay sesión activa')

      // 1. Crear usuario via Edge Function (nunca desde el cliente)
      const res = await fetch(
        `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/create-user`,
        {
          method:  'POST',
          headers: {
            'Content-Type':  'application/json',
            'Authorization': `Bearer ${session.access_token}`,
          },
          body: JSON.stringify({
            email:     email.trim().toLowerCase(),
            password,
            full_name: fullName.trim(),   // ← del formulario, no del admin
            phone:     phone.trim() || undefined,
            role:      'restaurant',
          }),
        }
      )

      const result = await res.json()
      if (!res.ok) throw new Error(result.error ?? 'Error al crear el usuario')

      // 2. Vincular el usuario nuevo con el restaurante
      if (result.user_id) {
        const { error: linkErr } = await supabase
          .from('restaurants')
          .update({ owner_id: result.user_id })
          .eq('id', restaurantId)

        if (linkErr) console.warn('No se pudo vincular con el restaurante:', linkErr.message)
      }

      onSuccess()
      onClose()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Error desconocido')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
      <div className="card w-full max-w-md p-6 animate-in">
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-[var(--radius-md)] bg-[var(--color-brand-100)] flex items-center justify-center">
              <UserPlus size={16} className="text-[var(--color-brand-700)]" />
            </div>
            <div>
              <h2 className="font-display text-lg font-bold text-[var(--color-ink)] leading-tight">
                Crear usuario restaurante
              </h2>
              <p className="text-xs text-[var(--color-ink-sub)]">{restaurantName}</p>
            </div>
          </div>
          <button onClick={onClose} className="text-[var(--color-ink-muted)] hover:text-[var(--color-ink)] transition-colors" aria-label="Cerrar">
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
              Nombre completo *
            </label>
            <input
              required
              value={fullName}
              onChange={e => setFullName(e.target.value)}
              placeholder="Pedro García"
              className="input"
              autoComplete="off"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
              Email *
            </label>
            <input
              type="email"
              required
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="pedro@restaurante.com"
              className="input"
              autoComplete="off"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
              Contraseña temporal *
            </label>
            <input
              type="password"
              required
              minLength={6}
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="Mínimo 6 caracteres"
              className="input"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
              Teléfono (opcional)
            </label>
            <input
              type="tel"
              value={phone}
              onChange={e => setPhone(e.target.value)}
              placeholder="+593 99 999 9999"
              className="input"
            />
          </div>

          {error && (
            <div className="flex items-start gap-2 bg-red-50 border border-red-200 text-red-700 text-sm rounded-[var(--radius-md)] px-3 py-2.5" role="alert">
              <AlertCircle size={15} className="shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <div className="flex gap-2 pt-2">
            <button type="button" onClick={onClose} className="btn-ghost flex-1 justify-center" disabled={loading}>
              Cancelar
            </button>
            <button type="submit" className="btn-primary flex-1 justify-center" disabled={loading}>
              {loading ? (
                <><span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />Creando…</>
              ) : 'Crear usuario'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
