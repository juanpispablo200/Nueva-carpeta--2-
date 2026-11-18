import { useState, type ReactNode } from 'react'
import { Menu, LogOut, User } from 'lucide-react'
import Sidebar from './Sidebar'
import { supabase } from '../../lib/supabase'
import { useNavigate } from 'react-router-dom'
import type { UserRole } from '../../lib/supabase'

interface Props {
  role:     UserRole
  userName: string
  children: ReactNode
}

export default function AppLayout({ role, userName, children }: Props) {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const navigate = useNavigate()

  const handleLogout = async () => {
    await supabase.auth.signOut()
    navigate('/login')
  }

  return (
    <div className="h-screen flex overflow-hidden">
      {/* Sidebar desktop */}
      <div className="hidden md:block">
        <Sidebar role={role} />
      </div>

      {/* Sidebar mobile — overlay */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-40 md:hidden">
          <div
            className="absolute inset-0 bg-black/40 backdrop-blur-sm"
            onClick={() => setSidebarOpen(false)}
            aria-hidden
          />
          <div className="absolute left-0 top-0 bottom-0 w-[var(--sidebar-w)] animate-in">
            <Sidebar role={role} onNavigate={() => setSidebarOpen(false)} />
          </div>
        </div>
      )}

      {/* Main content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Header */}
        <header className="h-[var(--header-h)] bg-[var(--color-surface)] border-b border-[var(--color-border)] flex items-center justify-between px-4 md:px-6">
          {/* Hamburger mobile */}
          <button
            onClick={() => setSidebarOpen(true)}
            aria-label="Abrir menú"
            className="md:hidden text-[var(--color-ink-muted)] hover:text-[var(--color-ink)] transition-colors"
          >
            <Menu size={22} />
          </button>

          {/* Spacer desktop */}
          <div className="hidden md:block" />

          {/* User menu */}
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 text-sm">
              <div className="w-8 h-8 rounded-full bg-[var(--color-brand-100)] flex items-center justify-center">
                <User size={14} className="text-[var(--color-brand-700)]" />
              </div>
              <span className="hidden sm:inline text-[var(--color-ink)] font-medium">{userName}</span>
            </div>
            <button
              onClick={handleLogout}
              aria-label="Cerrar sesión"
              className="btn-ghost px-2 py-2"
              title="Cerrar sesión"
            >
              <LogOut size={16} />
            </button>
          </div>
        </header>

        {/* Content */}
        <main className="flex-1 overflow-auto bg-[var(--color-bg)] p-4 md:p-6">
          {children}
        </main>
      </div>
    </div>
  )
}
