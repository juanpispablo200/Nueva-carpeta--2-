import { useEffect } from 'react'
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import { isConfigured, supabase } from './lib/supabase'
import LoginPage from './pages/LoginPage'
import AccessDeniedPage from './pages/AccessDeniedPage'
import RequireRole from './components/RequireRole'
import { ToastProvider } from './components/common/ToastContext'

// Admin
import AdminLayout from './pages/admin/AdminLayout'
import ResumenPage from './pages/admin/ResumenPage'
import PedidosAdminPage from './pages/admin/PedidosAdminPage'
import RepartidoresPage from './pages/admin/RepartidoresPage'
import RestaurantesAdminPage from './pages/admin/RestaurantesAdminPage'
import UsuariosAdminPage from './pages/admin/UsuariosAdminPage'
import ReportesAdminPage from './pages/admin/ReportesAdminPage'
import AjustesPage from './pages/admin/AjustesPage'
import AuditLogPage from './pages/admin/AuditLogPage'
import CalificacionesPage from './pages/admin/CalificacionesPage'
import PagosAdminPage from './pages/admin/PagosAdminPage'
import FinanzasPage from './pages/admin/FinanzasPage'
import PrivacyPolicyPage from './pages/PrivacyPolicyPage'
import DeleteAccountPage from './pages/DeleteAccountPage'
import TermsPage from './pages/TermsPage'

// Restaurant
import RestaurantHomePage from './pages/restaurant/RestaurantHomePage'

function SetupRequired() {
  return (
    <div className="min-h-screen bg-[var(--color-bg)] flex items-center justify-center px-4">
      <div className="card p-8 w-full max-w-lg text-center">
        <div className="text-5xl mb-4">⚙️</div>
        <h1 className="font-display text-xl font-bold text-[var(--color-ink)] mb-3">
          Configuración requerida
        </h1>
        <p className="text-[var(--color-ink-muted)] text-sm mb-5">
          Las claves de Supabase no están configuradas. Edita{' '}
          <code className="bg-[var(--color-bg)] px-1.5 py-0.5 rounded-[var(--radius-md)] text-[var(--color-brand-700)] font-mono text-xs">
            web/.env
          </code>{' '}
          y agrega tus claves:
        </p>
        <pre className="bg-[var(--color-bg)] rounded-[var(--radius-lg)] p-4 text-left text-xs text-[var(--color-ink)] mb-5">
{`VITE_SUPABASE_URL=https://TUPROYECTO.supabase.co
VITE_SUPABASE_ANON_KEY=eyJ...`}
        </pre>
        <p className="text-xs text-[var(--color-ink-sub)]">
          Encuéntralas en{' '}
          <a href="https://supabase.com" target="_blank" rel="noopener noreferrer"
            className="text-[var(--color-brand-700)] underline">
            supabase.com
          </a>{' '}
          → tu proyecto → Settings → API.
          <br />Luego reinicia con <code className="font-mono">npm run dev</code>.
        </p>
      </div>
    </div>
  )
}

export default function App() {
  const navigate = useNavigate()

  useEffect(() => {
    // Escuchar cambios de autenticación globalmente
    const { data: { subscription } } = supabase.auth.onAuthStateChange((event, session) => {
      if (event === 'SIGNED_OUT') {
        // Cerrar todos los canales Realtime
        supabase.removeAllChannels()
        navigate('/login', { replace: true })
      }
    })

    return () => subscription.unsubscribe()
  }, [navigate])

  if (!isConfigured) return <SetupRequired />

  return (
    <ToastProvider>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<LoginPage />} />

        <Route
          path="/admin"
          element={
            <RequireRole allowedRoles={['admin']}>
              <AdminLayout />
            </RequireRole>
          }
        >
          <Route index element={<ResumenPage />} />
          <Route path="pedidos" element={<PedidosAdminPage />} />
          <Route path="pagos"            element={<PagosAdminPage />} />
          <Route path="finanzas"         element={<FinanzasPage />} />
          <Route path="repartidores"    element={<RepartidoresPage />} />
          <Route path="restaurantes"    element={<RestaurantesAdminPage />} />
          <Route path="calificaciones"  element={<CalificacionesPage />} />
          <Route path="usuarios" element={<UsuariosAdminPage />} />
          <Route path="reportes" element={<ReportesAdminPage />} />
          <Route path="ajustes" element={<AjustesPage />} />
          <Route path="auditoria" element={<AuditLogPage />} />
        </Route>

        <Route
          path="/restaurant"
          element={
            <RequireRole allowedRoles={['restaurant']}>
              <RestaurantHomePage />
            </RequireRole>
          }
        />

        <Route path="/acceso-denegado" element={<AccessDeniedPage />} />
        <Route path="/privacy" element={<PrivacyPolicyPage />} />
        <Route path="/delete-account" element={<DeleteAccountPage />} />
        <Route path="/terms" element={<TermsPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </ToastProvider>
  )
}
