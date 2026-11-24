/**
 * ResetPasswordPage — destino del enlace "Olvidé mi contraseña".
 * Supabase abre esta URL con una sesión de recuperación (evento PASSWORD_RECOVERY);
 * aquí el usuario define su contraseña nueva. Ruta pública: /reset-password
 */
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { supabase } from '../lib/supabase'

export default function ResetPasswordPage() {
  const navigate = useNavigate()
  const [ready, setReady] = useState(false)
  const [pass, setPass] = useState('')
  const [pass2, setPass2] = useState('')
  const [err, setErr] = useState('')
  const [done, setDone] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const { data: { subscription } } = supabase.auth.onAuthStateChange((event) => {
      if (event === 'PASSWORD_RECOVERY' || event === 'SIGNED_IN') setReady(true)
    })
    // El enlace ya pudo haber creado la sesión antes de montar esta página
    supabase.auth.getSession().then(({ data }) => { if (data.session) setReady(true) })
    return () => subscription.unsubscribe()
  }, [])

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setErr('')
    if (pass.length < 8) { setErr('La contraseña debe tener al menos 8 caracteres.'); return }
    if (pass !== pass2) { setErr('Las contraseñas no coinciden.'); return }
    setBusy(true)
    const { error } = await supabase.auth.updateUser({ password: pass })
    setBusy(false)
    if (error) { setErr('No se pudo cambiar la contraseña. El enlace pudo vencer; pide uno nuevo.'); return }
    setDone(true)
    await supabase.auth.signOut()
    setTimeout(() => navigate('/login', { replace: true }), 2500)
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--color-bg)] px-4">
      <form onSubmit={submit} className="card w-full max-w-sm p-6 space-y-4" aria-labelledby="rp-title">
        <h1 id="rp-title" className="font-display text-lg font-bold">Nueva contraseña</h1>
        {done ? (
          <p role="status" className="text-sm text-green-700">Contraseña actualizada. Te llevamos a iniciar sesión…</p>
        ) : !ready ? (
          <p className="text-sm">Abre esta página desde el enlace del correo de recuperación. Si ya lo hiciste y no avanza, el enlace venció: solicita uno nuevo desde el inicio de sesión.</p>
        ) : (
          <>
            <label className="block text-xs font-semibold">Contraseña nueva
              <input className="input mt-1" type="password" autoComplete="new-password" value={pass} onChange={e => setPass(e.target.value)} required />
            </label>
            <label className="block text-xs font-semibold">Repite la contraseña
              <input className="input mt-1" type="password" autoComplete="new-password" value={pass2} onChange={e => setPass2(e.target.value)} required />
            </label>
            {err && <p role="alert" className="text-sm font-semibold text-red-700">{err}</p>}
            <button className="btn-primary w-full" disabled={busy}>{busy ? 'Guardando…' : 'Guardar contraseña'}</button>
          </>
        )}
      </form>
    </div>
  )
}
