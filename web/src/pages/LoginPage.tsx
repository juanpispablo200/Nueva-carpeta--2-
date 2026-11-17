import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Mail, Lock, TrendingUp, AlertCircle } from 'lucide-react'
import { supabase, getMyProfile } from '../lib/supabase'

export default function LoginPage() {
  const navigate = useNavigate()
  const [email,    setEmail]    = useState('')
  const [password, setPassword] = useState('')
  const [error,    setError]    = useState('')
  const [loading,  setLoading]  = useState(false)

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault()
    setError(''); setLoading(true)
    try {
      const { error: authError } = await supabase.auth.signInWithPassword({ email, password })
      if (authError) throw new Error(authError.message)
      const profile = await getMyProfile()
      if (!profile) throw new Error('No se encontró el perfil de usuario')
      if (profile.role === 'restaurant') navigate('/restaurant', { replace: true })
      else if (profile.role === 'admin') navigate('/admin', { replace: true })
      else { await supabase.auth.signOut(); setError('Esta cuenta es de tipo cliente. Usa la app móvil de Jagoo.') }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error desconocido'
      if (msg.toLowerCase().includes('invalid login')) setError('Email o contraseña incorrectos')
      else if (msg.toLowerCase().includes('network'))  setError('Sin conexión. Verifica tu internet')
      else setError(msg)
    } finally { setLoading(false) }
  }

  return (
    <div className="min-h-screen bg-[var(--c-bg)] flex items-center justify-center px-4">
      <div className="w-full max-w-sm animate-fade-in">

        {/* Logo */}
        <div className="flex flex-col items-center mb-8">
          <div className="w-12 h-12 rounded-2xl bg-[var(--c-brand)] flex items-center justify-center mb-4 shadow-modal">
            <TrendingUp size={22} color="#fff" strokeWidth={2.5}/>
          </div>
          <h1 className="font-display text-2xl font-bold text-[var(--c-ink)]">Jagoo Panel</h1>
          <p className="text-sm text-[var(--c-ink-muted)] mt-1">Acceso exclusivo para operadores</p>
        </div>

        {/* Card */}
        <div className="card p-7">
          <form onSubmit={handleLogin} className="space-y-4">

            {/* Email */}
            <div>
              <label className="block text-xs font-semibold text-[var(--c-ink-muted)] uppercase tracking-wide mb-1.5">
                Email
              </label>
              <div className="relative">
                <Mail size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--c-ink-subtle)]"/>
                <input
                  type="email" required value={email} onChange={e => setEmail(e.target.value)}
                  placeholder="tu@email.com" autoComplete="email"
                  className="w-full border border-[var(--c-border)] bg-[var(--c-surface)] text-[var(--c-ink)] text-sm rounded-[var(--radius-input)] pl-9 pr-3 py-2.5 placeholder:text-[var(--c-ink-subtle)] focus:outline-none focus:ring-2 focus:ring-[var(--c-brand)] focus:border-transparent transition"
                />
              </div>
            </div>

            {/* Contraseña */}
            <div>
              <label className="block text-xs font-semibold text-[var(--c-ink-muted)] uppercase tracking-wide mb-1.5">
                Contraseña
              </label>
              <div className="relative">
                <Lock size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--c-ink-subtle)]"/>
                <input
                  type="password" required value={password} onChange={e => setPassword(e.target.value)}
                  placeholder="••••••••" autoComplete="current-password"
                  className="w-full border border-[var(--c-border)] bg-[var(--c-surface)] text-[var(--c-ink)] text-sm rounded-[var(--radius-input)] pl-9 pr-3 py-2.5 placeholder:text-[var(--c-ink-subtle)] focus:outline-none focus:ring-2 focus:ring-[var(--c-brand)] focus:border-transparent transition"
                />
              </div>
            </div>

            {/* Error */}
            {error && (
              <div className="flex items-start gap-2 bg-red-50 border border-red-200 text-red-700 text-sm rounded-[var(--radius-input)] px-3 py-2.5 animate-fade-in">
                <AlertCircle size={15} className="shrink-0 mt-0.5"/>
                <span>{error}</span>
              </div>
            )}

            <button type="submit" disabled={loading} className="btn-primary w-full justify-center py-2.5 text-sm disabled:opacity-50">
              {loading ? (
                <span className="flex items-center gap-2">
                  <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"/>
                  Ingresando…
                </span>
              ) : 'Ingresar'}
            </button>
          </form>
        </div>

        <p className="text-xs text-center text-[var(--c-ink-subtle)] mt-5">
          ¿Eres cliente? Usa la <strong>app móvil de Jagoo</strong>
        </p>
      </div>
    </div>
  )
}
