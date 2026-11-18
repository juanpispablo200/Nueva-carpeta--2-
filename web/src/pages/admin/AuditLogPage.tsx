import { useEffect, useState } from 'react'
import { FileText } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import { EmptyState, Skeleton, SectionHeader } from '../../components/ui'

interface AuditEntry {
  id: string
  user_id: string | null
  action: string
  table_name: string | null
  record_id: string | null
  created_at: string
  old_data: any
  new_data: any
}

export default function AuditLogPage() {
  const [logs, setLogs] = useState<AuditEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const PAGE_SIZE = 20

  useEffect(() => {
    async function load() {
      const { data } = await supabase
        .from('audit_log')
        .select('*')
        .order('created_at', { ascending: false })
        .range(page * PAGE_SIZE, (page + 1) * PAGE_SIZE - 1)

      setLogs((data as AuditEntry[]) ?? [])
      setLoading(false)
    }
    load()
  }, [page])

  const ACTION_LABEL: Record<string, string> = {
    create: 'Crear',
    update: 'Actualizar',
    delete: 'Eliminar',
    login: 'Login',
    logout: 'Logout',
    role_change: 'Cambio de rol',
    status_change: 'Cambio de estado',
    assign_courier: 'Asignar repartidor',
    create_courier: 'Crear repartidor',
  }

  return (
    <div className="animate-in">
      <header className="mb-6">
        <p className="text-xs font-semibold uppercase tracking-widest text-[var(--color-ink-sub)] mb-1">Admin</p>
        <h1 className="font-display text-2xl font-bold text-[var(--color-ink)]">Auditoría</h1>
      </header>

      <div className="card overflow-hidden">
        {loading ? (
          <div className="p-5 space-y-3">
            {Array(8).fill(0).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
          </div>
        ) : logs.length === 0 ? (
          <EmptyState
            icon={<FileText size={20} />}
            title="Sin registros"
            description="No hay eventos de auditoría registrados."
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-[var(--color-border)] bg-[var(--color-bg)]">
                    <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">
                      Fecha
                    </th>
                    <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">
                      Acción
                    </th>
                    <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">
                      Tabla
                    </th>
                    <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">
                      Usuario
                    </th>
                    <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">
                      Detalles
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map(log => (
                    <tr
                      key={log.id}
                      className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-bg)] transition-colors"
                    >
                      <td className="px-4 py-3 text-xs text-[var(--color-ink-muted)]">
                        {new Date(log.created_at).toLocaleString('es', {
                          day: '2-digit',
                          month: 'short',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </td>
                      <td className="px-4 py-3">
                        <span className="badge">
                          {ACTION_LABEL[log.action] || log.action}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-[var(--color-ink-muted)] font-mono text-xs">
                        {log.table_name || '—'}
                      </td>
                      <td className="px-4 py-3 text-[var(--color-ink-muted)] font-mono text-xs">
                        {log.user_id?.slice(0, 8) || 'Sistema'}
                      </td>
                      <td className="px-4 py-3 text-xs">
                        {log.new_data && (
                          <details className="cursor-pointer">
                            <summary className="text-[var(--color-brand-700)] hover:underline">
                              Ver JSON
                            </summary>
                            <pre className="mt-2 p-2 bg-[var(--color-bg)] rounded text-xs overflow-x-auto max-w-xs">
                              {JSON.stringify(log.new_data, null, 2)}
                            </pre>
                          </details>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Paginación */}
            <div className="flex items-center justify-between px-5 py-3 border-t border-[var(--color-border)]">
              <button
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
                className="btn-ghost py-1 px-3 text-xs disabled:opacity-40"
              >
                ← Anterior
              </button>
              <span className="text-xs text-[var(--color-ink-sub)]">Página {page + 1}</span>
              <button
                disabled={logs.length < PAGE_SIZE}
                onClick={() => setPage(p => p + 1)}
                className="btn-ghost py-1 px-3 text-xs disabled:opacity-40"
              >
                Siguiente →
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
