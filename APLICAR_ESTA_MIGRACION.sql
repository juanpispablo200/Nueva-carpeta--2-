-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO: Migración completa de mejoras web
-- INSTRUCCIONES: Pega este SQL en el SQL Editor de Supabase y ejecuta.
-- URL: https://supabase.com/dashboard/project/jhgwtdcajgealfuwkifk/sql/new
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

CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action);

-- ── 2. Columnas nuevas ────────────────────────────────────────────────────────
ALTER TABLE dishes ADD COLUMN IF NOT EXISTS deleted_at timestamptz;
ALTER TABLE dishes ADD COLUMN IF NOT EXISTS image_path text;
CREATE INDEX IF NOT EXISTS idx_dishes_deleted_at ON dishes(deleted_at) WHERE deleted_at IS NULL;

ALTER TABLE profiles ADD COLUMN IF NOT EXISTS is_active boolean DEFAULT true;
UPDATE profiles SET is_active = true WHERE is_active IS NULL;
CREATE INDEX IF NOT EXISTS idx_profiles_active ON profiles(is_active) WHERE is_active = true;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS courier_id uuid REFERENCES profiles(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_orders_courier ON orders(courier_id);

ALTER TABLE order_events ADD COLUMN IF NOT EXISTS reason text;

-- ── 3. Trigger: validar transición de estados ─────────────────────────────────
CREATE OR REPLACE FUNCTION validate_order_status_transition()
RETURNS TRIGGER AS $$
DECLARE
  user_role text;
BEGIN
  IF NEW.status = OLD.status THEN
    RETURN NEW;
  END IF;

  SELECT role INTO user_role FROM profiles WHERE id = auth.uid();

  IF user_role = 'admin' THEN
    RETURN NEW;
  END IF;

  IF user_role = 'restaurant' THEN
    IF (OLD.status = 'pending'   AND NEW.status = 'accepted')  OR
       (OLD.status = 'accepted'  AND NEW.status = 'preparing') OR
       (OLD.status = 'preparing' AND NEW.status = 'ready')     OR
       (NEW.status = 'cancelled') THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Transición no permitida: % → % para rol restaurant', OLD.status, NEW.status;
  END IF;

  IF user_role = 'repartidor' THEN
    IF (OLD.status = 'ready'      AND NEW.status = 'picked_up')  OR
       (OLD.status = 'picked_up'  AND NEW.status = 'on_the_way') OR
       (OLD.status = 'on_the_way' AND NEW.status = 'delivered')  THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Transición no permitida: % → % para rol repartidor', OLD.status, NEW.status;
  END IF;

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

-- ── 5. Trigger: auditoría de cambios de rol ───────────────────────────────────
CREATE OR REPLACE FUNCTION audit_role_change()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.role <> OLD.role THEN
    INSERT INTO audit_log (user_id, action, table_name, record_id, old_data, new_data)
    VALUES (auth.uid(), 'role_change', 'profiles', NEW.id::text,
            jsonb_build_object('role', OLD.role), jsonb_build_object('role', NEW.role));
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS audit_role_change_trigger ON profiles;
CREATE TRIGGER audit_role_change_trigger
  AFTER UPDATE OF role ON profiles
  FOR EACH ROW
  EXECUTE FUNCTION audit_role_change();

-- ── 6. Función: prevenir quitar el último admin ───────────────────────────────
CREATE OR REPLACE FUNCTION prevent_last_admin_removal()
RETURNS TRIGGER AS $$
DECLARE
  admin_count int;
BEGIN
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

-- ── 7. Función helper: registrar auditoría manual ─────────────────────────────
CREATE OR REPLACE FUNCTION log_audit(
  p_action      text,
  p_table_name  text    DEFAULT NULL,
  p_record_id   text    DEFAULT NULL,
  p_old_data    jsonb   DEFAULT NULL,
  p_new_data    jsonb   DEFAULT NULL
)
RETURNS void AS $$
BEGIN
  INSERT INTO audit_log (user_id, action, table_name, record_id, old_data, new_data)
  VALUES (auth.uid(), p_action, p_table_name, p_record_id, p_old_data, p_new_data);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ── 8. RLS ────────────────────────────────────────────────────────────────────
ALTER TABLE audit_log    ENABLE ROW LEVEL SECURITY;
ALTER TABLE dishes       ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders       ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items  ENABLE ROW LEVEL SECURITY;
ALTER TABLE profiles     ENABLE ROW LEVEL SECURITY;
ALTER TABLE restaurants  ENABLE ROW LEVEL SECURITY;

-- audit_log: solo admin
DROP POLICY IF EXISTS audit_log_admin_read ON audit_log;
CREATE POLICY audit_log_admin_read ON audit_log FOR SELECT
  USING (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- profiles
DROP POLICY IF EXISTS profiles_select_own ON profiles;
CREATE POLICY profiles_select_own ON profiles FOR SELECT
  USING (id = auth.uid() OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

DROP POLICY IF EXISTS profiles_update_admin ON profiles;
CREATE POLICY profiles_update_admin ON profiles FOR UPDATE
  USING (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

DROP POLICY IF EXISTS profiles_insert_admin ON profiles;
CREATE POLICY profiles_insert_admin ON profiles FOR INSERT
  WITH CHECK (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- restaurants
DROP POLICY IF EXISTS restaurants_select ON restaurants;
CREATE POLICY restaurants_select ON restaurants FOR SELECT USING (TRUE);

DROP POLICY IF EXISTS restaurants_update ON restaurants;
CREATE POLICY restaurants_update ON restaurants FOR UPDATE
  USING (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin') OR owner_id = auth.uid());

DROP POLICY IF EXISTS restaurants_insert_admin ON restaurants;
CREATE POLICY restaurants_insert_admin ON restaurants FOR INSERT
  WITH CHECK (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- dishes
DROP POLICY IF EXISTS dishes_select ON dishes;
CREATE POLICY dishes_select ON dishes FOR SELECT USING (deleted_at IS NULL);

DROP POLICY IF EXISTS dishes_insert ON dishes;
CREATE POLICY dishes_insert ON dishes FOR INSERT
  WITH CHECK (EXISTS (
    SELECT 1 FROM restaurants r JOIN profiles p ON p.id = auth.uid()
    WHERE r.id = restaurant_id AND (p.role = 'admin' OR r.owner_id = p.id)
  ));

DROP POLICY IF EXISTS dishes_update ON dishes;
CREATE POLICY dishes_update ON dishes FOR UPDATE
  USING (EXISTS (
    SELECT 1 FROM restaurants r JOIN profiles p ON p.id = auth.uid()
    WHERE r.id = restaurant_id AND (p.role = 'admin' OR r.owner_id = p.id)
  ));

-- orders
DROP POLICY IF EXISTS orders_select ON orders;
CREATE POLICY orders_select ON orders FOR SELECT
  USING (
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    OR restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
    OR courier_id  = auth.uid()
    OR customer_id = auth.uid()
  );

DROP POLICY IF EXISTS orders_update ON orders;
CREATE POLICY orders_update ON orders FOR UPDATE
  USING (
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    OR restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
    OR courier_id = auth.uid()
  );

DROP POLICY IF EXISTS orders_insert ON orders;
CREATE POLICY orders_insert ON orders FOR INSERT
  WITH CHECK (customer_id = auth.uid() OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- order_events
DROP POLICY IF EXISTS order_events_select ON order_events;
CREATE POLICY order_events_select ON order_events FOR SELECT
  USING (EXISTS (
    SELECT 1 FROM orders o WHERE o.id = order_id AND (
      EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
      OR o.restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
      OR o.courier_id  = auth.uid()
      OR o.customer_id = auth.uid()
    )
  ));

DROP POLICY IF EXISTS order_events_insert ON order_events;
CREATE POLICY order_events_insert ON order_events FOR INSERT
  WITH CHECK (EXISTS (
    SELECT 1 FROM orders o WHERE o.id = order_id AND (
      EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
      OR o.restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
      OR o.courier_id = auth.uid()
    )
  ));

-- order_items
DROP POLICY IF EXISTS order_items_select ON order_items;
CREATE POLICY order_items_select ON order_items FOR SELECT
  USING (EXISTS (
    SELECT 1 FROM orders o WHERE o.id = order_id AND (
      EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
      OR o.restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
      OR o.courier_id  = auth.uid()
      OR o.customer_id = auth.uid()
    )
  ));

-- ── 9. Verificación final ─────────────────────────────────────────────────────
SELECT 'audit_log'    AS tabla, COUNT(*) FROM audit_log
UNION ALL
SELECT 'profiles con is_active', COUNT(*) FROM profiles WHERE is_active IS NOT NULL
UNION ALL
SELECT 'orders con courier_id col', COUNT(*) FROM information_schema.columns
  WHERE table_name = 'orders' AND column_name = 'courier_id';
