import { useEffect, useState, useCallback, useRef } from 'react'
import { supabase } from '../lib/supabase'
import type { Order, OrderStatus } from '../lib/types'

/**
 * useOrders — carga los pedidos de un restaurante y los mantiene
 * actualizados en tiempo real via Supabase Realtime.
 * Reproduce sonido cuando llega un pedido nuevo.
 *
 * @param restaurantId  id del restaurante (ej: "r1")
 */
export function useOrders(restaurantId: string | null) {
  const [orders,  setOrders]  = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [error,   setError]   = useState<string | null>(null)
  const prevPendingCount = useRef(0)

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
      const newOrders = (data as Order[]) ?? []
      setOrders(newOrders)

      // Inicializar contador de pending
      prevPendingCount.current = newOrders.filter(o => o.status === 'pending').length
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
        (payload) => {
          // Si es INSERT, reproducir sonido
          if (payload.eventType === 'INSERT') {
            const newOrder = payload.new as Order
            if (newOrder.status === 'pending') {
              // Reproducir sonido
              const audio = new Audio('/sounds/notification.mp3')
              audio.play().catch(() => {}) // ignorar error si no hay permiso
            }
          }
          // Recargar lista completa
          fetchOrders()
        }
      )
      .subscribe()

    return () => {
      channel.unsubscribe()
      supabase.removeChannel(channel)
    }
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

