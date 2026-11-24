import { useEffect, useRef, useState } from 'react'
import { Plus, Pencil, Trash2, Package, Upload, X, ImageIcon } from 'lucide-react'
import { supabase } from '../../lib/supabase'
import type { Dish } from '../../lib/types'
import { Skeleton, EmptyState, Input, SectionHeader } from '../ui'

interface Props { restaurantId: string }

const EMPTY: Omit<Dish, 'id' | 'restaurant_id' | 'restaurant_name'> = {
  section_name: '', name: '', description: '', price: 0,
  image_url: '', is_available: true, is_popular: false, is_offer: false, offer_badge: null, requires_prescription: false,
}

export default function MenuEditor({ restaurantId }: Props) {
  const [dishes,       setDishes]       = useState<Dish[]>([])
  const [loading,      setLoading]      = useState(true)
  const [editing,      setEditing]      = useState<Dish | null | 'new'>(null)
  const [form,         setForm]         = useState(EMPTY)
  const [saving,       setSaving]       = useState(false)
  const [msg,          setMsg]          = useState<{ type: 'ok' | 'err'; text: string } | null>(null)
  const [imageFile,    setImageFile]    = useState<File | null>(null)
  const [imagePreview, setImagePreview] = useState<string | null>(null)
  const [uploading,    setUploading]    = useState(false)
  // Medicamentos (farmacia): solo venta libre. El tipo del negocio decide si se muestra el aviso.
  const [isPharmacy,   setIsPharmacy]   = useState(false)
  const [otcConfirmed, setOtcConfirmed] = useState(false)
  useEffect(() => {
    supabase.from('restaurants').select('business_type').eq('id', restaurantId).maybeSingle()
      .then(({ data }) => setIsPharmacy(data?.business_type === 'farmacia'))
  }, [restaurantId])
  const fileInputRef = useRef<HTMLInputElement>(null)

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
    setForm({
      section_name: dish.section_name, name: dish.name, description: dish.description,
      price: dish.price, image_url: dish.image_url, is_available: dish.is_available,
      is_popular: dish.is_popular, is_offer: dish.is_offer, offer_badge: dish.offer_badge, requires_prescription: dish.requires_prescription ?? false,
    })
    setImageFile(null)
    setImagePreview(dish.image_url || null)
  }

  function openNew() {
    setEditing('new')
    setForm(EMPTY)
    setImageFile(null)
    setImagePreview(null)
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setImageFile(file)
    setImagePreview(URL.createObjectURL(file))
    setForm(f => ({ ...f, image_url: '' })) // limpiar URL manual
  }

  async function uploadImage(file: File): Promise<string> {
    const ext      = file.name.split('.').pop() ?? 'jpg'
    const path     = `${restaurantId}/${Date.now()}.${ext}`
    const { error } = await supabase.storage.from('menu-images').upload(path, file, {
      contentType: file.type,
      upsert: true,
    })
    if (error) throw error
    const { data } = supabase.storage.from('menu-images').getPublicUrl(path)
    return data.publicUrl
  }

  async function save(e: React.FormEvent) {
    e.preventDefault()
    if (!form.name.trim() || form.price <= 0) {
      setMsg({ type: 'err', text: 'Nombre y precio son obligatorios' })
      return
    }
    if (isPharmacy && !otcConfirmed) {
      setMsg({ type: 'err', text: 'Confirma que el producto es de venta libre (sin receta). Medicamentos con receta no se pueden vender.' })
      return
    }
    setSaving(true); setMsg(null)

    try {
      let finalImageUrl = form.image_url

      // Si hay archivo nuevo, subirlo primero
      if (imageFile) {
        setUploading(true)
        finalImageUrl = await uploadImage(imageFile)
        setUploading(false)
      }

      const payload = { ...form, image_url: finalImageUrl, requires_prescription: false }

      if (editing && editing !== 'new') {
        await supabase.from('dishes').update(payload).eq('id', editing.id)
        setDishes(p => p.map(d => d.id === (editing as Dish).id ? { ...d, ...payload } : d))
      } else {
        const newDish = { ...payload, id: `d_${Date.now()}`, restaurant_id: restaurantId, restaurant_name: '' }
        const { data } = await supabase.from('dishes').insert(newDish).select().single()
        if (data) setDishes(p => [...p, data as Dish])
      }

      setEditing(null)
      setForm(EMPTY)
      setImageFile(null)
      setImagePreview(null)
      setMsg({ type: 'ok', text: 'Guardado correctamente' })
      setTimeout(() => setMsg(null), 3000)
    } catch (err: any) {
      setUploading(false)
      setMsg({ type: 'err', text: err.message ?? 'Error al guardar. Intenta de nuevo.' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <SectionHeader title="Menú">
        <button className="btn-primary" onClick={openNew}>
          <Plus size={15} /> Nuevo plato
        </button>
      </SectionHeader>

      {msg && (
        <div className={`mb-4 px-4 py-2.5 rounded-[var(--radius-md)] text-sm font-medium animate-in
          ${msg.type === 'ok' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}`}>
          {msg.text}
        </div>
      )}

      {/* Formulario de edición */}
      {editing !== null && (
        <form onSubmit={save} className="card p-5 mb-6 animate-in">
          <div className="flex items-center justify-between mb-4">
            <p className="font-display font-bold text-[var(--color-ink)]">
              {editing === 'new' ? 'Nuevo plato' : `Editar: ${(editing as Dish).name}`}
            </p>
            <button type="button" onClick={() => setEditing(null)} className="btn-ghost p-1">
              <X size={16} />
            </button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Imagen */}
            <div className="sm:col-span-2">
              <p className="text-xs font-semibold text-[var(--color-ink-sub)] mb-2">Imagen del plato</p>
              <div className="flex gap-3 items-start">
                {/* Preview */}
                <div
                  className="w-24 h-24 rounded-[var(--radius-lg)] border-2 border-dashed border-[var(--color-border)] flex items-center justify-center overflow-hidden shrink-0 cursor-pointer hover:border-[var(--color-brand-700)] transition-colors"
                  onClick={() => fileInputRef.current?.click()}
                >
                  {imagePreview ? (
                    <img src={imagePreview} alt="Preview" className="w-full h-full object-cover" />
                  ) : (
                    <ImageIcon size={28} className="text-[var(--color-ink-sub)]" />
                  )}
                </div>

                <div className="flex-1">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    onChange={handleFileChange}
                    className="hidden"
                  />
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    className="flex items-center gap-2 px-3 py-2 rounded-[var(--radius-md)] border border-[var(--color-border)] text-sm text-[var(--color-ink-muted)] hover:border-[var(--color-brand-700)] hover:text-[var(--color-ink)] transition-colors mb-2"
                  >
                    <Upload size={14} />
                    {imageFile ? 'Cambiar imagen' : 'Subir imagen'}
                  </button>
                  {imageFile && (
                    <p className="text-xs text-[var(--color-ink-sub)]">{imageFile.name}</p>
                  )}
                  <p className="text-xs text-[var(--color-ink-sub)] mt-1">O pega una URL:</p>
                  <Input
                    placeholder="https://..."
                    value={form.image_url}
                    onChange={e => {
                      setForm(f => ({ ...f, image_url: e.target.value }))
                      if (e.target.value) {
                        setImageFile(null)
                        setImagePreview(e.target.value)
                      }
                    }}
                    className="mt-1 text-xs"
                  />
                </div>
              </div>
            </div>

            <Input
              required
              placeholder="Nombre del plato *"
              value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              className="sm:col-span-2"
            />

            <Input
              placeholder="Sección (ej: Hamburguesas, Bebidas)"
              value={form.section_name}
              onChange={e => setForm(f => ({ ...f, section_name: e.target.value }))}
            />

            <Input
              type="number"
              min="0"
              step="0.01"
              placeholder="Precio ($) *"
              value={form.price || ''}
              onChange={e => setForm(f => ({ ...f, price: parseFloat(e.target.value) || 0 }))}
            />

            <textarea
              placeholder="Descripción del plato"
              value={form.description}
              onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
              rows={2}
              className="sm:col-span-2 w-full border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-ink)] text-sm rounded-[var(--radius-md)] px-3 py-2 placeholder:text-[var(--color-ink-muted)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-700)] resize-none"
            />

            <Input
              placeholder="Badge oferta (ej: -20%)"
              value={form.offer_badge ?? ''}
              onChange={e => setForm(f => ({ ...f, offer_badge: e.target.value || null }))}
            />
          </div>

          {/* Toggles */}
          {isPharmacy && (
            <div className="mt-4 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" role="note">
              <p className="font-semibold">Solo medicamentos de venta libre, sin receta</p>
              <label className="mt-2 flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={otcConfirmed} onChange={e => setOtcConfirmed(e.target.checked)} className="w-4 h-4" />
                Confirmo que este producto es de venta libre y no requiere receta médica
              </label>
            </div>
          )}

          <div className="flex flex-wrap gap-5 mt-4">
            {([
              ['is_available', 'Disponible'],
              ['is_popular',   'Popular'],
              ['is_offer',     'En oferta'],
            ] as const).map(([key, label]) => (
              <label key={key} className="flex items-center gap-2 cursor-pointer select-none text-sm text-[var(--color-ink-muted)]">
                <input
                  type="checkbox"
                  checked={!!form[key]}
                  onChange={e => setForm(f => ({ ...f, [key]: e.target.checked }))}
                  className="w-4 h-4 accent-[var(--color-brand-700)]"
                />
                {label}
              </label>
            ))}
          </div>

          <div className="flex gap-2 mt-5">
            <button type="submit" disabled={saving || uploading} className="btn-primary disabled:opacity-50">
              {uploading ? (
                <><span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />Subiendo imagen…</>
              ) : saving ? (
                <><span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />Guardando…</>
              ) : (
                'Guardar plato'
              )}
            </button>
            <button type="button" onClick={() => setEditing(null)} className="btn-ghost">
              Cancelar
            </button>
          </div>
        </form>
      )}

      {/* Lista de platos */}
      {loading ? (
        <div className="space-y-2">
          {Array(4).fill(0).map((_, i) => <Skeleton key={i} className="h-16 w-full" />)}
        </div>
      ) : dishes.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={<Package size={20} />}
            title="Sin platos aún"
            description="Agrega el primer plato con el botón de arriba."
            action={
              <button className="btn-primary" onClick={openNew}>
                <Plus size={15} /> Agregar plato
              </button>
            }
          />
        </div>
      ) : (
        sections.map(sec => (
          <div key={sec} className="mb-5">
            <p className="text-xs font-bold uppercase tracking-widest text-[var(--color-ink-sub)] mb-2 px-1">
              {sec || 'Sin sección'}
            </p>
            <div className="card overflow-hidden divide-y divide-[var(--color-border)]">
              {dishes.filter(d => d.section_name === sec).map(dish => (
                <div
                  key={dish.id}
                  className="flex items-center gap-3 px-4 py-3 hover:bg-[var(--color-bg)] transition-colors"
                >
                  {/* Imagen */}
                  {dish.image_url ? (
                    <img
                      src={dish.image_url}
                      alt={dish.name}
                      className="w-12 h-12 rounded-[var(--radius-md)] object-cover shrink-0"
                    />
                  ) : (
                    <div className="w-12 h-12 rounded-[var(--radius-md)] bg-[var(--color-bg)] flex items-center justify-center shrink-0">
                      <Package size={18} className="text-[var(--color-ink-sub)]" />
                    </div>
                  )}

                  {/* Info */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-semibold text-[var(--color-ink)] truncate">{dish.name}</p>
                      {dish.is_popular && (
                        <span className="text-xs px-1.5 py-0.5 rounded-full bg-amber-100 text-amber-700 font-medium shrink-0">
                          Popular
                        </span>
                      )}
                      {dish.is_offer && dish.offer_badge && (
                        <span className="text-xs px-1.5 py-0.5 rounded-full bg-red-100 text-red-600 font-medium shrink-0">
                          {dish.offer_badge}
                        </span>
                      )}
                    </div>
                    <p className="text-xs font-bold text-[var(--color-brand-700)]">${dish.price.toFixed(2)}</p>
                  </div>

                  {/* Toggle disponible */}
                  <label className="relative inline-flex items-center cursor-pointer shrink-0" title={dish.is_available ? 'Disponible' : 'No disponible'}>
                    <input
                      type="checkbox"
                      checked={dish.is_available}
                      onChange={() => toggleAvailable(dish)}
                      className="sr-only peer"
                    />
                    <div className="w-9 h-5 bg-[var(--color-border)] rounded-full peer-checked:bg-[var(--color-brand-700)] transition-colors after:content-[''] after:absolute after:top-0.5 after:left-0.5 after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:after:translate-x-4" />
                  </label>

                  <button
                    onClick={() => openEdit(dish)}
                    className="p-1.5 rounded hover:bg-[var(--color-bg)] text-[var(--color-ink-sub)] hover:text-[var(--color-ink)] transition-colors"
                    title="Editar"
                  >
                    <Pencil size={14} />
                  </button>
                  <button
                    onClick={() => deleteDish(dish.id)}
                    className="p-1.5 rounded hover:bg-red-50 text-[var(--color-ink-sub)] hover:text-red-600 transition-colors"
                    title="Eliminar"
                  >
                    <Trash2 size={14} />
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
