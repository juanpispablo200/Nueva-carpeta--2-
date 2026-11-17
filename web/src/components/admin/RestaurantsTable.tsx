import { useEffect, useState } from 'react'
import { Search, Star, Store } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import type { Restaurant } from '../../lib/types'
import { StatusBadge, Skeleton, EmptyState, Input, SectionHeader } from '../ui'

export default function RestaurantsTable() {
  const [restaurants, setRestaurants] = useState<Restaurant[]>([])
  const [loading,     setLoading]     = useState(true)
  const [search,      setSearch]      = useState('')

  useEffect(() => {
    supabase.from('restaurants').select('*').order('name')
      .then(({ data }) => { setRestaurants((data as Restaurant[]) ?? []); setLoading(false) })
  }, [])

  async function toggleOpen(r: Restaurant) {
    await supabase.from('restaurants').update({ is_open: !r.is_open }).eq('id', r.id)
    setRestaurants(prev => prev.map(x => x.id === r.id ? { ...x, is_open: !x.is_open } : x))
  }

  const filtered = restaurants.filter(r =>
    r.name.toLowerCase().includes(search.toLowerCase()) ||
    r.category.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="animate-fade-in">
      <SectionHeader title={`Restaurantes${restaurants.length ? ` (${restaurants.length})` : ''}`}>
        <div className="relative w-full sm:w-64">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--c-ink-subtle)]" />
          <Input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Buscar…"
            aria-label="Buscar restaurantes por nombre o categoría"
            className="pl-8"
          />
        </div>
      </SectionHeader>

      <div className="card overflow-hidden">
        {loading ? (
          <div className="p-5 space-y-3">
            {Array(5).fill(0).map((_, i) => <Skeleton key={i} className="h-12 w-full" />)}
          </div>
        ) : filtered.length === 0 ? (
          <EmptyState icon={<Store size={20}/>} title="Sin restaurantes" description="No encontramos restaurantes con esa búsqueda." />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Restaurante</th>
                  <th>Categoría</th>
                  <th>Calificación</th>
                  <th>Estado</th>
                  <th>Recibir pedidos</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(r => (
                  <tr key={r.id} className="animate-fade-in">
                    <td>
                      <div className="flex items-center gap-3">
                        {r.logo_url ? (
                          <img src={r.logo_url} alt="" className="w-10 h-10 rounded-[var(--radius-input)] object-cover shrink-0" />
                        ) : (
                          <div className="w-10 h-10 rounded-[var(--radius-input)] bg-[var(--c-success-bg)] text-[var(--c-success-ink)] flex items-center justify-center font-bold shrink-0">
                            {r.name[0]}
                          </div>
                        )}
                        <span className="font-semibold text-[var(--c-ink)]">{r.name}</span>
                      </div>
                    </td>
                    <td className="text-[var(--c-ink-muted)]">{r.category}</td>
                    <td>
                      <span className="inline-flex items-center gap-1 font-semibold text-[var(--c-ink)]">
                        <Star size={14} className="text-amber-500" aria-hidden="true" />{r.rating}
                      </span>
                    </td>
                    <td><StatusBadge status={r.is_open ? 'open' : 'closed'} /></td>
                    <td>
                      <label className="inline-flex items-center gap-3 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={r.is_open}
                          onChange={() => toggleOpen(r)}
                          className="sr-only peer"
                          aria-label={`${r.is_open ? 'Pausar' : 'Activar'} pedidos para ${r.name}`}
                        />
                        <span className="relative w-10 h-6 rounded-full bg-[var(--c-border)] transition-colors peer-checked:bg-[var(--c-brand)] peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-[var(--c-brand)] after:content-[''] after:absolute after:top-1 after:left-1 after:w-4 after:h-4 after:rounded-full after:bg-white after:transition-transform peer-checked:after:translate-x-4" />
                      </label>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
