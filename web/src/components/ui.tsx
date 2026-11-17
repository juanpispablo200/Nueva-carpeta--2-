/**
 * ui.tsx — componentes primitivos reutilizables del design system Jagoo Web.
 * Sin lógica de negocio.
 */
import { type ReactNode } from 'react'

// ── Badge de estado ────────────────────────────────────────────────────────────
const STATUS_STYLES: Record<string, { tone: string; label: string }> = {
  pending:    { tone: 'warning', label: 'Pendiente'   },
  accepted:   { tone: 'info',    label: 'Aceptado'    },
  preparing:  { tone: 'info',    label: 'En cocina'   },
  on_the_way: { tone: 'info',    label: 'En camino'   },
  delivered:  { tone: 'success', label: 'Entregado'   },
  cancelled:  { tone: 'danger',  label: 'Cancelado'   },
  customer:   { tone: 'neutral', label: 'Cliente'     },
  restaurant: { tone: 'info',    label: 'Restaurante' },
  admin:      { tone: 'neutral', label: 'Admin'       },
  open:       { tone: 'success', label: 'Abierto'     },
  closed:     { tone: 'danger',  label: 'Cerrado'     },
}

export function StatusBadge({ status }: { status: string }) {
  const s = STATUS_STYLES[status] ?? STATUS_STYLES.pending
  return (
    <span className={`badge badge-${s.tone}`}>
      <span className="badge-dot" />
      {s.label}
    </span>
  )
}

// ── Skeleton ───────────────────────────────────────────────────────────────────
export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`skeleton ${className}`} />
}

export function CardSkeleton() {
  return (
    <div className="card p-5 space-y-3 animate-fade-in">
      <Skeleton className="h-4 w-1/3" />
      <Skeleton className="h-8 w-1/2" />
      <Skeleton className="h-3 w-2/3" />
    </div>
  )
}

// ── Empty state ────────────────────────────────────────────────────────────────
export function EmptyState({ icon, title, description, action }: {
  icon: ReactNode; title: string; description?: string; action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
      <div className="w-12 h-12 rounded-full bg-[var(--c-bg)] border border-[var(--c-border)] flex items-center justify-center mb-4 text-[var(--c-ink-subtle)]">
        {icon}
      </div>
      <p className="font-display font-semibold text-[var(--c-ink)] mb-1">{title}</p>
      {description && <p className="text-sm text-[var(--c-ink-muted)] mb-4 max-w-xs">{description}</p>}
      {action}
    </div>
  )
}

// ── KPI Card ───────────────────────────────────────────────────────────────────
export function KpiCard({ label, value, trend, trendUp, sub }: {
  label: string; value: string | number; trend?: string; trendUp?: boolean; sub?: string
}) {
  return (
    <div className="card p-5 animate-fade-in">
      <p className="text-xs font-semibold uppercase tracking-widest text-[var(--c-ink-subtle)] mb-3">{label}</p>
      <p className="font-display text-3xl font-bold text-[var(--c-ink)] leading-none mb-3">{value}</p>
      <div className="flex items-center gap-2">
        {trend && (
          <span className={`text-xs font-semibold px-1.5 py-0.5 rounded ${trendUp ? 'badge-success' : 'badge-danger'}`}>
            {trendUp ? '↑' : '↓'} {trend}
          </span>
        )}
        {sub && <span className="text-xs text-[var(--c-ink-subtle)]">{sub}</span>}
      </div>
    </div>
  )
}

// ── Section header ─────────────────────────────────────────────────────────────
export function SectionHeader({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
      <h2 className="font-display text-lg font-bold text-[var(--c-ink)]">{title}</h2>
      {children}
    </div>
  )
}

// ── Input ──────────────────────────────────────────────────────────────────────
export function Input({ className = '', ...props }: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      className={`w-full min-h-10 border border-[var(--c-border)] bg-[var(--c-surface)] text-[var(--c-ink)] text-sm rounded-[var(--radius-input)] px-3 py-2 placeholder:text-[var(--c-ink-subtle)] focus-visible:outline-2 focus-visible:outline-[var(--c-brand)] focus-visible:outline-offset-2 transition ${className}`}
    />
  )
}

// ── Sidebar layout ─────────────────────────────────────────────────────────────
export function AppShell({ sidebar, children }: { sidebar: ReactNode; children: ReactNode }) {
  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        {sidebar}
      </aside>
      <main className="app-main">
        {children}
      </main>
    </div>
  )
}
