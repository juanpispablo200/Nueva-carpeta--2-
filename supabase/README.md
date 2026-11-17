# Jagoo — Base de datos Supabase

## Cómo ejecutar los archivos

1. Ve a tu proyecto en https://supabase.com
2. En el menú izquierdo, haz clic en **SQL Editor**
3. Haz clic en **New query**
4. Pega el contenido de `migrations/001_schema.sql` y presiona **Run**
5. Espera que diga "Success" y luego crea otra query nueva
6. Pega el contenido de `migrations/002_seed.sql` y presiona **Run**

## ¿Qué crea cada archivo?

| Archivo | Qué hace |
|---|---|
| `001_schema.sql` | Crea las 8 tablas + trigger de registro automático |
| `002_seed.sql` | Inserta los 7 restaurantes y 25 platos de prueba |

## Tablas creadas

| Tabla | Equivalente SQLite | Para qué sirve |
|---|---|---|
| `profiles` | roles + users | Datos del usuario + su rol |
| `restaurants` | restaurants | Los restaurantes |
| `menu_sections` | menu_sections | Secciones del menú (Hamburguesas, Postres...) |
| `dishes` | dishes + option_groups + options | Platos con sus opciones en JSON |
| `addresses` | addresses | Direcciones de entrega del cliente |
| `orders` | orders | Pedidos con su estado actual |
| `order_items` | order_items | Los platos dentro de cada pedido |

## El ciclo de vida de un pedido (campo `status`)

```
pending → accepted → preparing → on_the_way → delivered
                                             ↘ cancelled
```

- **pending**: el cliente confirma y espera que el restaurante acepte
- **accepted**: el restaurante aceptó
- **preparing**: en cocina
- **on_the_way**: el repartidor salió
- **delivered**: entregado con éxito
- **cancelled**: cancelado en cualquier paso

## Roles de usuario (campo `role` en profiles)

- `customer` → usa la app Android (todos los nuevos usuarios son customer)
- `restaurant` → accede al panel web para ver pedidos y editar menú
- `admin` → accede al panel web para gestionar todo

**El rol NUNCA lo elige el usuario.** Lo asigna el administrador manualmente
desde el SQL Editor:
```sql
UPDATE profiles SET role = 'restaurant' WHERE id = 'uuid-del-usuario';
```
