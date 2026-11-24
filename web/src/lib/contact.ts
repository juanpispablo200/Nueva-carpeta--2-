/**
 * Email de contacto/soporte mostrado en las páginas públicas.
 * Configúralo con VITE_SUPPORT_EMAIL (ver .env.example). El valor por defecto
 * es un PLACEHOLDER: reemplázalo por un buzón real antes de publicar.
 */
export const SUPPORT_EMAIL: string =
  (import.meta.env.VITE_SUPPORT_EMAIL as string | undefined)?.trim() || 'privacidad@jagoo.com'
