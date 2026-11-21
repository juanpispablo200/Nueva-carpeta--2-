# Jagoo — Guía de Migraciones SQL

## Dónde están
Todas las migraciones viven en `/supabase/migrations/` con prefijo numérico que define el orden de aplicación.

## Cómo aplicar
1. Abre el [SQL Editor de Supabase](https://supabase.com/dashboard/project/tjgavwygdotetoatitju/sql/new)
2. Copia el contenido del archivo en orden
3. Ejecuta con **Ctrl+Enter**
4. Verifica que no aparezcan errores

> Todas las migraciones son **idempotentes**: puedes ejecutarlas múltiples veces sin romper datos.

---

## Orden de aplicación

| # | Archivo | Qué hace | Estado |
|---|---------|----------|--------|
| 1 | `20240101000001_schema.sql` | Esquema base: tablas principals (profiles, restaurants, dishes, orders, order_items, order_events) | ✅ Aplicar primero |
| 2 | `20240101000002_seed.sql` | Datos iniciales de ejemplo (restaurantes, platos) | ✅ Opcional en prod |
| 3 | `20240101000003_rls.sql` | Row Level Security básico inicial | ✅ Reemplazado por 20240301 |
| 4 | `20240101000004_repartidores.sql` | Tabla `repartidores`, `device_tokens`, extensión de estados de orden, RPC `accept_order` v1 (firma antigua) | ✅ Aplicar — la RPC se reemplaza en Fase 1 |
| 5 | `20240101000005_webhook_fcm.sql` | Webhook para notificaciones FCM (Firebase) | ✅ Aplicar si usas push |
| 6 | `20240201000001_order_events.sql` | Tabla `order_events` para historial de estados | ✅ Aplicar |
| 7 | `20240201000002_security.sql` | Mejoras de seguridad adicionales | ✅ Aplicar |
| 8 | `20240301000001_mejoras_web.sql` | RLS completo, `audit_log`, `is_active`, `courier_id`, triggers de transición y auditoría | ✅ Aplicar |
| 9 | `20240401000001_driver_phase1.sql` | **Fase 1**: `profiles.is_available`, RPC `accept_order` corregida (1 param), CHECK audit_log, política RLS disponibilidad | ✅ Aplicar |
| 10 | `20240401000002_coupons.sql` | **Fase 2**: Tabla `coupons`, RPC `validate_coupon`, RPC `apply_coupon`, seed de cupones BIENVENIDO y JAGOO10 | ✅ Aplicar |
| 11 | `20240401000003_payments_manual.sql` | **Fase 3**: Tabla `payment_proofs`, columnas `payment_method`/`payment_status` en `orders`, RPC `upload_payment_proof`, RPC `verify_payment_proof`, datos bancarios en `settings` | ✅ Aplicar |
| 12 | `20240401000004_earnings_storage.sql` | **Fase 4**: Tabla `settings` (con seed completo), RPC `get_driver_earnings`, bucket `payment-proofs` en Storage, políticas RLS de Storage | ✅ Aplicar |

> **Fase 5** no requiere migración SQL — solo cambios en app cliente, driver y panel web.

| 13 | `20240401000005_push_notifications.sql` | **Fase 6**: `profiles.fcm_token`, tabla `notification_log`, función `notify_order_status_change`, trigger `trg_order_status_notify` en `orders` | ✅ Aplicar |
| 14 | `20240401000006_gps_tracking.sql`        | **GPS**: `profiles.current_lat/lng`, RPC `update_driver_location`, RPC `get_driver_location`, política RLS | ✅ Aplicar |
| 15 | `20240401000007_kushki_payments.sql`     | **Kushki**: tabla `kushki_transactions`, RPC `register_kushki_charge`, settings `kushki_public_key` | ✅ Aplicar |
| 16 | `20240401000008_menu_images.sql`         | **Menú**: bucket `menu-images` público en Storage, políticas de upload/delete | ✅ Aplicar |
| 17 | `20240401000009_ratings.sql`             | **Calificaciones**: tabla `ratings`, vista `restaurants_avg_rating`, RPC `submit_rating`, RPC `get_order_rating` | ✅ Aplicar |
| 18 | `20240401000010_seed_users.sql`           | **Seed usuarios**: admin@jagoo.com / Admin2024! y restaurante@jagoo.com / Rest2024! | ✅ Aplicar |
| 19 | `20240401000011_restaurant_user_fix.sql`  | **Fixes críticos**: `create_restaurant_user` RPC, `get_my_role()` sin recursión RLS, `owner_id` en restaurants, políticas RLS corregidas | ✅ **NUEVO — Aplicar primero** |

---

## Archivos SQL sueltos en la raíz (histórico)

Estos archivos fueron usados durante el desarrollo para correcciones puntuales.
Su contenido ya está consolidado en las migraciones numeradas de arriba.
**No aplicar manualmente** — están aquí solo como referencia histórica.

| Archivo | Contenido | Dónde quedó |
|---------|-----------|-------------|
| `APLICAR_ESTA_MIGRACION.sql` | Primera versión de mejoras web | → `20240301000001_mejoras_web.sql` |
| `EJECUTAR_EN_SUPABASE.sql` | Ajustes de RLS tempranos | → `20240201000002_security.sql` |
| `MIGRACION_COMPLETA_V2.sql` | Migración V2 con audit_log y settings | → `20240401000001_driver_phase1.sql` |
| `ARREGLAR_PERMISOS_REPARTIDORES.sql` | Corrección de función sin columna email | → `20240401000001_driver_phase1.sql` |
| `ARREGLAR_FUNCION_COURIER.sql` | Función create_courier_profile sin email | → `20240401000001_driver_phase1.sql` |
| `CREAR_ADMIN_SIMPLE.sql` | Script de creación de usuario admin | Usar solo en setup inicial |
| `REPARAR_REPARTIDORES.sql` | Corrección de perfiles faltantes | → Correr una sola vez si hay repartidores sin perfil |
| `RLS_DRIVER.sql` | RLS para app Driver | → `20240301000001_mejoras_web.sql` + `20240401000001_driver_phase1.sql` |
| `RLS_REPARTIDOR.sql` | Versión anterior de RLS repartidor | → Reemplazado |
| `RESET_COMPLETO_JAGOO.sql` | Reset total de BD (⚠️ borra todos los datos) | Solo para dev/testing |
| `ULTIMO_SQL_EJECUTAR.sql` | Parches varios | → Consolidados |
| `CORRECCION_PASOS_8_9_11.sql` | Corrección triggers y RLS | → `20240401000001_driver_phase1.sql` |

---

## Variables de entorno requeridas

### App cliente Android (`local.properties`)
```
SUPABASE_URL=https://tjgavwygdotetoatitju.supabase.co
SUPABASE_ANON_KEY=eyJ...
```

### App Driver Android (`local.properties`)
```
SUPABASE_URL=https://tjgavwygdotetoatitju.supabase.co
SUPABASE_ANON_KEY=eyJ...
```

### Panel web (`web/.env`)
```
VITE_SUPABASE_URL=https://tjgavwygdotetoatitju.supabase.co
VITE_SUPABASE_ANON_KEY=eyJ...
```

> ⚠️ Nunca commitees `local.properties` ni `.env`. Ambos están en `.gitignore`.

---

## Tabla `settings` (configuración del sistema)

Después de aplicar la migración V2, la tabla `settings` tiene estos valores por defecto:

| Clave | Valor por defecto | Descripción |
|-------|------------------|-------------|
| `platform_name` | `Jagoo` | Nombre de la plataforma |
| `default_delivery_fee` | `2.50` | Tarifa de envío por defecto ($) |
| `maintenance_mode` | `false` | Modo mantenimiento |
| `global_schedule` | `08:00-22:00` | Horario global de operación |
| `driver_fee_per_delivery` | `1.50` | Ganancia del repartidor por entrega ($) |
