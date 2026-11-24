/**
 * PrivacyPolicyPage — Política de privacidad de Jagoo
 * Ruta: /privacy
 * Requerida por Google Play Console y App Store.
 */
import { Link } from 'react-router-dom'
import { SUPPORT_EMAIL } from '../lib/contact'

export default function PrivacyPolicyPage() {
  return (
    <div className="min-h-screen bg-white">
      {/* Header */}
      <div className="bg-[#1A6B3C] py-10 px-4">
        <div className="max-w-2xl mx-auto">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-10 h-10 rounded-xl bg-[#F5C842] flex items-center justify-center font-bold text-[#1A6B3C] text-lg">
              J
            </div>
            <span className="text-white font-bold text-xl">Jagoo</span>
          </div>
          <h1 className="text-2xl font-bold text-white">Política de Privacidad</h1>
          <p className="text-green-200 text-sm mt-1">Última actualización: enero de 2025</p>
        </div>
      </div>

      {/* Contenido */}
      <div className="max-w-2xl mx-auto px-4 py-10 space-y-8 text-gray-700 text-sm leading-relaxed">

        {/* Intro */}
        <p>
          Jagoo ("nosotros", "nuestra aplicación") es una plataforma de delivery que conecta
          clientes con restaurantes y repartidores. Esta política explica qué información
          recopilamos, cómo la usamos y qué derechos tienes sobre ella.
        </p>

        {/* 1 */}
        <Section title="1. Información que recopilamos">
          <SubSection title="1.1 Información que tú nos proporcionas">
            <ul>
              <li>Nombre completo, dirección de correo electrónico y contraseña (al registrarte)</li>
              <li>Número de teléfono (opcional, para coordinar entregas)</li>
              <li>Dirección de entrega (para procesar pedidos)</li>
              <li>Comprobantes de pago (imágenes de transferencias bancarias)</li>
              <li><strong>Fecha de nacimiento</strong> (solo si pides productos para mayores de 18): se registra una sola vez y se usa únicamente para comprobar que eres mayor de edad.</li>
              <li>Monto con el que pagas en efectivo (para preparar el cambio)</li>
            </ul>
          </SubSection>
          <SubSection title="1.2 Información que recopilamos automáticamente">
            <ul>
              <li>Historial de pedidos y preferencias de restaurantes</li>
              <li>Token de notificaciones push (para enviarte actualizaciones del pedido)</li>
              <li>
                <strong>Ubicación GPS</strong> — solo para repartidores, solo mientras tienen
                un pedido activo, y únicamente para mostrar la posición al cliente durante la entrega.
                Se deja de recopilar automáticamente al marcar el pedido como entregado.
              </li>
            </ul>
          </SubSection>
        </Section>

        {/* 2 */}
        <Section title="2. Cómo usamos tu información">
          <ul>
            <li>Procesar, preparar y entregar tus pedidos</li>
            <li>Enviarte notificaciones push sobre el estado de tu pedido (confirmación, en camino, entregado)</li>
            <li>Verificar comprobantes de pago por transferencia bancaria</li>
            <li>Confirmar tu mayoría de edad al pedir bebidas alcohólicas y permitir que el repartidor verifique tu cédula al entregar</li>
            <li>Preparar el cambio de un pago en efectivo</li>
            <li>Mostrar al cliente la ubicación del repartidor en tiempo real durante la entrega</li>
            <li>Mejorar el servicio mediante análisis de uso agregado y anónimo</li>
            <li>Contactarte si hay un problema con tu pedido</li>
          </ul>
        </Section>

        {/* 3 */}
        <Section title="3. Ubicación en segundo plano (repartidores)">
          <p>
            La aplicación <strong>Jagoo Driver</strong> solicita acceso a la ubicación del
            dispositivo cuando el repartidor acepta un pedido. Esta ubicación se comparte
            con el cliente únicamente durante el tiempo que dura la entrega.
          </p>
          <p className="mt-2">
            La recopilación de ubicación <strong>se detiene automáticamente</strong> cuando el
            repartidor marca el pedido como entregado. No recopilamos ubicación en segundo
            plano fuera de entregas activas. Los repartidores pueden rechazar el permiso
            de ubicación, pero en ese caso el seguimiento en tiempo real no estará disponible.
          </p>
        </Section>

        {/* 4 */}
        <Section title="4. Notificaciones push">
          <p>
            Usamos Firebase Cloud Messaging (FCM) para enviarte notificaciones sobre:
          </p>
          <ul>
            <li>Confirmación de tu pedido</li>
            <li>Actualización del estado (en preparación, listo, en camino)</li>
            <li>Verificación de pago</li>
            <li>Nuevos pedidos disponibles (solo para repartidores)</li>
          </ul>
          <p className="mt-2">
            Puedes desactivar las notificaciones en cualquier momento desde la configuración
            de tu dispositivo. Desactivarlas no afecta tu capacidad de hacer pedidos.
          </p>
        </Section>

        {/* 5 */}
        <Section title="5. Datos de pedidos">
          <p>
            Guardamos el historial de tus pedidos (restaurante, platos, monto, fecha) para:
          </p>
          <ul>
            <li>Mostrarte tu historial dentro de la app</li>
            <li>Resolver disputas o problemas con pedidos anteriores</li>
            <li>Generar reportes operacionales para los restaurantes (datos agregados)</li>
          </ul>
          <p className="mt-2">
            Los restaurantes solo ven los pedidos que les corresponden, nunca el historial
            completo del cliente.
          </p>
        </Section>

        {/* 6 */}
        <Section title="6. Compartir información con terceros">
          <p>Jagoo <strong>no vende</strong> tu información personal. Solo la compartimos con:</p>
          <ul>
            <li>
              <strong>Negocios (restaurantes, tiendas, farmacias y licorerías):</strong> nombre y dirección para preparar y entregar tu pedido
            </li>
            <li>
              <strong>Repartidores:</strong> dirección de entrega y nombre para completar la entrega
            </li>
            <li>
              <strong>Supabase:</strong> proveedor de base de datos y autenticación (servidores en AWS)
            </li>
            <li>
              <strong>Google Firebase:</strong> notificaciones push
            </li>
            <li>
              <strong>Google Maps:</strong> visualización de ubicación del repartidor en el mapa
            </li>
          </ul>
        </Section>

        {/* 7 */}
        <Section title="7. Almacenamiento y seguridad">
          <ul>
            <li>Datos almacenados en Supabase (infraestructura AWS) con cifrado en tránsito (TLS 1.2+)</li>
            <li>Contraseñas cifradas con bcrypt — nunca las almacenamos en texto plano</li>
            <li>Comprobantes de pago en Storage con acceso restringido por rol</li>
            <li>Tokens de sesión de corta duración con renovación automática</li>
          </ul>
        </Section>

        {/* 8 */}
        <Section title="8. Tus derechos">
          <p>Tienes derecho a:</p>
          <ul>
            <li><strong>Acceso:</strong> solicitar una copia de tus datos personales</li>
            <li><strong>Corrección:</strong> actualizar datos incorrectos desde la app (Perfil)</li>
            <li>
              <strong>Eliminación:</strong> eliminar tu cuenta desde la app (Perfil → Eliminar mi
              cuenta) o solicitarlo en{' '}
              <Link to="/delete-account" className="text-[#1A6B3C] underline">/delete-account</Link>.
              Los pedidos pasados se conservan anonimizados por obligación contable.
            </li>
            <li><strong>Portabilidad:</strong> solicitar tus datos en formato legible por máquina</li>
            <li><strong>Oposición:</strong> oponerte al procesamiento de tus datos para marketing</li>
          </ul>
          <p className="mt-2">
            Para ejercer cualquiera de estos derechos, contáctanos en{' '}
            <a href={`mailto:${SUPPORT_EMAIL}`} className="text-[#1A6B3C] underline">
              {SUPPORT_EMAIL}
            </a>
          </p>
        </Section>

        {/* 9 */}
        <Section title="9. Retención de datos">
          <p>Un proceso automático diario aplica estos plazos:</p>
          <ul>
            <li>Datos de cuenta (incluida la fecha de nacimiento): mientras la cuenta esté activa; se borran al eliminarla.</li>
            <li><strong>Fotos de comprobantes de pago:</strong> se borran 365 días después de subirse.</li>
            <li><strong>Datos personales de los pedidos</strong> (cliente, dirección, notas): se anonimizan a los 730 días (2 años).</li>
            <li>Importes, fechas y reparto de los pedidos: se conservan sin datos personales por obligación contable y fiscal (el plazo legal exacto está pendiente de confirmación con un contador).</li>
            <li>Historial de notificaciones: 90 días.</li>
            <li>Última ubicación conocida del repartidor: 24 horas.</li>
            <li>Tokens push: se eliminan automáticamente si el dispositivo deja de usarlos.</li>
          </ul>
        </Section>

        {/* 10 */}
        <Section title="10. Menores de edad">
          <p>
            Jagoo no está dirigido a menores de 13 años. No recopilamos conscientemente
            información de menores. Si detectamos que un menor se ha registrado, eliminaremos
            su cuenta.
          </p>
        </Section>

        {/* 11 */}
        <Section title="11. Cambios a esta política">
          <p>
            Notificaremos cambios significativos mediante una notificación en la app con al
            menos 7 días de anticipación. El uso continuado de Jagoo después de los cambios
            implica aceptación de la política actualizada.
          </p>
        </Section>

        {/* 12 */}
        <Section title="12. Contacto">
          <p>Para cualquier consulta sobre privacidad:</p>
          <ul>
            <li>
              Email:{' '}
              <a href={`mailto:${SUPPORT_EMAIL}`} className="text-[#1A6B3C] underline">
                {SUPPORT_EMAIL}
              </a>
            </li>
            <li>Respuesta en un plazo máximo de 30 días hábiles</li>
          </ul>
        </Section>

        <p className="text-xs text-gray-400 pt-4 border-t border-gray-100">
          © {new Date().getFullYear()} Jagoo. Todos los derechos reservados.
        </p>
      </div>
    </div>
  )
}

// ── Componentes auxiliares ────────────────────────────────────────────────────

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h2 className="text-base font-semibold text-gray-900 mb-3">{title}</h2>
      <div className="space-y-2">{children}</div>
    </div>
  )
}

function SubSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mb-3">
      <h3 className="text-sm font-medium text-gray-800 mb-1.5">{title}</h3>
      <ul className="list-disc ml-5 space-y-1">{children}</ul>
    </div>
  )
}
