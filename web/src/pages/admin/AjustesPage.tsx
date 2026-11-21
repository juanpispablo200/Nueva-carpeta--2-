import { useEffect, useState } from 'react'
import { Settings, Save, AlertCircle, CheckCircle } from 'lucide-react'
import { supabase } from '../../lib/supabase'

interface Setting { key: string; value: string }

const SETTING_META: Record<string, { label: string; description: string; type: 'text' | 'number' | 'toggle' }> = {
  platform_name:          { label: 'Nombre de la plataforma',       description: 'Aparece en notificaciones y correos.',                   type: 'text'   },
  default_delivery_fee:   { label: 'Costo de envío por defecto ($)', description: 'Usado si el restaurante no define uno.',                 type: 'number' },
  driver_fee_per_delivery:{ label: 'Comisión del repartidor ($)',    description: 'Ganancia fija por entrega completada.',                  type: 'number' },
  global_schedule:        { label: 'Horario global de operación',    description: 'Formato HH:MM-HH:MM, ej: 08:00-22:00.',                 type: 'text'   },
  maintenance_mode:       { label: 'Modo mantenimiento',             description: 'Muestra aviso de mantenimiento a los clientes.',         type: 'toggle' },
  bank_name:              { label: 'Banco',                          description: 'Nombre del banco para pagos por transferencia.',         type: 'text'   },
  bank_account_type:      { label: 'Tipo de cuenta',                 description: 'Ahorros o Corriente.',                                  type: 'text'   },
  bank_account_number:    { label: 'Número de cuenta',               description: 'Número completo de la cuenta bancaria.',                type: 'text'   },
  bank_account_holder:    { label: 'Titular de la cuenta',           description: 'Nombre del titular tal como aparece en el banco.',      type: 'text'   },
  bank_ruc:               { label: 'RUC / Cédula del titular',       description: 'RUC de la empresa o cédula del titular.',               type: 'text'   },
}

export default function AjustesPage() {
  const [settings, setSettings] = useState<Record<string, string>>({})
  const [loading,  setLoading]  = useState(true)
  const [saving,   setSaving]   = useState(false)
  const [msg,      setMsg]      = useState<{ type: 'ok' | 'err'; text: string } | null>(null)

  useEffect(() => {
    supabase.from('settings').select('key,value')
      .then(({ data }) => {
        const map: Record<string, string> = {}
        ;(data as Setting[] ?? []).forEach(s => { map[s.key] = s.value })
        setSettings(map)
        setLoading(false)
      })
  }, [])

  function set(key: string, value: string) {
    setSettings(prev => ({ ...prev, [key]: value }))
  }

  async function save() {
    setSaving(true); setMsg(null)
    try {
      const rows = Object.entries(settings).map(([key, value]) => ({ key, value }))
      const { error } = await supabase.from('settings').upsert(rows, { onConflict: 'key' })
      if (error) throw error
      setMsg({ type: 'ok', text: 'Ajustes guardados correctamente.' })
    } catch (err: any) {
      setMsg({ type: 'err', text: err.message ?? 'Error al guardar.' })
    } finally {
      setSaving(false)
      setTimeout(() => setMsg(null), 4000)
    }
  }

  if (loading) {
    return (
      <div className="animate-in">
        <header className="mb-6">
          <p className="text-xs font-semibold uppercase tracking-widest text-[var(--color-ink-sub)] mb-1">Admin</p>
          <h1 className="font-display text-2xl font-bold text-[var(--color-ink)]">Ajustes</h1>
        </header>
        <div className="card p-6 space-y-4">
          {Array(4).fill(0).map((_,i) => (
            <div key={i} className="space-y-2">
              <div className="skeleton h-4 w-32" />
              <div className="skeleton h-10 w-full" />
            </div>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="animate-in">
      <header className="mb-6 flex items-start justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-[var(--color-ink-sub)] mb-1">Admin</p>
          <h1 className="font-display text-2xl font-bold text-[var(--color-ink)]">Ajustes del sistema</h1>
        </div>
        <button onClick={save} disabled={saving} className="btn-primary disabled:opacity-50">
          {saving
            ? <><span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"/>Guardando…</>
            : <><Save size={15}/>Guardar cambios</>}
        </button>
      </header>

      {msg && (
        <div className={`flex items-center gap-2 px-4 py-3 rounded-[var(--radius-md)] text-sm font-medium mb-4 animate-in ${
          msg.type === 'ok'
            ? 'bg-green-50 text-green-700 border border-green-200'
            : 'bg-red-50 text-red-700 border border-red-200'
        }`}>
          {msg.type === 'ok' ? <CheckCircle size={15}/> : <AlertCircle size={15}/>}
          {msg.text}
        </div>
      )}

      <div className="card divide-y divide-[var(--color-border)]">
        {Object.entries(SETTING_META).map(([key, meta]) => {
          const value = settings[key] ?? ''
          return (
            <div key={key} className="p-5 flex flex-col sm:flex-row sm:items-center gap-4">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <Settings size={14} className="text-[var(--color-ink-sub)] shrink-0" />
                  <p className="text-sm font-semibold text-[var(--color-ink)]">{meta.label}</p>
                </div>
                <p className="text-xs text-[var(--color-ink-muted)] ml-5">{meta.description}</p>
              </div>

              <div className="sm:w-64 shrink-0">
                {meta.type === 'toggle' ? (
                  <label className="inline-flex items-center gap-3 cursor-pointer select-none">
                    <input type="checkbox"
                      checked={value === 'true'}
                      onChange={e => set(key, e.target.checked ? 'true' : 'false')}
                      className="sr-only peer" />
                    <span className="relative w-11 h-6 rounded-full bg-[var(--color-border)] transition-colors peer-checked:bg-[var(--color-brand-700)] after:content-[''] after:absolute after:top-1 after:left-1 after:w-4 after:h-4 after:rounded-full after:bg-white after:transition-transform peer-checked:after:translate-x-5" />
                    <span className={`text-sm font-medium ${value === 'true' ? 'text-[var(--color-brand-700)]' : 'text-[var(--color-ink-muted)]'}`}>
                      {value === 'true' ? 'Activado' : 'Desactivado'}
                    </span>
                  </label>
                ) : (
                  <input
                    type={meta.type === 'number' ? 'number' : 'text'}
                    value={value}
                    onChange={e => set(key, e.target.value)}
                    step={meta.type === 'number' ? '0.01' : undefined}
                    min={meta.type === 'number' ? '0' : undefined}
                    className="input w-full"
                  />
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
