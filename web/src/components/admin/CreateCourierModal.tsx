import { useState } from 'react'
import { X, UserPlus, AlertCircle } from 'lucide-react'
import { supabase } from '../../lib/supabase'

interface Props {
  onClose:   () => void
  onSuccess: () => void
}

export default function CreateCourierModal({ onClose, onSuccess }: Props) {
  const [email,    setEmail]    = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [phone,    setPhone]    = useState('')
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState('')

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      // Obtener token de la sesión actual del admin
      const { data: { session } } = await supabase.auth.getSession()
      if (!session) throw new Error('No hay sesión activa')

      // Llamar a la Edge Function — la sesión del admin NO cambia
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
            full_name: fullName.trim(),   // ← nombre del formulario, no del admin
            phone:     phone.trim() || undefined,
            role:      'repartidor',
          }),
        }
      )

      const result = await res.json()
      if (!res.ok) throw new Error(result.error ?? 'Error al crear el repartidor')

      onSuccess()
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error desconocido')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-in">
      <div className="card w-full max-w-md p-6 animate-in">
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-[var(--radius-md)] bg-[var(--color-brand-100)] flex items-center justify-center">
              <UserPlus size={16} className="text-[var(--color-brand-700)]" />
            </div>
            <h2 className="font-display text-lg font-bold text-[var(--color-ink)]">
              Crear repartidor
            </h2>
          </div>
          <button
            onClick={onClose}
            className="text-[var(--color-ink-muted)] hover:text-[var(--color-ink)] transition-colors"
            aria-label="Cerrar"
          >
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
              Nombre completo *
            </label>
            <input
              type="text"
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
              placeholder="pedro@example.com"
              className="input"
              autoComplete="off"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
              Contraseña *
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
              placeholder="+593 99 123 4567"
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
                <span className="flex items-center gap-2">
                  <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  Creando…
                </span>
              ) : 'Crear repartidor'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
