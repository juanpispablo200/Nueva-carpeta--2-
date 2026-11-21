import { NavLink } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { NAV } from '../../lib/nav'
import { supabase } from '../../lib/supabase'
import type { UserRole } from '../../lib/supabase'

interface Props {
  role: UserRole
  onNavigate?: () => void
}

export default function Sidebar({ role, onNavigate }: Props) {
  const items = NAV[role] || []

  // Badge de pagos pendientes — solo admin, actualizado en tiempo real
  const [pendingPayments, setPendingPayments] = useState(0)

  useEffect(() => {
    if (role !== 'admin') return

    const fetchCount = () => {
      supabase
        .from('payment_proofs')
        .select('id', { count: 'exact', head: true })
        .eq('status', 'pending')
        .then(({ count }) => setPendingPayments(count ?? 0))
    }

    fetchCount()

    const channel = supabase
      .channel('payment_proofs_badge')
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'payment_proofs' },
        fetchCount
      )
      .subscribe()

    return () => { supabase.removeChannel(channel) }
  }, [role])

  return (
    <aside className="w-[var(--sidebar-w)] h-full bg-[var(--color-surface)] border-r border-[var(--color-border)] flex flex-col">
      {/* Logo */}
      <div className="h-[var(--header-h)] px-5 flex items-center border-b border-[var(--color-border)]">
        <div className="w-9 h-9 rounded-full bg-[var(--color-brand-700)] flex items-center justify-center">
          <span className="font-display font-bold text-white text-sm">J</span>
        </div>
        <span className="ml-3 font-display font-bold text-[var(--color-ink)] text-lg">Jagoo</span>
      </div>

      {/* Nav items */}
      <nav className="flex-1 py-4 px-3 space-y-1 overflow-y-auto">
        {items.map(({ key, label, icon: Icon, path }) => (
          <NavLink
            key={key}
            to={path}
            onClick={onNavigate}
            className={({ isActive }) => `
              flex items-center gap-3 px-3 py-2 rounded-[var(--radius-md)] transition-all
              ${isActive
                ? 'bg-[var(--color-brand-700)] text-white font-semibold'
                : 'text-[var(--color-ink-muted)] hover:bg-[var(--color-bg)] hover:text-[var(--color-ink)]'
              }
            `}
          >
            <Icon size={18} />
            <span className="text-sm flex-1">{label}</span>

            {/* Badge de pagos pendientes */}
            {key === 'pagos' && pendingPayments > 0 && (
              <span className="ml-auto min-w-[20px] h-5 px-1.5 rounded-full bg-red-500 text-white text-xs font-bold flex items-center justify-center">
                {pendingPayments > 99 ? '99+' : pendingPayments}
              </span>
            )}
          </NavLink>
        ))}
      </nav>

      {/* Footer */}
      <div className="px-5 py-3 border-t border-[var(--color-border)] text-xs text-[var(--color-ink-sub)]">
        Panel {role === 'admin' ? 'Administrador' : role === 'restaurant' ? 'Restaurante' : 'Jagoo'}
      </div>
    </aside>
  )
}
