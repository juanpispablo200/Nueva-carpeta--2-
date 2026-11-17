import { useNavigate } from 'react-router-dom'
import { ShieldOff } from 'lucide-react'
import { supabase } from '../lib/supabase'

export default function AccessDeniedPage() {
  const navigate = useNavigate()
  async function handleLogout() { await supabase.auth.signOut(); navigate('/login', { replace: true }) }
  return (
    <div className="min-h-screen bg-[var(--c-bg)] flex items-center justify-center px-4">
      <div className="card p-10 w-full max-w-sm text-center animate-fade-in">
        <div className="w-12 h-12 rounded-full bg-red-50 flex items-center justify-center mx-auto mb-4">
          <ShieldOff size={22} className="text-red-500"/>
        </div>
        <h1 className="font-display text-lg font-bold text-[var(--c-ink)] mb-2">Sin acceso</h1>
        <p className="text-sm text-[var(--c-ink-muted)] mb-6">Tu cuenta no tiene permisos para esta sección. Contacta al administrador.</p>
        <button onClick={handleLogout} className="btn-primary w-full justify-center">Volver al inicio</button>
      </div>
    </div>
  )
}
