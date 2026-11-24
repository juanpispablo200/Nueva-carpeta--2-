import { useEffect, useState } from 'react'
import { ShoppingBag, DollarSign, CheckCircle, TrendingUp, Calendar } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import type { OrderStatus } from '../../lib/types'
import { CardSkeleton, EmptyState, KpiCard, SectionHeader } from '../ui'

interface DayStat { date: string; count: number; revenue: number }

interface Stats {
  total:      number
  revenue:    number
  delivered:  number
  cancelled:  number
  byStatus:   Record<string, number>
  topRest:    { name: string; logo: string; count: number; revenue: number }[]
  byDay:      DayStat[]
}

type Period = 'today' | 'week' | 'month' | 'all'

const PERIOD_LABELS: Record<Period, string> = {
  today: 'Hoy', week: 'Esta semana', month: 'Este mes', all: 'Todo el tiempo',
}

const STATUS_ORDER: OrderStatus[] = ['pending','accepted','preparing','ready','on_the_way','delivered']
const STATUS_STEP_LABEL: Record<string, string> = {
  pending:    'Pendiente',
  accepted:   'Aceptado',
  preparing:  'Cocina',
  ready:      'Listo',
  on_the_way: 'En camino',
  delivered:  'Entregado',
}

function periodFrom(p: Period): string | null {
  const now = new Date()
  if (p === 'today') {
    const d = new Date(now); d.setHours(0,0,0,0); return d.toISOString()
  }
  if (p === 'week') {
    const d = new Date(now); d.setDate(d.getDate() - 6); d.setHours(0,0,0,0); return d.toISOString()
  }
  if (p === 'month') {
    const d = new Date(now); d.setDate(1); d.setHours(0,0,0,0); return d.toISOString()
  }
  return null
}

// Gráfica de barras SVG simple
function BarChart({ data }: { data: DayStat[] }) {
  if (data.length === 0) return null
  const maxRev = Math.max(...data.map(d => d.revenue), 1)
  const maxCount = Math.max(...data.map(d => d.count), 1)
  const W = 600; const H = 120; const barW = Math.max(8, Math.floor((W - 40) / data.length) - 4)

  return (
    <div className="overflow-x-auto">
      <svg viewBox={`0 0 ${W} ${H + 36}`} className="w-full max-w-2xl" style={{ minWidth: 300 }}>
        {data.map((d, i) => {
          const x = 20 + i * ((W - 40) / data.length)
          const hRev   = (d.revenue / maxRev) * H
          const hCount = (d.count  / maxCount) * H
          const label  = d.date.slice(5) // MM-DD
          return (
            <g key={d.date}>
              {/* Barra ingresos */}
              <rect
                x={x} y={H - hRev} width={barW * 0.55} height={hRev}
                rx={3} fill="var(--color-brand-700)" opacity={0.85}
              />
              {/* Barra pedidos */}
              <rect
                x={x + barW * 0.6} y={H - hCount} width={barW * 0.4} height={hCount}
                rx={3} fill="var(--color-brand-700)" opacity={0.3}
              />
              {/* Label fecha */}
              <text x={x + barW * 0.4} y={H + 16} textAnchor="middle"
                fontSize={9} fill="var(--color-ink-sub)">{label}</text>
              {/* Tooltip ingresos */}
              {hRev > 10 && (
                <text x={x + barW * 0.27} y={H - hRev - 3} textAnchor="middle"
                  fontSize={8} fill="var(--color-brand-700)" fontWeight="bold">
                  ${d.revenue.toFixed(0)}
                </text>
              )}
            </g>
          )
        })}
        {/* Eje X */}
        <line x1={16} y1={H} x2={W - 10} y2={H} stroke="var(--color-border)" strokeWidth={1}/>
        {/* Leyenda */}
        <rect x={20} y={H + 24} width={10} height={6} rx={2} fill="var(--color-brand-700)" opacity={0.85}/>
        <text x={34} y={H + 30} fontSize={9} fill="var(--color-ink-sub)">Ingresos ($)</text>
        <rect x={130} y={H + 24} width={10} height={6} rx={2} fill="var(--color-brand-700)" opacity={0.3}/>
        <text x={144} y={H + 30} fontSize={9} fill="var(--color-ink-sub)">Pedidos</text>
      </svg>
    </div>
  )
}

export default function ReportsPanel() {
  const [stats,   setStats]   = useState<Stats | null>(null)
  const [loading, setLoading] = useState(true)
  const [period,  setPeriod]  = useState<Period>('week')

  useEffect(() => {
    setLoading(true)
    async function load() {
      const from = periodFrom(period)

      let q = supabase
        .from('orders')
        .select('id, status, total, restaurant_name, restaurant_logo_url, created_at')
        .order('created_at', { ascending: true })

      if (from) q = q.gte('created_at', from)

      const { data } = await q
      if (!data) { setLoading(false); return }

      const byStatus: Record<string, number> = {}
      const byRest:   Record<string, { count: number; revenue: number; logo: string }> = {}
      const byDayMap: Record<string, { count: number; revenue: number }> = {}
      let revenue = 0, delivered = 0, cancelled = 0

      for (const o of data) {
        byStatus[o.status] = (byStatus[o.status] ?? 0) + 1
        if (!byRest[o.restaurant_name]) {
          byRest[o.restaurant_name] = { count: 0, revenue: 0, logo: o.restaurant_logo_url ?? '' }
        }
        byRest[o.restaurant_name].count++

        const day = (o.created_at as string).slice(0, 10)
        if (!byDayMap[day]) byDayMap[day] = { count: 0, revenue: 0 }
        byDayMap[day].count++

        if (o.status === 'delivered') {
          byRest[o.restaurant_name].revenue += o.total
          byDayMap[day].revenue += o.total
          revenue += o.total
          delivered++
        }
        if (o.status === 'cancelled') cancelled++
      }

      setStats({
        total: data.length, revenue, delivered, cancelled,
        byStatus,
        topRest: Object.entries(byRest)
          .map(([name, v]) => ({ name, ...v }))
          .sort((a, b) => b.count - a.count)
          .slice(0, 5),
        byDay: Object.entries(byDayMap)
          .map(([date, v]) => ({ date, ...v }))
          .sort((a, b) => a.date.localeCompare(b.date))
          .slice(-14),  // últimos 14 días como máximo
      })
      setLoading(false)
    }
    load()
  }, [period])

  if (loading) return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
      {Array(4).fill(0).map((_, i) => <CardSkeleton key={i} />)}
    </div>
  )

  if (!stats || stats.total === 0) return (
    <div>
      {/* Selector de período */}
      <PeriodSelector value={period} onChange={setPeriod} />
      <EmptyState
        icon={<ShoppingBag size={20}/>}
        title="Sin datos en este período"
        description="Prueba seleccionando un período más amplio."
      />
    </div>
  )

  const deliveryRate = stats.total > 0 ? Math.round((stats.delivered / stats.total) * 100) : 0
  const avgTicket    = stats.delivered > 0 ? stats.revenue / stats.delivered : 0

  return (
    <div className="space-y-6 animate-in">

      {/* Selector de período */}
      <PeriodSelector value={period} onChange={setPeriod} />

      {/* KPIs */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard label="Total pedidos" value={stats.total}                    sub={PERIOD_LABELS[period]} />
        <KpiCard label="Ingresos"      value={`$${stats.revenue.toFixed(2)}`} sub="pedidos entregados" trendUp />
        <KpiCard label="Entregados"    value={stats.delivered} trend={`${deliveryRate}%`} trendUp />
        <KpiCard label="Cancelados"    value={stats.cancelled} />
      </div>

      {/* Gráfica de ventas por día */}
      {stats.byDay.length > 1 && (
        <div className="card p-6">
          <SectionHeader title="Ventas por día">
            <div className="flex items-center gap-1 text-xs text-[var(--color-ink-sub)]">
              <Calendar size={13} />
              {PERIOD_LABELS[period]}
            </div>
          </SectionHeader>
          <BarChart data={stats.byDay} />
        </div>
      )}

      {/* Flujo de estados */}
      <div className="card p-6">
        <SectionHeader title="Flujo de pedidos" />
        <div className="flex items-start gap-0 overflow-x-auto pb-2">
          {STATUS_ORDER.map((s, i) => {
            const count = stats.byStatus[s] ?? 0
            const pct   = stats.total > 0 ? Math.round((count / stats.total) * 100) : 0
            const isLast = i === STATUS_ORDER.length - 1
            return (
              <div key={s} className="flex items-center flex-1 min-w-[70px]">
                <div className="flex flex-col items-center flex-1">
                  <div className={`w-10 h-10 rounded-full border-2 flex items-center justify-center mb-1.5 ${
                    count
                      ? 'border-[var(--color-brand-700)] bg-[var(--color-brand-100)]'
                      : 'border-[var(--color-border)] bg-[var(--color-bg)]'
                  }`}>
                    <span className="font-display font-bold text-sm text-[var(--color-ink)]">{count}</span>
                  </div>
                  <span className="text-xs font-semibold text-[var(--color-ink)] text-center leading-tight">
                    {STATUS_STEP_LABEL[s]}
                  </span>
                  <p className="text-xs text-[var(--color-ink-sub)] mt-0.5">{pct}%</p>
                </div>
                {!isLast && (
                  <div className="h-px flex-1 min-w-3 bg-[var(--color-border)] mx-1 mt-[-28px]" />
                )}
              </div>
            )
          })}
        </div>
      </div>

      {/* Top restaurantes */}
      {stats.topRest.length > 0 && (
        <div className="card p-6">
          <SectionHeader title="Top negocios" />
          <div className="space-y-3">
            {stats.topRest.map((r, i) => (
              <div key={r.name} className="flex items-center gap-3 py-2 border-b border-[var(--color-border)] last:border-0">
                <span className="w-5 text-sm font-bold text-[var(--color-ink-sub)]">#{i+1}</span>
                {r.logo ? (
                  <img src={r.logo} alt={r.name} className="w-8 h-8 rounded-full object-cover" />
                ) : (
                  <div className="w-8 h-8 rounded-full bg-[var(--color-brand-700)] flex items-center justify-center text-white text-xs font-bold">
                    {r.name[0]}
                  </div>
                )}
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-[var(--color-ink)] truncate">{r.name}</p>
                  <p className="text-xs text-[var(--color-ink-muted)]">{r.count} pedidos</p>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-[var(--color-ink)]">${r.revenue.toFixed(2)}</p>
                  <div className="mt-1 h-1.5 w-20 bg-[var(--color-border)] rounded-full overflow-hidden">
                    <div
                      className="h-full bg-[var(--color-brand-700)] rounded-full"
                      style={{ width: `${Math.round((r.count / (stats.topRest[0]?.count || 1)) * 100)}%` }}
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Stats rápidas */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="card p-5 flex items-center gap-4">
          <div className="w-10 h-10 rounded-full bg-[var(--color-success-bg)] flex items-center justify-center">
            <CheckCircle size={18} style={{ color: 'var(--color-success-text)' }} />
          </div>
          <div>
            <p className="text-xs text-[var(--color-ink-sub)] font-medium uppercase tracking-wide">Tasa de éxito</p>
            <p className="font-display text-2xl font-bold text-[var(--color-ink)]">{deliveryRate}%</p>
          </div>
        </div>
        <div className="card p-5 flex items-center gap-4">
          <div className="w-10 h-10 rounded-full bg-[var(--color-info-bg)] flex items-center justify-center">
            <DollarSign size={18} style={{ color: 'var(--color-info-text)' }} />
          </div>
          <div>
            <p className="text-xs text-[var(--color-ink-sub)] font-medium uppercase tracking-wide">Ticket promedio</p>
            <p className="font-display text-2xl font-bold text-[var(--color-ink)]">
              {avgTicket > 0 ? `$${avgTicket.toFixed(2)}` : '—'}
            </p>
          </div>
        </div>
        <div className="card p-5 flex items-center gap-4">
          <div className="w-10 h-10 rounded-full bg-[var(--color-brand-100)] flex items-center justify-center">
            <TrendingUp size={18} className="text-[var(--color-brand-700)]" />
          </div>
          <div>
            <p className="text-xs text-[var(--color-ink-sub)] font-medium uppercase tracking-wide">Ingresos totales</p>
            <p className="font-display text-2xl font-bold text-[var(--color-ink)]">${stats.revenue.toFixed(2)}</p>
          </div>
        </div>
      </div>

    </div>
  )
}

function PeriodSelector({ value, onChange }: { value: Period; onChange: (p: Period) => void }) {
  return (
    <div className="flex gap-1 bg-[var(--color-bg)] rounded-[var(--radius-lg)] p-1 w-fit mb-2">
      {(Object.keys(PERIOD_LABELS) as Period[]).map(p => (
        <button
          key={p}
          onClick={() => onChange(p)}
          className={`px-3 py-1.5 rounded-[var(--radius-md)] text-xs font-semibold transition-all ${
            value === p
              ? 'bg-[var(--color-brand-700)] text-white shadow-sm'
              : 'text-[var(--color-ink-muted)] hover:text-[var(--color-ink)]'
          }`}
        >
          {PERIOD_LABELS[p]}
        </button>
      ))}
    </div>
  )
}
