/**
 * DeleteAccountPage — Cómo solicitar la eliminación de la cuenta y los datos
 * Ruta: /delete-account (pública)
 * Google Play exige una URL pública para solicitar la eliminación de cuenta.
 */
import { Link } from 'react-router-dom'
import { SUPPORT_EMAIL } from '../lib/contact'

const MAILTO =
  `mailto:${SUPPORT_EMAIL}` +
  `?subject=${encodeURIComponent('Solicitud de eliminación de cuenta Jagoo')}` +
  `&body=${encodeURIComponent(
    'Solicito la eliminación de mi cuenta de Jagoo.\n\nEmail con el que me registré: \n\n(Escribe desde ese mismo correo para que podamos verificar tu identidad.)'
  )}`

export default function DeleteAccountPage() {
  return (
    <div className="min-h-screen bg-white">
      <div className="bg-[#1A6B3C] py-10 px-4">
        <div className="max-w-2xl mx-auto">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-10 h-10 rounded-xl bg-[#F5C842] flex items-center justify-center font-bold text-[#1A6B3C] text-lg">
              J
            </div>
            <span className="text-white font-bold text-xl">Jagoo</span>
          </div>
          <h1 className="text-2xl font-bold text-white">Eliminar mi cuenta</h1>
          <p className="text-green-200 text-sm mt-1">Solicitud de eliminación de cuenta y datos</p>
        </div>
      </div>

      <div className="max-w-2xl mx-auto px-4 py-10 space-y-8 text-gray-700 text-sm leading-relaxed">
        <section>
          <h2 className="text-base font-semibold text-gray-900 mb-3">Opción 1: desde la app (inmediata)</h2>
          <ol className="list-decimal ml-5 space-y-1">
            <li>Abre la app Jagoo e inicia sesión.</li>
            <li>Ve a la pestaña <strong>Perfil</strong>.</li>
            <li>Toca <strong>Eliminar mi cuenta</strong> al final de la pantalla.</li>
            <li>Escribe <strong>ELIMINAR</strong> y confirma.</li>
          </ol>
          <p className="mt-2">
            No se puede eliminar la cuenta mientras tengas un pedido en curso. Cuando termine
            (o se cancele), podrás hacerlo.
          </p>
        </section>

        <section>
          <h2 className="text-base font-semibold text-gray-900 mb-3">Opción 2: por correo electrónico</h2>
          <p>
            Si ya no puedes entrar a la app, escríbenos desde el correo con el que te registraste
            y eliminaremos tu cuenta.
          </p>
          <p className="mt-3">
            <a
              href={MAILTO}
              className="inline-block bg-[#1A6B3C] text-white font-semibold rounded-full px-5 py-3"
            >
              Solicitar eliminación por correo
            </a>
          </p>
          <p className="mt-2 text-xs text-gray-500">
            O escribe a{' '}
            <a href={`mailto:${SUPPORT_EMAIL}`} className="text-[#1A6B3C] underline">{SUPPORT_EMAIL}</a>.
            Respondemos en un plazo máximo de 30 días hábiles.
          </p>
        </section>

        <section>
          <h2 className="text-base font-semibold text-gray-900 mb-3">Qué datos se eliminan</h2>
          <ul className="list-disc ml-5 space-y-1">
            <li>Tu cuenta de acceso (correo y contraseña) y tu perfil (nombre, teléfono).</li>
            <li>Tus direcciones guardadas y tus restaurantes favoritos.</li>
            <li>Los comprobantes de pago (imágenes) que subiste.</li>
            <li>Tu historial de notificaciones y el token de notificaciones push.</li>
            <li>Los comentarios escritos en tus calificaciones.</li>
          </ul>
        </section>

        <section>
          <h2 className="text-base font-semibold text-gray-900 mb-3">Qué datos se conservan (anonimizados)</h2>
          <p>
            Por obligaciones contables y fiscales conservamos los registros de pedidos ya
            entregados o cancelados (restaurante, platos, montos y fechas). Se desvinculan de ti:
            sin tu nombre, dirección de entrega, notas ni comprobantes. Las puntuaciones en estrellas
            se conservan sin ningún dato que te identifique.
          </p>
        </section>

        <p className="text-xs text-gray-400 pt-4 border-t border-gray-100">
          Más información en nuestra <Link to="/privacy" className="underline">Política de Privacidad</Link>.
          © {new Date().getFullYear()} Jagoo.
        </p>
      </div>
    </div>
  )
}
