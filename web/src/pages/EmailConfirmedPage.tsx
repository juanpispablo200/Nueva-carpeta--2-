/**
 * EmailConfirmedPage — destino del enlace de confirmación de correo.
 * Ruta: /email-confirmado (pública). Supabase redirige aquí tras verificar el correo.
 */
export default function EmailConfirmedPage() {
  return (
    <div className="min-h-screen bg-white flex items-center justify-center px-4">
      <div className="max-w-md text-center">
        <div className="mx-auto mb-5 w-14 h-14 rounded-2xl bg-[#F5C842] flex items-center justify-center font-bold text-[#1A6B3C] text-2xl">
          J
        </div>
        <h1 className="text-2xl font-bold text-[#1A6B3C]">¡Correo confirmado!</h1>
        <p className="mt-3 text-gray-600 text-sm leading-relaxed">
          Tu cuenta de Jagoo ya está activa. Vuelve a la aplicación e inicia sesión para empezar a pedir.
        </p>
      </div>
    </div>
  )
}
