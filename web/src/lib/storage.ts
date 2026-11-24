import { supabase } from './supabase'

/**
 * Los comprobantes y las recetas viven en buckets PRIVADOS. Para verlos se pide
 * una URL firmada que vence a los 5 minutos. Los comprobantes antiguos se
 * guardaron como URL completa: esas se devuelven tal cual.
 */
export async function signedUrl(bucket: 'payment-proofs' | 'prescriptions', path: string): Promise<string | null> {
  if (path.startsWith('http')) return path
  const { data, error } = await supabase.storage.from(bucket).createSignedUrl(path, 300)
  return error ? null : data.signedUrl
}