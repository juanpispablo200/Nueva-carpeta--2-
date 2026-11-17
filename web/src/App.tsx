import { Routes, Route, Navigate } from 'react-router-dom'
import { isConfigured }        from './lib/supabase'
import LoginPage               from './pages/LoginPage'
import AccessDeniedPage        from './pages/AccessDeniedPage'
import RestaurantHomePage      from './pages/restaurant/RestaurantHomePage'
import AdminHomePage           from './pages/admin/AdminHomePage'
import ProtectedRoute          from './components/ProtectedRoute'

// ── Pantalla de aviso cuando .env no está configurado ─────────────────────────
function SetupRequired() {
  return (
    <div className="min-h-screen bg-jagoo-bg flex items-center justify-center px-4">
      <div className="bg-white rounded-2xl shadow-lg p-8 w-full max-w-lg text-center">
        <div className="text-5xl mb-4">⚙️</div>
        <h1 className="text-xl font-bold text-gray-800 mb-3">Configuración requerida</h1>
        <p className="text-gray-500 text-sm mb-5">
          Las claves de Supabase no están configuradas. Edita el archivo{' '}
          <code className="bg-gray-100 px-1.5 py-0.5 rounded text-jagoo-green font-mono text-xs">
            web/.env
          </code>{' '}
          y agrega tus claves:
        </p>
        <pre className="bg-gray-50 rounded-xl p-4 text-left text-xs text-gray-700 mb-5">
{`VITE_SUPABASE_URL=https://TUPROYECTO.supabase.co
VITE_SUPABASE_ANON_KEY=eyJ...`}
        </pre>
        <p className="text-xs text-gray-400">
          Encuéntralas en{' '}
          <a href="https://supabase.com" target="_blank" rel="noopener noreferrer"
            className="text-jagoo-green underline">
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
  // Si las claves no están, mostrar aviso en lugar de página en blanco
  if (!isConfigured) return <SetupRequired />

  return (
    <Routes>
      <Route path="/"    element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />

      <Route path="/restaurant" element={
        <ProtectedRoute allowedRole="restaurant">
          <RestaurantHomePage />
        </ProtectedRoute>
      } />

      <Route path="/admin" element={
        <ProtectedRoute allowedRole="admin">
          <AdminHomePage />
        </ProtectedRoute>
      } />

      <Route path="/acceso-denegado" element={<AccessDeniedPage />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}
