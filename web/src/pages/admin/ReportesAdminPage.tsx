import { useState } from 'react'
import { Download } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import ReportsPanel from '../../components/admin/ReportsPanel'

type Period = 'today' | 'week' | 'month' | 'all'

function periodFrom(p: Period): string | null {
  const now = new Date()
  if (p === 'today') { const d = new Date(now); d.setHours(0,0,0,0); return d.toISOString() }
  if (p === 'week')  { const d = new Date(now); d.setDate(d.getDate() - 6); d.setHours(0,0,0,0); return d.toISOString() }
  if (p === 'month') { const d = new Date(now); d.setDate(1); d.setHours(0,0,0,0); return d.toISOString() }
  return null
}

function downloadCSV(rows: Record<string, unknown>[], filename: string) {
  if (rows.length === 0) return
  const headers = Object.keys(rows[0])
  const csv = [
    headers.join(','),
    ...rows.map(r =>
      headers.map(h => {
        const val = r[h] ?? ''
        const str = String(val).replace(/"/g, '""')
        return str.includes(',') || str.includes('\n') ? `"${str}"` : str
      }).join(',')
    )
  ].join('\n')

  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })
  const url  = URL.createObjectURL(blob)
  const a    = document.createElement('a')
  a.href     = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export default function ReportesAdminPage() {
  const [exporting, setExporting] = useState(false)
  const [period,    setPeriod]    = useState<Period>('month')

  async function exportOrders() {
    setExporting(true)
    try {
      const from = periodFrom(period)
      let q = supabase
        .from('orders')
        .select('id, created_at, status, restaurant_name, total, delivery_fee, delivery_address, payment_method, payment_status')
        .order('created_at', { ascending: false })

      if (from) q = q.gte('created_at', from)

      const { data } = await q
      if (!data || data.length === 0) { alert('Sin datos para exportar'); return }

      const rows = data.map((o: Record<string, unknown>) => ({
        ID:            o.id,
        Fecha:         new Date(o.created_at as string).toLocaleString('es'),
        Restaurante:   o.restaurant_name,
        Estado:        o.status,
        'Pago Estado': o.payment_status,
        'Método Pago': o.payment_method,
        'Fee Envío':   o.delivery_fee,
        Total:         o.total,
        Dirección:     o.delivery_address ?? '',
      }))

      const label = { today: 'hoy', week: 'semana', month: 'mes', all: 'total' }[period]
      downloadCSV(rows, `jagoo-pedidos-${label}-${new Date().toISOString().slice(0,10)}.csv`)
    } finally {
      setExporting(false)
    }
  }

  async function exportPayments() {
    setExporting(true)
    try {
      const { data } = await supabase
        .from('payment_proofs')
        .select('id, upload_date, amount, status, reference_number, rejection_reason, order_id')
        .order('upload_date', { ascending: false })

      if (!data || data.length === 0) { alert('Sin comprobantes para exportar'); return }

      const rows = data.map((p: Record<string, unknown>) => ({
        ID:          p.id,
        Fecha:       new Date(p.upload_date as string).toLocaleString('es'),
        Monto:       p.amount,
        Estado:      p.status,
        Referencia:  p.reference_number ?? '',
        Rechazo:     p.rejection_reason ?? '',
        'Pedido ID': p.order_id,
      }))

      downloadCSV(rows, `jagoo-comprobantes-${new Date().toISOString().slice(0,10)}.csv`)
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="animate-in">
      {/* Header con botones de exportación */}
      <div className="flex flex-wrap items-start justify-between gap-4 mb-6">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-[var(--color-ink-sub)] mb-1">Admin</p>
          <h1 className="font-display text-2xl font-bold text-[var(--color-ink)]">Reportes</h1>
        </div>

        <div className="flex flex-wrap gap-2">
          {/* Selector de período para exportar */}
          <select
            value={period}
            onChange={e => setPeriod(e.target.value as Period)}
            className="input text-sm py-1.5"
          >
            <option value="today">Hoy</option>
            <option value="week">Esta semana</option>
            <option value="month">Este mes</option>
            <option value="all">Todo</option>
          </select>

          <button
            onClick={exportOrders}
            disabled={exporting}
            className="flex items-center gap-2 px-4 py-2 rounded-[var(--radius-md)] bg-[var(--color-brand-700)] text-white text-sm font-semibold hover:opacity-90 disabled:opacity-50 transition-opacity"
          >
            {exporting
              ? <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
              : <Download size={15} />
            }
            Pedidos CSV
          </button>

          <button
            onClick={exportPayments}
            disabled={exporting}
            className="flex items-center gap-2 px-4 py-2 rounded-[var(--radius-md)] border border-[var(--color-border)] text-[var(--color-ink)] text-sm font-semibold hover:bg-[var(--color-bg)] disabled:opacity-50 transition-colors"
          >
            {exporting
              ? <span className="w-4 h-4 border-2 border-[var(--color-ink)] border-t-transparent rounded-full animate-spin" />
              : <Download size={15} />
            }
            Comprobantes CSV
          </button>
        </div>
      </div>

      <ReportsPanel />
    </div>
  )
}
