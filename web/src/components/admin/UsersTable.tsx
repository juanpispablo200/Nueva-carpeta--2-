import { useEffect, useState } from 'react'
import { Search, Users } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import type { Profile } from '../../lib/types'
import { StatusBadge, Skeleton, EmptyState, Input, SectionHeader } from '../ui'

const PAGE = 10

export default function UsersTable() {
  const [users,   setUsers]   = useState<Profile[]>([])
  const [loading, setLoading] = useState(true)
  const [search,  setSearch]  = useState('')
  const [page,    setPage]    = useState(0)
  const [saving,  setSaving]  = useState<string | null>(null)

  useEffect(() => {
    supabase.from('profiles').select('*').order('created_at', { ascending: false })
      .then(({ data }) => { setUsers((data as Profile[]) ?? []); setLoading(false) })
  }, [])

  async function changeRole(id: string, role: Profile['role']) {
    setSaving(id)
    await supabase.from('profiles').update({ role }).eq('id', id)
    setUsers(p => p.map(u => u.id === id ? { ...u, role } : u))
    setSaving(null)
  }

  const filtered = users.filter(u =>
    u.full_name.toLowerCase().includes(search.toLowerCase()) ||
    u.id.toLowerCase().includes(search.toLowerCase())
  )
  const pages = Math.ceil(filtered.length / PAGE)
  const paged = filtered.slice(page * PAGE, page * PAGE + PAGE)

  return (
    <div className="animate-in">
      <SectionHeader title={`Usuarios${users.length ? ` (${users.length})` : ''}`}>
        <div className="relative w-full sm:w-64">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-ink-sub)]" />
          <Input
            value={search}
            onChange={e => { setSearch(e.target.value); setPage(0) }}
            placeholder="Buscar por nombre…"
            aria-label="Buscar usuarios por nombre"
            className="pl-8"
          />
        </div>
      </SectionHeader>

      <div className="card overflow-hidden">
        {loading ? (
          <div className="p-5 space-y-3">
            {Array(5).fill(0).map((_,i) => <Skeleton key={i} className="h-10 w-full" />)}
          </div>
        ) : filtered.length === 0 ? (
          <EmptyState icon={<Users size={20}/>} title="Sin usuarios" description="Nadie se ha registrado aún." />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-[var(--color-border)] bg-[var(--color-bg)]">
                    <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">Usuario</th>
                    <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide hidden md:table-cell">ID</th>
                    <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">Rol</th>
                    <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--color-ink-sub)] uppercase tracking-wide">Cambiar rol</th>
                  </tr>
                </thead>
                <tbody>
                  {paged.map(u => (
                    <tr key={u.id} className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-bg)] transition-colors">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3">
                          <div className="w-9 h-9 rounded-full bg-[var(--color-brand-100)] text-[var(--color-brand-700)] flex items-center justify-center text-xs font-bold shrink-0">
                            {(u.full_name?.[0] ?? '?').toUpperCase()}
                          </div>
                          <span className="font-medium text-[var(--color-ink)]">{u.full_name || '—'}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-[var(--color-ink-sub)] hidden md:table-cell">{u.id.slice(0,8)}…</td>
                      <td className="px-4 py-3"><StatusBadge status={u.role} /></td>
                      <td className="px-4 py-3">
                        <select
                          value={u.role}
                          disabled={saving === u.id}
                          onChange={e => changeRole(u.id, e.target.value as Profile['role'])}
                          aria-label={`Cambiar rol de ${u.full_name || 'usuario'}`}
                          className="text-xs border border-[var(--color-border)] rounded-[var(--radius-md)] px-3 py-2 bg-[var(--color-surface)] text-[var(--color-ink)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-700)] disabled:opacity-50 cursor-pointer"
                        >
                          <option value="customer">cliente</option>
                          <option value="restaurant">restaurante</option>
                          <option value="admin">admin</option>
                          <option value="repartidor">repartidor</option>
                        </select>
                        {saving === u.id && <span className="ml-2 text-xs text-[var(--color-ink-sub)]">Guardando…</span>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Paginación */}
            {pages > 1 && (
              <div className="flex items-center justify-between px-5 py-3 border-t border-[var(--color-border)]">
                <p className="text-xs text-[var(--color-ink-sub)]">
                  {page * PAGE + 1}–{Math.min((page + 1) * PAGE, filtered.length)} de {filtered.length}
                </p>
                <div className="flex gap-1">
                  <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="btn-ghost py-1 px-2.5 text-xs disabled:opacity-40">← Anterior</button>
                  <button disabled={page >= pages - 1} onClick={() => setPage(p => p + 1)} className="btn-ghost py-1 px-2.5 text-xs disabled:opacity-40">Siguiente →</button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
