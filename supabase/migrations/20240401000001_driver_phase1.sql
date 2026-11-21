-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Fase 1: Desbloquear Driver
-- Prerequisito: todas las migraciones anteriores (001–20240301) aplicadas
-- Idempotente: seguro ejecutar múltiples veces
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. profiles.is_available
--    DriverRepartidorViewModel lee/escribe esta columna en profiles directamente.
--    La tabla repartidores tiene la suya, pero el ViewModel no usa esa tabla.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE profiles
  ADD COLUMN IF NOT EXISTS is_available boolean NOT NULL DEFAULT false;

-- Sincronizar con repartidores si ya existen filas históricas
UPDATE profiles p
SET    is_available = r.is_available
FROM   repartidores r
WHERE  r.id = p.id
  AND  p.role = 'repartidor';

CREATE INDEX IF NOT EXISTS idx_profiles_available
  ON profiles(is_available)
  WHERE is_available = true;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Actualizar CHECK de audit_log.action
--    La migración 20240301 no incluye 'accept_order', 'status_change',
--    'deactivate_user', 'create_restaurant_user'. El AssignCourierModal y
--    los triggers los usan, así que el INSERT falla sin este cambio.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE audit_log
  DROP CONSTRAINT IF EXISTS audit_log_action_check;

ALTER TABLE audit_log
  ADD CONSTRAINT audit_log_action_check
  CHECK (action IN (
    'create',
    'update',
    'delete',
    'login',
    'logout',
    'role_change',
    'status_change',
    'assign_courier',
    'create_courier',
    'accept_order',
    'deactivate_user',
    'create_restaurant_user'
  ));

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC accept_order(p_order_id uuid)
--
--    Firma esperada por DriverRepartidorViewModel.kt línea ~110:
--      supabase.postgrest.rpc("accept_order",
--        buildJsonObject { put("p_order_id", orderId) })
--
--    La migración 004 tenía accept_order(p_order_id uuid, p_repartidor_id uuid)
--    con status 'picked_up'. Esta versión la reemplaza con 1 parámetro y
--    usa auth.uid() para el courier_id, poniendo status 'on_the_way' para
--    coincidir con lo que el ViewModel espera ver en misPedidos.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION accept_order(p_order_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_role text;
  v_rows int;
BEGIN
  -- Solo repartidores activos
  SELECT role INTO v_role
  FROM   profiles
  WHERE  id = auth.uid() AND is_active = true;

  IF v_role IS NULL THEN
    RAISE EXCEPTION 'Usuario no autenticado, sin perfil o cuenta desactivada';
  END IF;

  IF v_role <> 'repartidor' THEN
    RAISE EXCEPTION 'Solo los repartidores pueden aceptar pedidos (rol actual: %)', v_role;
  END IF;

  -- Asignación atómica: falla si ya fue tomado o no está en ready
  UPDATE orders
  SET    courier_id = auth.uid(),
         status     = 'on_the_way'
  WHERE  id         = p_order_id
    AND  status     = 'ready'
    AND  courier_id IS NULL;

  GET DIAGNOSTICS v_rows = ROW_COUNT;

  IF v_rows = 0 THEN
    RAISE EXCEPTION 'El pedido ya fue tomado por otro repartidor o no está disponible';
  END IF;

  -- Auditoría
  INSERT INTO audit_log (user_id, action, table_name, record_id, new_data)
  VALUES (
    auth.uid(),
    'accept_order',
    'orders',
    p_order_id::text,
    jsonb_build_object('courier_id', auth.uid(), 'new_status', 'on_the_way')
  );
END;
$$;

-- Limpiar permiso de la firma antigua si existía
DO $$ BEGIN
  REVOKE ALL ON FUNCTION accept_order(uuid, uuid) FROM authenticated;
EXCEPTION WHEN others THEN NULL;
END $$;

GRANT EXECUTE ON FUNCTION accept_order(uuid) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. RLS: repartidor puede escribir su propio is_available en profiles
--    La política profiles_update_admin solo cubre admin. Sin esta política
--    el toggleDisponible() del ViewModel falla con "violates RLS".
-- ─────────────────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS profiles_update_own_availability ON profiles;
CREATE POLICY profiles_update_own_availability ON profiles
  FOR UPDATE
  USING  (id = auth.uid() AND role = 'repartidor')
  WITH CHECK (id = auth.uid() AND role = 'repartidor');

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Armonizar trigger de transiciones con la RPC
--    La migración 20240301 solo permitía ready→picked_up para repartidor.
--    accept_order salta a on_the_way directamente. El trigger debe permitirlo.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION validate_order_status_transition()
RETURNS TRIGGER AS $$
DECLARE
  v_role text;
BEGIN
  IF NEW.status = OLD.status THEN RETURN NEW; END IF;

  SELECT role INTO v_role FROM profiles WHERE id = auth.uid();

  -- Admin: cualquier transición
  IF v_role = 'admin' THEN RETURN NEW; END IF;

  -- Restaurante: flujo de cocina + cancelación
  IF v_role = 'restaurant' THEN
    IF (OLD.status = 'pending'   AND NEW.status = 'accepted')  OR
       (OLD.status = 'accepted'  AND NEW.status = 'preparing') OR
       (OLD.status = 'preparing' AND NEW.status = 'ready')     OR
       NEW.status = 'cancelled' THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Transición no permitida para restaurante: % → %', OLD.status, NEW.status;
  END IF;

  -- Repartidor: flujo de entrega (con y sin picked_up)
  IF v_role = 'repartidor' THEN
    IF (OLD.status = 'ready'      AND NEW.status = 'on_the_way') OR
       (OLD.status = 'ready'      AND NEW.status = 'picked_up')  OR
       (OLD.status = 'picked_up'  AND NEW.status = 'on_the_way') OR
       (OLD.status = 'on_the_way' AND NEW.status = 'delivered')  THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Transición no permitida para repartidor: % → %', OLD.status, NEW.status;
  END IF;

  RAISE EXCEPTION 'Rol % no autorizado para cambiar estados de pedido', v_role;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Verificación final
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE  table_name = 'profiles' AND column_name = 'is_available'
  ), 'FALLA: profiles.is_available no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines
    WHERE  routine_name = 'accept_order'
  ), 'FALLA: función accept_order no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.check_constraints
    WHERE  constraint_name = 'audit_log_action_check'
  ), 'FALLA: constraint audit_log_action_check no existe';

  RAISE NOTICE '✅ FASE 1: todas las verificaciones pasaron';
END;
$$;
