/**
 * FinanzasPage — Reparto por tipo de negocio, zonas de envío, efectivo de los
 * repartidores (liquidación) y reporte por tipo.
 *
 * Los porcentajes se calculan sobre el SUBTOTAL; el envío va íntegro al repartidor.
 * Un tipo sin porcentajes propios usa el reparto global de Ajustes.
 */
import { useCallback, useEffect, useState } from 'react'
import { supabase } from '../../lib/supabase'
import { BUSINESS_LABEL } from '../../lib/types'
import type { BusinessType } from '../../lib/types'

interface TypeConfig {
  business_type:  BusinessType
  pct_negocio:    number | null
  pct_driver:     number | null
  pct_plataforma: number | null
  min_order:      number | null
}
interface Zone { id: string; name: string; fee: number; is_active: boolean; sort_order: number }
interface Cash {
  id: string; order_id: string; driver_id: string | null; amount_collected: number
  driver_share: number; due_to_platform: number; status: 'collected' | 'settled'; collected_at: string
  driver?: { full_name: string } | null
}
interface ReportRow {
  business_type: BusinessType; orders_total: number; orders_delivered: number; orders_cancelled: number
  gross_total: number; business_amount: number; driver_amount: number; platform_amount: number
}

const money = (n: number | null | undefined) => `$${(n ?? 0).toFixed(2)}`

export default function FinanzasPage() {
  const [configs, setConfigs] = useState<TypeConfig[]>([])
  const [zones,   setZones]   = useState<Zone[]>([])
  const [cash,    setCash]    = useState<Cash[]>([])
  const [report,  setReport]  = useState<ReportRow[]>([])
  const [global,  setGlobal]  = useState({ n: '85', d: '5', p: '10' })
  const [msg,     setMsg]     = useState<{ ok: boolean; text: string } | null>(null)
  const [newZone, setNewZone] = useState({ name: '', fee: '' })
  const [from,    setFrom]    = useState('')
  const [to,      setTo]      = useState('')

  const load = useCallback(async () => {
    const [c, z, k, s] = await Promise.all([
      supabase.from('business_type_config').select('*').order('business_type'),
      supabase.from('delivery_zones').select('*').order('sort_order').order('name'),
      supabase.from('cash_collections')
        .select('*, driver:profiles!cash_collections_driver_id_fkey(full_name)')
        .order('collected_at', { ascending: false }).limit(200),
      supabase.from('settings').select('key,value').in('key', ['split_negocio_pct', 'split_driver_pct', 'split_plataforma_pct']),
    ])
    setConfigs((c.data ?? []) as TypeConfig[])
    setZones((z.data ?? []) as Zone[])
    setCash((k.data ?? []) as unknown as Cash[])
    const m: Record<string, string> = {}
    ;(s.data ?? []).forEach((r: { key: string; value: string }) => { m[r.key] = r.value })
    setGlobal({ n: m.split_negocio_pct ?? '85', d: m.split_driver_pct ?? '5', p: m.split_plataforma_pct ?? '10' })
  }, [])

  const loadReport = useCallback(async () => {
    const { data, error } = await supabase.rpc('admin_report_by_type', {
      p_from: from ? new Date(from).toISOString() : null,
      p_to:   to   ? new Date(new Date(to).getTime() + 86_400_000).toISOString() : null,
    })
    if (!error) setReport((data ?? []) as ReportRow[])
  }, [from, to])

  useEffect(() => { load() }, [load])
  useEffect(() => { loadReport() }, [loadReport])

  function say(ok: boolean, text: string) { setMsg({ ok, text }); setTimeout(() => setMsg(null), 6000) }

  async function saveGlobal() {
    const n = Number(global.n), d = Number(global.d), p = Number(global.p)
    if ([n, d, p].some(x => Number.isNaN(x) || x < 0) || Math.abs(n + d + p - 100) > 0.001) {
      say(false, 'Los tres porcentajes deben ser números positivos y sumar exactamente 100.'); return
    }
    const { error } = await supabase.from('settings').upsert([
      { key: 'split_negocio_pct', value: String(n) },
      { key: 'split_driver_pct', value: String(d) },
      { key: 'split_plataforma_pct', value: String(p) },
    ], { onConflict: 'key' })
    say(!error, error ? 'No se pudo guardar el reparto global.' : 'Reparto global guardado.')
  }

  async function saveType(t: TypeConfig) {
    const vals = [t.pct_negocio, t.pct_driver, t.pct_plataforma]
    const anySet = vals.some(v => v !== null)
    if (anySet && (vals.some(v => v === null) || Math.abs(vals.reduce<number>((a, v) => a + (v ?? 0), 0) - 100) > 0.001)) {
      say(false, `${BUSINESS_LABEL[t.business_type]}: completa los tres porcentajes y que sumen 100, o déjalos vacíos para usar el global.`); return
    }
    const { error } = await supabase.from('business_type_config').update({
      pct_negocio: t.pct_negocio, pct_driver: t.pct_driver, pct_plataforma: t.pct_plataforma,
      min_order: t.min_order, updated_at: new Date().toISOString(),
    }).eq('business_type', t.business_type)
    say(!error, error ? 'No se pudo guardar el tipo.' : `${BUSINESS_LABEL[t.business_type]} guardado.`)
  }

  function editType(bt: BusinessType, k: keyof TypeConfig, v: string) {
    setConfigs(cs => cs.map(c => c.business_type === bt ? { ...c, [k]: v === '' ? null : Number(v) } : c))
  }

  async function addZone() {
    const fee = Number(newZone.fee)
    if (!newZone.name.trim() || Number.isNaN(fee) || fee < 0) { say(false, 'Indica nombre y un costo de envío válido.'); return }
    const { error } = await supabase.from('delivery_zones').insert({ name: newZone.name.trim(), fee })
    if (!error) { setNewZone({ name: '', fee: '' }); load() }
    say(!error, error ? 'No se pudo crear la zona.' : 'Zona creada.')
  }

  async function updateZone(z: Zone, patch: Partial<Zone>) {
    const { error } = await supabase.from('delivery_zones').update(patch).eq('id', z.id)
    if (!error) load()
    else say(false, 'No se pudo actualizar la zona.')
  }

  async function settle(ids: string[]) {
    const { data, error } = await supabase.rpc('settle_cash_collections', { p_ids: ids, p_note: null })
    say(!error, error ? 'No se pudo liquidar.' : `Liquidados: ${data}.`)
    load()
  }

  const pending = cash.filter(c => c.status === 'collected')
  const byDriver = pending.reduce<Record<string, { name: string; ids: string[]; due: number }>>((acc, c) => {
    const k = c.driver_id ?? 'sin'
    acc[k] ??= { name: c.driver?.full_name ?? 'Repartidor', ids: [], due: 0 }
    acc[k].ids.push(c.id); acc[k].due += c.due_to_platform
    return acc
  }, {})

  return (
    <div className="space-y-8 p-4">
      <h1 className="font-display text-xl font-bold">Finanzas</h1>

      {msg && (
        <div role="status" aria-live="polite"
          className={`card p-3 text-sm ${msg.ok ? 'text-green-700' : 'text-red-700'}`}>{msg.text}</div>
      )}

      {/* Reparto global */}
      <section className="card p-4 space-y-3">
        <h2 className="font-semibold">Reparto global (sobre el subtotal)</h2>
        <p className="text-xs text-[var(--color-ink-sub)]">Aplica a efectivo y transferencia. Debe sumar 100. El envío va íntegro al repartidor.</p>
        <div className="grid grid-cols-3 gap-3 max-w-lg">
          {([['Negocio %', 'n'], ['Repartidor %', 'd'], ['Plataforma %', 'p']] as const).map(([label, k]) => (
            <label key={k} className="text-xs font-semibold">{label}
              <input className="input mt-1" type="number" min="0" max="100" step="0.01" value={global[k]}
                onChange={e => setGlobal(g => ({ ...g, [k]: e.target.value }))} />
            </label>
          ))}
        </div>
        <button className="btn-primary" onClick={saveGlobal}>Guardar reparto global</button>
      </section>

      {/* Por tipo */}
      <section className="card p-4 space-y-3 overflow-x-auto">
        <h2 className="font-semibold">Por tipo de negocio</h2>
        <p className="text-xs text-[var(--color-ink-sub)]">Deja los porcentajes vacíos para usar el global. El pedido mínimo del tipo reemplaza al del negocio.</p>
        <table className="w-full text-sm">
          <thead><tr className="text-left text-xs uppercase">
            <th className="p-2">Tipo</th><th className="p-2">Negocio %</th><th className="p-2">Repartidor %</th>
            <th className="p-2">Plataforma %</th><th className="p-2">Pedido mínimo $</th><th className="p-2" />
          </tr></thead>
          <tbody>
            {configs.map(t => (
              <tr key={t.business_type} className="border-t">
                <td className="p-2 font-semibold">{BUSINESS_LABEL[t.business_type]}</td>
                {(['pct_negocio', 'pct_driver', 'pct_plataforma', 'min_order'] as const).map(k => (
                  <td key={k} className="p-2">
                    <input className="input w-24" type="number" min="0" step="0.01" value={t[k] ?? ''}
                      aria-label={`${k} de ${BUSINESS_LABEL[t.business_type]}`}
                      onChange={e => editType(t.business_type, k, e.target.value)} />
                  </td>
                ))}
                <td className="p-2"><button className="btn-primary text-xs" onClick={() => saveType(t)}>Guardar</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {/* Zonas */}
      <section className="card p-4 space-y-3">
        <h2 className="font-semibold">Zonas de envío</h2>
        <p className="text-xs text-[var(--color-ink-sub)]">Si hay zonas activas, el cliente debe elegir una y el envío es la tarifa de la zona. Sin zonas se usa el costo por defecto de Ajustes.</p>
        <ul className="space-y-2">
          {zones.map(z => (
            <li key={z.id} className="flex flex-wrap items-center gap-3 text-sm">
              <span className="min-w-40 font-semibold">{z.name}</span>
              <input className="input w-24" type="number" min="0" step="0.01" defaultValue={z.fee}
                aria-label={`Tarifa de ${z.name}`}
                onBlur={e => { const v = Number(e.target.value); if (!Number.isNaN(v) && v >= 0 && v !== z.fee) updateZone(z, { fee: v }) }} />
              <button className="text-xs underline" onClick={() => updateZone(z, { is_active: !z.is_active })}>
                {z.is_active ? 'Desactivar' : 'Activar'}
              </button>
              {!z.is_active && <span className="text-xs text-red-600">Inactiva</span>}
            </li>
          ))}
        </ul>
        <div className="flex flex-wrap gap-2">
          <input className="input w-56" placeholder="Nombre de la zona" value={newZone.name} onChange={e => setNewZone(z => ({ ...z, name: e.target.value }))} />
          <input className="input w-28" type="number" min="0" step="0.01" placeholder="Tarifa $" value={newZone.fee} onChange={e => setNewZone(z => ({ ...z, fee: e.target.value }))} />
          <button className="btn-primary" onClick={addZone}>Agregar zona</button>
        </div>
      </section>

      {/* Efectivo */}
      <section className="card p-4 space-y-3 overflow-x-auto">
        <h2 className="font-semibold">Efectivo por liquidar (repartidores)</h2>
        <p className="text-xs text-[var(--color-ink-sub)]">Lo que cada repartidor cobró en efectivo menos su parte. Al recibir el dinero, marca como liquidado.</p>
        {Object.keys(byDriver).length === 0 ? <p className="text-sm">No hay efectivo pendiente.</p> : (
          <table className="w-full text-sm">
            <thead><tr className="text-left text-xs uppercase"><th className="p-2">Repartidor</th><th className="p-2">Pedidos</th><th className="p-2">Debe entregar</th><th className="p-2" /></tr></thead>
            <tbody>
              {Object.entries(byDriver).map(([k, d]) => (
                <tr key={k} className="border-t">
                  <td className="p-2 font-semibold">{d.name}</td>
                  <td className="p-2">{d.ids.length}</td>
                  <td className="p-2">{money(d.due)}</td>
                  <td className="p-2"><button className="btn-primary text-xs" onClick={() => settle(d.ids)}>Marcar liquidado</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="text-xs text-[var(--color-ink-sub)]">Liquidado en los últimos 200 movimientos: {cash.filter(c => c.status === 'settled').length}</p>
      </section>

      {/* Reporte por tipo */}
      <section className="card p-4 space-y-3 overflow-x-auto">
        <h2 className="font-semibold">Reporte por tipo de negocio (pedidos entregados)</h2>
        <div className="flex gap-3 text-xs">
          <label>Desde <input className="input" type="date" value={from} onChange={e => setFrom(e.target.value)} /></label>
          <label>Hasta <input className="input" type="date" value={to} onChange={e => setTo(e.target.value)} /></label>
        </div>
        <table className="w-full text-sm">
          <thead><tr className="text-left text-xs uppercase">
            <th className="p-2">Tipo</th><th className="p-2">Pedidos</th><th className="p-2">Entregados</th><th className="p-2">Cancelados</th>
            <th className="p-2">Ventas</th><th className="p-2">Negocio</th><th className="p-2">Repartidor</th><th className="p-2">Plataforma</th>
          </tr></thead>
          <tbody>
            {report.map(r => (
              <tr key={r.business_type} className="border-t">
                <td className="p-2 font-semibold">{BUSINESS_LABEL[r.business_type]}</td>
                <td className="p-2">{r.orders_total}</td><td className="p-2">{r.orders_delivered}</td><td className="p-2">{r.orders_cancelled}</td>
                <td className="p-2">{money(r.gross_total)}</td><td className="p-2">{money(r.business_amount)}</td>
                <td className="p-2">{money(r.driver_amount)}</td><td className="p-2">{money(r.platform_amount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  )
}
