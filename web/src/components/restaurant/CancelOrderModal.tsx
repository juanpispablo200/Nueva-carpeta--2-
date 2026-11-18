import { useState } from 'react'
import { X, AlertTriangle } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import { useToast } from '../common/ToastContext'

interface Props {
  orderId: string
  onClose: () => void
  onSuccess: () => void
}

export default function CancelOrderModal({ orderId, onClose, onSuccess }: Props) {
  const [reason, setReason] = useState('')
  const [loading, setLoading] = useState(false)
  const toast = useToast()

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!reason.trim()) {
      toast.error('Debes ingresar un motivo')
      return
    }

    setLoading(true)
    try {
      // 1. Cambiar estado del pedido a cancelled
      const { error: orderError } = await supabase
        .from('orders')
        .update({ status: 'cancelled' })
        .eq('id', orderId)

      if (orderError) throw orderError

      // 2. Registrar en order_events con motivo
      const { error: eventError } = await supabase
        .from('order_events')
        .insert({
          order_id: orderId,
          from_status: null,
          to_status: 'cancelled',
          actor_id: (await supabase.auth.getUser()).data.user?.id,
          actor_role: 'restaurant',
          reason: reason.trim(),
        })

      if (eventError) throw eventError

      toast.success('Pedido cancelado')
      onSuccess()
      onClose()
    } catch (error) {
      toast.error('Error al cancelar el pedido')
      console.error(error)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-in">
      <div className="card w-full max-w-md p-6 animate-in">
        {/* Header */}
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-[var(--radius-md)] bg-red-100 flex items-center justify-center">
              <AlertTriangle size={16} className="text-red-600" />
            </div>
            <h2 className="font-display text-lg font-bold text-[var(--color-ink)]">
              Cancelar pedido
            </h2>
          </div>
          <button
            onClick={onClose}
            className="text-[var(--color-ink-muted)] hover:text-[var(--color-ink)] transition-colors"
            aria-label="Cerrar"
          >
            <X size={20} />
          </button>
        </div>

        <p className="text-sm text-[var(--color-ink-muted)] mb-4">
          Esta acción cancelará el pedido y notificará al cliente.
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide mb-1.5">
              Motivo de cancelación
            </label>
            <textarea
              required
              value={reason}
              onChange={e => setReason(e.target.value)}
              placeholder="Ej: Ingredientes agotados, restaurante cerrado, etc."
              className="input min-h-[80px] resize-none"
              maxLength={200}
            />
            <p className="text-xs text-[var(--color-ink-sub)] mt-1">
              {reason.length}/200 caracteres
            </p>
          </div>

          <div className="flex gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="btn-ghost flex-1 justify-center"
              disabled={loading}
            >
              Volver
            </button>
            <button
              type="submit"
              className="btn-danger flex-1 justify-center"
              disabled={loading || !reason.trim()}
            >
              {loading ? (
                <span className="flex items-center gap-2">
                  <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  Cancelando…
                </span>
              ) : (
                'Confirmar cancelación'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
