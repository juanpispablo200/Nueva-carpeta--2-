/**
 * ui.tsx — componentes primitivos reutilizables del design system Jagoo Web.
 * Sin lógica de negocio. Usa tokens.css como única fuente de verdad.
 */
import { type ReactNode } from 'react'

// ── Badge de estado ────────────────────────────────────────────────────────────
const STATUS_STYLES: Record<string, { bg: string; text: string; label: string }> = {
  pending:    { bg: 'var(--color-warning-bg)',    text: 'var(--color-warning-text)',    label: 'Pendiente'   },
  accepted:   { bg: 'var(--color-info-bg)',       text: 'var(--color-info-text)',       label: 'Aceptado'    },
  preparing:  { bg: 'var(--color-preparing-bg)',  text: 'var(--color-preparing-text)',  label: 'En cocina'   },
  ready:      { bg: 'var(--color-ready-bg)',      text: 'var(--color-ready-text)',      label: 'Listo'       },
  picked_up:  { bg: 'var(--color-transit-bg)',    text: 'var(--color-transit-text)',    label: 'Recogido'    },
  on_the_way: { bg: 'var(--color-transit-bg)',    text: 'var(--color-transit-text)',    label: 'En camino'   },
  delivered:  { bg: 'var(--color-success-bg)',    text: 'var(--color-success-text)',    label: 'Entregado'   },
  cancelled:  { bg: 'var(--color-error-bg)',      text: 'var(--color-error-text)',      label: 'Cancelado'   },
  customer:   { bg: 'var(--color-border)',        text: 'var(--color-ink)',             label: 'Cliente'     },
  restaurant: { bg: 'var(--color-info-bg)',       text: 'var(--color-info-text)',       label: 'Restaurante' },
  admin:      { bg: 'var(--color-border)',        text: 'var(--color-ink)',             label: 'Admin'       },
  open:       { bg: 'var(--color-success-bg)',    text: 'var(--color-success-text)',    label: 'Abierto'     },
  closed:     { bg: 'var(--color-error-bg)',      text: 'var(--color-error-text)',      label: 'Cerrado'     },
}

export function StatusBadge({ status }: { status: string }) {
  const s = STATUS_STYLES[status] ?? STATUS_STYLES.pending
  return (
    <span className="badge" style={{ background: s.bg, color: s.text }}>
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
    <div className="card p-5 space-y-3 animate-in">
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
      <div className="w-12 h-12 rounded-[var(--radius-lg)] bg-[var(--color-bg)] border border-[var(--color-border)] flex items-center justify-center mb-4 text-[var(--color-ink-sub)]">
        {icon}
      </div>
      <p className="font-display font-semibold text-[var(--color-ink)] mb-1">{title}</p>
      {description && <p className="text-sm text-[var(--color-ink-muted)] mb-4 max-w-xs">{description}</p>}
      {action}
    </div>
  )
}

// ── KPI Card ───────────────────────────────────────────────────────────────────
export function KpiCard({ label, value, trend, trendUp, sub }: {
  label: string; value: string | number; trend?: string; trendUp?: boolean; sub?: string
}) {
  return (
    <div className="card p-5 animate-in">
      <p className="text-xs font-semibold uppercase tracking-widest text-[var(--color-ink-sub)] mb-3">{label}</p>
      <p className="font-display text-3xl font-bold text-[var(--color-ink)] leading-none mb-3">{value}</p>
      <div className="flex items-center gap-2">
        {trend && (
          <span
            className="text-xs font-semibold px-1.5 py-0.5 rounded-[var(--radius-md)]"
            style={{
              background: trendUp ? 'var(--color-success-bg)' : 'var(--color-error-bg)',
              color: trendUp ? 'var(--color-success-text)' : 'var(--color-error-text)',
            }}
          >
            {trendUp ? '↑' : '↓'} {trend}
          </span>
        )}
        {sub && <span className="text-xs text-[var(--color-ink-sub)]">{sub}</span>}
      </div>
    </div>
  )
}

// ── Section header ─────────────────────────────────────────────────────────────
export function SectionHeader({ title, description, children }: { title: string; description?: string; children?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
      <div>
        <h2 className="font-display text-lg font-bold text-[var(--color-ink)]">{title}</h2>
        {description && <p className="text-sm text-[var(--color-ink-muted)] mt-0.5">{description}</p>}
      </div>
      {children}
    </div>
  )
}

// ── Input ──────────────────────────────────────────────────────────────────────
export function Input({ className = '', ...props }: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`input ${className}`} />
}
