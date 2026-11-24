import { useEffect, useState } from 'react'
import { Plus, Trash2, Tag, Phone } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import { Skeleton, EmptyState, Input, SectionHeader } from '../ui'

interface Props { restaurantId: string }

interface Promotion {
  id: string
  restaurant_id: string
  title: string
  discount_percent: number
  starts_at: string
  ends_at: string
  is_active: boolean
}

/** yyyy-MM-ddTHH:mm local para <input type="datetime-local"> */
function toLocalInput(d: Date) {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

function estado(p: Promotion): { label: string; cls: string } {
  const now = Date.now()
  if (!p.is_active) return { label: 'Apagada', cls: 'bg-gray-100 text-gray-600' }
  if (now < new Date(p.starts_at).getTime()) return { label: 'Programada', cls: 'bg-blue-100 text-blue-700' }
  if (now >= new Date(p.ends_at).getTime()) return { label: 'Vencida', cls: 'bg-red-100 text-red-700' }
  return { label: 'Vigente', cls: 'bg-emerald-100 text-emerald-700' }
}

const fmt = (iso: string) =>
  new Date(iso).toLocaleString('es-EC', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })

export default function PromotionsEditor({ restaurantId }: Props) {
  const [promos, setPromos] = useState<Promotion[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [msg, setMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null)

  const now = new Date()
  const week = new Date(now.getTime() + 7 * 24 * 3600 * 1000)
  const [title, setTitle] = useState('')
  const [percent, setPercent] = useState(10)
  const [startsAt, setStartsAt] = useState(toLocalInput(now))
  const [endsAt, setEndsAt] = useState(toLocalInput(week))

  // Teléfono del negocio: lo usa el pedido por WhatsApp
  const [phone, setPhone] = useState('')
  const [phoneSaved, setPhoneSaved] = useState('')
  const [phoneBusy, setPhoneBusy] = useState(false)

  useEffect(() => {
    supabase.from('promotions').select('*').eq('restaurant_id', restaurantId).order('ends_at', { ascending: false })
      .then(({ data }) => { setPromos((data as Promotion[]) ?? []); setLoading(false) })
    supabase.from('restaurants').select('phone').eq('id', restaurantId).maybeSingle()
      .then(({ data }) => { const p = (data?.phone as string | null) ?? ''; setPhone(p); setPhoneSaved(p) })
  }, [restaurantId])

  async function savePhone() {
    const clean = phone.trim()
    if (clean && !/^(\+?593|0)9\d{8}$/.test(clean.replace(/[\s-]/g, ''))) {
      setMsg({ type: 'err', text: 'Escribe un celular de Ecuador válido, por ejemplo 0991234567.' })
      return
    }
    setPhoneBusy(true)
    const { error } = await supabase.from('restaurants').update({ phone: clean || null }).eq('id', restaurantId)
    setPhoneBusy(false)
    if (error) { setMsg({ type: 'err', text: 'No se pudo guardar el teléfono.' }); return }
    setPhoneSaved(clean)
    setMsg({ type: 'ok', text: clean ? 'Teléfono guardado.' : 'Teléfono eliminado.' })
  }

  async function create(e: React.FormEvent) {
    e.preventDefault()
    setMsg(null)
    const t = title.trim()
    const s = new Date(startsAt), en = new Date(endsAt)
    if (t.length < 3 || t.length > 60) return setMsg({ type: 'err', text: 'El título debe tener entre 3 y 60 caracteres.' })
    if (!Number.isInteger(percent) || percent < 1 || percent > 70) return setMsg({ type: 'err', text: 'El descuento debe ser un número entero entre 1 y 70.' })
    if (isNaN(s.getTime()) || isNaN(en.getTime()) || en <= s) return setMsg({ type: 'err', text: 'La fecha de fin debe ser posterior a la de inicio.' })
    setSaving(true)
    const { data, error } = await supabase.from('promotions')
      .insert({ restaurant_id: restaurantId, title: t, discount_percent: percent, starts_at: s.toISOString(), ends_at: en.toISOString(), is_active: true })
      .select().single()
    setSaving(false)
    if (error || !data) { setMsg({ type: 'err', text: 'No se pudo crear la promoción. Revisa los datos e intenta de nuevo.' }); return }
    setPromos(p => [data as Promotion, ...p])
    setTitle('')
    setMsg({ type: 'ok', text: 'Promoción creada. Los clientes verán el descuento en el carrito.' })
  }

  async function toggle(p: Promotion) {
    const { error } = await supabase.from('promotions').update({ is_active: !p.is_active }).eq('id', p.id)
    if (error) { setMsg({ type: 'err', text: 'No se pudo cambiar el estado.' }); return }
    setPromos(list => list.map(x => x.id === p.id ? { ...x, is_active: !x.is_active } : x))
  }

  async function remove(p: Promotion) {
    if (!confirm(`¿Eliminar la promoción "${p.title}"?`)) return
    const { error } = await supabase.from('promotions').delete().eq('id', p.id)
    if (error) { setMsg({ type: 'err', text: 'No se pudo eliminar.' }); return }
    setPromos(list => list.filter(x => x.id !== p.id))
  }

  return (
    <div className="space-y-6">
      <SectionHeader
        title="Promociones"
        description="El descuento se aplica solo en los pedidos hechos desde la app y lo asume tu negocio."
      />

      {msg && (
        <div role="status" className={`rounded-lg px-4 py-2 text-sm ${msg.type === 'ok' ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-700'}`}>
          {msg.text}
        </div>
      )}

      <form onSubmit={create} className="card p-4 space-y-3">
        <p className="font-semibold text-sm flex items-center gap-2"><Plus size={16} /> Nueva promoción</p>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="text-xs font-medium sm:col-span-2">
            Título (lo ve el cliente en el inicio)
            <Input value={title} maxLength={60} onChange={e => setTitle(e.target.value)} placeholder="Ej.: Martes de combos" required />
          </label>
          <label className="text-xs font-medium">
            Descuento (%)
            <Input type="number" min={1} max={70} step={1} value={percent} onChange={e => setPercent(Number(e.target.value))} required />
          </label>
          <span className="hidden sm:block" />
          <label className="text-xs font-medium">
            Empieza
            <Input type="datetime-local" value={startsAt} onChange={e => setStartsAt(e.target.value)} required />
          </label>
          <label className="text-xs font-medium">
            Termina
            <Input type="datetime-local" value={endsAt} onChange={e => setEndsAt(e.target.value)} required />
          </label>
        </div>
        <button type="submit" disabled={saving} className="btn-primary disabled:opacity-50">
          {saving ? 'Guardando…' : 'Crear promoción'}
        </button>
      </form>

      {loading ? (
        <Skeleton className="h-24 w-full" />
      ) : promos.length === 0 ? (
        <EmptyState icon={<Tag size={20} />} title="Sin promociones" description="Crea la primera para que aparezca en el inicio de la app." />
      ) : (
        <ul className="space-y-3">
          {promos.map(p => {
            const st = estado(p)
            return (
              <li key={p.id} className="card p-4 flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-semibold truncate">{p.title} <span className="text-emerald-700">-{p.discount_percent}%</span></p>
                  <p className="text-xs text-[var(--color-ink-muted)]">{fmt(p.starts_at)} → {fmt(p.ends_at)}</p>
                  <span className={`inline-block mt-1 text-[11px] font-bold px-2 py-0.5 rounded-full ${st.cls}`}>{st.label}</span>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <button type="button" onClick={() => toggle(p)} className="btn-ghost text-sm" aria-label={p.is_active ? `Apagar ${p.title}` : `Activar ${p.title}`}>
                    {p.is_active ? 'Apagar' : 'Activar'}
                  </button>
                  <button type="button" onClick={() => remove(p)} className="btn-ghost p-2 text-red-600" aria-label={`Eliminar ${p.title}`}>
                    <Trash2 size={16} />
                  </button>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      <div className="card p-4 space-y-2">
        <p className="font-semibold text-sm flex items-center gap-2"><Phone size={16} /> Celular para pedidos por WhatsApp</p>
        <p className="text-xs text-[var(--color-ink-muted)]">
          Los clientes pueden enviarte su carrito por WhatsApp. No está disponible para licorerías ni farmacias.
        </p>
        <div className="flex gap-2">
          <Input value={phone} onChange={e => setPhone(e.target.value)} placeholder="0991234567" inputMode="tel" aria-label="Celular del negocio" />
          <button type="button" onClick={savePhone} disabled={phoneBusy || phone.trim() === phoneSaved} className="btn-primary disabled:opacity-50">
            Guardar
          </button>
        </div>
      </div>
    </div>
  )
}
