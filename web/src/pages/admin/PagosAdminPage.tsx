import { useEffect, useState, useCallback } from 'react'
import { CheckCircle, XCircle, Eye, Clock, AlertCircle, ChevronLeft, ChevronRight } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import { useToast } from '../../components/common/ToastContext'
import { EmptyState, Skeleton, SectionHeader } from '../../components/ui'

interface PaymentProof {
  id:               string
  order_id:         string
  customer_id:      string
  image_url:        string
  reference_number: string | null
  amount:           number
  status:           'pending' | 'verified' | 'rejected'
  rejection_reason: string | null
  upload_date:      string
  verified_at:      string | null
  // join
  customer_name?:   string
  restaurant_name?: string
}

const PAGE_SIZE = 20

const STATUS_STYLES: Record<string, string> = {
  pending:  'bg-yellow-50 text-yellow-700 border-yellow-200',
  verified: 'bg-green-50  text-green-700  border-green-200',
  rejected: 'bg-red-50    text-red-700    border-red-200',
}
const STATUS_LABEL: Record<string, string> = {
  pending:  'Pendiente',
  verified: 'Verificado',
  rejected: 'Rechazado',
}

export default function PagosAdminPage() {
  const [proofs,        setProofs]        = useState<PaymentProof[]>([])
  const [total,         setTotal]         = useState(0)
  const [loading,       setLoading]       = useState(true)
  const [page,          setPage]          = useState(0)
  const [filterStatus,  setFilterStatus]  = useState<string>('pending')
  const [previewUrl,    setPreviewUrl]    = useState<string | null>(null)
  const [rejectId,      setRejectId]      = useState<string | null>(null)
  const [rejectReason,  setRejectReason]  = useState('')
  const [acting,        setActing]        = useState(false)
  const toast = useToast()

  const load = useCallback(async () => {
    setLoading(true)
    let q = supabase
      .from('payment_proofs')
      .select('*', { count: 'exact' })
      .order('upload_date', { ascending: false })
      .range(page * PAGE_SIZE, (page + 1) * PAGE_SIZE - 1)

    if (filterStatus) q = q.eq('status', filterStatus)

    const { data, count } = await q
    setProofs((data as PaymentProof[]) ?? [])
    setTotal(count ?? 0)
    setLoading(false)
  }, [filterStatus, page])

  useEffect(() => { load() }, [load])

  // Recargar cuando cambia filtro
  useEffect(() => { setPage(0) }, [filterStatus])

  async function approve(proofId: string) {
    setActing(true)
    try {
      const { error } = await supabase.rpc('verify_payment_proof', {
        p_proof_id: proofId,
        p_approve: true,
        p_rejection_reason: null,
      })
      if (error) throw error
      toast.success('Pago verificado. El pedido avanzó a Aceptado.')
      load()
    } catch (e: any) {
      toast.error(e.message ?? 'Error al verificar')
    } finally {
      setActing(false)
    }
  }

  async function reject() {
    if (!rejectId) return
    setActing(true)
    try {
      const { error } = await supabase.rpc('verify_payment_proof', {
        p_proof_id: rejectId,
        p_approve: false,
        p_rejection_reason: rejectReason.trim() || 'No especificado',
      })
      if (error) throw error
      toast.success('Comprobante rechazado.')
      setRejectId(null)
      setRejectReason('')
      load()
    } catch (e: any) {
      toast.error(e.message ?? 'Error al rechazar')
    } finally {
      setActing(false)
    }
  }

  const pages = Math.ceil(total / PAGE_SIZE)

  return (
    <div className="animate-in">
      <SectionHeader
        title={`Pagos${total ? ` (${total})` : ''}`}
        description="Revisa y aprueba los comprobantes de transferencia bancaria enviados por los clientes."
      />

      {/* Filtros */}
      <div className="flex gap-2 mb-4 flex-wrap">
        {(['pending', 'verified', 'rejected', ''] as const).map(s => (
          <button
            key={s}
            onClick={() => setFilterStatus(s)}
            className={`px-4 py-1.5 rounded-full text-xs font-semibold border transition-all ${
              filterStatus === s
                ? 'bg-[var(--color-brand-700)] text-white border-[var(--color-brand-700)]'
                : 'bg-transparent text-[var(--color-ink-muted)] border-[var(--color-border)] hover:border-[var(--color-brand-700)]'
            }`}
          >
            {s === '' ? 'Todos' : STATUS_LABEL[s]}
          </button>
        ))}
      </div>

      {/* Tabla */}
      <div className="card overflow-hidden">
        {loading ? (
          <div className="p-5 space-y-3">
            {Array(5).fill(0).map((_, i) => <Skeleton key={i} className="h-14 w-full" />)}
          </div>
        ) : proofs.length === 0 ? (
          <EmptyState
            icon={<Clock size={20} />}
            title="Sin comprobantes"
            description={
              filterStatus === 'pending'
                ? 'No hay pagos pendientes de verificación.'
                : 'No hay comprobantes en este estado.'
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] bg-[var(--color-bg)]">
                  {['Pedido', 'Monto', 'Referencia', 'Fecha', 'Estado', 'Comprobante', ''].map(h => (
                    <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {proofs.map(p => (
                  <tr key={p.id} className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-bg)] transition-colors">
                    {/* Pedido */}
                    <td className="px-4 py-3">
                      <span className="font-mono text-xs text-[var(--color-ink-sub)]">
                        {p.order_id.slice(0, 8)}…
                      </span>
                    </td>

                    {/* Monto */}
                    <td className="px-4 py-3 font-semibold text-[var(--color-ink)]">
                      ${p.amount.toFixed(2)}
                    </td>

                    {/* Referencia */}
                    <td className="px-4 py-3 text-[var(--color-ink-muted)] text-xs">
                      {p.reference_number ?? '—'}
                    </td>

                    {/* Fecha */}
                    <td className="px-4 py-3 text-[var(--color-ink-muted)] text-xs whitespace-nowrap">
                      {new Date(p.upload_date).toLocaleDateString('es', {
                        day: '2-digit', month: 'short', year: 'numeric',
                        hour: '2-digit', minute: '2-digit'
                      })}
                    </td>

                    {/* Estado */}
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium border ${STATUS_STYLES[p.status]}`}>
                        {p.status === 'pending'  && <Clock size={11} />}
                        {p.status === 'verified' && <CheckCircle size={11} />}
                        {p.status === 'rejected' && <XCircle size={11} />}
                        {STATUS_LABEL[p.status]}
                      </span>
                      {p.status === 'rejected' && p.rejection_reason && (
                        <p className="text-xs text-red-500 mt-0.5 max-w-[160px] truncate" title={p.rejection_reason}>
                          {p.rejection_reason}
                        </p>
                      )}
                    </td>

                    {/* Ver comprobante */}
                    <td className="px-4 py-3">
                      <button
                        onClick={() => setPreviewUrl(p.image_url)}
                        className="flex items-center gap-1 text-xs font-medium text-[var(--color-brand-700)] hover:underline"
                      >
                        <Eye size={12} /> Ver
                      </button>
                    </td>

                    {/* Acciones */}
                    <td className="px-4 py-3">
                      {p.status === 'pending' && (
                        <div className="flex gap-2">
                          <button
                            onClick={() => approve(p.id)}
                            disabled={acting}
                            className="flex items-center gap-1 px-3 py-1 rounded-full text-xs font-semibold bg-green-600 text-white hover:bg-green-700 disabled:opacity-50 transition-colors"
                          >
                            <CheckCircle size={11} /> Aprobar
                          </button>
                          <button
                            onClick={() => { setRejectId(p.id); setRejectReason('') }}
                            disabled={acting}
                            className="flex items-center gap-1 px-3 py-1 rounded-full text-xs font-semibold bg-red-500 text-white hover:bg-red-600 disabled:opacity-50 transition-colors"
                          >
                            <XCircle size={11} /> Rechazar
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Paginación */}
        {pages > 1 && (
          <div className="flex items-center justify-between px-5 py-3 border-t border-[var(--color-border)]">
            <span className="text-xs text-[var(--color-ink-sub)]">
              {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, total)} de {total}
            </span>
            <div className="flex gap-1">
              <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
                className="btn-ghost p-1.5 disabled:opacity-40"><ChevronLeft size={15} /></button>
              <span className="px-3 py-1 text-xs text-[var(--color-ink-sub)]">Pág. {page + 1} / {pages}</span>
              <button disabled={page >= pages - 1} onClick={() => setPage(p => p + 1)}
                className="btn-ghost p-1.5 disabled:opacity-40"><ChevronRight size={15} /></button>
            </div>
          </div>
        )}
      </div>

      {/* Modal: previsualizar imagen */}
      {previewUrl && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm"
          onClick={() => setPreviewUrl(null)}
        >
          <div className="relative max-w-2xl w-full mx-4" onClick={e => e.stopPropagation()}>
            <button
              onClick={() => setPreviewUrl(null)}
              className="absolute -top-10 right-0 text-white hover:text-gray-300 text-sm flex items-center gap-1"
            >
              <XCircle size={18} /> Cerrar
            </button>
            <img
              src={previewUrl}
              alt="Comprobante de pago"
              className="w-full rounded-2xl shadow-2xl max-h-[80vh] object-contain"
            />
          </div>
        </div>
      )}

      {/* Modal: motivo de rechazo */}
      {rejectId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
          <div className="card p-6 w-full max-w-md mx-4 animate-in">
            <div className="flex items-center gap-2 mb-4">
              <AlertCircle size={18} className="text-red-500" />
              <h2 className="font-display font-bold text-lg text-[var(--color-ink)]">Rechazar comprobante</h2>
            </div>
            <p className="text-sm text-[var(--color-ink-muted)] mb-4">
              Indica el motivo del rechazo. El cliente verá este mensaje.
            </p>
            <textarea
              value={rejectReason}
              onChange={e => setRejectReason(e.target.value)}
              placeholder="Ej: El monto no coincide con el total del pedido"
              rows={3}
              className="input w-full resize-none mb-4"
            />
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => { setRejectId(null); setRejectReason('') }}
                className="btn-ghost"
              >
                Cancelar
              </button>
              <button
                onClick={reject}
                disabled={acting}
                className="flex items-center gap-2 px-4 py-2 rounded-[var(--radius-md)] bg-red-500 text-white text-sm font-semibold hover:bg-red-600 disabled:opacity-50 transition-colors"
              >
                {acting
                  ? <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  : <XCircle size={15} />
                }
                Confirmar rechazo
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
