import { useEffect, useState } from 'react'
import { ShoppingBag, DollarSign, CheckCircle, XCircle } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import type { OrderStatus } from '../../lib/types'
import { CardSkeleton, EmptyState, KpiCard, SectionHeader } from '../ui'

interface Stats {
  total:    number
  revenue:  number
  delivered: number
  cancelled: number
  byStatus:  Record<string, number>
  topRest:   { name: string; logo: string; count: number; revenue: number }[]
}

const STATUS_ORDER: OrderStatus[] = ['pending','accepted','preparing','on_the_way','delivered']
const STATUS_STEP_LABEL: Record<string, string> = {
  pending: 'Pendiente', accepted: 'Aceptado', preparing: 'En cocina',
  on_the_way: 'En camino', delivered: 'Entregado', cancelled: 'Cancelado',
}

export default function ReportsPanel() {
  const [stats,   setStats]   = useState<Stats | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function load() {
      const { data } = await supabase.from('orders').select('id, status, total, restaurant_name, restaurant_logo_url')
      if (!data) { setLoading(false); return }

      const byStatus: Record<string, number> = {}
      const byRest:   Record<string, { count: number; revenue: number; logo: string }> = {}
      let revenue = 0, delivered = 0, cancelled = 0

      for (const o of data) {
        byStatus[o.status] = (byStatus[o.status] ?? 0) + 1
        if (!byRest[o.restaurant_name]) byRest[o.restaurant_name] = { count: 0, revenue: 0, logo: o.restaurant_logo_url ?? '' }
        byRest[o.restaurant_name].count++
        if (o.status === 'delivered') { byRest[o.restaurant_name].revenue += o.total; revenue += o.total; delivered++ }
        if (o.status === 'cancelled') cancelled++
      }

      setStats({
        total: data.length, revenue, delivered, cancelled,
        byStatus,
        topRest: Object.entries(byRest).map(([name, v]) => ({ name, ...v })).sort((a,b) => b.count - a.count).slice(0,5),
      })
      setLoading(false)
    }
    load()
  }, [])

  if (loading) return <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">{Array(4).fill(0).map((_,i) => <CardSkeleton key={i} />)}</div>
  if (!stats)  return <EmptyState icon={<ShoppingBag size={20}/>} title="Sin datos disponibles" description="Aún no hay pedidos registrados." />

  const deliveryRate = stats.total > 0 ? Math.round((stats.delivered / stats.total) * 100) : 0

  return (
    <div className="space-y-8 animate-fade-in">

      {/* KPIs */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard label="Total pedidos"   value={stats.total}                 sub="en toda la plataforma" />
        <KpiCard label="Ingresos"        value={`$${stats.revenue.toFixed(2)}`} sub="pedidos entregados" trendUp />
        <KpiCard label="Entregados"      value={stats.delivered}             trend={`${deliveryRate}%`} trendUp />
        <KpiCard label="Cancelados"      value={stats.cancelled}             sub="requieren revisión" />
      </div>

      {/* Flujo de estados */}
      <div className="card p-6">
        <SectionHeader title="Flujo de pedidos" />
        <div className="flex items-start gap-0 overflow-x-auto pb-2">
          {STATUS_ORDER.map((s, i) => {
            const count = stats.byStatus[s] ?? 0
            const pct   = stats.total > 0 ? Math.round((count / stats.total) * 100) : 0
            const isLast = i === STATUS_ORDER.length - 1
            return (
              <div key={s} className="flex items-center flex-1">
                <div className="flex flex-col items-center min-w-[90px] flex-1">
                  <div className={`w-11 h-11 rounded-full border flex items-center justify-center mb-2 ${count ? 'border-[var(--c-brand)] bg-[var(--c-success-bg)]' : 'border-[var(--c-border)] bg-[var(--c-bg)]'}`}>
                    <span className="font-display font-bold text-sm text-[var(--c-ink)]">{count}</span>
                  </div>
                  <span className="text-xs font-semibold text-[var(--c-ink)] text-center">{STATUS_STEP_LABEL[s]}</span>
                  <p className="text-xs text-[var(--c-ink-subtle)] mt-1">{pct}%</p>
                </div>
                {!isLast && (
                  <div className="h-px flex-1 min-w-5 bg-[var(--c-border)] mx-2 mt-[-22px] relative">
                    <span className="absolute -right-0.5 -top-[3px] h-[7px] w-[7px] rotate-45 border-t border-r border-[var(--c-ink-subtle)]" />
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </div>

      {/* Top restaurantes */}
      {stats.topRest.length > 0 && (
        <div className="card p-6">
          <SectionHeader title="Restaurantes destacados" />
          <div className="space-y-3">
            {stats.topRest.map((r, i) => (
              <div key={r.name} className="flex items-center gap-3 py-2 border-b border-[var(--c-border)] last:border-0">
                <span className="w-5 text-sm font-bold text-[var(--c-ink-subtle)]">#{i+1}</span>
                {r.logo ? (
                  <img src={r.logo} alt={r.name} className="w-8 h-8 rounded-full object-cover" />
                ) : (
                  <div className="w-8 h-8 rounded-full bg-[var(--c-brand)] flex items-center justify-center text-white text-xs font-bold">{r.name[0]}</div>
                )}
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-[var(--c-ink)] truncate">{r.name}</p>
                  <p className="text-xs text-[var(--c-ink-muted)]">{r.count} pedidos</p>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-[var(--c-ink)]">${r.revenue.toFixed(2)}</p>
                  <div className="mt-2 h-1.5 w-24 bg-[var(--c-border)] rounded-full overflow-hidden" aria-label={`${Math.round((r.count/(stats.topRest[0]?.count||1))*100)}% del máximo de pedidos`}>
                    <div
                      className="h-full bg-[var(--c-brand)] rounded-full transition-all"
                      style={{ width: `${Math.round((r.count/(stats.topRest[0]?.count||1))*100)}%` }}
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Stats rápidas */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="card p-5 flex items-center gap-4">
          <div className="w-10 h-10 rounded-full bg-[var(--c-success-bg)] flex items-center justify-center">
            <CheckCircle size={20} className="text-[var(--c-success-ink)]" />
          </div>
          <div>
            <p className="text-xs text-[var(--c-ink-subtle)] font-medium uppercase tracking-wide">Tasa de éxito</p>
            <p className="font-display text-2xl font-bold text-[var(--c-ink)]">{deliveryRate}%</p>
          </div>
        </div>
        <div className="card p-5 flex items-center gap-4">
          <div className="w-10 h-10 rounded-full bg-[var(--c-info-bg)] flex items-center justify-center">
            <DollarSign size={20} className="text-[var(--c-info-ink)]" />
          </div>
          <div>
            <p className="text-xs text-[var(--c-ink-subtle)] font-medium uppercase tracking-wide">Ticket promedio</p>
            <p className="font-display text-2xl font-bold text-[var(--c-ink)]">
              {stats.delivered > 0 ? `$${(stats.revenue/stats.delivered).toFixed(2)}` : '—'}
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
