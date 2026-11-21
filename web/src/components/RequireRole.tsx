import { useEffect, useState, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { supabase, type UserRole } from '../lib/supabase'
import { ROLE_HOME } from '../lib/nav'

interface Props {
  allowedRoles: UserRole[]
  children:     ReactNode
}

type CheckState = 'loading' | 'allowed' | 'redirect'

export default function RequireRole({ allowedRoles, children }: Props) {
  const [state,       setState]       = useState<CheckState>('loading')
  const [redirectTo,  setRedirectTo]  = useState('/login')
  const location = useLocation()

  useEffect(() => {
    async function check() {
      try {
        // 1. Verificar sesión activa
        const { data: { user } } = await supabase.auth.getUser()
        if (!user) {
          sessionStorage.setItem('returnUrl', location.pathname)
          setRedirectTo('/login')
          setState('redirect')
          return
        }

        // 2. Leer perfil — única fuente de verdad
        const { data: profile, error } = await supabase
          .from('profiles')
          .select('role, is_active')
          .eq('id', user.id)
          .single()

        // Sin perfil → logout y login con mensaje
        if (error || !profile) {
          await supabase.auth.signOut()
          sessionStorage.setItem('authError', 'Tu cuenta no tiene un perfil válido. Contacta al administrador.')
          setRedirectTo('/login')
          setState('redirect')
          return
        }

        const userRole = profile.role as UserRole

        // Inactivo → logout
        if (!profile.is_active) {
          await supabase.auth.signOut()
          sessionStorage.setItem('authError', 'Tu cuenta ha sido desactivada. Contacta al administrador.')
          setRedirectTo('/login')
          setState('redirect')
          return
        }

        // Rol permitido → mostrar contenido
        if (allowedRoles.includes(userRole)) {
          setState('allowed')
          return
        }

        // Rol no permitido → redirigir al home correcto para ese rol.
        // Para repartidor y customer eso es /acceso-denegado (ver ROLE_HOME en nav.ts).
        setRedirectTo(ROLE_HOME[userRole] ?? '/login')
        setState('redirect')

      } catch {
        setRedirectTo('/login')
        setState('redirect')
      }
    }

    check()
  }, [allowedRoles, location.pathname])

  if (state === 'loading') {
    return (
      <div className="h-screen flex items-center justify-center bg-[var(--color-bg)]">
        <span className="w-8 h-8 border-2 border-[var(--color-brand-700)] border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  if (state === 'redirect') {
    return <Navigate to={redirectTo} replace />
  }

  return <>{children}</>
}
