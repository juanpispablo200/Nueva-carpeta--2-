import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { getMyProfile, type UserRole } from '../lib/supabase'

interface Props {
  allowedRole: UserRole   // 'restaurant' o 'admin'
  children: React.ReactNode
}

/**
 * ProtectedRoute — guarda una ruta según el rol del usuario.
 *
 * ¿Cómo funciona?
 * 1. Consulta Supabase para obtener el rol del usuario logueado.
 * 2. Si el rol coincide con allowedRole → muestra el contenido.
 * 3. Si el usuario no está logueado    → redirige a /login.
 * 4. Si el rol es incorrecto           → redirige a /acceso-denegado.
 */
export default function ProtectedRoute({ allowedRole, children }: Props) {
  const [status, setStatus] = useState<'loading' | 'ok' | 'unauthorized' | 'no-session'>('loading')

  useEffect(() => {
    getMyProfile().then(profile => {
      if (!profile)                    setStatus('no-session')
      else if (profile.role === allowedRole) setStatus('ok')
      else                             setStatus('unauthorized')
    })
  }, [allowedRole])

  if (status === 'loading') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-jagoo-bg">
        <div className="animate-spin rounded-full h-10 w-10 border-4 border-jagoo-green border-t-transparent" />
      </div>
    )
  }

  if (status === 'no-session')   return <Navigate to="/login" replace />
  if (status === 'unauthorized') return <Navigate to="/acceso-denegado" replace />

  return <>{children}</>
}
