import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { UtensilsCrossed, BookOpen, LogOut, TrendingUp, Bell } from 'lucide-react'
import { supabase, getMyProfile } from '../../lib/supabase'
import type { Profile, Restaurant, Order } from '../../lib/types'
import { useOrders }   from '../../hooks/useOrders'
import OrderCard       from '../../components/restaurant/OrderCard'
import MenuEditor      from '../../components/restaurant/MenuEditor'
import { EmptyState, AppShell } from '../../components/ui'

type Tab = 'pedidos' | 'menu'

// Columnas Kanban
const COLUMNS: { status: Order['status'][]; label: string; color: string }[] = [
  { status: ['pending'],            label: 'Nuevos',    color: 'border-yellow-400' },
  { status: ['accepted','preparing'],label: 'En cocina', color: 'border-blue-400'   },
  { status: ['on_the_way'],         label: 'En camino', color: 'border-violet-400'  },
  { status: ['delivered'],          label: 'Entregados', color: 'border-emerald-400' },
]

export default function RestaurantHomePage() {
  const navigate = useNavigate()
  const [profile,    setProfile]    = useState<Profile | null>(null)
  const [restaurant, setRestaurant] = useState<Restaurant | null>(null)
  const [tab,        setTab]        = useState<Tab>('pedidos')

  useEffect(() => {
    async function load() {
      const p = await getMyProfile()
      if (!p) return
      setProfile(p as Profile)
      const { data } = await supabase.from('restaurants').select('*').eq('owner_id', p.id).single()
      if (data) setRestaurant(data as Restaurant)
    }
    load()
  }, [])

  const { orders, loading, updateStatus } = useOrders(restaurant?.id ?? null)
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

  async function handleLogout() {
    await supabase.auth.signOut()
    navigate('/login', { replace: true })
  }

  const NAV: { key: Tab; label: string; icon: typeof UtensilsCrossed }[] = [
    { key: 'pedidos', label: 'Pedidos', icon: UtensilsCrossed },
    { key: 'menu',    label: 'Menú',    icon: BookOpen         },
  ]

  return (
    <AppShell
      sidebar={
        <>
          {/* Logo */}
          <div className="px-5 py-5 border-b border-[var(--c-border)]">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-[var(--c-brand)] flex items-center justify-center">
                <TrendingUp size={16} color="#fff" strokeWidth={2.5}/>
              </div>
              <div>
                <p className="font-display font-bold text-sm text-[var(--c-ink)] leading-tight truncate max-w-[120px]">
                  {restaurant?.name ?? 'Mi Restaurante'}
                </p>
                <p className="text-[11px] text-[var(--c-ink-subtle)]">Restaurante</p>
              </div>
            </div>
          </div>

          {/* Nav */}
          <nav className="flex-1 px-3 py-4 space-y-0.5">
            {NAV.map(({ key, label, icon: Icon }) => (
              <button key={key} onClick={() => setTab(key)}
                className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-[var(--radius-input)] text-sm font-medium transition-colors
                  ${tab === key ? 'bg-[var(--c-brand)] text-white' : 'text-[var(--c-ink-muted)] hover:bg-[var(--c-bg)] hover:text-[var(--c-ink)]'}`}
              >
                <Icon size={16} strokeWidth={2}/>
                {label}
                {key === 'pedidos' && pendingCount > 0 && (
                  <span className="ml-auto bg-yellow-400 text-yellow-900 text-xs font-bold px-1.5 py-0.5 rounded-pill leading-none">
                    {pendingCount}
                  </span>
                )}
              </button>
            ))}
          </nav>

          {/* Estado del local */}
          <div className="px-4 py-3 border-t border-[var(--c-border)]">
            <div className="flex items-center justify-between mb-3 px-1">
              <p className="text-xs font-semibold text-[var(--c-ink-muted)]">Estado</p>
              <label className="relative inline-flex items-center cursor-pointer">
                <input type="checkbox" checked={restaurant?.is_open ?? false}
                  onChange={toggleOpen} className="sr-only peer" disabled={!restaurant}/>
                <div className="w-9 h-5 bg-[var(--c-border)] rounded-full peer-checked:bg-[var(--c-brand)] transition-colors after:content-[''] after:absolute after:top-0.5 after:left-0.5 after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:after:translate-x-4"/>
              </label>
            </div>
            <p className={`text-xs font-bold px-1 ${restaurant?.is_open ? 'text-emerald-600' : 'text-red-500'}`}>
              {restaurant?.is_open ? '● Recibiendo pedidos' : '● Pausado'}
            </p>
          </div>

          {/* User */}
          <div className="px-3 py-3 border-t border-[var(--c-border)]">
            <button onClick={handleLogout}
              className="w-full flex items-center gap-2 px-3 py-2 text-xs text-[var(--c-ink-muted)] hover:text-red-600 hover:bg-red-50 rounded-[var(--radius-input)] transition-colors">
              <LogOut size={14} strokeWidth={2}/>
              Cerrar sesión
            </button>
          </div>
        </>
      }
    >
      {/* Header */}
      <header className="sticky top-0 z-10 bg-[var(--c-surface)]/90 backdrop-blur border-b border-[var(--c-border)] px-6 py-4 flex items-center justify-between">
        <h1 className="font-display text-xl font-bold text-[var(--c-ink)]">
          {tab === 'pedidos' ? 'Pedidos' : 'Menú'}
        </h1>
        {tab === 'pedidos' && pendingCount > 0 && (
          <div className="flex items-center gap-2 text-sm text-yellow-700 bg-yellow-50 border border-yellow-200 px-3 py-1.5 rounded-pill">
            <Bell size={14}/>
            {pendingCount} nuevo{pendingCount > 1 ? 's' : ''}
          </div>
        )}
      </header>

      {/* Contenido */}
      {tab === 'pedidos' ? (
        loading ? (
          <div className="flex items-center justify-center h-64">
            <div className="w-8 h-8 border-4 border-[var(--c-brand)] border-t-transparent rounded-full animate-spin"/>
          </div>
        ) : orders.filter(o => !['cancelled'].includes(o.status)).length === 0 ? (
          <div className="p-8">
            <EmptyState icon={<UtensilsCrossed size={20}/>}
              title="Sin pedidos activos"
              description="Los pedidos nuevos aparecerán aquí en tiempo real."
            />
          </div>
        ) : (
          /* ── Kanban ─────────────────────────────────────────────── */
          <div className="flex gap-4 p-6 overflow-x-auto min-h-[calc(100vh-80px)] items-start">
            {COLUMNS.map(col => {
              const colOrders = orders.filter(o => col.status.includes(o.status))
              return (
                <div key={col.label} className={`kanban-col flex-shrink-0 border-t-4 ${col.color} rounded-card bg-[var(--c-bg)]`}>
                  {/* Col header */}
                  <div className="px-4 pt-3 pb-2 flex items-center justify-between">
                    <p className="font-display font-bold text-sm text-[var(--c-ink)]">{col.label}</p>
                    {colOrders.length > 0 && (
                      <span className="text-xs font-bold bg-[var(--c-surface)] border border-[var(--c-border)] px-2 py-0.5 rounded-pill text-[var(--c-ink-muted)]">
                        {colOrders.length}
                      </span>
                    )}
                  </div>
                  {/* Cards */}
                  <div className="px-3 pb-4 space-y-3">
                    {colOrders.length === 0 ? (
                      <p className="text-xs text-center text-[var(--c-ink-subtle)] py-6">Vacío</p>
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
        <div className="p-6 max-w-3xl">
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
    </AppShell>
  )
}
