import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { BarChart2, Users, Store, LogOut, TrendingUp } from 'lucide-react'
import { supabase, getMyProfile } from '../../lib/supabase'
import type { Profile } from '../../lib/types'
import UsersTable       from '../../components/admin/UsersTable'
import RestaurantsTable from '../../components/admin/RestaurantsTable'
import ReportsPanel     from '../../components/admin/ReportsPanel'
import { AppShell }     from '../../components/ui'

type Tab = 'reportes' | 'usuarios' | 'restaurantes'

const NAV: { key: Tab; label: string; icon: typeof BarChart2 }[] = [
  { key: 'reportes',     label: 'Reportes',     icon: BarChart2 },
  { key: 'usuarios',     label: 'Usuarios',     icon: Users     },
  { key: 'restaurantes', label: 'Restaurantes', icon: Store     },
]

export default function AdminHomePage() {
  const navigate  = useNavigate()
  const [profile, setProfile] = useState<Profile | null>(null)
  const [tab,     setTab]     = useState<Tab>('reportes')

  useEffect(() => { getMyProfile().then(p => setProfile(p as Profile)) }, [])

  async function handleLogout() {
    await supabase.auth.signOut()
    navigate('/login', { replace: true })
  }

  const TAB_TITLE: Record<Tab, string> = {
    reportes:     'Reportes',
    usuarios:     'Usuarios',
    restaurantes: 'Restaurantes',
  }

  return (
    <AppShell
      sidebar={
        <>
          {/* Logo */}
          <div className="admin-logo px-5 py-5">
            <div className="flex items-center gap-2.5">
              <div className="w-9 h-9 rounded-[var(--radius-input)] bg-[var(--c-brand)] flex items-center justify-center">
                <TrendingUp size={16} color="#fff" strokeWidth={2.5} />
              </div>
              <div>
                <p className="font-display font-bold text-sm text-[var(--c-ink)] leading-tight">Jagoo</p>
                <p className="text-[11px] text-[var(--c-ink-subtle)]">Admin</p>
              </div>
            </div>
          </div>

          {/* Nav */}
          <nav className="admin-nav flex-1 px-3 py-4 space-y-0.5">
            {NAV.map(({ key, label, icon: Icon }) => (
              <button
                key={key}
                onClick={() => setTab(key)}
                aria-current={tab === key ? 'page' : undefined}
                className={`admin-nav-link w-full flex items-center gap-3 px-3 py-2.5 rounded-[var(--radius-input)] text-sm font-medium transition-colors
                  ${tab === key
                    ? 'bg-[var(--c-brand)] text-white'
                    : 'text-[var(--c-ink-muted)] hover:bg-[var(--c-bg)] hover:text-[var(--c-ink)]'
                  }`}
              >
                <Icon size={16} strokeWidth={2} />
                {label}
              </button>
            ))}
          </nav>

          {/* User */}
          <div className="admin-account px-3 py-4">
            <div className="flex items-center gap-2.5 px-3 mb-2">
              <div className="w-7 h-7 rounded-full bg-[var(--c-brand)] flex items-center justify-center text-white text-xs font-bold">
                {(profile?.full_name?.[0] ?? 'A').toUpperCase()}
              </div>
              <p className="text-xs font-medium text-[var(--c-ink)] truncate flex-1">{profile?.full_name ?? 'Admin'}</p>
            </div>
            <button
              onClick={handleLogout}
              className="w-full flex items-center gap-2 px-3 py-2 text-xs text-[var(--c-ink-muted)] hover:text-red-600 hover:bg-red-50 rounded-[var(--radius-input)] transition-colors"
            >
              <LogOut size={14} strokeWidth={2} />
              Cerrar sesión
            </button>
          </div>
        </>
      }
    >
      {/* Page header */}
      <header className="page-header admin-header px-8 py-5">
        <p className="text-xs font-semibold uppercase tracking-[.12em] text-[var(--c-ink-subtle)] mb-1">Administración</p>
        <h1 className="font-display text-2xl font-bold text-[var(--c-ink)]">{TAB_TITLE[tab]}</h1>
      </header>

      {/* Content */}
      <div className="admin-content px-8 py-7">
        {tab === 'reportes'     && <ReportsPanel />}
        {tab === 'usuarios'     && <UsersTable />}
        {tab === 'restaurantes' && <RestaurantsTable />}
      </div>
    </AppShell>
  )
}
