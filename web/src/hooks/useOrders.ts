import { useEffect, useState, useCallback } from 'react'
import { supabase } from '../lib/supabase'
import type { Order, OrderStatus } from '../lib/types'

/**
 * useOrders — carga los pedidos de un restaurante y los mantiene
 * actualizados en tiempo real via Supabase Realtime.
 *
 * @param restaurantId  id del restaurante (ej: "r1")
 */
export function useOrders(restaurantId: string | null) {
  const [orders,  setOrders]  = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [error,   setError]   = useState<string | null>(null)

  // ── Carga inicial ───────────────────────────────────────────────────────────
  const fetchOrders = useCallback(async () => {
    if (!restaurantId) return
    setLoading(true)
    setError(null)
    try {
      const { data, error: err } = await supabase
        .from('orders')
        .select(`
          *,
          items:order_items(*)
        `)
        .eq('restaurant_id', restaurantId)
        .order('created_at', { ascending: false })

      if (err) throw err
      setOrders((data as Order[]) ?? [])
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Error cargando pedidos')
    } finally {
      setLoading(false)
    }
  }, [restaurantId])

  // ── Realtime — escuchar pedidos nuevos y cambios de status ─────────────────
  useEffect(() => {
    if (!restaurantId) return
    fetchOrders()

    const channel = supabase
      .channel(`orders-restaurant-${restaurantId}`)
      .on(
        'postgres_changes',
        {
          event:  '*',           // INSERT, UPDATE, DELETE
          schema: 'public',
          table:  'orders',
          filter: `restaurant_id=eq.${restaurantId}`,
        },
        () => {
          // Al cualquier cambio, recargamos la lista completa
          // (simple y seguro; para miles de pedidos usaríamos merge parcial)
          fetchOrders()
        }
      )
      .subscribe()

    return () => { supabase.removeChannel(channel) }
  }, [restaurantId, fetchOrders])

  // ── Cambiar el status de un pedido ─────────────────────────────────────────
  async function updateStatus(orderId: string, newStatus: OrderStatus) {
    const { error: err } = await supabase
      .from('orders')
      .update({ status: newStatus })
      .eq('id', orderId)

    if (err) throw err
    // Actualizar localmente para feedback inmediato (sin esperar Realtime)
    setOrders(prev =>
      prev.map(o => o.id === orderId ? { ...o, status: newStatus } : o)
    )
  }

  return { orders, loading, error, updateStatus, refetch: fetchOrders }
}
