import { useEffect, useState } from 'react'
import { X, Bike, AlertCircle } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import type { Profile } from '../../lib/types'
import { Skeleton } from '../ui'

interface Props {
  orderId:          string
  currentCourierId: string | null
  onClose:          () => void
  onSuccess:        (courierId: string, courierName: string) => void
}

export default function AssignCourierModal({ orderId, currentCourierId, onClose, onSuccess }: Props) {
  const [couriers, setCouriers] = useState<Profile[]>([])
  const [selected, setSelected] = useState<string>(currentCourierId ?? '')
  const [loading,  setLoading]  = useState(true)
  const [saving,   setSaving]   = useState(false)
  const [error,    setError]    = useState('')

  useEffect(() => {
    supabase.from('profiles')
      .select('id, full_name, phone, role, is_active')
      .eq('role', 'repartidor')
      .eq('is_active', true)
      .order('full_name')
      .then(({ data }) => { setCouriers((data as Profile[]) ?? []); setLoading(false) })
  }, [])

  async function handleAssign() {
    if (!selected) { setError('Selecciona un repartidor'); return }
    setSaving(true); setError('')
    try {
      const { error: upErr } = await supabase
        .from('orders')
        .update({ courier_id: selected })
        .eq('id', orderId)
      if (upErr) throw upErr

      // Registrar en audit_log
      await supabase.from('audit_log').insert({
        action:    'assign_courier',
        entity:    'orders',
        entity_id: orderId,
        details:   { courier_id: selected, previous_courier_id: currentCourierId },
      })

      const courier = couriers.find(c => c.id === selected)
      onSuccess(selected, courier?.full_name ?? '')
      onClose()
    } catch (err: any) {
      setError(err.message ?? 'Error al asignar')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
      <div className="card w-full max-w-sm p-6 animate-in">
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-[var(--radius-md)] bg-[var(--color-brand-100)] flex items-center justify-center">
              <Bike size={16} className="text-[var(--color-brand-700)]" />
            </div>
            <h2 className="font-display text-lg font-bold text-[var(--color-ink)]">Asignar repartidor</h2>
          </div>
          <button onClick={onClose} className="text-[var(--color-ink-muted)] hover:text-[var(--color-ink)] transition-colors">
            <X size={20} />
          </button>
        </div>

        {loading ? (
          <div className="space-y-2">{Array(3).fill(0).map((_,i) => <Skeleton key={i} className="h-12 w-full" />)}</div>
        ) : couriers.length === 0 ? (
          <p className="text-sm text-[var(--color-ink-muted)] text-center py-6">No hay repartidores activos disponibles.</p>
        ) : (
          <div className="space-y-2 max-h-64 overflow-y-auto">
            {couriers.map(c => (
              <label key={c.id}
                className={`flex items-center gap-3 p-3 rounded-[var(--radius-md)] border cursor-pointer transition-colors ${
                  selected === c.id
                    ? 'border-[var(--color-brand-700)] bg-[var(--color-brand-100)]'
                    : 'border-[var(--color-border)] hover:bg-[var(--color-bg)]'
                }`}
              >
                <input type="radio" name="courier" value={c.id} checked={selected === c.id}
                  onChange={() => setSelected(c.id)} className="sr-only" />
                <div className="w-9 h-9 rounded-full bg-[var(--color-brand-100)] text-[var(--color-brand-700)] flex items-center justify-center text-xs font-bold shrink-0">
                  {(c.full_name?.[0] ?? '?').toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-[var(--color-ink)] truncate">{c.full_name}</p>
                  {c.phone && <p className="text-xs text-[var(--color-ink-muted)]">{c.phone}</p>}
                </div>
                {currentCourierId === c.id && (
                  <span className="text-xs text-[var(--color-brand-700)] font-semibold">Actual</span>
                )}
              </label>
            ))}
          </div>
        )}

        {error && (
          <div className="flex items-start gap-2 bg-red-50 border border-red-200 text-red-700 text-sm rounded-[var(--radius-md)] px-3 py-2.5 mt-3">
            <AlertCircle size={15} className="shrink-0 mt-0.5" /><span>{error}</span>
          </div>
        )}

        <div className="flex gap-2 mt-4">
          <button onClick={onClose} className="btn-ghost flex-1 justify-center" disabled={saving}>Cancelar</button>
          <button onClick={handleAssign} className="btn-primary flex-1 justify-center" disabled={saving || !selected || loading}>
            {saving
              ? <><span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"/>Asignando…</>
              : 'Asignar'}
          </button>
        </div>
      </div>
    </div>
  )
}
