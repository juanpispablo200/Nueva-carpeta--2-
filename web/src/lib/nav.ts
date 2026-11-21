import {
  LayoutDashboard, ShoppingBag, Bike, Store,
  Users, BarChart2, Settings, UtensilsCrossed, BookOpen, FileText, CreditCard, Star,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { UserRole } from './supabase'

export interface NavItem {
  key:   string
  label: string
  icon:  LucideIcon
  path:  string        // ruta relativa dentro del panel (para highlight)
}

export const NAV: Record<UserRole, NavItem[]> = {
  admin: [
    { key: 'resumen',          label: 'Resumen',          icon: LayoutDashboard, path: '/admin'                   },
    { key: 'pedidos',          label: 'Pedidos',          icon: ShoppingBag,     path: '/admin/pedidos'           },
    { key: 'pagos',            label: 'Pagos',            icon: CreditCard,      path: '/admin/pagos'             },
    { key: 'repartidores',     label: 'Repartidores',     icon: Bike,            path: '/admin/repartidores'      },
    { key: 'restaurantes',     label: 'Restaurantes',     icon: Store,           path: '/admin/restaurantes'      },
    { key: 'calificaciones',   label: 'Calificaciones',   icon: Star,            path: '/admin/calificaciones'    },
    { key: 'usuarios',         label: 'Usuarios',         icon: Users,           path: '/admin/usuarios'          },
    { key: 'reportes',         label: 'Reportes',         icon: BarChart2,       path: '/admin/reportes'          },
    { key: 'auditoria',        label: 'Auditoría',        icon: FileText,        path: '/admin/auditoria'         },
    { key: 'ajustes',          label: 'Ajustes',          icon: Settings,        path: '/admin/ajustes'           },
  ],
  restaurant: [
    { key: 'pedidos', label: 'Pedidos', icon: UtensilsCrossed, path: '/restaurant'      },
    { key: 'menu',    label: 'Menú',    icon: BookOpen,         path: '/restaurant/menu' },
  ],
  repartidor: [],   // repartidor usa app Android; la web es solo vista de emergencia
  customer:   [],   // customers no tienen panel web
}

/** Ruta raíz de cada rol (a dónde redirige el login) */
export const ROLE_HOME: Record<UserRole, string> = {
  admin:      '/admin',
  restaurant: '/restaurant',
  repartidor: '/acceso-denegado',   // repartidores usan la app Driver, no el panel web
  customer:   '/acceso-denegado',
}
