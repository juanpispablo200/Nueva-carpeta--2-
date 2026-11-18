import ReportsPanel from '../../components/admin/ReportsPanel'

export default function ReportesAdminPage() {
  return (
    <div className="animate-in">
      <header className="mb-6">
        <p className="text-xs font-semibold uppercase tracking-widest text-[var(--color-ink-sub)] mb-1">Admin</p>
        <h1 className="font-display text-2xl font-bold text-[var(--color-ink)]">Reportes</h1>
      </header>
      <ReportsPanel />
    </div>
  )
}
