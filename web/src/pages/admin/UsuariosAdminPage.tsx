import UsersTable from '../../components/admin/UsersTable'

export default function UsuariosAdminPage() {
  return (
    <div className="animate-in">
      <header className="mb-6">
        <p className="text-xs font-semibold uppercase tracking-widest text-[var(--color-ink-sub)] mb-1">Admin</p>
        <h1 className="font-display text-2xl font-bold text-[var(--color-ink)]">Usuarios</h1>
      </header>
      <UsersTable />
    </div>
  )
}
