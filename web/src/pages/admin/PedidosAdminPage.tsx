import { useEffect, useState, useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ShoppingBag, Bike, X, ChevronLeft, ChevronRight } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import type { Order, OrderStatus, Restaurant } from '../../lib/types'
import type { Profile } from '../../lib/types'
import { StatusBadge, EmptyState, Skeleton, SectionHeader } from '../../components/ui'
import { STATUS_LABEL } from '../../lib/types'
import AssignCourierModal from '../../components/admin/AssignCourierModal'
import { useToast } from '../../components/common/ToastContext'

const PAGE_SIZE = 20
const ALL_STATUSES: OrderStatus[] = ['pending','accepted','preparing','ready','on_the_way','delivered','cancelled']

interface OrderWithCourier extends Order {
  courier_id:   string | null
  courier_name?: string
}

export default function PedidosAdminPage() {
  const [params, setParams]       = useSearchParams()
  const [orders,       setOrders]       = useState<OrderWithCourier[]>([])
  const [total,        setTotal]        = useState(0)
  const [loading,      setLoading]      = useState(true)
  const [restaurants,  setRestaurants]  = useState<Restaurant[]>([])
  const [couriers,     setCouriers]     = useState<Profile[]>([])
  const [assignOrder,  setAssignOrder]  = useState<OrderWithCourier | null>(null)
  const toast = useToast()

  // ── Filtros desde URL ──────────────────────────────────────────────────────
  const status       = params.get('status')   ?? ''
  const restaurantId = params.get('rest')     ?? ''
  const courierId    = params.get('courier')  ?? ''
  const dateFrom     = params.get('from')     ?? ''
  const dateTo       = params.get('to')       ?? ''
  const search       = params.get('q')        ?? ''
  const page         = parseInt(params.get('page') ?? '0', 10)

  function setFilter(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value); else next.delete(key)
    next.delete('page') // reset paginación al filtrar
    setParams(next)
  }
  function clearFilters() { setParams({}) }

  const hasFilters = !!(status || restaurantId || courierId || dateFrom || dateTo || search)

  // ── Cargar listas secundarias ──────────────────────────────────────────────
  useEffect(() => {
    supabase.from('restaurants').select('id,name').order('name')
      .then(({ data }) => setRestaurants((data as Restaurant[]) ?? []))
    supabase.from('profiles').select('id,full_name,role,is_active,phone').eq('role','repartidor').order('full_name')
      .then(({ data }) => setCouriers((data as Profile[]) ?? []))
  }, [])

  // ── Cargar pedidos ─────────────────────────────────────────────────────────
  const load = useCallback(async () => {
    setLoading(true)
    let q = supabase
      .from('orders')
      .select('*', { count: 'exact' })
      .order('created_at', { ascending: false })
      .range(page * PAGE_SIZE, (page + 1) * PAGE_SIZE - 1)

    if (status)       q = q.eq('status', status)
    if (restaurantId) q = q.eq('restaurant_id', restaurantId)
    if (courierId)    q = q.eq('courier_id', courierId)
    if (dateFrom)     q = q.gte('created_at', dateFrom)
    if (dateTo)       q = q.lte('created_at', dateTo + 'T23:59:59')
    if (search)       q = q.or(`id.ilike.%${search}%,customer_id.ilike.%${search}%,restaurant_name.ilike.%${search}%`)

    const { data, count } = await q
    setOrders((data as OrderWithCourier[]) ?? [])
    setTotal(count ?? 0)
    setLoading(false)
  }, [status, restaurantId, courierId, dateFrom, dateTo, search, page])

  useEffect(() => { load() }, [load])

  const pages = Math.ceil(total / PAGE_SIZE)

  return (
    <div className="animate-in">
      <SectionHeader title={`Pedidos${total ? ` (${total})` : ''}`} />

      {/* ── Filtros ─────────────────────────────────────────────────────── */}
      <div className="card p-4 mb-4">
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          {/* Búsqueda */}
          <input value={search} onChange={e => setFilter('q', e.target.value)}
            placeholder="ID, cliente, restaurante…"
            className="input lg:col-span-2 text-sm" />

          {/* Estado */}
          <select value={status} onChange={e => setFilter('status', e.target.value)} className="input text-sm cursor-pointer">
            <option value="">Todos los estados</option>
            {ALL_STATUSES.map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>

          {/* Restaurante */}
          <select value={restaurantId} onChange={e => setFilter('rest', e.target.value)} className="input text-sm cursor-pointer">
            <option value="">Todos los restaurantes</option>
            {restaurants.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
          </select>

          {/* Repartidor */}
          <select value={courierId} onChange={e => setFilter('courier', e.target.value)} className="input text-sm cursor-pointer">
            <option value="">Todos los repartidores</option>
            {couriers.map(c => <option key={c.id} value={c.id}>{c.full_name}</option>)}
          </select>

          {/* Fecha desde */}
          <input type="date" value={dateFrom} onChange={e => setFilter('from', e.target.value)} className="input text-sm cursor-pointer" />
        </div>

        <div className="flex items-center gap-3 mt-3">
          <input type="date" value={dateTo} onChange={e => setFilter('to', e.target.value)}
            placeholder="Hasta" className="input text-sm w-40 cursor-pointer" />
          {hasFilters && (
            <button onClick={clearFilters}
              className="flex items-center gap-1.5 text-xs font-medium text-[var(--color-ink-muted)] hover:text-[var(--color-ink)] transition-colors">
              <X size={13} /> Limpiar filtros
            </button>
          )}
          <span className="ml-auto text-xs text-[var(--color-ink-sub)]">
            {total} resultado{total !== 1 ? 's' : ''}
          </span>
        </div>
      </div>

      {/* ── Tabla ───────────────────────────────────────────────────────── */}
      <div className="card overflow-hidden">
        {loading ? (
          <div className="p-5 space-y-3">
            {Array(6).fill(0).map((_, i) => <Skeleton key={i} className="h-12 w-full" />)}
          </div>
        ) : orders.length === 0 ? (
          <EmptyState icon={<ShoppingBag size={20} />} title="Sin pedidos"
            description={hasFilters ? 'Ningún pedido coincide con los filtros.' : 'Aún no hay pedidos registrados.'} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] bg-[var(--color-bg)]">
                  {['ID','Restaurante','Total','Estado','Repartidor','Fecha',''].map(h => (
                    <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {orders.map(o => {
                  const courier = couriers.find(c => c.id === o.courier_id)
                  const canAssign = !['delivered','cancelled'].includes(o.status)
                  return (
                    <tr key={o.id} className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-bg)] transition-colors">
                      <td className="px-4 py-3 font-mono text-xs text-[var(--color-ink-sub)]">{o.id.slice(0,8)}…</td>
                      <td className="px-4 py-3 font-medium text-[var(--color-ink)] max-w-[160px] truncate">{o.restaurant_name}</td>
                      <td className="px-4 py-3 font-semibold text-[var(--color-ink)]">${o.total.toFixed(2)}</td>
                      <td className="px-4 py-3"><StatusBadge status={o.status} /></td>
                      <td className="px-4 py-3">
                        {courier ? (
                          <span className="inline-flex items-center gap-1.5 text-xs text-[var(--color-ink-muted)]">
                            <Bike size={12} className="text-[var(--color-brand-700)]" />
                            {courier.full_name}
                          </span>
                        ) : (
                          <span className="text-xs text-[var(--color-ink-sub)]">Sin asignar</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-[var(--color-ink-muted)] text-xs whitespace-nowrap">
                        {new Date(o.created_at).toLocaleDateString('es', { day:'2-digit', month:'short', year:'numeric' })}
                      </td>
                      <td className="px-4 py-3">
                        {canAssign && (
                          <button onClick={() => setAssignOrder(o)}
                            className="flex items-center gap-1 text-xs font-medium text-[var(--color-brand-700)] hover:underline whitespace-nowrap">
                            <Bike size={12} /> {o.courier_id ? 'Reasignar' : 'Asignar'}
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* ── Paginación ─────────────────────────────────────────────────── */}
        {pages > 1 && (
          <div className="flex items-center justify-between px-5 py-3 border-t border-[var(--color-border)]">
            <span className="text-xs text-[var(--color-ink-sub)]">
              {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, total)} de {total}
            </span>
            <div className="flex gap-1">
              <button disabled={page === 0} onClick={() => setFilter('page', String(page - 1))}
                className="btn-ghost p-1.5 disabled:opacity-40"><ChevronLeft size={15} /></button>
              <span className="px-3 py-1 text-xs text-[var(--color-ink-sub)]">Pág. {page + 1} / {pages}</span>
              <button disabled={page >= pages - 1} onClick={() => setFilter('page', String(page + 1))}
                className="btn-ghost p-1.5 disabled:opacity-40"><ChevronRight size={15} /></button>
            </div>
          </div>
        )}
      </div>

      {/* ── Modal asignar repartidor ─────────────────────────────────────── */}
      {assignOrder && (
        <AssignCourierModal
          orderId={assignOrder.id}
          currentCourierId={assignOrder.courier_id}
          onClose={() => setAssignOrder(null)}
          onSuccess={(courierId, courierName) => {
            setOrders(prev => prev.map(o =>
              o.id === assignOrder.id ? { ...o, courier_id: courierId } : o
            ))
            toast.success(`Repartidor ${courierName} asignado`)
            setAssignOrder(null)
          }}
        />
      )}
    </div>
  )
}
