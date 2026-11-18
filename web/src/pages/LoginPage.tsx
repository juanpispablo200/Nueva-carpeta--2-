import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Mail, Lock, TrendingUp, AlertCircle, ArrowLeft } from 'lucide-react'
import { supabase, getMyProfile } from '../lib/supabase'
import { ROLE_HOME } from '../lib/nav'

export default function LoginPage() {
  const navigate = useNavigate()
  const [email,    setEmail]    = useState('')
  const [password, setPassword] = useState('')
  const [error,    setError]    = useState('')
  const [loading,  setLoading]  = useState(false)
  const [mode,     setMode]     = useState<'login' | 'reset'>('login')
  const [resetSent, setResetSent] = useState(false)

  // Mostrar error guardado en sessionStorage (de RequireRole)
  useEffect(() => {
    const authError = sessionStorage.getItem('authError')
    if (authError) {
      setError(authError)
      sessionStorage.removeItem('authError')
    }
  }, [])

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault()
    setError(''); setLoading(true)
    try {
      const { error: authError } = await supabase.auth.signInWithPassword({ email, password })
      if (authError) throw new Error(authError.message)

      const profile = await getMyProfile()
      if (!profile) throw new Error('No se encontró el perfil de usuario')
      if (!profile.is_active) {
        await supabase.auth.signOut()
        throw new Error('Tu cuenta ha sido desactivada. Contacta al administrador.')
      }

      // Si es repartidor, ir directo a su página
      const returnUrl = sessionStorage.getItem('returnUrl')
      sessionStorage.removeItem('returnUrl')
      navigate(returnUrl || ROLE_HOME[profile.role], { replace: true })
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error desconocido'
      if (msg.toLowerCase().includes('invalid login')) setError('Email o contraseña incorrectos')
      else if (msg.toLowerCase().includes('network'))  setError('Sin conexión. Verifica tu internet')
      else setError(msg)
    } finally { setLoading(false) }
  }

  async function handleResetPassword(e: React.FormEvent) {
    e.preventDefault()
    setError(''); setLoading(true)
    try {
      const { error } = await supabase.auth.resetPasswordForEmail(email, {
        redirectTo: `${window.location.origin}/reset-password`,
      })
      if (error) throw error
      setResetSent(true)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Error al enviar el correo')
    } finally { setLoading(false) }
  }

  return (
    <div className="min-h-screen bg-[var(--color-bg)] flex items-center justify-center px-4">
      <div className="w-full max-w-sm animate-in">

        {/* Logo */}
        <div className="flex flex-col items-center mb-8">
          <div className="w-12 h-12 rounded-2xl bg-[var(--color-brand-700)] flex items-center justify-center mb-4 shadow-lg">
            <TrendingUp size={22} color="#fff" strokeWidth={2.5}/>
          </div>
          <h1 className="font-display text-2xl font-bold text-[var(--color-ink)]">Jagoo Panel</h1>
          <p className="text-sm text-[var(--color-ink-muted)] mt-1">
            {mode === 'login' ? 'Acceso exclusivo para operadores' : 'Recuperar contraseña'}
          </p>
        </div>

        {/* Card */}
        <div className="card p-7">
          {mode === 'login' ? (
            <form onSubmit={handleLogin} className="space-y-4">
              {/* Email */}
              <div>
                <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
                  Email
                </label>
                <div className="relative">
                  <Mail size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-ink-sub)]"/>
                  <input
                    type="email" required value={email} onChange={e => setEmail(e.target.value)}
                    placeholder="tu@email.com" autoComplete="email"
                    className="input pl-9"
                  />
                </div>
              </div>

              {/* Contraseña */}
              <div>
                <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
                  Contraseña
                </label>
                <div className="relative">
                  <Lock size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-ink-sub)]"/>
                  <input
                    type="password" required value={password} onChange={e => setPassword(e.target.value)}
                    placeholder="••••••••" autoComplete="current-password"
                    className="input pl-9"
                  />
                </div>
              </div>

              {/* Error */}
              {error && (
                <div className="flex items-start gap-2 bg-red-50 border border-red-200 text-red-700 text-sm rounded-[var(--radius-md)] px-3 py-2.5 animate-in">
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

              <button
                type="button"
                onClick={() => { setMode('reset'); setError(''); setResetSent(false) }}
                className="w-full text-center text-xs text-[var(--color-ink-muted)] hover:text-[var(--color-brand-700)] transition-colors"
              >
                ¿Olvidaste tu contraseña?
              </button>
            </form>
          ) : (
            <form onSubmit={handleResetPassword} className="space-y-4">
              {resetSent ? (
                <div className="text-center py-4">
                  <div className="w-12 h-12 rounded-full bg-[var(--color-success-bg)] flex items-center justify-center mx-auto mb-3">
                    <Mail size={20} className="text-[var(--color-success-text)]" />
                  </div>
                  <p className="text-sm text-[var(--color-ink)] font-medium mb-1">Correo enviado</p>
                  <p className="text-xs text-[var(--color-ink-muted)]">
                    Revisa tu bandeja de entrada. Si no lo ves, revisa spam.
                  </p>
                </div>
              ) : (
                <>
                  <p className="text-xs text-[var(--color-ink-muted)]">
                    Ingresa tu email y te enviaremos un enlace para restablecer tu contraseña.
                  </p>
                  <div>
                    <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
                      Email
                    </label>
                    <div className="relative">
                      <Mail size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-ink-sub)]"/>
                      <input
                        type="email" required value={email} onChange={e => setEmail(e.target.value)}
                        placeholder="tu@email.com" autoComplete="email"
                        className="input pl-9"
                      />
                    </div>
                  </div>

                  {error && (
                    <div className="flex items-start gap-2 bg-red-50 border border-red-200 text-red-700 text-sm rounded-[var(--radius-md)] px-3 py-2.5 animate-in">
                      <AlertCircle size={15} className="shrink-0 mt-0.5"/>
                      <span>{error}</span>
                    </div>
                  )}

                  <button type="submit" disabled={loading} className="btn-primary w-full justify-center py-2.5 text-sm disabled:opacity-50">
                    {loading ? (
                      <span className="flex items-center gap-2">
                        <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"/>
                        Enviando…
                      </span>
                    ) : 'Enviar enlace'}
                  </button>
                </>
              )}

              <button
                type="button"
                onClick={() => { setMode('login'); setError(''); setResetSent(false) }}
                className="w-full flex items-center justify-center gap-1 text-xs text-[var(--color-ink-muted)] hover:text-[var(--color-brand-700)] transition-colors"
              >
                <ArrowLeft size={12} />
                Volver al login
              </button>
            </form>
          )}
        </div>

        <p className="text-xs text-center text-[var(--color-ink-sub)] mt-5">
          ¿Eres cliente? Usa la <strong>app móvil de Jagoo</strong>
        </p>
      </div>
    </div>
  )
}
