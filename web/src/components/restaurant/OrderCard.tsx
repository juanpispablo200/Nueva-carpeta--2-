import { useState, useEffect } from 'react'
import { Clock, MapPin, ChevronDown, ChevronUp, CheckCircle, XCircle, Bike } from 'lucide-react'
import type { Order } from '../../lib/types'
import { NEXT_STATUS } from '../../lib/types'
import { StatusBadge } from '../ui'
import PrescriptionReview from './PrescriptionReview'

interface Props {
  order:     Order
  onAdvance: (id: string, s: Order['status']) => Promise<void>
  onCancel:  (id: string) => Promise<void>
}

function useElapsed(createdAt: string) {
  const [mins, setMins] = useState(0)
  useEffect(() => {
    const update = () => setMins(Math.floor((Date.now() - new Date(createdAt).getTime()) / 60000))
    update()
    const id = setInterval(update, 30000)
    return () => clearInterval(id)
  }, [createdAt])
  return mins
}

const ADVANCE_LABEL: Partial<Record<Order['status'], string>> = {
  pending:   'Aceptar pedido',
  accepted:  'Iniciar preparación',
  preparing: 'Marcar como listo',
  on_the_way:'Marcar entregado',
}

export default function OrderCard({ order, onAdvance, onCancel }: Props) {
  const [busy,     setBusy]     = useState(false)
  const [expanded, setExpanded] = useState(order.status === 'pending')
  const mins = useElapsed(order.created_at)
  const next = NEXT_STATUS[order.status]

  async function advance() {
    if (!next) return
    setBusy(true); try { await onAdvance(order.id, next) } finally { setBusy(false) }
  }
  async function cancel() {
    setBusy(true); try { await onCancel(order.id) } finally { setBusy(false) }
  }

  const time = new Date(order.created_at).toLocaleTimeString('es-EC', { hour:'2-digit', minute:'2-digit' })
  const isUrgent = order.status === 'pending' && mins > 5
  const done = ['delivered','cancelled'].includes(order.status)

  return (
    <article className={`card overflow-hidden transition-shadow ${isUrgent ? 'ring-2 ring-amber-400' : ''}`}>
      {/* Header */}
      <button
        type="button"
        onClick={() => setExpanded(e => !e)}
        aria-expanded={expanded}
        className="flex w-full items-start justify-between gap-3 px-4 pt-4 pb-3 text-left"
      >
        <div className="min-w-0 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-display text-sm font-bold text-[var(--c-ink)]">#{order.id.slice(-6).toUpperCase()}</p>
            <StatusBadge status={order.status} />
            {isUrgent && <span className="text-xs font-semibold text-[var(--c-warning-ink)]">Esperando respuesta</span>}
          </div>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-[var(--c-ink-muted)]">
            <span className="inline-flex items-center gap-1"><Clock size={12}/>{time}</span>
            <span className={`inline-flex items-center gap-1 font-semibold ${isUrgent ? 'text-[var(--c-warning-ink)]' : 'text-[var(--c-ink-muted)]'}`}><Clock size={12}/>{mins} min</span>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <p className="font-display text-base font-bold text-[var(--c-ink)]">${order.total.toFixed(2)}</p>
          {expanded ? <ChevronUp size={15} className="text-[var(--c-ink-subtle)]"/> : <ChevronDown size={15} className="text-[var(--c-ink-subtle)]"/>}
        </div>
      </button>

      {/* Dirección */}
      {order.delivery_address && (
        <div className="flex items-start gap-2 px-4 pb-3 text-xs text-[var(--c-ink-muted)]">
          <MapPin size={13} className="mt-0.5 shrink-0 text-[var(--c-ink-subtle)]"/>
          <span>{order.delivery_address}</span>
        </div>
      )}

      {/* Detalles expandibles */}
      {expanded && (
        <div className="mt-1 space-y-2 border-t border-[var(--c-border)] px-4 pt-3 pb-4 animate-fade-in">
          {order.items && order.items.length > 0 ? order.items.map(item => (
            <div key={item.id} className="flex justify-between text-sm">
              <span className="text-[var(--c-ink)]">
              <span className="mr-1 font-bold text-[var(--c-brand-ink)]">{item.quantity}×</span>{item.dish_name}
                {item.special_instructions && (
                <span className="mt-1 block text-xs text-[var(--c-ink-muted)]">Nota: {item.special_instructions}</span>
                )}
              </span>
              <span className="ml-2 shrink-0 text-[var(--c-ink-muted)]">${(item.unit_price * item.quantity).toFixed(2)}</span>
            </div>
          )) : (
            <p className="text-xs text-[var(--c-ink-subtle)]">Sin detalle de ítems</p>
          )}

          <div className="flex justify-between border-t border-[var(--c-border)] pt-2 text-xs text-[var(--c-ink-muted)]">
            <span>Envío</span><span>${order.delivery_fee.toFixed(2)}</span>
          </div>
          <div className="flex justify-between text-sm font-bold text-[var(--c-ink)]">
            <span>Total</span><span>${order.total.toFixed(2)}</span>
          </div>
        </div>
      )}

      {/* Receta (farmacia): sin receta aprobada el pedido no avanza */}
      {order.prescription_required && order.prescription_status === 'pending' && (
        <PrescriptionReview orderId={order.id} path={order.prescription_path ?? null} />
      )}
      {order.prescription_required && order.prescription_status === 'approved' && (
        <p className="px-4 pb-2 text-xs font-semibold text-green-700" role="status">Receta aprobada</p>
      )}
      {order.age_check_required && (
        <p className="px-4 pb-2 text-xs font-semibold text-amber-700">Incluye productos +18: el repartidor verificará la cédula.</p>
      )}
      {order.payment_method === 'cash' && order.change_due != null && (
        <p className="px-4 pb-2 text-xs text-[var(--c-ink-muted)]">Efectivo: paga con ${(order.total + order.change_due).toFixed(2)} (cambio ${order.change_due.toFixed(2)})</p>
      )}
      {/* Acciones */}
      {!done && (
        <div className="mt-1 flex gap-2 px-4 pb-4">
          {next && (
            <button
              disabled={busy}
              onClick={advance}
              aria-label={ADVANCE_LABEL[order.status] ?? 'Avanzar pedido'}
              className="btn-primary flex-1 justify-center text-xs py-2 disabled:opacity-50"
            >
              {next === 'on_the_way' ? <Bike size={13}/> : <CheckCircle size={13}/>}
              {ADVANCE_LABEL[order.status] ?? 'Avanzar'}
            </button>
          )}
          <button
            disabled={busy}
            onClick={cancel}
            aria-label="Rechazar pedido"
            className="btn-ghost px-3 py-2 text-xs text-[var(--c-danger-ink)] hover:bg-[var(--c-danger-bg)] disabled:opacity-50"
          >
            <XCircle size={13}/><span>Rechazar</span>
          </button>
        </div>
      )}
    </article>
  )
}
