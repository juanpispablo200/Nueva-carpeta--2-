import { useEffect, useState } from 'react'
import { BarChart2, Users, Store } from 'lucide-react'
import { getMyProfile, type Profile } from '../../lib/supabase'
import UsersTable       from '../../components/admin/UsersTable'
import RestaurantsTable from '../../components/admin/RestaurantsTable'
import ReportsPanel     from '../../components/admin/ReportsPanel'
import AppLayout        from '../../components/layout/AppLayout'

type Tab = 'reportes' | 'usuarios' | 'restaurantes'

const NAV: { key: Tab; label: string; icon: typeof BarChart2 }[] = [
  { key: 'reportes',     label: 'Reportes',     icon: BarChart2 },
  { key: 'usuarios',     label: 'Usuarios',     icon: Users     },
  { key: 'restaurantes', label: 'Restaurantes', icon: Store     },
]

export default function AdminHomePage() {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [tab,     setTab]     = useState<Tab>('reportes')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getMyProfile().then(p => {
      setProfile(p)
      setLoading(false)
    })
  }, [])

  if (loading) {
    return (
      <div className="h-screen flex items-center justify-center bg-[var(--color-bg)]">
        <div className="skeleton w-64 h-48" />
      </div>
    )
  }

  const TAB_TITLE: Record<Tab, string> = {
    reportes:     'Reportes',
    usuarios:     'Usuarios',
    restaurantes: 'Restaurantes',
  }

  return (
    <AppLayout role="admin" userName={profile?.full_name ?? 'Admin'}>
      {/* Subtabs */}
      <div className="flex gap-2 mb-6">
        {NAV.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`flex items-center gap-2 px-4 py-2 rounded-[var(--radius-md)] text-sm font-medium transition-all ${
              tab === key
                ? 'bg-[var(--color-brand-700)] text-white'
                : 'bg-[var(--color-surface)] text-[var(--color-ink-muted)] hover:text-[var(--color-ink)] border border-[var(--color-border)]'
            }`}
          >
            <Icon size={16} />
            {label}
          </button>
        ))}
      </div>

      {/* Page header */}
      <header className="mb-6">
        <p className="text-xs font-semibold uppercase tracking-widest text-[var(--color-ink-sub)] mb-1">Administración</p>
        <h1 className="font-display text-2xl font-bold text-[var(--color-ink)]">{TAB_TITLE[tab]}</h1>
      </header>

      {/* Content */}
      <div>
        {tab === 'reportes'     && <ReportsPanel />}
        {tab === 'usuarios'     && <UsersTable />}
        {tab === 'restaurantes' && <RestaurantsTable />}
      </div>
    </AppLayout>
  )
}
