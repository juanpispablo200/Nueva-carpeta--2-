import { useEffect, useState } from 'react'
import { Bike, LogOut, MapPin, Clock, ChevronDown, ChevronUp } from 'lucide-react'
import { supabase, getMyProfile, type Profile } from '../../lib/supabase'
import type { Order, OrderStatus } from '../../lib/types'
import { StatusBadge } from '../../components/ui'

// Estados que el repartidor puede avanzar
const COURIER_NEXT: Partial<Record<OrderStatus, OrderStatus>> = {
  ready:      'on_the_way',
  on_the_way: 'delivered',
}

const COURIER_LABEL: Partial<Record<OrderStatus, string>> = {
  ready:      'Recoger pedido',
  on_the_way: 'Marcar entregado',
}

// Columnas que el repartidor ve
const ACTIVE_STATUSES: OrderStatus[] = ['ready', 'on_the_way']

function ElapsedMins({ createdAt }: { createdAt: string }) {
  const [mins, setMins] = useState(0)
  useEffect(() => {
    const update = () => setMins(Math.floor((Date.now() - new Date(createdAt).getTime()) / 60000))
    update()
    const id = setInterval(update, 30000)
    return () => clearInterval(id)
  }, [createdAt])
  return <span>{mins} min</span>
}

export default function RepartidorPage() {
  const [profile,   setProfile]   = useState<Profile | null>(null)
  const [orders,    setOrders]    = useState<Order[]>([])
  const [delivered, setDelivered] = useState<Order[]>([])
  const [loading,   setLoading]   = useState(true)
  const [busy,      setBusy]      = useState<string | null>(null)
  const [expanded,  setExpanded]  = useState<string | null>(null)
  const [error,     setError]     = useState('')

  // ── Cargar perfil y pedidos ─────────────────────────────────────────────────
  useEffect(() => {
    async function init() {
      const p = await getMyProfile()
      
      // Sin perfil o no es repartidor → ir al login
      if (!p) {
        await supabase.auth.signOut()
        window.location.href = '/login'
        return
      }
      
      // Si es admin o restaurante → redirigir a su panel
      if (p.role === 'admin') { window.location.href = '/admin'; return }
      if (p.role === 'restaurant') { window.location.href = '/restaurant'; return }
      
      // Si no es repartidor → login
      if (p.role !== 'repartidor') {
        window.location.href = '/login'
        return
      }

      setProfile(p)

      // Pedidos activos asignados a este repartidor
      const { data: active } = await supabase
        .from('orders')
        .select('*, items:order_items(*)')
        .eq('courier_id', p.id)
        .in('status', ACTIVE_STATUSES)
        .order('created_at', { ascending: true })

      // Últimas entregas del día (historial)
      const today = new Date().toISOString().slice(0, 10)
      const { data: done } = await supabase
        .from('orders')
        .select('id, restaurant_name, total, status, created_at, delivery_address')
        .eq('courier_id', p.id)
        .eq('status', 'delivered')
        .gte('created_at', today)
        .order('created_at', { ascending: false })
        .limit(10)

      setOrders((active as Order[]) ?? [])
      setDelivered((done as Order[]) ?? [])
      setLoading(false)
    }
    init()

    // Realtime: escuchar cambios en pedidos propios
    let channel: ReturnType<typeof supabase.channel> | null = null
    supabase.auth.getUser().then(({ data: { user } }) => {
      if (!user) return
      channel = supabase
        .channel('courier-orders')
        .on('postgres_changes', {
          event: '*', schema: 'public', table: 'orders',
          filter: `courier_id=eq.${user.id}`
        }, () => init())
        .subscribe()
    })
    return () => { channel?.unsubscribe() }
  }, [])

  async function advance(orderId: string, next: OrderStatus) {
    setBusy(orderId); setError('')
    const { error } = await supabase
      .from('orders')
      .update({ status: next })
      .eq('id', orderId)
    if (error) setError(error.message)
    else setOrders(prev => prev.filter(o => o.id !== orderId))
    setBusy(null)
  }

  async function logout() {
    await supabase.auth.signOut()
    window.location.href = '/login'
  }

  // ── Render ─────────────────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="h-screen flex items-center justify-center bg-[var(--color-bg)]">
        <span className="w-8 h-8 border-2 border-[var(--color-brand-700)] border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-[var(--color-bg)]">
      {/* Header */}
      <header className="bg-[var(--color-surface)] border-b border-[var(--color-border)] px-4 py-3 flex items-center justify-between sticky top-0 z-10">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-[var(--color-brand-100)] text-[var(--color-brand-700)] flex items-center justify-center font-bold">
            {(profile?.full_name?.[0] ?? '?').toUpperCase()}
          </div>
          <div>
            <p className="font-display text-sm font-bold text-[var(--color-ink)] leading-tight">{profile?.full_name}</p>
            <p className="text-xs text-[var(--color-ink-sub)]">Repartidor</p>
          </div>
        </div>
        <button onClick={logout}
          className="flex items-center gap-1.5 text-xs text-[var(--color-ink-muted)] hover:text-[var(--color-ink)] transition-colors">
          <LogOut size={14} /> Salir
        </button>
      </header>

      <main className="max-w-lg mx-auto px-4 py-6 space-y-6">

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-[var(--radius-md)]">
            {error}
          </div>
        )}

        {/* ── Pedidos activos ─────────────────────────────────────────── */}
        <section>
          <div className="flex items-center gap-2 mb-3">
            <Bike size={16} className="text-[var(--color-brand-700)]" />
            <h2 className="font-display font-bold text-[var(--color-ink)]">
              Mis pedidos activos
              {orders.length > 0 && (
                <span className="ml-2 text-xs bg-[var(--color-brand-100)] text-[var(--color-brand-700)] px-2 py-0.5 rounded-full font-semibold">
                  {orders.length}
                </span>
              )}
            </h2>
          </div>

          {orders.length === 0 ? (
            <div className="card p-8 text-center">
              <Bike size={28} className="text-[var(--color-ink-sub)] mx-auto mb-3" />
              <p className="font-semibold text-[var(--color-ink)] mb-1">Sin pedidos activos</p>
              <p className="text-sm text-[var(--color-ink-muted)]">Te avisaremos cuando te asignen uno.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {orders.map(order => {
                const next = COURIER_NEXT[order.status]
                const isOpen = expanded === order.id
                return (
                  <article key={order.id} className="card overflow-hidden">
                    {/* Card header */}
                    <button type="button" onClick={() => setExpanded(isOpen ? null : order.id)}
                      className="w-full flex items-start justify-between gap-3 px-4 pt-4 pb-3 text-left">
                      <div className="space-y-1.5">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-display text-sm font-bold text-[var(--color-ink)]">
                            #{order.id.slice(-6).toUpperCase()}
                          </span>
                          <StatusBadge status={order.status} />
                        </div>
                        <p className="text-sm font-medium text-[var(--color-ink-muted)]">{order.restaurant_name}</p>
                        <div className="flex items-center gap-1 text-xs text-[var(--color-ink-sub)]">
                          <Clock size={11} />
                          <ElapsedMins createdAt={order.created_at} />
                        </div>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <span className="font-display font-bold text-[var(--color-ink)]">${order.total.toFixed(2)}</span>
                        {isOpen ? <ChevronUp size={15} className="text-[var(--color-ink-sub)]" /> : <ChevronDown size={15} className="text-[var(--color-ink-sub)]" />}
                      </div>
                    </button>

                    {/* Dirección */}
                    {order.delivery_address && (
                      <div className="flex items-start gap-2 px-4 pb-3 text-xs text-[var(--color-ink-muted)]">
                        <MapPin size={12} className="mt-0.5 shrink-0 text-[var(--color-ink-sub)]" />
                        <span>{order.delivery_address}</span>
                      </div>
                    )}

                    {/* Detalles expandibles */}
                    {isOpen && order.items && order.items.length > 0 && (
                      <div className="border-t border-[var(--color-border)] px-4 py-3 space-y-1.5">
                        {order.items.map(item => (
                          <div key={item.id} className="flex justify-between text-sm">
                            <span className="text-[var(--color-ink)]">
                              <span className="font-bold text-[var(--color-brand-700)] mr-1">{item.quantity}×</span>
                              {item.dish_name}
                            </span>
                            <span className="text-[var(--color-ink-muted)] shrink-0 ml-2">
                              ${(item.unit_price * item.quantity).toFixed(2)}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}

                    {/* Botón de avance */}
                    {next && (
                      <div className="px-4 pb-4 pt-2">
                        <button
                          onClick={() => advance(order.id, next)}
                          disabled={busy === order.id}
                          className="btn-primary w-full justify-center disabled:opacity-50"
                        >
                          {busy === order.id
                            ? <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                            : COURIER_LABEL[order.status]}
                        </button>
                      </div>
                    )}
                  </article>
                )
              })}
            </div>
          )}
        </section>

        {/* ── Historial del día ───────────────────────────────────────── */}
        {delivered.length > 0 && (
          <section>
            <h2 className="font-display font-bold text-[var(--color-ink)] mb-3">
              Entregas de hoy ({delivered.length})
            </h2>
            <div className="card divide-y divide-[var(--color-border)]">
              {delivered.map(o => (
                <div key={o.id} className="flex items-center justify-between px-4 py-3">
                  <div>
                    <p className="text-sm font-medium text-[var(--color-ink)]">{o.restaurant_name}</p>
                    <p className="text-xs text-[var(--color-ink-sub)] font-mono">#{o.id.slice(-6).toUpperCase()}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-bold text-[var(--color-ink)]">${o.total.toFixed(2)}</p>
                    <StatusBadge status={o.status} />
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}
      </main>
    </div>
  )
}
