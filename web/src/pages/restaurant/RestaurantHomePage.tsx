import { useEffect, useState } from 'react'
import { UtensilsCrossed, BookOpen, Bell } from 'lucide-react'
import { supabase, getMyProfile, type Profile } from '../../lib/supabase'
import type { Restaurant, Order } from '../../lib/types'
import { useOrders }   from '../../hooks/useOrders'
import OrderCard       from '../../components/restaurant/OrderCard'
import MenuEditor      from '../../components/restaurant/MenuEditor'
import { EmptyState } from '../../components/ui'
import AppLayout from '../../components/layout/AppLayout'

type Tab = 'pedidos' | 'menu'

// Columnas Kanban
const COLUMNS: { status: Order['status'][]; label: string; color: string }[] = [
  { status: ['pending'],            label: 'Nuevos',    color: 'border-yellow-400' },
  { status: ['accepted','preparing'],label: 'En cocina', color: 'border-blue-400'   },
  { status: ['ready'],              label: 'Listos',    color: 'border-purple-400'  },
  { status: ['picked_up','on_the_way'],label: 'En camino', color: 'border-violet-400'  },
]

export default function RestaurantHomePage() {
  const [profile,    setProfile]    = useState<Profile | null>(null)
  const [restaurant, setRestaurant] = useState<Restaurant | null>(null)
  const [tab,        setTab]        = useState<Tab>('pedidos')
  const [loading,    setLoading]    = useState(true)

  useEffect(() => {
    async function load() {
      const p = await getMyProfile()
      if (!p) {
        setLoading(false)
        return
      }
      setProfile(p)
      const { data } = await supabase.from('restaurants').select('*').eq('owner_id', p.id).single()
      if (data) setRestaurant(data as Restaurant)
      setLoading(false)
    }
    load()
  }, [])

  const { orders, loading: ordersLoading, updateStatus } = useOrders(restaurant?.id ?? null)
  const pendingCount = orders.filter(o => o.status === 'pending').length

  // Notificación cuando llega pedido nuevo
  useEffect(() => {
    if (pendingCount > 0 && 'Notification' in window && Notification.permission === 'granted') {
      new Notification('Jagoo — Pedido nuevo', { body: `Tienes ${pendingCount} pedido${pendingCount>1?'s':''} esperando` })
    }
  }, [pendingCount])

  async function toggleOpen() {
    if (!restaurant) return
    await supabase.from('restaurants').update({ is_open: !restaurant.is_open }).eq('id', restaurant.id)
    setRestaurant(r => r ? { ...r, is_open: !r.is_open } : r)
  }

  if (loading) {
    return (
      <div className="h-screen flex items-center justify-center bg-[var(--color-bg)]">
        <div className="skeleton w-64 h-48" />
      </div>
    )
  }

  return (
    <AppLayout role="restaurant" userName={restaurant?.name ?? 'Restaurante'}>
      {/* Tabs */}
      <div className="flex gap-2 mb-6">
        <button onClick={() => setTab('pedidos')}
          className={`flex items-center gap-2 px-4 py-2 rounded-[var(--radius-md)] text-sm font-medium transition-all ${
            tab === 'pedidos'
              ? 'bg-[var(--color-brand-700)] text-white'
              : 'bg-[var(--color-surface)] text-[var(--color-ink-muted)] hover:text-[var(--color-ink)] border border-[var(--color-border)]'
          }`}
        >
          <UtensilsCrossed size={16}/>
          Pedidos
          {pendingCount > 0 && (
            <span className="bg-yellow-400 text-yellow-900 text-xs font-bold px-1.5 py-0.5 rounded-[var(--radius-full)] leading-none">
              {pendingCount}
            </span>
          )}
        </button>
        <button onClick={() => setTab('menu')}
          className={`flex items-center gap-2 px-4 py-2 rounded-[var(--radius-md)] text-sm font-medium transition-all ${
            tab === 'menu'
              ? 'bg-[var(--color-brand-700)] text-white'
              : 'bg-[var(--color-surface)] text-[var(--color-ink-muted)] hover:text-[var(--color-ink)] border border-[var(--color-border)]'
          }`}
        >
          <BookOpen size={16}/>
          Menú
        </button>
      </div>

      {/* Estado abierto/cerrado */}
      <div className="card p-4 mb-6 flex items-center justify-between">
        <div>
          <p className="text-sm font-semibold text-[var(--color-ink)] mb-1">Estado del restaurante</p>
          <p className={`text-xs font-bold ${restaurant?.is_open ? 'text-emerald-600' : 'text-red-500'}`}>
            {restaurant?.is_open ? '● Recibiendo pedidos' : '● Pausado'}
          </p>
        </div>
        <label className="relative inline-flex items-center cursor-pointer">
          <input type="checkbox" checked={restaurant?.is_open ?? false}
            onChange={toggleOpen} className="sr-only peer" disabled={!restaurant}/>
          <div className="w-11 h-6 bg-[var(--color-border)] rounded-[var(--radius-full)] peer-checked:bg-[var(--color-brand-700)] transition-colors after:content-[''] after:absolute after:top-1 after:left-1 after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:after:translate-x-5"/>
        </label>
      </div>

      {/* Contenido */}
      {tab === 'pedidos' ? (
        ordersLoading ? (
          <div className="flex items-center justify-center h-64">
            <div className="skeleton w-64 h-48"/>
          </div>
        ) : orders.filter(o => !['cancelled','delivered'].includes(o.status)).length === 0 ? (
          <EmptyState icon={<UtensilsCrossed size={20}/>}
            title="Sin pedidos activos"
            description="Los pedidos nuevos aparecerán aquí en tiempo real."
          />
        ) : (
          /* ── Kanban ─────────────────────────────────────────────── */
          <div className="flex gap-4 overflow-x-auto min-h-[500px] items-start pb-4">
            {COLUMNS.map(col => {
              const colOrders = orders.filter(o => col.status.includes(o.status))
              return (
                <div key={col.label} className={`kanban-col border-t-4 ${col.color} rounded-[var(--radius-lg)] bg-[var(--color-surface)] shadow-[var(--shadow-md)]`}>
                  {/* Col header */}
                  <div className="px-4 pt-3 pb-2 flex items-center justify-between">
                    <p className="font-display font-bold text-sm text-[var(--color-ink)]">{col.label}</p>
                    {colOrders.length > 0 && (
                      <span className="text-xs font-bold bg-[var(--color-bg)] border border-[var(--color-border)] px-2 py-0.5 rounded-[var(--radius-full)] text-[var(--color-ink-muted)]">
                        {colOrders.length}
                      </span>
                    )}
                  </div>
                  {/* Cards */}
                  <div className="px-3 pb-4 space-y-3">
                    {colOrders.length === 0 ? (
                      <p className="text-xs text-center text-[var(--color-ink-sub)] py-6">Vacío</p>
                    ) : colOrders.map(order => (
                      <OrderCard key={order.id} order={order}
                        onAdvance={(id, s) => updateStatus(id, s)}
                        onCancel={id => updateStatus(id, 'cancelled')}
                      />
                    ))}
                  </div>
                </div>
              )
            })}
          </div>
        )
      ) : (
        <div className="max-w-3xl">
          {restaurant ? (
            <MenuEditor restaurantId={restaurant.id} />
          ) : (
            <EmptyState icon={<BookOpen size={20}/>}
              title="Sin restaurante vinculado"
              description="Pide al administrador que vincule tu cuenta a un restaurante."
            />
          )}
        </div>
      )}
    </AppLayout>
  )
}
