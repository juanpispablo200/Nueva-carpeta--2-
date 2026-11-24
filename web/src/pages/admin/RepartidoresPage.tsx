import { useEffect, useState } from 'react'
import { Bike, UserPlus, Power, PowerOff } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import type { Profile } from '../../lib/supabase'
import { EmptyState, Skeleton, StatusBadge } from '../../components/ui'
import CreateCourierModal from '../../components/admin/CreateCourierModal'
import { useToast } from '../../components/common/ToastContext'

export default function RepartidoresPage() {
  const [repartidores, setRepartidores] = useState<Profile[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [showModal, setShowModal] = useState(false)
  const [toggling, setToggling] = useState<string | null>(null)
  const toast = useToast()

  async function loadRepartidores() {
    setLoading(true)
    setLoadError(null)
    try {
      const { data, error } = await supabase
        .from('profiles')
        .select('*')
        .eq('role', 'repartidor')
        .order('full_name')
      if (error) throw error
      setRepartidores((data as Profile[]) ?? [])
    } catch {
      setLoadError('No se pudo cargar la lista. Verifica tu conexión.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadRepartidores() }, [])

  async function toggleActive(id: string, currentActive: boolean) {
    setToggling(id)
    try {
      const { error } = await supabase
        .from('profiles')
        .update({ is_active: !currentActive })
        .eq('id', id)

      if (error) throw error

      setRepartidores(prev =>
        prev.map(r => (r.id === id ? { ...r, is_active: !currentActive } : r))
      )
      toast.success(currentActive ? 'Repartidor desactivado' : 'Repartidor activado')
    } catch (error) {
      toast.error('Error al cambiar estado')
    } finally {
      setToggling(null)
    }
  }

  return (
    <div className="animate-in">
      <header className="mb-6 flex items-start justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-[var(--color-ink-sub)] mb-1">Admin</p>
          <h1 className="font-display text-2xl font-bold text-[var(--color-ink)]">
            Repartidores {repartidores.length > 0 && `(${repartidores.length})`}
          </h1>
        </div>
        <button onClick={() => setShowModal(true)} className="btn-primary">
          <UserPlus size={16} />
          Crear repartidor
        </button>
      </header>

      <div className="card overflow-hidden">
        {loadError ? (
          <div className="p-8 text-center space-y-3">
            <p className="text-[var(--color-ink-muted)]">{loadError}</p>
            <button onClick={loadRepartidores} className="btn-primary mx-auto">
              Reintentar
            </button>
          </div>
        ) : loading ? (
          <div className="p-5 space-y-3">
            {Array(4).fill(0).map((_, i) => <Skeleton key={i} className="h-12 w-full" />)}
          </div>
        ) : repartidores.length === 0 ? (
          <EmptyState
            icon={<Bike size={20} />}
            title="Sin repartidores"
            description="Crea el primer repartidor usando el botón de arriba."
            action={
              <button onClick={() => setShowModal(true)} className="btn-primary">
                <UserPlus size={16} />
                Crear repartidor
              </button>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] bg-[var(--color-bg)]">
                  <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">
                    Nombre
                  </th>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">
                    Teléfono
                  </th>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">
                    Estado
                  </th>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">
                    Activar/Desactivar
                  </th>
                </tr>
              </thead>
              <tbody>
                {repartidores.map(r => (
                  <tr
                    key={r.id}
                    className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-bg)] transition-colors"
                  >
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-full bg-[var(--color-brand-100)] text-[var(--color-brand-700)] flex items-center justify-center text-xs font-bold shrink-0">
                          {(r.full_name?.[0] ?? '?').toUpperCase()}
                        </div>
                        <span className="font-medium text-[var(--color-ink)]">{r.full_name || '—'}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-[var(--color-ink-muted)]">{r.phone || '—'}</td>
                    <td className="px-4 py-3">
                      <StatusBadge status={r.is_active ? 'open' : 'closed'} />
                    </td>
                    <td className="px-4 py-3">
                      <button
                        onClick={() => toggleActive(r.id, r.is_active)}
                        disabled={toggling === r.id}
                        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-[var(--radius-md)] text-xs font-medium transition-colors ${
                          r.is_active
                            ? 'bg-red-50 text-red-700 hover:bg-red-100'
                            : 'bg-green-50 text-green-700 hover:bg-green-100'
                        } disabled:opacity-50`}
                      >
                        {toggling === r.id ? (
                          <span className="w-3 h-3 border-2 border-current border-t-transparent rounded-full animate-spin" />
                        ) : r.is_active ? (
                          <PowerOff size={14} />
                        ) : (
                          <Power size={14} />
                        )}
                        {r.is_active ? 'Desactivar' : 'Activar'}
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
        <CreateCourierModal
          onClose={() => setShowModal(false)}
          onSuccess={() => {
            loadRepartidores()
            toast.success('Repartidor creado exitosamente')
          }}
        />
      )}
    </div>
  )
}
