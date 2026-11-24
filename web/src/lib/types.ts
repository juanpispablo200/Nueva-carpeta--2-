// ── Tipos compartidos entre restaurante y admin ───────────────────────────────

export type OrderStatus =
  | 'pending'
  | 'accepted'
  | 'preparing'
  | 'ready'
  | 'picked_up'
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
  courier_id:           string | null
  created_at:           string
  items?:               OrderItem[]
  payment_method?:      'cash' | 'transfer'
  payment_status?:      'pending' | 'verifying' | 'verified' | 'rejected'
  cash_tendered?:       number | null
  change_due?:          number | null
  business_type?:       BusinessType
  prescription_required?: boolean
  prescription_status?: 'none' | 'pending' | 'approved' | 'rejected'
  prescription_path?:   string | null
  age_check_required?:  boolean
  age_verified_at?:     string | null
  business_amount?:     number | null
  driver_amount?:       number | null
  platform_amount?:     number | null
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
  /** Solo farmacias: el producto exige receta. Si es false, es de venta libre. */
  requires_prescription?: boolean
  /** Lo fija el servidor: todo producto de una licorería es +18. */
  is_age_restricted?: boolean
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
  business_type: BusinessType
  permit_number: string | null
  is_active:     boolean
}

export type BusinessType = 'restaurante' | 'tienda' | 'farmacia' | 'licoreria'

export const BUSINESS_TYPES: { key: BusinessType; label: string }[] = [
  { key: 'restaurante', label: 'Restaurante' },
  { key: 'tienda',      label: 'Tienda' },
  { key: 'farmacia',    label: 'Farmacia' },
  { key: 'licoreria',   label: 'Licorer\u00eda' },
]

export const BUSINESS_LABEL: Record<BusinessType, string> = {
  restaurante: 'Restaurante', tienda: 'Tienda', farmacia: 'Farmacia', licoreria: 'Licorer\u00eda',
}

export interface Profile {
  id:         string
  full_name:  string
  phone:      string
  role:       'customer' | 'restaurant' | 'admin' | 'repartidor'
  is_active:  boolean
  created_at?: string
}

export interface OrderEvent {
  id:          string
  order_id:    string
  from_status: OrderStatus | null
  to_status:   OrderStatus
  actor_id:    string | null
  actor_role:  string | null
  note:        string | null
  created_at:  string
}

// ── Helpers visuales ──────────────────────────────────────────────────────────

export const STATUS_LABEL: Record<OrderStatus, string> = {
  pending:    'Pendiente',
  accepted:   'Aceptado',
  preparing:  'Preparando',
  ready:      'Listo',
  picked_up:  'Recogido',
  on_the_way: 'En camino',
  delivered:  'Entregado',
  cancelled:  'Cancelado',
}

export const STATUS_COLOR: Record<OrderStatus, string> = {
  pending:    'bg-yellow-100 text-yellow-800',
  accepted:   'bg-blue-100 text-blue-800',
  preparing:  'bg-orange-100 text-orange-800',
  ready:      'bg-purple-100 text-purple-800',
  picked_up:  'bg-indigo-100 text-indigo-800',
  on_the_way: 'bg-violet-100 text-violet-800',
  delivered:  'bg-green-100 text-green-800',
  cancelled:  'bg-red-100 text-red-800',
}

// Pasos posibles en los que el restaurante puede avanzar el pedido
export const NEXT_STATUS: Partial<Record<OrderStatus, OrderStatus>> = {
  pending:   'accepted',
  accepted:  'preparing',
  preparing: 'ready',
}
