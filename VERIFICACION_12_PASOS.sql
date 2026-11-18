-- ══════════════════════════════════════════════════════════════════════════════
-- VERIFICACIÓN DE LOS 12 PASOS — tabla paso | resultado
-- Ejecutar en Supabase SQL Editor después de aplicar todas las migraciones
-- ══════════════════════════════════════════════════════════════════════════════

SELECT paso, descripcion, resultado FROM (

  -- PASO 6: orders.courier_id existe (asignación de repartidor posible)
  SELECT
    6 AS paso,
    'orders.courier_id existe como columna FK' AS descripcion,
    EXISTS(
      SELECT 1 FROM information_schema.columns
      WHERE table_name = 'column_name' AND column_name = 'courier_id'
      -- corrección: buscar en la tabla correcta
    ) AS resultado

  UNION ALL SELECT 6, 'orders.courier_id existe',
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_name = 'orders' AND column_name = 'courier_id')

  -- PASO 7: RLS orders_select existe y cubre courier_id = auth.uid()
  UNION ALL SELECT 7, 'policy orders_select existe',
    EXISTS(SELECT 1 FROM pg_policies
           WHERE tablename = 'orders' AND policyname = 'orders_select')

  UNION ALL SELECT 7, 'policy orders_select cubre courier_id',
    EXISTS(SELECT 1 FROM pg_policies
           WHERE tablename = 'orders' AND policyname = 'orders_select'
             AND qual LIKE '%courier_id%')

  -- PASO 8: Trigger de validación de transiciones existe
  UNION ALL SELECT 8, 'trigger trg_validate_order_transition existe',
    EXISTS(SELECT 1 FROM information_schema.triggers
           WHERE trigger_name = 'trg_validate_order_transition')

  -- PASO 9: Trigger de audit_log para cambios de estado existe
  UNION ALL SELECT 9, 'trigger trg_log_order_status existe',
    EXISTS(SELECT 1 FROM information_schema.triggers
           WHERE trigger_name = 'trg_log_order_status')

  UNION ALL SELECT 9, 'tabla audit_log existe',
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_name = 'audit_log')

  UNION ALL SELECT 9, 'audit_log.entity columna existe',
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_name = 'audit_log' AND column_name = 'entity')

  -- PASO 10: profiles.is_active existe (para desactivar repartidor)
  UNION ALL SELECT 10, 'profiles.is_active existe',
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_name = 'profiles' AND column_name = 'is_active')

  UNION ALL SELECT 10, 'profiles_update policy existe (solo admin)',
    EXISTS(SELECT 1 FROM pg_policies
           WHERE tablename = 'profiles' AND policyname = 'profiles_update')

  -- PASO 11: profiles_select bloquea usuarios inactivos
  UNION ALL SELECT 11, 'profiles_select tiene condicion is_active',
    EXISTS(SELECT 1 FROM pg_policies
           WHERE tablename = 'profiles' AND policyname = 'profiles_select'
             AND qual LIKE '%is_active%')

  UNION ALL SELECT 11, 'trigger trg_log_profile_deactivation existe',
    EXISTS(SELECT 1 FROM information_schema.triggers
           WHERE trigger_name = 'trg_log_profile_deactivation')

  -- PASO 12: repartidores con perfil correcto
  UNION ALL SELECT 12, 'al menos 1 repartidor activo con perfil',
    EXISTS(SELECT 1 FROM profiles WHERE role = 'repartidor' AND is_active = true)

  UNION ALL SELECT 12, 'cero repartidores sin perfil en auth.users',
    NOT EXISTS(
      SELECT 1 FROM auth.users u
      LEFT JOIN profiles p ON p.id = u.id
      WHERE p.id IS NULL
        AND u.raw_user_meta_data->>'role' = 'repartidor'
    )

) sub
ORDER BY paso, descripcion;

-- ─────────────────────────────────────────────────────────────────────────────
-- DETALLE: estado de cada repartidor (para paso 12)
-- ─────────────────────────────────────────────────────────────────────────────
SELECT
  u.email,
  p.full_name                                                      AS nombre,
  p.role                                                           AS rol,
  p.is_active                                                      AS activo,
  COUNT(o.id)                                                      AS pedidos_asignados,
  COUNT(o.id) FILTER (WHERE o.status = 'delivered')               AS entregados,
  COUNT(o.id) FILTER (WHERE o.status IN ('ready','on_the_way'))   AS en_curso,
  COUNT(o.id) FILTER (WHERE o.status = 'cancelled')               AS cancelados
FROM auth.users u
JOIN  profiles p ON p.id = u.id
LEFT JOIN orders o ON o.courier_id = p.id
WHERE p.role = 'repartidor'
GROUP BY u.email, p.full_name, p.role, p.is_active
ORDER BY u.email;
