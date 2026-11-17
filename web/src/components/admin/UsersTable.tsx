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
  const pages  = Math.ceil(filtered.length / PAGE)
  const paged  = filtered.slice(page * PAGE, page * PAGE + PAGE)

  return (
    <div className="animate-fade-in">
      <SectionHeader title={`Usuarios${users.length ? ` (${users.length})` : ''}`}>
        <div className="relative w-full sm:w-64">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--c-ink-subtle)]" />
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
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Usuario</th>
                    <th className="hidden md:table-cell">ID</th>
                    <th>Rol</th>
                    <th>Cambiar rol</th>
                  </tr>
                </thead>
                <tbody>
                  {paged.map(u => (
                    <tr key={u.id}>
                      <td>
                        <div className="flex items-center gap-3">
                          <div className="w-9 h-9 rounded-full bg-[var(--c-success-bg)] text-[var(--c-success-ink)] flex items-center justify-center text-xs font-bold shrink-0">
                            {(u.full_name?.[0] ?? '?').toUpperCase()}
                          </div>
                          <span className="font-medium text-[var(--c-ink)]">{u.full_name || '—'}</span>
                        </div>
                      </td>
                      <td className="font-mono text-xs text-[var(--c-ink-subtle)] hidden md:table-cell">{u.id.slice(0,8)}…</td>
                      <td><StatusBadge status={u.role} /></td>
                      <td>
                        <select
                          value={u.role}
                          disabled={saving === u.id}
                          onChange={e => changeRole(u.id, e.target.value as Profile['role'])}
                          aria-label={`Cambiar rol de ${u.full_name || 'usuario'}`}
                          className="text-xs border border-[var(--c-border)] rounded-[var(--radius-input)] px-3 py-2 bg-[var(--c-surface)] text-[var(--c-ink)] focus-visible:outline-2 focus-visible:outline-[var(--c-brand)] disabled:opacity-50 cursor-pointer"
                        >
                          <option value="customer">cliente</option>
                          <option value="restaurant">restaurante</option>
                          <option value="admin">admin</option>
                        </select>
                        {saving === u.id && <span className="ml-2 text-xs text-[var(--c-ink-subtle)]">Guardando…</span>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Paginación */}
            {pages > 1 && (
              <div className="flex items-center justify-between px-5 py-3 border-t border-[var(--c-border)]">
                <p className="text-xs text-[var(--c-ink-subtle)]">
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
