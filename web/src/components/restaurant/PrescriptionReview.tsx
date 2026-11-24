import { useState } from 'react'
import { supabase } from '../../lib/supabase'
import { signedUrl } from '../../lib/storage'

/** El negocio revisa la foto de la receta. Aprobar permite aceptar el pedido; rechazar lo cancela. */
export default function PrescriptionReview({ orderId, path }: { orderId: string; path: string | null }) {
  const [url, setUrl]   = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [err, setErr]   = useState('')
  const [note, setNote] = useState('')

  async function view() {
    if (!path) return
    const u = await signedUrl('prescriptions', path)
    if (u) setUrl(u); else setErr('No se pudo abrir la receta.')
  }
  async function review(approve: boolean) {
    if (!approve && !note.trim()) { setErr('Escribe el motivo del rechazo.'); return }
    setBusy(true); setErr('')
    const { error } = await supabase.rpc('review_prescription', { p_order_id: orderId, p_approve: approve, p_note: note.trim() || null })
    setBusy(false)
    if (error) setErr('No se pudo guardar la revisión.')
  }

  return (
    <div className="mx-4 mb-3 rounded-md border border-amber-300 bg-amber-50 p-3 text-xs space-y-2" role="group" aria-label="Revisión de receta">
      <p className="font-semibold text-amber-900">Receta por revisar: sin aprobarla no puedes aceptar el pedido.</p>
      {!url ? (
        <button className="btn-ghost px-3 py-2" onClick={view} disabled={!path}>Ver receta</button>
      ) : (
        <a href={url} target="_blank" rel="noopener noreferrer"><img src={url} alt="Foto de la receta" className="max-h-64 rounded" /></a>
      )}
      <input className="input" placeholder="Motivo (obligatorio si rechazas)" value={note} onChange={e => setNote(e.target.value)} aria-label="Motivo del rechazo" />
      {err && <p role="alert" className="font-semibold text-red-700">{err}</p>}
      <div className="flex gap-2">
        <button className="btn-primary px-3 py-2" disabled={busy} onClick={() => review(true)}>Aprobar receta</button>
        <button className="btn-ghost px-3 py-2 text-red-700" disabled={busy} onClick={() => review(false)}>Rechazar y cancelar</button>
      </div>
    </div>
  )
}