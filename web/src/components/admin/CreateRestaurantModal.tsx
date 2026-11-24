import { useState } from 'react'
import { X, Store, AlertCircle } from 'lucide-react'
import { supabase } from '../../lib/supabase'

interface Props {
  onClose:   () => void
  onSuccess: () => void
}

export default function CreateRestaurantModal({ onClose, onSuccess }: Props) {
  const [form, setForm] = useState({
    name:          '',
    category:      '',
    business_type: 'restaurante',
    permit_number: '',
    description:   '',
    delivery_time: '30-45 min',
    delivery_fee:  2.50,
    min_order:     5.00,
  })
  const [loading, setLoading] = useState(false)
  const [error,   setError]   = useState('')

  function set(k: string, v: any) { setForm(f => ({ ...f, [k]: v })) }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!form.name.trim() || !form.category.trim()) {
      setError('Nombre y categoría son obligatorios'); return
    }
    setError(''); setLoading(true)
    try {
      const { data: { user } } = await supabase.auth.getUser()
      const { error } = await supabase.from('restaurants').insert({
        name:          form.name.trim(),
        category:      form.category.trim(),
        business_type: form.business_type,
        permit_number: form.permit_number.trim() || null,
        description:   form.description.trim(),
        delivery_time: form.delivery_time,
        delivery_fee:  form.delivery_fee,
        min_order:     form.min_order,
        is_open:       true,
        is_featured:   false,
        rating:        0,
        review_count:  0,
        owner_id:      user?.id ?? null,
      })
      if (error) throw error
      onSuccess(); onClose()
    } catch (err: any) {
      setError(err.message ?? 'Error al crear restaurante')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
      <div className="card w-full max-w-md p-6 animate-in">
        {/* Header */}
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-[var(--radius-md)] bg-[var(--color-brand-100)] flex items-center justify-center">
              <Store size={16} className="text-[var(--color-brand-700)]" />
            </div>
            <h2 className="font-display text-lg font-bold text-[var(--color-ink)]">Nuevo restaurante</h2>
          </div>
          <button onClick={onClose} className="text-[var(--color-ink-muted)] hover:text-[var(--color-ink)] transition-colors">
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-3">
          {/* Tipo de negocio */}
          <div>
            <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
              Tipo de negocio *
            </label>
            <select value={form.business_type} onChange={e => set('business_type', e.target.value)} className="input cursor-pointer">
              <option value="restaurante">Restaurante</option>
              <option value="tienda">Tienda</option>
              <option value="farmacia">Farmacia</option>
              <option value="licoreria">Licorería (venta de alcohol, solo +18)</option>
            </select>
          </div>
          {/* Número de permiso (opcional) */}
          <div>
            <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
              Número de permiso (opcional)
            </label>
            <input value={form.permit_number} onChange={e => set('permit_number', e.target.value)}
              placeholder="Permiso de funcionamiento / sanitario" className="input" />
          </div>
          {/* Nombre */}
          <div>
            <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
              Nombre del restaurante *
            </label>
            <input required value={form.name} onChange={e => set('name', e.target.value)}
              placeholder="Ej: Burger Master" className="input" />
          </div>

          {/* Categoría */}
          <div>
            <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
              Categoría *
            </label>
            <select value={form.category} onChange={e => set('category', e.target.value)}
              className="input cursor-pointer">
              <option value="">Seleccionar categoría…</option>
              <option>Hamburguesas</option>
              <option>Pizza</option>
              <option>Sushi</option>
              <option>Tacos</option>
              <option>Pollo</option>
              <option>Mariscos</option>
              <option>Ensaladas</option>
              <option>Postres</option>
              <option>Bebidas</option>
              <option>Comida rápida</option>
              <option>Comida ecuatoriana</option>
              <option>Italiana</option>
              <option>China</option>
              <option>Otro</option>
            </select>
          </div>

          {/* Descripción */}
          <div>
            <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
              Descripción
            </label>
            <textarea value={form.description} onChange={e => set('description', e.target.value)}
              placeholder="Breve descripción del restaurante…" rows={2}
              className="w-full border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-ink)] text-sm rounded-[var(--radius-input)] px-3 py-2 placeholder:text-[var(--color-ink-subtle)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-700)] resize-none" />
          </div>

          {/* Fila: delivery time + fee */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
                Tiempo de entrega
              </label>
              <input value={form.delivery_time} onChange={e => set('delivery_time', e.target.value)}
                placeholder="30-45 min" className="input" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
                Costo de envío ($)
              </label>
              <input type="number" min="0" step="0.01" value={form.delivery_fee}
                onChange={e => set('delivery_fee', parseFloat(e.target.value) || 0)} className="input" />
            </div>
          </div>

          {/* Pedido mínimo */}
          <div>
            <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
              Pedido mínimo ($)
            </label>
            <input type="number" min="0" step="0.01" value={form.min_order}
              onChange={e => set('min_order', parseFloat(e.target.value) || 0)} className="input" />
          </div>

          {error && (
            <div className="flex items-start gap-2 bg-red-50 border border-red-200 text-red-700 text-sm rounded-[var(--radius-md)] px-3 py-2.5">
              <AlertCircle size={15} className="shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <div className="flex gap-2 pt-2">
            <button type="button" onClick={onClose} className="btn-ghost flex-1 justify-center" disabled={loading}>
              Cancelar
            </button>
            <button type="submit" className="btn-primary flex-1 justify-center" disabled={loading}>
              {loading ? (
                <span className="flex items-center gap-2">
                  <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  Creando…
                </span>
              ) : 'Crear restaurante'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
