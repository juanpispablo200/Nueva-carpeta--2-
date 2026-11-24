-- ==========================================================================
-- JAGOO - PASO 1 de 3: mejoras web, conductor, cupones, pagos, ganancias, push, GPS, menu, calificaciones
-- Concatenacion literal de supabase/migrations/ (no se modifico ningun archivo).
-- Va en UNA transaccion: si algo falla, no se guarda nada de ESTA parte.
-- Si falla, copia el PRIMER mensaje de error (no los 'current transaction is aborted').
-- ==========================================================================

BEGIN;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240301000001_mejoras_web.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- Mejoras web Jagoo: RLS completo, auditoría, borrado lógico, validaciones
-- ══════════════════════════════════════════════════════════════════════════════

-- ── 1. Tabla de auditoría ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_log (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  action      text NOT NULL CHECK (action IN ('create', 'update', 'delete', 'login', 'logout', 'role_change', 'status_change', 'assign_courier', 'create_courier')),
  table_name  text,
  record_id   text,
  old_data    jsonb,
  new_data    jsonb,
  ip_address  inet,
  user_agent  text,
  created_at  timestamptz DEFAULT now()
);

CREATE INDEX idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at DESC);
CREATE INDEX idx_audit_log_action ON audit_log(action);

-- ── 2. Columnas nuevas ────────────────────────────────────────────────────────
-- dishes: borrado lógico + imagen en Storage
ALTER TABLE dishes ADD COLUMN IF NOT EXISTS deleted_at timestamptz;
ALTER TABLE dishes ADD COLUMN IF NOT EXISTS image_path text;
CREATE INDEX IF NOT EXISTS idx_dishes_deleted_at ON dishes(deleted_at) WHERE deleted_at IS NULL;

-- profiles: estado activo
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS is_active boolean DEFAULT true;
CREATE INDEX IF NOT EXISTS idx_profiles_active ON profiles(is_active) WHERE is_active = true;

-- orders: courier asignado
ALTER TABLE orders ADD COLUMN IF NOT EXISTS courier_id uuid REFERENCES profiles(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_orders_courier ON orders(courier_id);

-- order_events: motivo de cancelación/rechazo
ALTER TABLE order_events ADD COLUMN IF NOT EXISTS reason text;

-- ── 3. Trigger: validar transición de estados ─────────────────────────────────
CREATE OR REPLACE FUNCTION validate_order_status_transition()
RETURNS TRIGGER AS $$
DECLARE
  user_role text;
BEGIN
  -- Solo si cambia el status
  IF NEW.status = OLD.status THEN
    RETURN NEW;
  END IF;

  -- Obtener rol del usuario
  SELECT role INTO user_role
  FROM profiles
  WHERE id = auth.uid();

  -- Admin puede todo
  IF user_role = 'admin' THEN
    RETURN NEW;
  END IF;

  -- Restaurante solo puede: pending→accepted, accepted→preparing, preparing→ready
  IF user_role = 'restaurant' THEN
    IF (OLD.status = 'pending' AND NEW.status = 'accepted') OR
       (OLD.status = 'accepted' AND NEW.status = 'preparing') OR
       (OLD.status = 'preparing' AND NEW.status = 'ready') OR
       (NEW.status = 'cancelled') THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Transición no permitida: % → % para rol restaurant', OLD.status, NEW.status;
  END IF;

  -- Repartidor solo puede: ready→picked_up, picked_up→on_the_way, on_the_way→delivered
  IF user_role = 'repartidor' THEN
    IF (OLD.status = 'ready' AND NEW.status = 'picked_up') OR
       (OLD.status = 'picked_up' AND NEW.status = 'on_the_way') OR
       (OLD.status = 'on_the_way' AND NEW.status = 'delivered') THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Transición no permitida: % → % para rol repartidor', OLD.status, NEW.status;
  END IF;

  -- Otros roles no pueden cambiar estados
  RAISE EXCEPTION 'Rol % no autorizado para cambiar estados', user_role;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS validate_order_status_transition_trigger ON orders;
CREATE TRIGGER validate_order_status_transition_trigger
  BEFORE UPDATE OF status ON orders
  FOR EACH ROW
  EXECUTE FUNCTION validate_order_status_transition();

-- ── 4. Trigger: registrar cambio de estado en order_events ────────────────────
CREATE OR REPLACE FUNCTION log_order_status_change()
RETURNS TRIGGER AS $$
DECLARE
  user_role text;
BEGIN
  IF NEW.status = OLD.status THEN
    RETURN NEW;
  END IF;

  SELECT role INTO user_role FROM profiles WHERE id = auth.uid();

  INSERT INTO order_events (order_id, from_status, to_status, actor_id, actor_role)
  VALUES (NEW.id, OLD.status, NEW.status, auth.uid(), user_role);

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS log_order_status_change_trigger ON orders;
CREATE TRIGGER log_order_status_change_trigger
  AFTER UPDATE OF status ON orders
  FOR EACH ROW
  EXECUTE FUNCTION log_order_status_change();

-- ── 5. Trigger: auditoría automática en cambios de rol ────────────────────────
CREATE OR REPLACE FUNCTION audit_role_change()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.role <> OLD.role THEN
    INSERT INTO audit_log (user_id, action, table_name, record_id, old_data, new_data)
    VALUES (
      auth.uid(),
      'role_change',
      'profiles',
      NEW.id::text,
      jsonb_build_object('role', OLD.role),
      jsonb_build_object('role', NEW.role)
    );
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS audit_role_change_trigger ON profiles;
CREATE TRIGGER audit_role_change_trigger
  AFTER UPDATE OF role ON profiles
  FOR EACH ROW
  EXECUTE FUNCTION audit_role_change();

-- ── 6. RLS por rol ────────────────────────────────────────────────────────────
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE dishes ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE restaurants ENABLE ROW LEVEL SECURITY;

-- ── audit_log: solo admin lee ─────────────────────────────────────────────────
DROP POLICY IF EXISTS audit_log_admin_read ON audit_log;
CREATE POLICY audit_log_admin_read ON audit_log
  FOR SELECT
  USING (EXISTS (
    SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'
  ));

-- ── profiles: todos leen su propio perfil, admin lee/modifica todo ────────────
DROP POLICY IF EXISTS profiles_select_own ON profiles;
CREATE POLICY profiles_select_own ON profiles
  FOR SELECT
  USING (id = auth.uid() OR EXISTS (
    SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'
  ));

DROP POLICY IF EXISTS profiles_update_admin ON profiles;
CREATE POLICY profiles_update_admin ON profiles
  FOR UPDATE
  USING (EXISTS (
    SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'
  ));

DROP POLICY IF EXISTS profiles_insert_admin ON profiles;
CREATE POLICY profiles_insert_admin ON profiles
  FOR INSERT
  WITH CHECK (EXISTS (
    SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'
  ));

-- ── restaurants: admin todo, restaurante solo su propio restaurante ───────────
DROP POLICY IF EXISTS restaurants_select ON restaurants;
CREATE POLICY restaurants_select ON restaurants
  FOR SELECT
  USING (
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    OR owner_id = auth.uid()
    OR TRUE  -- todos ven restaurantes (para clientes en app móvil)
  );

DROP POLICY IF EXISTS restaurants_update ON restaurants;
CREATE POLICY restaurants_update ON restaurants
  FOR UPDATE
  USING (
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    OR owner_id = auth.uid()
  );

DROP POLICY IF EXISTS restaurants_insert_admin ON restaurants;
CREATE POLICY restaurants_insert_admin ON restaurants
  FOR INSERT
  WITH CHECK (EXISTS (
    SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'
  ));

-- ── dishes: todos leen activos, restaurante edita los suyos ───────────────────
DROP POLICY IF EXISTS dishes_select ON dishes;
CREATE POLICY dishes_select ON dishes
  FOR SELECT
  USING (
    deleted_at IS NULL
    AND (
      TRUE  -- todos ven platos activos
      OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role IN ('admin', 'restaurant'))
    )
  );

DROP POLICY IF EXISTS dishes_insert ON dishes;
CREATE POLICY dishes_insert ON dishes
  FOR INSERT
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM restaurants r
      INNER JOIN profiles p ON p.id = auth.uid()
      WHERE r.id = restaurant_id
        AND (p.role = 'admin' OR r.owner_id = p.id)
    )
  );

DROP POLICY IF EXISTS dishes_update ON dishes;
CREATE POLICY dishes_update ON dishes
  FOR UPDATE
  USING (
    EXISTS (
      SELECT 1 FROM restaurants r
      INNER JOIN profiles p ON p.id = auth.uid()
      WHERE r.id = restaurant_id
        AND (p.role = 'admin' OR r.owner_id = p.id)
    )
  );

-- ── orders: admin todo, restaurante sus pedidos, repartidor asignados ─────────
DROP POLICY IF EXISTS orders_select ON orders;
CREATE POLICY orders_select ON orders
  FOR SELECT
  USING (
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    OR restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
    OR courier_id = auth.uid()
    OR customer_id = auth.uid()
  );

DROP POLICY IF EXISTS orders_update ON orders;
CREATE POLICY orders_update ON orders
  FOR UPDATE
  USING (
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    OR restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
    OR courier_id = auth.uid()
  );

DROP POLICY IF EXISTS orders_insert ON orders;
CREATE POLICY orders_insert ON orders
  FOR INSERT
  WITH CHECK (customer_id = auth.uid() OR EXISTS (
    SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'
  ));

-- ── order_events: todos leen eventos de sus pedidos ───────────────────────────
DROP POLICY IF EXISTS order_events_select ON order_events;
CREATE POLICY order_events_select ON order_events
  FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM orders o
      WHERE o.id = order_id
        AND (
          EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
          OR o.restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
          OR o.courier_id = auth.uid()
          OR o.customer_id = auth.uid()
        )
    )
  );

DROP POLICY IF EXISTS order_events_insert ON order_events;
CREATE POLICY order_events_insert ON order_events
  FOR INSERT
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM orders o
      WHERE o.id = order_id
        AND (
          EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
          OR o.restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
          OR o.courier_id = auth.uid()
        )
    )
  );

-- ── order_items: igual que orders ─────────────────────────────────────────────
DROP POLICY IF EXISTS order_items_select ON order_items;
CREATE POLICY order_items_select ON order_items
  FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM orders o
      WHERE o.id = order_id
        AND (
          EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
          OR o.restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
          OR o.courier_id = auth.uid()
          OR o.customer_id = auth.uid()
        )
    )
  );

-- ── 7. Función: prevenir eliminación del último admin ─────────────────────────
CREATE OR REPLACE FUNCTION prevent_last_admin_removal()
RETURNS TRIGGER AS $$
DECLARE
  admin_count int;
BEGIN
  -- Solo verificar si se cambia de admin a otro rol
  IF OLD.role = 'admin' AND NEW.role <> 'admin' THEN
    SELECT COUNT(*) INTO admin_count
    FROM profiles
    WHERE role = 'admin' AND id <> NEW.id AND is_active = true;

    IF admin_count = 0 THEN
      RAISE EXCEPTION 'No se puede quitar el rol de admin al último administrador activo';
    END IF;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS prevent_last_admin_removal_trigger ON profiles;
CREATE TRIGGER prevent_last_admin_removal_trigger
  BEFORE UPDATE OF role ON profiles
  FOR EACH ROW
  EXECUTE FUNCTION prevent_last_admin_removal();

-- ── 8. Función helper: registrar auditoría manual ─────────────────────────────
CREATE OR REPLACE FUNCTION log_audit(
  p_action text,
  p_table_name text DEFAULT NULL,
  p_record_id text DEFAULT NULL,
  p_old_data jsonb DEFAULT NULL,
  p_new_data jsonb DEFAULT NULL
)
RETURNS void AS $$
BEGIN
  INSERT INTO audit_log (user_id, action, table_name, record_id, old_data, new_data)
  VALUES (auth.uid(), p_action, p_table_name, p_record_id, p_old_data, p_new_data);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON TABLE audit_log IS 'Registro de auditoría de acciones críticas';
COMMENT ON FUNCTION log_audit IS 'Registrar entrada de auditoría manualmente desde la app';


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000001_driver_phase1.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
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


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000002_coupons.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Fase 2: Tabla coupons + RPC validate_coupon
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Tabla coupons
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS coupons (
  id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  code         text        NOT NULL UNIQUE,
  type         text        NOT NULL CHECK (type IN ('percent', 'fixed')),
  value        numeric     NOT NULL CHECK (value > 0),
  min_order    numeric     NOT NULL DEFAULT 0,
  max_uses     int         NOT NULL DEFAULT 1,
  uses         int         NOT NULL DEFAULT 0,
  active       boolean     NOT NULL DEFAULT true,
  valid_from   timestamptz NOT NULL DEFAULT now(),
  valid_until  timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_coupons_code   ON coupons(code);
CREATE INDEX IF NOT EXISTS idx_coupons_active ON coupons(active) WHERE active = true;

-- RLS: solo admin gestiona cupones; clientes solo validan via RPC
ALTER TABLE coupons ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS coupons_admin ON coupons;
CREATE POLICY coupons_admin ON coupons FOR ALL
  USING  (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'))
  WITH CHECK (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- Seed de cupón de bienvenida para pruebas
INSERT INTO coupons (code, type, value, min_order, max_uses, active, valid_until)
VALUES ('BIENVENIDO', 'fixed', 2.00, 5.00, 500, true, now() + interval '1 year')
ON CONFLICT (code) DO NOTHING;

INSERT INTO coupons (code, type, value, min_order, max_uses, active, valid_until)
VALUES ('JAGOO10', 'percent', 10.00, 10.00, 200, true, now() + interval '6 months')
ON CONFLICT (code) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. RPC validate_coupon(p_code text, p_order_total numeric)
--
--    Llamada desde CartViewModel antes de confirmar pedido.
--    Devuelve la fila del cupón si es válido; lanza excepción si no.
--    NO incrementa el contador — eso lo hace apply_coupon al confirmar el pedido.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION validate_coupon(
  p_code        text,
  p_order_total numeric
)
RETURNS TABLE (
  id          uuid,
  code        text,
  type        text,
  value       numeric,
  min_order   numeric,
  discount    numeric   -- monto calculado de descuento
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_coupon coupons%ROWTYPE;
  v_discount numeric;
BEGIN
  SELECT * INTO v_coupon
  FROM   coupons c
  WHERE  UPPER(c.code) = UPPER(p_code)
    AND  c.active      = true
    AND  c.uses        < c.max_uses
    AND  (c.valid_until IS NULL OR c.valid_until > now())
    AND  (c.valid_from <= now());

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Cupón no válido o expirado';
  END IF;

  IF p_order_total < v_coupon.min_order THEN
    RAISE EXCEPTION 'El pedido mínimo para este cupón es $%', v_coupon.min_order;
  END IF;

  -- Calcular descuento
  IF v_coupon.type = 'percent' THEN
    v_discount := ROUND((p_order_total * v_coupon.value / 100.0)::numeric, 2);
  ELSE
    v_discount := LEAST(v_coupon.value, p_order_total);
  END IF;

  RETURN QUERY SELECT
    v_coupon.id,
    v_coupon.code,
    v_coupon.type,
    v_coupon.value,
    v_coupon.min_order,
    v_discount;
END;
$$;

GRANT EXECUTE ON FUNCTION validate_coupon(text, numeric) TO authenticated;
GRANT EXECUTE ON FUNCTION validate_coupon(text, numeric) TO anon;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC apply_coupon(p_code text, p_order_id uuid)
--
--    Incrementa el contador una vez que el pedido queda confirmado.
--    Llamada desde CartViewModel.confirmOrder() en background.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION apply_coupon(
  p_code     text,
  p_order_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  UPDATE coupons
  SET    uses = uses + 1
  WHERE  UPPER(code) = UPPER(p_code)
    AND  active      = true
    AND  uses        < max_uses;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Cupón no válido o ya agotado';
  END IF;

  -- Registrar uso en audit_log
  INSERT INTO audit_log (user_id, action, table_name, record_id, new_data)
  VALUES (
    auth.uid(),
    'create',
    'coupons',
    p_code,
    jsonb_build_object('order_id', p_order_id, 'code', p_code)
  );
END;
$$;

GRANT EXECUTE ON FUNCTION apply_coupon(text, uuid) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_name = 'coupons'
  ), 'FALLA: tabla coupons no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'validate_coupon'
  ), 'FALLA: función validate_coupon no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'apply_coupon'
  ), 'FALLA: función apply_coupon no existe';

  RAISE NOTICE '✅ FASE 2 coupons: todas las verificaciones pasaron';
END;
$$;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000003_payments_manual.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Fase 3: Pagos con transferencia bancaria + comprobante manual
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Columnas de pago en orders
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS payment_method text DEFAULT 'transfer',
  ADD COLUMN IF NOT EXISTS payment_status text DEFAULT 'pending'
    CHECK (payment_status IN ('pending', 'verifying', 'verified', 'rejected'));

CREATE INDEX IF NOT EXISTS idx_orders_payment_status
  ON orders(payment_status)
  WHERE payment_status IN ('pending', 'verifying');

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Tabla payment_proofs — comprobantes subidos por clientes
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payment_proofs (
  id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id         uuid        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  customer_id      uuid        NOT NULL REFERENCES profiles(id),
  image_url        text        NOT NULL,  -- URL de Supabase Storage o externa
  reference_number text,                  -- número de referencia del banco
  amount           numeric     NOT NULL,
  upload_date      timestamptz NOT NULL DEFAULT now(),
  verified_by      uuid        REFERENCES profiles(id),
  verified_at      timestamptz,
  status           text        NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'verified', 'rejected')),
  rejection_reason text,
  created_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_payment_proofs_order    ON payment_proofs(order_id);
CREATE INDEX IF NOT EXISTS idx_payment_proofs_customer ON payment_proofs(customer_id);
CREATE INDEX IF NOT EXISTS idx_payment_proofs_status   ON payment_proofs(status)
  WHERE status = 'pending';

-- RLS: cliente ve sus propios comprobantes, admin ve todos
ALTER TABLE payment_proofs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS payment_proofs_select ON payment_proofs;
CREATE POLICY payment_proofs_select ON payment_proofs
  FOR SELECT
  USING (
    customer_id = auth.uid()
    OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
  );

DROP POLICY IF EXISTS payment_proofs_insert ON payment_proofs;
CREATE POLICY payment_proofs_insert ON payment_proofs
  FOR INSERT
  WITH CHECK (customer_id = auth.uid());

DROP POLICY IF EXISTS payment_proofs_update_admin ON payment_proofs;
CREATE POLICY payment_proofs_update_admin ON payment_proofs
  FOR UPDATE
  USING (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC upload_payment_proof(p_order_id, p_image_url, p_reference_number, p_amount)
--
--    Cliente sube comprobante después de transferir.
--    Cambia el payment_status del pedido a 'verifying'.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION upload_payment_proof(
  p_order_id         uuid,
  p_image_url        text,
  p_reference_number text DEFAULT NULL,
  p_amount           numeric DEFAULT 0
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_customer_id uuid;
  v_proof_id    uuid;
BEGIN
  -- Verificar que el pedido pertenezca al usuario
  SELECT customer_id INTO v_customer_id
  FROM   orders
  WHERE  id = p_order_id;

  IF v_customer_id IS NULL THEN
    RAISE EXCEPTION 'Pedido no encontrado';
  END IF;

  IF v_customer_id <> auth.uid() THEN
    RAISE EXCEPTION 'No tienes permiso para subir comprobante de este pedido';
  END IF;

  -- Crear registro de comprobante
  INSERT INTO payment_proofs (order_id, customer_id, image_url, reference_number, amount)
  VALUES (p_order_id, auth.uid(), p_image_url, p_reference_number, p_amount)
  RETURNING id INTO v_proof_id;

  -- Cambiar estado del pedido a 'verifying'
  UPDATE orders
  SET    payment_status = 'verifying'
  WHERE  id = p_order_id;

  -- Auditoría
  INSERT INTO audit_log (user_id, action, table_name, record_id, new_data)
  VALUES (
    auth.uid(),
    'create',
    'payment_proofs',
    v_proof_id::text,
    jsonb_build_object(
      'order_id', p_order_id,
      'image_url', p_image_url,
      'reference_number', p_reference_number
    )
  );

  RETURN v_proof_id;
END;
$$;

GRANT EXECUTE ON FUNCTION upload_payment_proof(uuid, text, text, numeric) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. RPC verify_payment_proof(p_proof_id, p_approve, p_rejection_reason)
--
--    Admin aprueba o rechaza el comprobante desde el panel web.
--    Si aprueba: payment_status del pedido → 'verified', status → 'accepted'
--    Si rechaza: payment_status → 'rejected'
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION verify_payment_proof(
  p_proof_id        uuid,
  p_approve         boolean,
  p_rejection_reason text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_order_id uuid;
  v_role     text;
BEGIN
  -- Solo admin puede verificar
  SELECT role INTO v_role FROM profiles WHERE id = auth.uid();
  IF v_role <> 'admin' THEN
    RAISE EXCEPTION 'Solo administradores pueden verificar comprobantes';
  END IF;

  -- Obtener order_id del comprobante
  SELECT order_id INTO v_order_id
  FROM   payment_proofs
  WHERE  id = p_proof_id;

  IF v_order_id IS NULL THEN
    RAISE EXCEPTION 'Comprobante no encontrado';
  END IF;

  IF p_approve THEN
    -- Aprobar
    UPDATE payment_proofs
    SET    status       = 'verified',
           verified_by  = auth.uid(),
           verified_at  = now()
    WHERE  id = p_proof_id;

    UPDATE orders
    SET    payment_status = 'verified',
           status         = 'accepted'  -- el pedido pasa de pending a accepted
    WHERE  id = v_order_id;

    INSERT INTO audit_log (user_id, action, table_name, record_id, new_data)
    VALUES (auth.uid(), 'status_change', 'payment_proofs', p_proof_id::text,
            jsonb_build_object('approved', true, 'order_id', v_order_id));
  ELSE
    -- Rechazar
    UPDATE payment_proofs
    SET    status           = 'rejected',
           verified_by      = auth.uid(),
           verified_at      = now(),
           rejection_reason = p_rejection_reason
    WHERE  id = p_proof_id;

    UPDATE orders
    SET    payment_status = 'rejected'
    WHERE  id = v_order_id;

    INSERT INTO audit_log (user_id, action, table_name, record_id, new_data)
    VALUES (auth.uid(), 'status_change', 'payment_proofs', p_proof_id::text,
            jsonb_build_object('approved', false, 'reason', p_rejection_reason));
  END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION verify_payment_proof(uuid, boolean, text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Configuración de cuenta bancaria en settings
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO settings (key, value, description)
VALUES
  ('bank_name', 'Banco Pichincha', 'Nombre del banco para transferencias')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description)
VALUES
  ('bank_account_type', 'Ahorros', 'Tipo de cuenta bancaria')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description)
VALUES
  ('bank_account_number', '2100123456', 'Número de cuenta bancaria')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description)
VALUES
  ('bank_account_holder', 'Jagoo Delivery S.A.', 'Titular de la cuenta')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description)
VALUES
  ('bank_ruc', '1792345678001', 'RUC de la empresa')
ON CONFLICT (key) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'orders' AND column_name = 'payment_status'
  ), 'FALLA: orders.payment_status no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_name = 'payment_proofs'
  ), 'FALLA: tabla payment_proofs no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'upload_payment_proof'
  ), 'FALLA: función upload_payment_proof no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'verify_payment_proof'
  ), 'FALLA: función verify_payment_proof no existe';

  RAISE NOTICE '✅ FASE 3 pagos manuales: todas las verificaciones pasaron';
END;
$$;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000004_earnings_storage.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Fase 4: Tabla settings, ganancias en backend, bucket Storage
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Tabla settings (clave-valor de configuración del sistema)
--    Usada por: ganancias del driver, datos bancarios, tarifa de envío, etc.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS settings (
  key         text PRIMARY KEY,
  value       text NOT NULL DEFAULT '',
  description text,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

-- RLS: solo admin escribe; autenticados pueden leer
ALTER TABLE settings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS settings_read ON settings;
CREATE POLICY settings_read ON settings
  FOR SELECT USING (auth.role() = 'authenticated');

DROP POLICY IF EXISTS settings_write ON settings;
CREATE POLICY settings_write ON settings
  FOR ALL
  USING  (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'))
  WITH CHECK (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- Seed de valores por defecto (idempotente con ON CONFLICT)
INSERT INTO settings (key, value, description) VALUES
  ('driver_fee_per_delivery', '1.50',          'Ganancia del repartidor por entrega ($)')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('default_delivery_fee',   '2.50',          'Tarifa de envío cobrada al cliente ($)')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('platform_name',          'Jagoo',         'Nombre de la plataforma')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('bank_name',              'Banco Pichincha','Nombre del banco para transferencias')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('bank_account_type',      'Ahorros',       'Tipo de cuenta bancaria')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('bank_account_number',    '2100123456',    'Número de cuenta bancaria')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('bank_account_holder',    'Jagoo Delivery S.A.', 'Titular de la cuenta')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('bank_ruc',               '1792345678001', 'RUC de la empresa')
ON CONFLICT (key) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. RPC get_driver_earnings(p_driver_id uuid, p_period text)
--
--    Calcula ganancias del repartidor en servidor usando la tarifa de settings.
--    p_period: 'today' | 'week' | 'month' | 'all'
--    Reemplaza el cálculo hardcodeado de 1.50 * entregas.size en Kotlin.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION get_driver_earnings(
  p_driver_id uuid,
  p_period    text DEFAULT 'all'
)
RETURNS TABLE (
  total_deliveries  bigint,
  fee_per_delivery  numeric,
  total_earnings    numeric,
  period            text
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_fee          numeric;
  v_from         timestamptz;
  v_caller_id    uuid;
  v_caller_role  text;
BEGIN
  -- Solo el propio repartidor o admin puede ver las ganancias
  v_caller_id := auth.uid();
  SELECT role INTO v_caller_role FROM profiles WHERE id = v_caller_id;

  IF v_caller_id <> p_driver_id AND v_caller_role <> 'admin' THEN
    RAISE EXCEPTION 'No tienes permiso para ver las ganancias de este repartidor';
  END IF;

  -- Leer tarifa desde settings
  SELECT value::numeric INTO v_fee
  FROM settings WHERE key = 'driver_fee_per_delivery';

  v_fee := COALESCE(v_fee, 1.50);  -- fallback si no existe

  -- Calcular fecha de inicio según período
  v_from := CASE p_period
    WHEN 'today' THEN date_trunc('day',   now())
    WHEN 'week'  THEN date_trunc('week',  now())
    WHEN 'month' THEN date_trunc('month', now())
    ELSE          TIMESTAMPTZ '-infinity'
  END;

  RETURN QUERY
  SELECT
    COUNT(*)::bigint           AS total_deliveries,
    v_fee                      AS fee_per_delivery,
    COUNT(*) * v_fee           AS total_earnings,
    p_period                   AS period
  FROM orders
  WHERE courier_id = p_driver_id
    AND status     = 'delivered'
    AND created_at >= v_from;
END;
$$;

GRANT EXECUTE ON FUNCTION get_driver_earnings(uuid, text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Bucket de Supabase Storage para comprobantes de pago
--
--    Se crea con SQL directamente en storage.buckets.
--    public = false → solo usuarios autenticados acceden.
--    file_size_limit = 5 MB, mime types = imágenes.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'payment-proofs',
  'payment-proofs',
  false,
  5242880,   -- 5 MB
  ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/heic']
)
ON CONFLICT (id) DO NOTHING;

-- Política: cliente autenticado puede subir sus propios comprobantes
DROP POLICY IF EXISTS storage_payment_proofs_insert ON storage.objects;
CREATE POLICY storage_payment_proofs_insert
  ON storage.objects FOR INSERT
  WITH CHECK (
    bucket_id = 'payment-proofs'
    AND auth.role() = 'authenticated'
  );

-- Política: el dueño del archivo y admin pueden leerlo
DROP POLICY IF EXISTS storage_payment_proofs_select ON storage.objects;
CREATE POLICY storage_payment_proofs_select
  ON storage.objects FOR SELECT
  USING (
    bucket_id = 'payment-proofs'
    AND (
      auth.uid()::text = (storage.foldername(name))[1]
      OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    )
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_name = 'settings'
  ), 'FALLA: tabla settings no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'get_driver_earnings'
  ), 'FALLA: función get_driver_earnings no existe';

  ASSERT EXISTS (
    SELECT 1 FROM storage.buckets WHERE id = 'payment-proofs'
  ), 'FALLA: bucket payment-proofs no existe';

  RAISE NOTICE '✅ FASE 4 earnings+storage: todas las verificaciones pasaron';
END;
$$;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000005_push_notifications.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Fase 6: Notificaciones push (FCM via Edge Function)
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Columna fcm_token en profiles
--    La app cliente y la Driver guardan aquí su token FCM al iniciar sesión.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE profiles
  ADD COLUMN IF NOT EXISTS fcm_token text;

-- Índice para buscar token por user_id rápidamente (lo usa la Edge Function)
CREATE INDEX IF NOT EXISTS idx_profiles_fcm_token
  ON profiles(id)
  WHERE fcm_token IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Tabla notification_log — historial de notificaciones enviadas
--    Útil para debug y para evitar duplicados.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notification_log (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid        REFERENCES profiles(id) ON DELETE SET NULL,
  order_id    uuid        REFERENCES orders(id)   ON DELETE SET NULL,
  type        text        NOT NULL,  -- 'order_status' | 'payment_verified' | 'new_order_driver'
  title       text        NOT NULL,
  body        text        NOT NULL,
  sent_at     timestamptz NOT NULL DEFAULT now(),
  success     boolean     NOT NULL DEFAULT true,
  error_msg   text
);

CREATE INDEX IF NOT EXISTS idx_notif_log_user  ON notification_log(user_id);
CREATE INDEX IF NOT EXISTS idx_notif_log_order ON notification_log(order_id);

ALTER TABLE notification_log ENABLE ROW LEVEL SECURITY;

-- Solo admin puede leer logs completos
DROP POLICY IF EXISTS notif_log_admin ON notification_log;
CREATE POLICY notif_log_admin ON notification_log
  FOR SELECT
  USING (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Función auxiliar notify_order_status_change()
--    Se llama desde el trigger de orders. Invoca la Edge Function via pg_net.
--    pg_net está disponible en Supabase por defecto.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION notify_order_status_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_title      text;
  v_body       text;
  v_type       text := 'order_status';
  v_project_id text;
BEGIN
  -- Solo disparar cuando el status cambia
  IF OLD.status = NEW.status AND OLD.payment_status IS NOT DISTINCT FROM NEW.payment_status THEN
    RETURN NEW;
  END IF;

  -- Obtener URL del proyecto desde settings (o usar variable de entorno)
  SELECT current_setting('app.supabase_url', true) INTO v_project_id;

  -- Construir título y cuerpo según el nuevo estado
  IF NEW.payment_status = 'verified' AND OLD.payment_status = 'verifying' THEN
    v_title := '✅ Pago verificado';
    v_body  := 'Tu pago fue confirmado. Tu pedido está siendo preparado.';
    v_type  := 'payment_verified';
  ELSE
    v_title := CASE NEW.status
      WHEN 'accepted'   THEN '📋 Pedido aceptado'
      WHEN 'preparing'  THEN '👨‍🍳 En preparación'
      WHEN 'ready'      THEN '✅ Pedido listo'
      WHEN 'on_the_way' THEN '🛵 Repartidor en camino'
      WHEN 'delivered'  THEN '🎉 ¡Pedido entregado!'
      WHEN 'cancelled'  THEN '❌ Pedido cancelado'
      ELSE                   '📦 Actualización de pedido'
    END;

    v_body := CASE NEW.status
      WHEN 'accepted'   THEN 'El restaurante aceptó tu pedido.'
      WHEN 'preparing'  THEN 'El restaurante está preparando tu pedido.'
      WHEN 'ready'      THEN 'Tu pedido está listo para ser recogido.'
      WHEN 'on_the_way' THEN 'Tu pedido va en camino. ¡Pronto llegará!'
      WHEN 'delivered'  THEN '¡Tu pedido fue entregado! Que lo disfrutes.'
      WHEN 'cancelled'  THEN 'Tu pedido fue cancelado.'
      ELSE                   'El estado de tu pedido fue actualizado.'
    END;
  END IF;

  -- Invocar la Edge Function send-push-notification via HTTP (pg_net)
  -- La Edge Function se encarga de leer el fcm_token del usuario y enviar a FCM
  PERFORM net.http_post(
    url     := current_setting('app.edge_function_url', true)
               || '/send-push-notification',
    headers := jsonb_build_object(
      'Content-Type',  'application/json',
      'Authorization', 'Bearer ' || current_setting('app.service_role_key', true)
    ),
    body    := jsonb_build_object(
      'user_id',  NEW.customer_id,
      'order_id', NEW.id,
      'type',     v_type,
      'title',    v_title,
      'body',     v_body
    )::text
  );

  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  -- No bloquear la transacción si la notificación falla
  RETURN NEW;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Trigger en orders
-- ─────────────────────────────────────────────────────────────────────────────
DROP TRIGGER IF EXISTS trg_order_status_notify ON orders;
CREATE TRIGGER trg_order_status_notify
  AFTER UPDATE OF status, payment_status ON orders
  FOR EACH ROW
  EXECUTE FUNCTION notify_order_status_change();

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'profiles' AND column_name = 'fcm_token'
  ), 'FALLA: profiles.fcm_token no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_name = 'notification_log'
  ), 'FALLA: tabla notification_log no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.triggers
    WHERE trigger_name = 'trg_order_status_notify'
  ), 'FALLA: trigger trg_order_status_notify no existe';

  RAISE NOTICE '✅ FASE 6 push notifications: todas las verificaciones pasaron';
END;
$$;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000006_gps_tracking.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — GPS Tracking: ubicación en tiempo real del repartidor
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Columnas de ubicación en profiles del repartidor
--    lat/lng actualizadas cada ~5 segundos por la app Driver.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE profiles
  ADD COLUMN IF NOT EXISTS current_lat  double precision,
  ADD COLUMN IF NOT EXISTS current_lng  double precision,
  ADD COLUMN IF NOT EXISTS location_updated_at timestamptz;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. RPC update_driver_location(p_lat, p_lng)
--    Solo el propio repartidor puede actualizar su posición.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_driver_location(
  p_lat double precision,
  p_lng double precision
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  UPDATE profiles
  SET
    current_lat          = p_lat,
    current_lng          = p_lng,
    location_updated_at  = now()
  WHERE id = auth.uid()
    AND role = 'repartidor';

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Solo los repartidores pueden actualizar su ubicación';
  END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION update_driver_location(double precision, double precision) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC get_driver_location(p_order_id)
--    El cliente obtiene la ubicación del repartidor de su pedido activo.
--    Solo el dueño del pedido puede consultar.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION get_driver_location(p_order_id uuid)
RETURNS TABLE (
  lat                double precision,
  lng                double precision,
  location_updated_at timestamptz,
  courier_name       text
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_customer_id uuid;
  v_courier_id  uuid;
BEGIN
  -- Verificar que el pedido pertenece al cliente
  SELECT customer_id, courier_id
  INTO   v_customer_id, v_courier_id
  FROM   orders
  WHERE  id = p_order_id;

  IF v_customer_id IS NULL THEN
    RAISE EXCEPTION 'Pedido no encontrado';
  END IF;

  IF v_customer_id <> auth.uid() THEN
    RAISE EXCEPTION 'No tienes permiso para ver esta información';
  END IF;

  IF v_courier_id IS NULL THEN
    RETURN; -- Sin repartidor asignado aún
  END IF;

  RETURN QUERY
  SELECT
    p.current_lat,
    p.current_lng,
    p.location_updated_at,
    p.full_name
  FROM profiles p
  WHERE p.id = v_courier_id;
END;
$$;

GRANT EXECUTE ON FUNCTION get_driver_location(uuid) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Política RLS para que el cliente pueda ver lat/lng del repartidor asignado
-- ─────────────────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS profiles_location_select ON profiles;
CREATE POLICY profiles_location_select ON profiles
  FOR SELECT
  USING (
    -- Propio perfil
    id = auth.uid()
    -- O es admin
    OR EXISTS (SELECT 1 FROM profiles p2 WHERE p2.id = auth.uid() AND p2.role = 'admin')
    -- O es repartidor asignado a un pedido del cliente autenticado
    OR EXISTS (
      SELECT 1 FROM orders o
      WHERE o.courier_id = profiles.id
        AND o.customer_id = auth.uid()
        AND o.status IN ('on_the_way', 'ready', 'picked_up')
    )
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'profiles' AND column_name = 'current_lat'
  ), 'FALLA: profiles.current_lat no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines
    WHERE routine_name = 'update_driver_location'
  ), 'FALLA: función update_driver_location no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines
    WHERE routine_name = 'get_driver_location'
  ), 'FALLA: función get_driver_location no existe';

  RAISE NOTICE '✅ FASE GPS: todas las verificaciones pasaron';
END;
$$;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000007_kushki_payments.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Kushki: pasarela de pago con tarjeta
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Ampliar el CHECK de payment_method en orders
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE orders
  DROP CONSTRAINT IF EXISTS orders_payment_method_check;

ALTER TABLE orders
  ADD CONSTRAINT orders_payment_method_check
  CHECK (payment_method IN ('transfer', 'cash', 'card'));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Tabla kushki_transactions — log de cada cobro intentado
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS kushki_transactions (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id        uuid        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  customer_id     uuid        NOT NULL REFERENCES profiles(id),
  kushki_token    text        NOT NULL,  -- token de un solo uso del SDK
  amount          numeric     NOT NULL,
  currency        text        NOT NULL DEFAULT 'USD',
  status          text        NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'approved', 'declined', 'error')),
  kushki_response jsonb,                 -- respuesta completa de Kushki API
  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_kushki_tx_order ON kushki_transactions(order_id);

ALTER TABLE kushki_transactions ENABLE ROW LEVEL SECURITY;

-- Solo admin ve todas las transacciones; cliente solo las suyas
DROP POLICY IF EXISTS kushki_tx_select ON kushki_transactions;
CREATE POLICY kushki_tx_select ON kushki_transactions
  FOR SELECT
  USING (
    customer_id = auth.uid()
    OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC register_kushki_charge(p_order_id, p_token, p_amount)
--    Llamado SOLO desde la Edge Function (con service_role).
--    Registra el intento y actualiza orders si fue aprobado.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION register_kushki_charge(
  p_order_id       uuid,
  p_token          text,
  p_amount         numeric,
  p_status         text,
  p_kushki_response jsonb DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_customer_id uuid;
  v_tx_id       uuid;
BEGIN
  SELECT customer_id INTO v_customer_id FROM orders WHERE id = p_order_id;
  IF v_customer_id IS NULL THEN
    RAISE EXCEPTION 'Pedido no encontrado';
  END IF;

  INSERT INTO kushki_transactions
    (order_id, customer_id, kushki_token, amount, status, kushki_response)
  VALUES
    (p_order_id, v_customer_id, p_token, p_amount, p_status, p_kushki_response)
  RETURNING id INTO v_tx_id;

  IF p_status = 'approved' THEN
    UPDATE orders
    SET
      payment_method = 'card',
      payment_status = 'verified'
    WHERE id = p_order_id;
  END IF;

  RETURN v_tx_id;
END;
$$;

-- Solo service_role puede llamar esta función
REVOKE ALL ON FUNCTION register_kushki_charge(uuid, text, numeric, text, jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION register_kushki_charge(uuid, text, numeric, text, jsonb) TO service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Setting: clave pública de Kushki (la app Android la necesita para tokenizar)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO settings (key, value, description)
VALUES ('kushki_public_key', 'TU_KUSHKI_PUBLIC_KEY', 'Clave pública de Kushki (sandbox o producción)')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description)
VALUES ('kushki_enabled', 'false', 'Activar cobro con tarjeta Kushki (true/false)')
ON CONFLICT (key) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_name = 'kushki_transactions'
  ), 'FALLA: tabla kushki_transactions no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'register_kushki_charge'
  ), 'FALLA: función register_kushki_charge no existe';

  RAISE NOTICE '✅ FASE Kushki: todas las verificaciones pasaron';
END;
$$;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000008_menu_images.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Bucket Storage para imágenes del menú
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- Bucket público para imágenes de platos (se sirven directamente al cliente)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'menu-images',
  'menu-images',
  true,         -- público: la URL se puede usar directamente en <img>
  3145728,      -- 3 MB por imagen
  ARRAY['image/jpeg','image/png','image/webp','image/gif']
)
ON CONFLICT (id) DO NOTHING;

-- Política: restaurante autenticado puede subir imágenes
DROP POLICY IF EXISTS menu_images_insert ON storage.objects;
CREATE POLICY menu_images_insert
  ON storage.objects FOR INSERT
  WITH CHECK (
    bucket_id = 'menu-images'
    AND auth.role() = 'authenticated'
  );

-- Política: todo el mundo puede leer (bucket público igual, pero es buena práctica)
DROP POLICY IF EXISTS menu_images_select ON storage.objects;
CREATE POLICY menu_images_select
  ON storage.objects FOR SELECT
  USING (bucket_id = 'menu-images');

-- Política: solo el que subió o admin puede borrar
DROP POLICY IF EXISTS menu_images_delete ON storage.objects;
CREATE POLICY menu_images_delete
  ON storage.objects FOR DELETE
  USING (
    bucket_id = 'menu-images'
    AND (
      owner = auth.uid()
      OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    )
  );

DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM storage.buckets WHERE id = 'menu-images'
  ), 'FALLA: bucket menu-images no existe';
  RAISE NOTICE '✅ FASE menu-images: bucket creado correctamente';
END;
$$;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000009_ratings.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Calificaciones y reseñas
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Tabla ratings — una calificación por pedido entregado
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ratings (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id        uuid        NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
  customer_id     uuid        NOT NULL REFERENCES profiles(id),
  restaurant_id   text        NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
  courier_id      uuid        REFERENCES profiles(id),
  -- Estrellas: 1-5
  restaurant_stars integer    NOT NULL CHECK (restaurant_stars BETWEEN 1 AND 5),
  courier_stars    integer    CHECK (courier_stars BETWEEN 1 AND 5),
  -- Texto opcional
  restaurant_comment text,
  courier_comment    text,
  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ratings_restaurant ON ratings(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_ratings_customer   ON ratings(customer_id);
CREATE INDEX IF NOT EXISTS idx_ratings_courier    ON ratings(courier_id);

ALTER TABLE ratings ENABLE ROW LEVEL SECURITY;

-- Cliente crea y lee sus propias calificaciones
DROP POLICY IF EXISTS ratings_select ON ratings;
CREATE POLICY ratings_select ON ratings
  FOR SELECT
  USING (
    customer_id = auth.uid()
    OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role IN ('admin','restaurant'))
  );

DROP POLICY IF EXISTS ratings_insert ON ratings;
CREATE POLICY ratings_insert ON ratings
  FOR INSERT
  WITH CHECK (customer_id = auth.uid());

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Vista restaurants_avg_rating — promedio visible para la app
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW restaurants_avg_rating AS
SELECT
  restaurant_id,
  ROUND(AVG(restaurant_stars)::numeric, 1) AS avg_stars,
  COUNT(*)                                  AS total_ratings
FROM ratings
GROUP BY restaurant_id;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC submit_rating — cliente califica el pedido
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION submit_rating(
  p_order_id           uuid,
  p_restaurant_stars   integer,
  p_courier_stars      integer  DEFAULT NULL,
  p_restaurant_comment text     DEFAULT NULL,
  p_courier_comment    text     DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_customer_id   uuid;
  v_restaurant_id text;
  v_courier_id    uuid;
  v_status        text;
  v_rating_id     uuid;
BEGIN
  -- Verificar que el pedido es del cliente y está entregado
  SELECT customer_id, restaurant_id, courier_id, status
  INTO   v_customer_id, v_restaurant_id, v_courier_id, v_status
  FROM   orders WHERE id = p_order_id;

  IF v_customer_id IS NULL THEN
    RAISE EXCEPTION 'Pedido no encontrado';
  END IF;
  IF v_customer_id <> auth.uid() THEN
    RAISE EXCEPTION 'No puedes calificar este pedido';
  END IF;
  IF v_status <> 'delivered' THEN
    RAISE EXCEPTION 'Solo puedes calificar pedidos entregados';
  END IF;

  INSERT INTO ratings (
    order_id, customer_id, restaurant_id, courier_id,
    restaurant_stars, courier_stars,
    restaurant_comment, courier_comment
  )
  VALUES (
    p_order_id, auth.uid(), v_restaurant_id, v_courier_id,
    p_restaurant_stars, p_courier_stars,
    p_restaurant_comment, p_courier_comment
  )
  ON CONFLICT (order_id) DO UPDATE
    SET restaurant_stars   = EXCLUDED.restaurant_stars,
        courier_stars      = EXCLUDED.courier_stars,
        restaurant_comment = EXCLUDED.restaurant_comment,
        courier_comment    = EXCLUDED.courier_comment
  RETURNING id INTO v_rating_id;

  RETURN v_rating_id;
END;
$$;

GRANT EXECUTE ON FUNCTION submit_rating(uuid, integer, integer, text, text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. RPC get_order_rating(p_order_id) — obtiene la calificación de un pedido
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION get_order_rating(p_order_id uuid)
RETURNS TABLE (
  restaurant_stars   integer,
  courier_stars      integer,
  restaurant_comment text,
  courier_comment    text
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT restaurant_stars, courier_stars, restaurant_comment, courier_comment
  FROM   ratings
  WHERE  order_id    = p_order_id
    AND  customer_id = auth.uid();
$$;

GRANT EXECUTE ON FUNCTION get_order_rating(uuid) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_name = 'ratings'
  ), 'FALLA: tabla ratings no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'submit_rating'
  ), 'FALLA: función submit_rating no existe';

  RAISE NOTICE '✅ FASE ratings: todas las verificaciones pasaron';
END;
$$;

COMMIT;

select 'PASO 1 OK: parte A aplicada' as resultado;
