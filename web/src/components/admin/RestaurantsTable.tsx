import { useState, useEffect } from 'react'
import { Search, Star, Store, UserPlus } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import type { Restaurant } from '../../lib/types'
import { StatusBadge, Skeleton, EmptyState, Input, SectionHeader } from '../ui'
import CreateRestaurantModal from './CreateRestaurantModal'
import CreateRestaurantUserModal from './CreateRestaurantUserModal'
import { useToast } from '../common/ToastContext'

export default function RestaurantsTable() {
  const [restaurants, setRestaurants] = useState<Restaurant[]>([])
  const [loading,     setLoading]     = useState(true)
  const [search,      setSearch]      = useState('')
  const [showModal,   setShowModal]   = useState(false)
  const [userModal,   setUserModal]   = useState<Restaurant | null>(null)
  const toast = useToast()

  function load() {
    setLoading(true)
    supabase.from('restaurants').select('*').order('name')
      .then(({ data }) => { setRestaurants((data as Restaurant[]) ?? []); setLoading(false) })
  }

  useEffect(() => { load() }, [])

  async function toggleOpen(r: Restaurant) {
    await supabase.from('restaurants').update({ is_open: !r.is_open }).eq('id', r.id)
    setRestaurants(prev => prev.map(x => x.id === r.id ? { ...x, is_open: !x.is_open } : x))
  }

  const filtered = restaurants.filter(r =>
    r.name.toLowerCase().includes(search.toLowerCase()) ||
    r.category.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="animate-in">
      <SectionHeader title={`Restaurantes${restaurants.length ? ` (${restaurants.length})` : ''}`}>
        <div className="flex items-center gap-3">
          <div className="relative w-full sm:w-56">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-ink-sub)]" />
            <Input value={search} onChange={e => setSearch(e.target.value)}
              placeholder="Buscar…" aria-label="Buscar restaurantes" className="pl-8" />
          </div>
          <button onClick={() => setShowModal(true)} className="btn-primary shrink-0">
            <Store size={15} /> Nuevo
          </button>
        </div>
      </SectionHeader>

      <div className="card overflow-hidden">
        {loading ? (
          <div className="p-5 space-y-3">
            {Array(5).fill(0).map((_, i) => <Skeleton key={i} className="h-12 w-full" />)}
          </div>
        ) : filtered.length === 0 ? (
          <EmptyState icon={<Store size={20}/>} title="Sin restaurantes"
            description="Crea el primer restaurante usando el botón Nuevo."
            action={<button onClick={() => setShowModal(true)} className="btn-primary"><Store size={15}/> Crear restaurante</button>}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] bg-[var(--color-bg)]">
                  {['Restaurante','Categoría','Rating','Estado','Pedidos','Acciones'].map(h => (
                    <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map(r => (
                  <tr key={r.id} className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-bg)] transition-colors">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        {r.logo_url ? (
                          <img src={r.logo_url} alt="" className="w-10 h-10 rounded-[var(--radius-md)] object-cover shrink-0" />
                        ) : (
                          <div className="w-10 h-10 rounded-[var(--radius-md)] bg-[var(--color-brand-100)] text-[var(--color-brand-700)] flex items-center justify-center font-bold shrink-0">
                            {r.name[0]}
                          </div>
                        )}
                        <div>
                          <p className="font-semibold text-[var(--color-ink)]">{r.name}</p>
                          {r.owner_id && (
                            <p className="text-xs text-[var(--color-ink-sub)]">Con usuario vinculado</p>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-[var(--color-ink-muted)]">{r.category}</td>
                    <td className="px-4 py-3">
                      <span className="inline-flex items-center gap-1 font-semibold text-[var(--color-ink)]">
                        <Star size={14} className="text-amber-500" />
                        {r.rating ?? 0}
                      </span>
                    </td>
                    <td className="px-4 py-3"><StatusBadge status={r.is_open ? 'open' : 'closed'} /></td>
                    <td className="px-4 py-3">
                      <label className="inline-flex items-center gap-3 cursor-pointer">
                        <input type="checkbox" checked={r.is_open} onChange={() => toggleOpen(r)}
                          className="sr-only peer" aria-label={`${r.is_open ? 'Pausar' : 'Activar'} ${r.name}`} />
                        <span className="relative w-10 h-6 rounded-full bg-[var(--color-border)] transition-colors peer-checked:bg-[var(--color-brand-700)] after:content-[''] after:absolute after:top-1 after:left-1 after:w-4 after:h-4 after:rounded-full after:bg-white after:transition-transform peer-checked:after:translate-x-4" />
                      </label>
                    </td>
                    <td className="px-4 py-3">
                      <button onClick={() => setUserModal(r)}
                        className="flex items-center gap-1.5 text-xs font-medium text-[var(--color-brand-700)] hover:underline whitespace-nowrap">
                        <UserPlus size={13} />
                        {r.owner_id ? 'Cambiar usuario' : 'Crear usuario'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showModal && (
        <CreateRestaurantModal onClose={() => setShowModal(false)} onSuccess={load} />
      )}

      {userModal && (
        <CreateRestaurantUserModal
          restaurantId={userModal.id}
          restaurantName={userModal.name}
          onClose={() => setUserModal(null)}
          onSuccess={() => { load(); toast.success(`Usuario creado para ${userModal!.name}`) }}
        />
      )}
    </div>
  )
}
