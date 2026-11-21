-- ══════════════════════════════════════════════════════════════════════════════
-- CORRECCIÓN pasos 8, 9 y 11 — idempotente
-- Ejecutar en: https://supabase.com/dashboard/project/tjgavwygdotetoatitju/sql/new
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 8: Trigger que rechaza transiciones inválidas POR ROL
-- Repartidor solo puede: ready → on_the_way → delivered
-- Restaurante solo puede: pending → accepted → preparing → ready, o → cancelled
-- Admin puede cualquier transición
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION fn_validate_order_transition()
RETURNS TRIGGER AS $$
DECLARE
  actor_role text;
BEGIN
  IF NEW.status = OLD.status THEN RETURN NEW; END IF;

  -- Leer rol del actor
  SELECT role INTO actor_role FROM profiles WHERE id = auth.uid();

  -- Admin: cualquier transición permitida
  IF actor_role = 'admin' THEN RETURN NEW; END IF;

  -- Cancelled: permitido desde cualquier estado excepto delivered (restaurant o admin)
  IF NEW.status = 'cancelled' AND OLD.status <> 'delivered' THEN
    IF actor_role IN ('admin', 'restaurant') THEN RETURN NEW; END IF;
    RAISE EXCEPTION 'Solo el restaurante o admin pueden cancelar un pedido';
  END IF;

  -- Restaurante: pending→accepted→preparing→ready
  IF actor_role = 'restaurant' THEN
    IF (OLD.status = 'pending'   AND NEW.status = 'accepted')  OR
       (OLD.status = 'accepted'  AND NEW.status = 'preparing') OR
       (OLD.status = 'preparing' AND NEW.status = 'ready')
    THEN RETURN NEW; END IF;
    RAISE EXCEPTION 'Transición inválida para restaurante: % → %', OLD.status, NEW.status;
  END IF;

  -- Repartidor: ready→on_the_way→delivered
  IF actor_role = 'repartidor' THEN
    IF (OLD.status = 'ready'      AND NEW.status = 'on_the_way') OR
       (OLD.status = 'on_the_way' AND NEW.status = 'delivered')
    THEN RETURN NEW; END IF;
    RAISE EXCEPTION 'Transición inválida para repartidor: % → %. Solo puedes hacer ready→on_the_way→delivered',
      OLD.status, NEW.status;
  END IF;

  RAISE EXCEPTION 'Transición no permitida: % → % (rol: %)', OLD.status, NEW.status, actor_role;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_validate_order_transition ON orders;
CREATE TRIGGER trg_validate_order_transition
  BEFORE UPDATE OF status ON orders
  FOR EACH ROW EXECUTE FUNCTION fn_validate_order_transition();

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 9: Trigger que registra cambios de estado en audit_log
-- Aplica para cualquier actor (admin, restaurante, repartidor)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION fn_log_order_status_change()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.status = OLD.status THEN RETURN NEW; END IF;

  INSERT INTO audit_log (user_id, action, entity, entity_id, details)
  VALUES (
    auth.uid(),
    'status_change',
    'orders',
    NEW.id::text,
    jsonb_build_object(
      'from',        OLD.status,
      'to',          NEW.status,
      'courier_id',  NEW.courier_id,
      'restaurant',  NEW.restaurant_name
    )
  );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_log_order_status ON orders;
CREATE TRIGGER trg_log_order_status
  AFTER UPDATE OF status ON orders
  FOR EACH ROW EXECUTE FUNCTION fn_log_order_status_change();

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 11: Trigger que bloquea sesiones de usuarios inactivos por RLS
-- Si is_active = false, la policy de SELECT en profiles devuelve 0 filas
-- → getMyProfile() devuelve null → la app hace signOut
-- Este trigger también registra la desactivación en audit_log
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION fn_log_profile_deactivation()
RETURNS TRIGGER AS $$
BEGIN
  IF OLD.is_active = true AND NEW.is_active = false THEN
    INSERT INTO audit_log (user_id, action, entity, entity_id, details)
    VALUES (
      auth.uid(),
      'deactivate_user',
      'profiles',
      NEW.id::text,
      jsonb_build_object('role', NEW.role, 'full_name', NEW.full_name)
    );
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_log_profile_deactivation ON profiles;
CREATE TRIGGER trg_log_profile_deactivation
  AFTER UPDATE OF is_active ON profiles
  FOR EACH ROW EXECUTE FUNCTION fn_log_profile_deactivation();

-- RLS reforzado: usuario inactivo no puede leer su propio perfil
-- → getMyProfile() devuelve null → RequireRole / RepartidorPage hacen signOut
DROP POLICY IF EXISTS profiles_select ON profiles;
CREATE POLICY profiles_select ON profiles FOR SELECT
  USING (
    -- Admin ve todos
    EXISTS (SELECT 1 FROM profiles p2 WHERE p2.id = auth.uid() AND p2.role = 'admin')
    -- Cada usuario solo ve su propio perfil si está activo
    OR (id = auth.uid() AND is_active = true)
  );

-- audit_log: cualquier autenticado puede insertar (triggers lo necesitan)
DROP POLICY IF EXISTS audit_log_insert     ON audit_log;
DROP POLICY IF EXISTS audit_log_admin_insert ON audit_log;
CREATE POLICY audit_log_insert ON audit_log FOR INSERT
  WITH CHECK (auth.uid() IS NOT NULL);

-- ─────────────────────────────────────────────────────────────────────────────
-- VERIFICACIÓN
-- ─────────────────────────────────────────────────────────────────────────────
SELECT
  'trg_validate_order_transition' AS trigger_name,
  EXISTS(SELECT 1 FROM information_schema.triggers
         WHERE trigger_name = 'trg_validate_order_transition') AS existe
UNION ALL SELECT
  'trg_log_order_status',
  EXISTS(SELECT 1 FROM information_schema.triggers
         WHERE trigger_name = 'trg_log_order_status')
UNION ALL SELECT
  'trg_log_profile_deactivation',
  EXISTS(SELECT 1 FROM information_schema.triggers
         WHERE trigger_name = 'trg_log_profile_deactivation');

SELECT '✅ Correcciones pasos 8, 9 y 11 aplicadas' AS resultado;
