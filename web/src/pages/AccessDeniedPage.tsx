import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Smartphone, ShieldOff, LogOut } from 'lucide-react'
import { supabase, getMyProfile } from '../lib/supabase'
import type { UserRole } from '../lib/supabase'

export default function AccessDeniedPage() {
  const navigate = useNavigate()
  const [role,     setRole]     = useState<UserRole | null>(null)
  const [loading,  setLoading]  = useState(true)

  useEffect(() => {
    getMyProfile().then(p => {
      setRole((p?.role as UserRole) ?? null)
      setLoading(false)
    })
  }, [])

  async function handleLogout() {
    await supabase.auth.signOut()
    navigate('/login', { replace: true })
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-[var(--color-bg)] flex items-center justify-center">
        <span className="w-8 h-8 border-2 border-[var(--color-brand-700)] border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  // ── Repartidor → pantalla "usa la app" ───────────────────────────────────
  if (role === 'repartidor') {
    return (
      <div className="min-h-screen bg-[var(--color-bg)] flex items-center justify-center px-4">
        <div className="card p-10 w-full max-w-sm text-center">
          <div className="w-14 h-14 rounded-full bg-[var(--color-brand-100)] flex items-center justify-center mx-auto mb-5">
            <Smartphone size={26} className="text-[var(--color-brand-700)]" />
          </div>
          <h1 className="font-display text-xl font-bold text-[var(--color-ink)] mb-2">
            Usa la app de repartidor
          </h1>
          <p className="text-sm text-[var(--color-ink-muted)] mb-6">
            Este panel web es exclusivo para administradores y restaurantes.
            Los repartidores deben usar la <strong>app móvil Jagoo Driver</strong>.
          </p>
          <div className="bg-[var(--color-bg)] rounded-[var(--radius-md)] p-4 mb-6 text-left space-y-1.5">
            <p className="text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-2">Cómo acceder</p>
            <p className="text-sm text-[var(--color-ink-muted)]">1. Descarga la app <strong>Jagoo Driver</strong></p>
            <p className="text-sm text-[var(--color-ink-muted)]">2. Inicia sesión con tus credenciales</p>
            <p className="text-sm text-[var(--color-ink-muted)]">3. Acepta los pedidos asignados</p>
          </div>
          <button onClick={handleLogout} className="btn-ghost w-full justify-center text-sm">
            <LogOut size={15} /> Cerrar sesión
          </button>
        </div>
      </div>
    )
  }

  // ── Cualquier otro rol sin permiso ────────────────────────────────────────
  return (
    <div className="min-h-screen bg-[var(--color-bg)] flex items-center justify-center px-4">
      <div className="card p-10 w-full max-w-sm text-center">
        <div className="w-12 h-12 rounded-full bg-red-50 flex items-center justify-center mx-auto mb-4">
          <ShieldOff size={22} className="text-red-500" />
        </div>
        <h1 className="font-display text-lg font-bold text-[var(--color-ink)] mb-2">Sin acceso</h1>
        <p className="text-sm text-[var(--color-ink-muted)] mb-6">
          Tu cuenta no tiene permisos para esta sección. Contacta al administrador.
        </p>
        <button onClick={handleLogout} className="btn-primary w-full justify-center">
          <LogOut size={15} /> Volver al inicio
        </button>
      </div>
    </div>
  )
}
