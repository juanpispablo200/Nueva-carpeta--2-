import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Clock, Star, CreditCard, Bike } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import ReportsPanel from '../../components/admin/ReportsPanel'

interface QuickStats {
  pendingPayments:  number
  avgRating:        number
  activeDrivers:    number
  ordersToday:      number
}

function QuickStatCard({
  icon, label, value, color, onClick,
}: {
  icon: React.ReactNode
  label: string
  value: string | number
  color: string
  onClick?: () => void
}) {
  return (
    <div
      onClick={onClick}
      className={`card p-4 flex items-center gap-3 ${onClick ? 'cursor-pointer hover:shadow-md transition-shadow' : ''}`}
    >
      <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${color}`}>
        {icon}
      </div>
      <div>
        <p className="text-xs text-[var(--color-ink-sub)] font-medium">{label}</p>
        <p className="font-display text-xl font-bold text-[var(--color-ink)]">{value}</p>
      </div>
    </div>
  )
}

export default function ResumenPage() {
  const navigate = useNavigate()
  const [quick, setQuick] = useState<QuickStats | null>(null)

  useEffect(() => {
    async function load() {
      const todayStart = new Date(); todayStart.setHours(0,0,0,0)

      const [paymentsRes, ratingsRes, driversRes, ordersRes] = await Promise.all([
        supabase.from('payment_proofs').select('id', { count: 'exact', head: true }).eq('status', 'pending'),
        supabase.from('ratings').select('restaurant_stars'),
        supabase.from('profiles').select('id', { count: 'exact', head: true }).eq('role', 'repartidor').eq('is_available', true),
        supabase.from('orders').select('id', { count: 'exact', head: true }).gte('created_at', todayStart.toISOString()),
      ])

      const stars = (ratingsRes.data ?? []) as { restaurant_stars: number }[]
      const avg = stars.length > 0
        ? stars.reduce((s, r) => s + r.restaurant_stars, 0) / stars.length
        : 0

      setQuick({
        pendingPayments: paymentsRes.count ?? 0,
        avgRating:       Math.round(avg * 10) / 10,
        activeDrivers:   driversRes.count ?? 0,
        ordersToday:     ordersRes.count ?? 0,
      })
    }
    load()
  }, [])

  return (
    <div className="animate-in space-y-6">
      <header>
        <p className="text-xs font-semibold uppercase tracking-widest text-[var(--color-ink-sub)] mb-1">Admin</p>
        <h1 className="font-display text-2xl font-bold text-[var(--color-ink)]">Resumen</h1>
      </header>

      {/* Métricas rápidas */}
      {quick && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          <QuickStatCard
            icon={<Clock size={18} className="text-amber-600" />}
            label="Pagos pendientes"
            value={quick.pendingPayments}
            color="bg-amber-100"
            onClick={() => navigate('/admin/pagos')}
          />
          <QuickStatCard
            icon={<Star size={18} className="text-yellow-500" />}
            label="Rating promedio"
            value={quick.avgRating > 0 ? `${quick.avgRating} ★` : '—'}
            color="bg-yellow-100"
            onClick={() => navigate('/admin/calificaciones')}
          />
          <QuickStatCard
            icon={<Bike size={18} className="text-green-600" />}
            label="Repartidores activos"
            value={quick.activeDrivers}
            color="bg-green-100"
            onClick={() => navigate('/admin/repartidores')}
          />
          <QuickStatCard
            icon={<CreditCard size={18} className="text-blue-600" />}
            label="Pedidos hoy"
            value={quick.ordersToday}
            color="bg-blue-100"
            onClick={() => navigate('/admin/pedidos')}
          />
        </div>
      )}

      {/* Panel de reportes completo */}
      <ReportsPanel />
    </div>
  )
}
