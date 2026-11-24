/**
 * TermsPage — Términos y condiciones de uso de Jagoo
 * Ruta: /terms (pública)
 *
 * BORRADOR: texto base adaptado a Jagoo. Debe ser revisado por un abogado
 * antes de publicarse. Los puntos marcados con [REVISAR] requieren datos reales.
 */
import { Link } from 'react-router-dom'
import { SUPPORT_EMAIL } from '../lib/contact'

export default function TermsPage() {
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
          <h1 className="text-2xl font-bold text-white">Términos y Condiciones</h1>
          <p className="text-green-200 text-sm mt-1">Versión borrador</p>
        </div>
      </div>

      <div className="max-w-2xl mx-auto px-4 py-10 space-y-8 text-gray-700 text-sm leading-relaxed">
        <div className="rounded-lg border border-amber-300 bg-amber-50 text-amber-900 p-4 text-xs">
          <strong>Borrador pendiente de revisión legal.</strong> Este texto es una base de trabajo y
          no constituye asesoría jurídica. Debe ser revisado por un abogado y completado con los
          datos legales de la empresa antes de su publicación definitiva.
        </div>

        <Section title="1. Quiénes somos y aceptación">
          <p>
            Jagoo es una plataforma de pedidos a domicilio que conecta a clientes con negocios (restaurantes, tiendas, farmacias y licorerías)
            y repartidores independientes en la Amazonía ecuatoriana. Al crear una cuenta o usar la
            aplicación aceptas estos términos y nuestra{' '}
            <Link to="/privacy" className="text-[#1A6B3C] underline">Política de Privacidad</Link>.
            Si no estás de acuerdo, no uses la aplicación.
          </p>
          <p>[REVISAR] Razón social, RUC y domicilio del titular de Jagoo.</p>
        </Section>

        <Section title="2. Tu cuenta">
          <ul>
            <li>Debes ser mayor de 18 años, o contar con autorización de tu representante legal.</li>
            <li>La información que registres debe ser verdadera y estar actualizada.</li>
            <li>Eres responsable de la confidencialidad de tu contraseña y de la actividad de tu cuenta.</li>
            <li>Puedes eliminar tu cuenta desde la app (Perfil → Eliminar mi cuenta) o en{' '}
              <Link to="/delete-account" className="text-[#1A6B3C] underline">/delete-account</Link>.
            </li>
            <li>Podemos suspender cuentas que incumplan estos términos o se usen de forma fraudulenta.</li>
          </ul>
        </Section>

        <Section title="3. Cómo funciona el servicio">
          <ul>
            <li>Jagoo es un intermediario tecnológico. Los alimentos los preparan y venden los restaurantes, y la entrega la realizan repartidores.</li>
            <li>Los precios, menús y disponibilidad los define cada restaurante y pueden cambiar sin previo aviso.</li>
            <li>Un pedido queda confirmado cuando el restaurante lo acepta. Los tiempos de entrega son estimados.</li>
            <li>Debes indicar una dirección de entrega correcta y estar disponible para recibir el pedido.</li>
          </ul>
        </Section>

        <Section title="4. Precios y formas de pago">
          <p>El total del pedido incluye el subtotal de los productos y, si corresponde, el costo de envío, que se muestra antes de confirmar.</p>
          <ul>
            <li><strong>Efectivo:</strong> pagas al repartidor al recibir el pedido. Procura tener el monto exacto o cambio disponible.</li>
            <li><strong>Transferencia bancaria:</strong> debes transferir el monto indicado y subir el comprobante en la app. Tienes 30 minutos desde que haces el pedido: si no subes el comprobante a tiempo, el pedido se cancela automáticamente. El pedido se acepta cuando el pago es verificado. Un comprobante falso o alterado puede causar la cancelación del pedido y la suspensión de la cuenta.</li>
            <li><strong>Solo efectivo y transferencia:</strong> por ahora Jagoo no procesa pagos con tarjeta ni guarda datos de tarjetas.</li>
          </ul>
          <p>[REVISAR] Impuestos aplicables (IVA) y comisiones, según la facturación real.</p>
        </Section>

        <Section title="4 bis. Productos con restricciones (alcohol y medicamentos)">
          <p className="rounded border border-amber-300 bg-amber-50 p-3 text-xs text-amber-900">
            <strong>[REVISAR LEGALMENTE]</strong> La venta y entrega de bebidas alcohólicas y de medicamentos
            está regulada en Ecuador. Un abogado debe confirmar licencias, horarios, autorizaciones sanitarias
            y responsabilidades antes de publicar esta sección.
          </p>
          <ul>
            <li><strong>Bebidas alcohólicas (solo mayores de 18):</strong> para pedirlas debes indicar tu fecha de nacimiento (se registra una sola vez en tu perfil). El repartidor verificará tu cédula al entregar y no entregará el pedido si no puede comprobar que eres mayor de edad o si estás en un estado que lo impida. Jagoo puede cancelar el pedido en ese caso.</li>
            <li><strong>Medicamentos con receta:</strong> solo se piden subiendo una foto de tu receta. La farmacia la revisa y puede aprobarla o rechazarla; mientras no esté aprobada, el pedido no avanza, y si se rechaza el pedido se cancela. Los productos de venta libre no necesitan receta.</li>
            <li>Jagoo no da consejo médico. Lee las indicaciones y consulta a un profesional de la salud.</li>
            <li>El negocio es responsable de contar con los permisos que su actividad exige.</li>
          </ul>
        </Section>

        <Section title="5. Cancelaciones y reembolsos">
          <ul>
            <li>Puedes cancelar un pedido mientras el restaurante no haya comenzado a prepararlo.</li>
            <li>Si el pedido ya está en preparación o en camino, la cancelación puede no ser posible o no generar reembolso.</li>
            <li>Si pagaste por adelantado y el pedido no se entrega por causas ajenas a ti, se te reembolsará el valor pagado por el mismo medio, o por otro acordado contigo.</li>
            <li>Si hay un problema con tu pedido (faltantes, producto equivocado, demora excesiva), contáctanos lo antes posible.</li>
          </ul>
          <p>[REVISAR] Plazos y condiciones exactas de reembolso.</p>
        </Section>

        <Section title="6. Responsabilidades">
          <p><strong>Del cliente:</strong></p>
          <ul>
            <li>Proporcionar datos de contacto y dirección correctos.</li>
            <li>Tratar con respeto al personal del restaurante y al repartidor.</li>
            <li>Informar al restaurante sobre alergias o restricciones alimentarias en las notas del pedido. Jagoo no puede garantizar la ausencia de alérgenos.</li>
          </ul>
          <p className="mt-2"><strong>Del restaurante:</strong> la calidad, higiene, composición y precio de los alimentos, y el cumplimiento de la normativa sanitaria vigente.</p>
          <p className="mt-2"><strong>Del repartidor:</strong> entregar el pedido de forma diligente y devolver correctamente el dinero cobrado en efectivo.</p>
          <p className="mt-2"><strong>De Jagoo:</strong> mantener la plataforma operativa con diligencia razonable. No garantizamos disponibilidad ininterrumpida y no somos responsables por la preparación de los alimentos ni por demoras causadas por clima, mal estado de las vías, falta de conectividad u otras causas fuera de nuestro control, en la medida que la ley lo permita.</p>
        </Section>

        <Section title="7. Uso aceptable">
          <ul>
            <li>No usar la app para actividades ilícitas o fraudulentas.</li>
            <li>No intentar acceder sin autorización a cuentas, sistemas o datos de otros.</li>
            <li>No realizar pedidos falsos ni abusar de promociones.</li>
            <li>No publicar calificaciones o comentarios ofensivos, falsos o discriminatorios.</li>
          </ul>
        </Section>

        <Section title="8. Calificaciones">
          <p>
            Puedes calificar restaurantes y entregas. Podemos moderar o retirar contenido que
            incumpla estos términos.
          </p>
        </Section>

        <Section title="9. Propiedad intelectual">
          <p>
            La marca Jagoo, el diseño y el software de la aplicación son propiedad de su titular.
            No se permite copiarlos ni usarlos sin autorización.
          </p>
        </Section>

        <Section title="10. Datos personales">
          <p>
            Tratamos tus datos personales según nuestra{' '}
            <Link to="/privacy" className="text-[#1A6B3C] underline">Política de Privacidad</Link>.
          </p>
        </Section>

        <Section title="11. Cambios en los términos">
          <p>
            Podemos actualizar estos términos. Los cambios importantes se comunicarán dentro de la
            app. Si continúas usando Jagoo después de la actualización, se entiende que los aceptas.
          </p>
        </Section>

        <Section title="12. Ley aplicable y contacto">
          <p>
            Estos términos se rigen por las leyes de la República del Ecuador.
            [REVISAR] Jurisdicción y mecanismo de resolución de conflictos.
          </p>
          <p>
            Contacto:{' '}
            <a href={`mailto:${SUPPORT_EMAIL}`} className="text-[#1A6B3C] underline">{SUPPORT_EMAIL}</a>
          </p>
        </Section>

        <p className="text-xs text-gray-400 pt-4 border-t border-gray-100">
          © {new Date().getFullYear()} Jagoo. Todos los derechos reservados.
        </p>
      </div>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="[&_ul]:list-disc [&_ul]:ml-5 [&_ul]:space-y-1">
      <h2 className="text-base font-semibold text-gray-900 mb-3">{title}</h2>
      <div className="space-y-2">{children}</div>
    </div>
  )
}
