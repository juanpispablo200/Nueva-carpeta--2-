-- ============================================================================
-- JAGOO — SCRIPT DE VERIFICACIÓN OFICIAL DE TAREA 1
-- ============================================================================
-- Ejecuta este script en Supabase SQL Editor para verificar que cada punto
-- retorne `true`.
-- ============================================================================

WITH test_verification AS (
    SELECT
        -- 1. Tablas y columnas requeridas
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'orders' AND column_name = 'courier_id'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'profiles' AND column_name = 'is_available'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_name = 'device_tokens'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_name = 'order_declines'
        ) AS paso1_columnas_y_tablas,

        -- 2. RLS Activo
        (
            SELECT rowsecurity FROM pg_tables WHERE tablename = 'orders' AND schemaname = 'public'
        ) AND (
            SELECT rowsecurity FROM pg_tables WHERE tablename = 'profiles' AND schemaname = 'public'
        ) AND (
            SELECT rowsecurity FROM pg_tables WHERE tablename = 'device_tokens' AND schemaname = 'public'
        ) AND (
            SELECT rowsecurity FROM pg_tables WHERE tablename = 'order_declines' AND schemaname = 'public'
        ) AS paso2_rls_activo,

        -- 3. Función accept_order existe y es SECURITY DEFINER
        EXISTS (
            SELECT 1 FROM pg_proc p
            JOIN pg_namespace n ON p.pronamespace = n.oid
            WHERE n.nspname = 'public'
              AND p.proname = 'accept_order'
              AND p.prosecdef = true
        ) AS paso3_accept_order_secdef,

        -- 4. Trigger de restricción de columnas de repartidor existe
        EXISTS (
            SELECT 1 FROM pg_trigger
            WHERE tgname = 'trg_restrict_courier_updates'
        ) AS paso4_trigger_columnas_repartidor,

        -- 5. Trigger de validación de transiciones existe
        EXISTS (
            SELECT 1 FROM pg_trigger
            WHERE tgname = 'trg_validate_order_status'
        ) AS paso5_trigger_transiciones,

        -- 6. Función de seguridad is_active_available_courier existe
        EXISTS (
            SELECT 1 FROM pg_proc p
            JOIN pg_namespace n ON p.pronamespace = n.oid
            WHERE n.nspname = 'public'
              AND p.proname = 'is_active_available_courier'
        ) AS paso6_funcion_courier_activo
)
SELECT 1 AS paso, 'Tablas (device_tokens, order_declines) y columnas (orders.courier_id, profiles.is_available) creadas' AS descripcion, paso1_columnas_y_tablas AS resultado FROM test_verification
UNION ALL
SELECT 2, 'RLS activo en orders, profiles, device_tokens y order_declines', paso2_rls_activo FROM test_verification
UNION ALL
SELECT 3, 'Función accept_order creada como SECURITY DEFINER en esquema public', paso3_accept_order_secdef FROM test_verification
UNION ALL
SELECT 4, 'Trigger BEFORE UPDATE trg_restrict_courier_updates (bloquea cambios de precio a repartidor)', paso4_trigger_columnas_repartidor FROM test_verification
UNION ALL
SELECT 5, 'Trigger BEFORE UPDATE trg_validate_order_status (valida flujo ready->picked_up->on_the_way->delivered)', paso5_trigger_transiciones FROM test_verification
UNION ALL
SELECT 6, 'Función public.is_active_available_courier (valida is_active y is_available)', paso6_funcion_courier_activo FROM test_verification;
