import { useEffect, useRef } from 'react'
import { AlertTriangle, X } from 'lucide-react'

interface Props {
  title:       string
  description: string
  confirmLabel?: string
  cancelLabel?:  string
  danger?:     boolean
  onConfirm:   () => void
  onCancel:    () => void
}

/**
 * Modal de confirmación reutilizable.
 * Uso: <ConfirmModal title="¿Cancelar pedido?" description="..." onConfirm={...} onCancel={...} />
 */
export default function ConfirmModal({
  title, description,
  confirmLabel = 'Confirmar', cancelLabel = 'Cancelar',
  danger = false,
  onConfirm, onCancel,
}: Props) {
  const confirmRef = useRef<HTMLButtonElement>(null)

  // Focus en el botón de confirmación al abrir
  useEffect(() => { confirmRef.current?.focus() }, [])

  // Cerrar con Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onCancel() }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [onCancel])

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
    >
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/40 backdrop-blur-sm"
        onClick={onCancel}
        aria-hidden
      />

      {/* Panel */}
      <div className="relative card p-6 w-full max-w-sm shadow-[var(--shadow-xl)] animate-in">
        <button
          onClick={onCancel}
          aria-label="Cerrar"
          className="absolute top-4 right-4 text-[var(--color-ink-muted)] hover:text-[var(--color-ink)] transition-colors"
        >
          <X size={18} />
        </button>

        <div className="flex items-start gap-3 mb-4">
          <div className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 ${
            danger ? 'bg-red-100' : 'bg-[var(--color-brand-100)]'
          }`}>
            <AlertTriangle size={18} className={danger ? 'text-red-600' : 'text-[var(--color-brand-700)]'} />
          </div>
          <div>
            <h2 id="modal-title" className="font-display font-bold text-[var(--color-ink)] text-base leading-tight">
              {title}
            </h2>
            <p className="text-sm text-[var(--color-ink-muted)] mt-1 leading-relaxed">{description}</p>
          </div>
        </div>

        <div className="flex gap-2 justify-end">
          <button onClick={onCancel} className="btn-ghost text-sm">{cancelLabel}</button>
          <button
            ref={confirmRef}
            onClick={onConfirm}
            className={danger ? 'btn-danger' : 'btn-primary'}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
