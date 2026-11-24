import { useEffect, useState } from 'react'
import { Star, Store, Bike, MessageSquare } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import { Skeleton, EmptyState, SectionHeader } from '../../components/ui'

interface RestaurantRating {
  restaurant_id: string
  avg_stars: number
  total_ratings: number
  name?: string
}

interface CourierRating {
  courier_id: string
  avg_stars: number
  total_ratings: number
  full_name?: string
}

interface RecentRating {
  id: string
  restaurant_stars: number
  courier_stars: number | null
  restaurant_comment: string | null
  courier_comment: string | null
  created_at: string
  restaurant_id: string
  courier_id: string | null
}

function StarDisplay({ value }: { value: number }) {
  return (
    <span className="inline-flex items-center gap-0.5">
      {[1, 2, 3, 4, 5].map(i => (
        <Star
          key={i}
          size={12}
          className={i <= Math.round(value) ? 'text-amber-400 fill-amber-400' : 'text-gray-300 fill-gray-100'}
        />
      ))}
      <span className="ml-1 text-xs font-bold text-[var(--color-ink)]">{value.toFixed(1)}</span>
    </span>
  )
}

export default function CalificacionesPage() {
  const [restaurants, setRestaurants] = useState<RestaurantRating[]>([])
  const [couriers,    setCouriers]    = useState<CourierRating[]>([])
  const [recents,     setRecents]     = useState<RecentRating[]>([])
  const [loading,     setLoading]     = useState(true)
  const [tab,         setTab]         = useState<'restaurants' | 'couriers' | 'recent'>('restaurants')

  useEffect(() => {
    async function load() {
      // Promedios por restaurante
      const { data: restData } = await supabase
        .from('restaurants_avg_rating')
        .select('*')
        .order('avg_stars', { ascending: false })

      // Enriquecer con nombres de restaurantes
      const restIds = (restData ?? []).map((r: any) => r.restaurant_id)
      const { data: restNames } = restIds.length
        ? await supabase.from('restaurants').select('id,name').in('id', restIds)
        : { data: [] }

      const nameMap: Record<string, string> = {}
      ;(restNames ?? []).forEach((r: any) => { nameMap[r.id] = r.name })

      setRestaurants(
        (restData ?? []).map((r: any) => ({
          ...r,
          avg_stars: parseFloat(r.avg_stars),
          name: nameMap[r.restaurant_id] ?? 'Restaurante',
        }))
      )

      // Promedios por repartidor
      const { data: courierData } = await supabase
        .from('ratings')
        .select('courier_id')
        .not('courier_id', 'is', null)

      if (courierData && courierData.length > 0) {
        const courierIds = [...new Set(courierData.map((r: any) => r.courier_id))]
        const { data: courierRaw } = await supabase
          .from('ratings')
          .select('courier_id, courier_stars')
          .not('courier_stars', 'is', null)
          .in('courier_id', courierIds)

        const { data: courierProfiles } = await supabase
          .from('profiles')
          .select('id,full_name')
          .in('id', courierIds)

        const profileMap: Record<string, string> = {}
        ;(courierProfiles ?? []).forEach((p: any) => { profileMap[p.id] = p.full_name })

        const grouped: Record<string, number[]> = {}
        ;(courierRaw ?? []).forEach((r: any) => {
          if (!grouped[r.courier_id]) grouped[r.courier_id] = []
          grouped[r.courier_id].push(r.courier_stars)
        })

        setCouriers(
          Object.entries(grouped).map(([id, stars]) => ({
            courier_id:    id,
            avg_stars:     stars.reduce((a, b) => a + b, 0) / stars.length,
            total_ratings: stars.length,
            full_name:     profileMap[id] ?? 'Repartidor',
          })).sort((a, b) => b.avg_stars - a.avg_stars)
        )
      }

      // Calificaciones recientes
      const { data: recentData } = await supabase
        .from('ratings')
        .select('*')
        .order('created_at', { ascending: false })
        .limit(20)

      setRecents((recentData ?? []) as RecentRating[])
      setLoading(false)
    }

    load()
  }, [])

  const tabs = [
    { key: 'restaurants' as const, label: 'Negocios', icon: Store },
    { key: 'couriers'    as const, label: 'Repartidores', icon: Bike  },
    { key: 'recent'      as const, label: 'Recientes',    icon: MessageSquare },
  ]

  return (
    <div className="animate-in">
      <SectionHeader
        title="Calificaciones"
        description="Promedio de estrellas por restaurante y repartidor."
      />

      {/* Tabs */}
      <div className="flex gap-1 mb-5 bg-[var(--color-bg)] rounded-[var(--radius-lg)] p-1 w-fit">
        {tabs.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`flex items-center gap-1.5 px-4 py-2 rounded-[var(--radius-md)] text-sm font-medium transition-all ${
              tab === key
                ? 'bg-[var(--color-brand-700)] text-white shadow-sm'
                : 'text-[var(--color-ink-muted)] hover:text-[var(--color-ink)]'
            }`}
          >
            <Icon size={14} />
            {label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="space-y-3">
          {Array(5).fill(0).map((_, i) => <Skeleton key={i} className="h-14 w-full" />)}
        </div>
      ) : tab === 'restaurants' ? (
        <div className="card overflow-hidden">
          {restaurants.length === 0 ? (
            <EmptyState icon={<Star size={20} />} title="Sin calificaciones" description="Aún no hay pedidos calificados." />
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] bg-[var(--color-bg)]">
                  {['Restaurante', 'Promedio', 'Total reseñas'].map(h => (
                    <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {restaurants.map(r => (
                  <tr key={r.restaurant_id} className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-bg)]">
                    <td className="px-4 py-3 font-medium text-[var(--color-ink)]">{r.name}</td>
                    <td className="px-4 py-3"><StarDisplay value={r.avg_stars} /></td>
                    <td className="px-4 py-3 text-[var(--color-ink-muted)]">{r.total_ratings} reseña{r.total_ratings !== 1 ? 's' : ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      ) : tab === 'couriers' ? (
        <div className="card overflow-hidden">
          {couriers.length === 0 ? (
            <EmptyState icon={<Bike size={20} />} title="Sin calificaciones" description="Aún no hay repartidores calificados." />
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] bg-[var(--color-bg)]">
                  {['Repartidor', 'Promedio', 'Total reseñas'].map(h => (
                    <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {couriers.map(c => (
                  <tr key={c.courier_id} className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-bg)]">
                    <td className="px-4 py-3 font-medium text-[var(--color-ink)]">{c.full_name}</td>
                    <td className="px-4 py-3"><StarDisplay value={c.avg_stars} /></td>
                    <td className="px-4 py-3 text-[var(--color-ink-muted)]">{c.total_ratings} reseña{c.total_ratings !== 1 ? 's' : ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          {recents.length === 0 ? (
            <div className="card">
              <EmptyState icon={<MessageSquare size={20} />} title="Sin reseñas" description="Aún no hay calificaciones registradas." />
            </div>
          ) : recents.map(r => (
            <div key={r.id} className="card p-4">
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1 space-y-1.5">
                  <div className="flex items-center gap-3">
                    <Store size={14} className="text-[var(--color-ink-sub)]" />
                    <StarDisplay value={r.restaurant_stars} />
                    {r.restaurant_comment && (
                      <span className="text-xs text-[var(--color-ink-muted)] italic">"{r.restaurant_comment}"</span>
                    )}
                  </div>
                  {r.courier_stars != null && (
                    <div className="flex items-center gap-3">
                      <Bike size={14} className="text-[var(--color-ink-sub)]" />
                      <StarDisplay value={r.courier_stars} />
                      {r.courier_comment && (
                        <span className="text-xs text-[var(--color-ink-muted)] italic">"{r.courier_comment}"</span>
                      )}
                    </div>
                  )}
                </div>
                <span className="text-xs text-[var(--color-ink-sub)] whitespace-nowrap shrink-0">
                  {new Date(r.created_at).toLocaleDateString('es', { day: '2-digit', month: 'short' })}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
