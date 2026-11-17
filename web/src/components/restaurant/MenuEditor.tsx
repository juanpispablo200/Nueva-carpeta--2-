import { useEffect, useState } from 'react'
import { Plus, Pencil, Trash2, Package } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import type { Dish } from '../../lib/types'
import { Skeleton, EmptyState, Input, SectionHeader } from '../ui'

interface Props { restaurantId: string }

const EMPTY: Omit<Dish,'id'|'restaurant_id'|'restaurant_name'> = {
  section_name:'', name:'', description:'', price:0,
  image_url:'', is_available:true, is_popular:false, is_offer:false, offer_badge:null,
}

export default function MenuEditor({ restaurantId }: Props) {
  const [dishes,  setDishes]  = useState<Dish[]>([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<Dish | null | 'new'>(null)
  const [form,    setForm]    = useState(EMPTY)
  const [saving,  setSaving]  = useState(false)
  const [msg,     setMsg]     = useState<{type:'ok'|'err';text:string}|null>(null)

  useEffect(() => {
    supabase.from('dishes').select('*').eq('restaurant_id', restaurantId).order('section_name')
      .then(({ data }) => { setDishes((data as Dish[]) ?? []); setLoading(false) })
  }, [restaurantId])

  const sections = [...new Set(dishes.map(d => d.section_name))]

  async function toggleAvailable(dish: Dish) {
    await supabase.from('dishes').update({ is_available: !dish.is_available }).eq('id', dish.id)
    setDishes(p => p.map(d => d.id === dish.id ? { ...d, is_available: !d.is_available } : d))
  }

  async function deleteDish(id: string) {
    if (!confirm('¿Eliminar este plato?')) return
    await supabase.from('dishes').delete().eq('id', id)
    setDishes(p => p.filter(d => d.id !== id))
  }

  function openEdit(dish: Dish) {
    setEditing(dish)
    setForm({ section_name:dish.section_name, name:dish.name, description:dish.description,
      price:dish.price, image_url:dish.image_url, is_available:dish.is_available,
      is_popular:dish.is_popular, is_offer:dish.is_offer, offer_badge:dish.offer_badge })
  }

  async function save(e: React.FormEvent) {
    e.preventDefault()
    if (!form.name.trim() || form.price <= 0) { setMsg({type:'err',text:'Nombre y precio son obligatorios'}); return }
    setSaving(true); setMsg(null)
    try {
      if (editing && editing !== 'new') {
        await supabase.from('dishes').update(form).eq('id', editing.id)
        setDishes(p => p.map(d => d.id === (editing as Dish).id ? { ...d, ...form } : d))
      } else {
        const newDish = { ...form, id:`d_${Date.now()}`, restaurant_id:restaurantId, restaurant_name:'' }
        const { data } = await supabase.from('dishes').insert(newDish).select().single()
        if (data) setDishes(p => [...p, data as Dish])
      }
      setEditing(null); setForm(EMPTY)
      setMsg({type:'ok', text:'Guardado correctamente'})
      setTimeout(() => setMsg(null), 3000)
    } catch { setMsg({type:'err', text:'Error al guardar. Intenta de nuevo.'}) }
    finally { setSaving(false) }
  }

  return (
    <div>
      <SectionHeader title="Menú">
        <button className="btn-primary" onClick={() => { setEditing('new'); setForm(EMPTY) }}>
          <Plus size={15}/> Nuevo plato
        </button>
      </SectionHeader>

      {msg && (
        <div className={`mb-4 px-4 py-2.5 rounded-[var(--radius-input)] text-sm font-medium animate-fade-in
          ${msg.type==='ok' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}`}>
          {msg.text}
        </div>
      )}

      {/* Formulario */}
      {editing !== null && (
        <form onSubmit={save} className="card p-5 mb-6 animate-fade-in">
          <p className="font-display font-bold text-[var(--c-ink)] mb-4">
            {editing === 'new' ? 'Nuevo plato' : `Editar: ${(editing as Dish).name}`}
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input required placeholder="Nombre del plato" value={form.name}
              onChange={e => setForm(f => ({...f, name: e.target.value}))} className="sm:col-span-2" />
            <Input placeholder="Sección (ej: Hamburguesas)" value={form.section_name}
              onChange={e => setForm(f => ({...f, section_name: e.target.value}))} />
            <Input type="number" min="0" step="0.01" placeholder="Precio ($)" value={form.price || ''}
              onChange={e => setForm(f => ({...f, price: parseFloat(e.target.value)||0}))} />
            <textarea placeholder="Descripción" value={form.description}
              onChange={e => setForm(f => ({...f, description: e.target.value}))} rows={2}
              className="sm:col-span-2 w-full border border-[var(--c-border)] bg-[var(--c-surface)] text-[var(--c-ink)] text-sm rounded-[var(--radius-input)] px-3 py-2 placeholder:text-[var(--c-ink-subtle)] focus:outline-none focus:ring-2 focus:ring-[var(--c-brand)] resize-none" />
            <Input placeholder="URL de imagen" value={form.image_url}
              onChange={e => setForm(f => ({...f, image_url: e.target.value}))} className="sm:col-span-2" />
          </div>
          <div className="flex gap-4 mt-3 text-sm text-[var(--c-ink-muted)]">
            {(['is_available','is_popular'] as const).map(k => (
              <label key={k} className="flex items-center gap-2 cursor-pointer select-none">
                <input type="checkbox" checked={!!form[k]}
                  onChange={e => setForm(f => ({...f, [k]: e.target.checked}))}
                  className="w-4 h-4 accent-[var(--c-brand)]" />
                {k === 'is_available' ? 'Disponible' : 'Popular'}
              </label>
            ))}
          </div>
          <div className="flex gap-2 mt-4">
            <button type="submit" disabled={saving} className="btn-primary disabled:opacity-50">
              {saving ? 'Guardando…' : 'Guardar plato'}
            </button>
            <button type="button" onClick={() => setEditing(null)} className="btn-ghost">Cancelar</button>
          </div>
        </form>
      )}

      {/* Lista */}
      {loading ? (
        <div className="space-y-2">{Array(4).fill(0).map((_,i) => <Skeleton key={i} className="h-16 w-full rounded-card" />)}</div>
      ) : dishes.length === 0 ? (
        <div className="card">
          <EmptyState icon={<Package size={20}/>} title="Sin platos aún"
            description="Agrega el primer plato con el botón de arriba."
            action={<button className="btn-primary" onClick={() => { setEditing('new'); setForm(EMPTY) }}><Plus size={15}/>Agregar plato</button>} />
        </div>
      ) : (
        sections.map(sec => (
          <div key={sec} className="mb-5">
            <p className="text-xs font-bold uppercase tracking-widest text-[var(--c-ink-subtle)] mb-2 px-1">{sec || 'Sin sección'}</p>
            <div className="card overflow-hidden divide-y divide-[var(--c-border)]">
              {dishes.filter(d => d.section_name === sec).map(dish => (
                <div key={dish.id} className="flex items-center gap-3 px-4 py-3 hover:bg-[var(--c-bg)] transition-colors">
                  {dish.image_url ? (
                    <img src={dish.image_url} alt={dish.name} className="w-11 h-11 rounded-[var(--radius-badge)] object-cover shrink-0" />
                  ) : (
                    <div className="w-11 h-11 rounded-[var(--radius-badge)] bg-[var(--c-bg)] flex items-center justify-center shrink-0">
                      <Package size={18} className="text-[var(--c-ink-subtle)]" />
                    </div>
                  )}
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-[var(--c-ink)] truncate">{dish.name}</p>
                    <p className="text-xs text-[var(--c-brand)] font-bold">${dish.price.toFixed(2)}</p>
                  </div>
                  {/* Toggle disponible */}
                  <label className="relative inline-flex items-center cursor-pointer shrink-0">
                    <input type="checkbox" checked={dish.is_available} onChange={() => toggleAvailable(dish)} className="sr-only peer" />
                    <div className="w-9 h-5 bg-[var(--c-border)] rounded-full peer-checked:bg-[var(--c-brand)] transition-colors after:content-[''] after:absolute after:top-0.5 after:left-0.5 after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:after:translate-x-4" />
                  </label>
                  <button onClick={() => openEdit(dish)} className="p-1.5 rounded hover:bg-[var(--c-bg)] text-[var(--c-ink-subtle)] hover:text-[var(--c-ink)] transition-colors">
                    <Pencil size={14}/>
                  </button>
                  <button onClick={() => deleteDish(dish.id)} className="p-1.5 rounded hover:bg-red-50 text-[var(--c-ink-subtle)] hover:text-red-600 transition-colors">
                    <Trash2 size={14}/>
                  </button>
                </div>
              ))}
            </div>
          </div>
        ))
      )}
    </div>
  )
}
