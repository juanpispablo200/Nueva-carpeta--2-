// ── Tipos compartidos entre restaurante y admin ───────────────────────────────

export type OrderStatus =
  | 'pending'
  | 'accepted'
  | 'preparing'
  | 'on_the_way'
  | 'delivered'
  | 'cancelled'

export interface OrderItem {
  id:                    string
  dish_id:               string
  dish_name:             string
  unit_price:            number
  quantity:              number
  special_instructions:  string
}

export interface Order {
  id:                   string
  customer_id:          string
  restaurant_id:        string
  restaurant_name:      string
  restaurant_logo_url:  string
  subtotal:             number
  delivery_fee:         number
  total:                number
  status:               OrderStatus
  delivery_address:     string | null
  created_at:           string
  items?:               OrderItem[]
}

export interface Dish {
  id:              string
  restaurant_id:   string
  restaurant_name: string
  section_name:    string
  name:            string
  description:     string
  price:           number
  image_url:       string
  is_available:    boolean
  is_popular:      boolean
  is_offer:        boolean
  offer_badge:     string | null
}

export interface Restaurant {
  id:            string
  name:          string
  category:      string
  description:   string
  logo_url:      string
  cover_url:     string
  brand_color:   string
  rating:        number
  review_count:  number
  delivery_time: string
  delivery_fee:  number
  min_order:     number
  is_open:       boolean
  is_featured:   boolean
  owner_id:      string | null
}

export interface Profile {
  id:         string
  full_name:  string
  phone:      string
  role:       'customer' | 'restaurant' | 'admin'
  created_at: string
}

// ── Helpers visuales ──────────────────────────────────────────────────────────

export const STATUS_LABEL: Record<OrderStatus, string> = {
  pending:    'Pendiente',
  accepted:   'Aceptado',
  preparing:  'Preparando',
  on_the_way: 'En camino',
  delivered:  'Entregado',
  cancelled:  'Cancelado',
}

export const STATUS_COLOR: Record<OrderStatus, string> = {
  pending:    'bg-yellow-100 text-yellow-800',
  accepted:   'bg-blue-100 text-blue-800',
  preparing:  'bg-orange-100 text-orange-800',
  on_the_way: 'bg-purple-100 text-purple-800',
  delivered:  'bg-green-100 text-green-800',
  cancelled:  'bg-red-100 text-red-800',
}

// Pasos posibles en los que el restaurante puede avanzar el pedido
export const NEXT_STATUS: Partial<Record<OrderStatus, OrderStatus>> = {
  pending:   'accepted',
  accepted:  'preparing',
  preparing: 'on_the_way',
}
