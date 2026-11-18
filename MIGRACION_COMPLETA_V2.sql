-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Migración completa V2  (idempotente, sin romper datos)
-- Ejecutar en: https://supabase.com/dashboard/project/tjgavwygdotetoatitju/sql/new
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. TABLA audit_log
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_log (
  id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid        REFERENCES auth.users(id) ON DELETE SET NULL,
  action     text        NOT NULL,
  entity     text,
  entity_id  text,
  details    jsonb,
  created_at timestamptz DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_user_id    ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity     ON audit_log(entity, entity_id);

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS audit_log_admin_read   ON audit_log;
DROP POLICY IF EXISTS audit_log_admin_insert ON audit_log;

CREATE POLICY audit_log_admin_read ON audit_log FOR SELECT
  USING (EXISTS (
    SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'
  ));

-- Permite que cualquier usuario autenticado inserte (triggers + RPC lo usan)
CREATE POLICY audit_log_admin_insert ON audit_log FOR INSERT
  WITH CHECK (auth.uid() IS NOT NULL);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. COLUMNAS NUEVAS
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS is_active boolean DEFAULT true;
UPDATE profiles SET is_active = true WHERE is_active IS NULL;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS courier_id uuid
  REFERENCES profiles(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_orders_courier_id ON orders(courier_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. TABLA settings
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS settings (
  key        text PRIMARY KEY,
  value      text NOT NULL,
  updated_at timestamptz DEFAULT now(),
  updated_by uuid REFERENCES auth.users(id) ON DELETE SET NULL
);

ALTER TABLE settings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS settings_admin_all  ON settings;
DROP POLICY IF EXISTS settings_read_all   ON settings;

CREATE POLICY settings_admin_all ON settings FOR ALL
  USING (EXISTS (
    SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'
  ));

-- Lectura pública de settings no sensibles
CREATE POLICY settings_read_all ON settings FOR SELECT USING (true);

-- Seed de valores por defecto
INSERT INTO settings (key, value) VALUES
  ('platform_name',    'Jagoo')          ON CONFLICT (key) DO NOTHING;
INSERT INTO settings (key, value) VALUES
  ('default_delivery_fee', '2.50')       ON CONFLICT (key) DO NOTHING;
INSERT INTO settings (key, value) VALUES
  ('maintenance_mode', 'false')          ON CONFLICT (key) DO NOTHING;
INSERT INTO settings (key, value) VALUES
  ('global_schedule', '08:00-22:00')     ON CONFLICT (key) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. TRIGGER: validar transiciones de estado en orders
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION fn_validate_order_transition()
RETURNS TRIGGER AS $$
DECLARE
  valid boolean := false;
BEGIN
  IF NEW.status = OLD.status THEN RETURN NEW; END IF;

  -- Cancelled permitido desde cualquier estado excepto delivered
  IF NEW.status = 'cancelled' AND OLD.status <> 'delivered' THEN
    RETURN NEW;
  END IF;

  -- Flujo lineal permitido
  valid := (OLD.status = 'pending'    AND NEW.status = 'accepted')
        OR (OLD.status = 'accepted'   AND NEW.status = 'preparing')
        OR (OLD.status = 'preparing'  AND NEW.status = 'ready')
        OR (OLD.status = 'ready'      AND NEW.status = 'on_the_way')
        OR (OLD.status = 'on_the_way' AND NEW.status = 'delivered');

  -- Admin puede hacer cualquier transición
  IF EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin') THEN
    RETURN NEW;
  END IF;

  IF NOT valid THEN
    RAISE EXCEPTION 'Transición inválida: % → %. Flujo permitido: pending→accepted→preparing→ready→on_the_way→delivered',
      OLD.status, NEW.status;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_validate_order_transition ON orders;
CREATE TRIGGER trg_validate_order_transition
  BEFORE UPDATE OF status ON orders
  FOR EACH ROW EXECUTE FUNCTION fn_validate_order_transition();

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. FUNCIÓN: crear perfil de repartidor (sin columna email)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION create_courier_profile(
  p_id        uuid,
  p_full_name text,
  p_phone     text,
  p_email     text
) RETURNS void AS $$
BEGIN
  INSERT INTO profiles (id, full_name, phone, role, is_active)
  VALUES (p_id, p_full_name, p_phone, 'repartidor', true)
  ON CONFLICT (id) DO UPDATE SET
    full_name = p_full_name,
    phone     = p_phone,
    role      = 'repartidor',
    is_active = true;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION create_courier_profile(uuid,text,text,text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. FUNCIÓN: crear usuario restaurante desde admin
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION create_restaurant_user(
  p_id            uuid,
  p_full_name     text,
  p_phone         text,
  p_restaurant_id uuid
) RETURNS void AS $$
BEGIN
  -- Crear perfil con rol restaurant
  INSERT INTO profiles (id, full_name, phone, role, is_active)
  VALUES (p_id, p_full_name, p_phone, 'restaurant', true)
  ON CONFLICT (id) DO UPDATE SET
    full_name = p_full_name,
    phone     = p_phone,
    role      = 'restaurant',
    is_active = true;

  -- Vincular usuario al restaurante como owner
  UPDATE restaurants SET owner_id = p_id WHERE id = p_restaurant_id;

  -- Registrar en audit_log
  INSERT INTO audit_log (user_id, action, entity, entity_id, details)
  VALUES (
    auth.uid(),
    'create_restaurant_user',
    'restaurants',
    p_restaurant_id::text,
    jsonb_build_object('new_user_id', p_id, 'full_name', p_full_name)
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION create_restaurant_user(uuid,uuid,text,text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. POLÍTICAS RLS (idempotentes)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE profiles     ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders       ENABLE ROW LEVEL SECURITY;
ALTER TABLE restaurants  ENABLE ROW LEVEL SECURITY;

-- profiles: select propio o admin
DROP POLICY IF EXISTS profiles_select ON profiles;
CREATE POLICY profiles_select ON profiles FOR SELECT
  USING (id = auth.uid() OR EXISTS (
    SELECT 1 FROM profiles p2 WHERE p2.id = auth.uid() AND p2.role = 'admin'
  ));

-- profiles: insert propio o admin
DROP POLICY IF EXISTS profiles_insert ON profiles;
CREATE POLICY profiles_insert ON profiles FOR INSERT
  WITH CHECK (
    id = auth.uid()
    OR EXISTS (SELECT 1 FROM profiles p2 WHERE p2.id = auth.uid() AND p2.role = 'admin')
  );

-- profiles: update admin
DROP POLICY IF EXISTS profiles_update ON profiles;
CREATE POLICY profiles_update ON profiles FOR UPDATE
  USING (EXISTS (SELECT 1 FROM profiles p2 WHERE p2.id = auth.uid() AND p2.role = 'admin'));

-- restaurants: lectura pública
DROP POLICY IF EXISTS restaurants_select ON restaurants;
CREATE POLICY restaurants_select ON restaurants FOR SELECT USING (true);

-- restaurants: insert admin
DROP POLICY IF EXISTS restaurants_insert ON restaurants;
CREATE POLICY restaurants_insert ON restaurants FOR INSERT
  WITH CHECK (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- restaurants: update admin o owner
DROP POLICY IF EXISTS restaurants_update ON restaurants;
CREATE POLICY restaurants_update ON restaurants FOR UPDATE
  USING (
    owner_id = auth.uid()
    OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
  );

-- orders: select según rol
DROP POLICY IF EXISTS orders_select ON orders;
CREATE POLICY orders_select ON orders FOR SELECT
  USING (
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    OR restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
    OR courier_id = auth.uid()
    OR customer_id = auth.uid()
  );

-- orders: update admin, restaurante o repartidor asignado
DROP POLICY IF EXISTS orders_update ON orders;
CREATE POLICY orders_update ON orders FOR UPDATE
  USING (
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    OR restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
    OR courier_id = auth.uid()
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. SEED — usuario restaurante de prueba (2 líneas funcionales)
-- ─────────────────────────────────────────────────────────────────────────────
-- Ejecutar DESPUÉS de que exista al menos un restaurante.
-- Reemplaza 'ID_DEL_RESTAURANTE' con un ID real de tu tabla restaurants.
--
-- INSERT INTO auth.users (id,instance_id,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change_token,recovery_token)
-- VALUES (gen_random_uuid(),'00000000-0000-0000-0000-000000000000','restaurante@jagoo.com',crypt('Rest123',gen_salt('bf')),NOW(),'{"provider":"email","providers":["email"]}','{}',NOW(),NOW(),'','','');
-- SELECT create_restaurant_user(id, 'Dueño Restaurante', '+593999000111', (SELECT id FROM restaurants LIMIT 1)) FROM auth.users WHERE email='restaurante@jagoo.com';

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. VERIFICACIÓN FINAL
-- ─────────────────────────────────────────────────────────────────────────────
SELECT 'audit_log existe'       AS check, EXISTS(SELECT 1 FROM information_schema.tables WHERE table_name='audit_log')    AS ok
UNION ALL
SELECT 'settings existe',         EXISTS(SELECT 1 FROM information_schema.tables WHERE table_name='settings')
UNION ALL
SELECT 'courier_id en orders',    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_name='orders' AND column_name='courier_id')
UNION ALL
SELECT 'is_active en profiles',   EXISTS(SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='is_active')
UNION ALL
SELECT 'trigger transiciones',    EXISTS(SELECT 1 FROM information_schema.triggers WHERE trigger_name='trg_validate_order_transition')
UNION ALL
SELECT 'fn create_courier',       EXISTS(SELECT 1 FROM information_schema.routines WHERE routine_name='create_courier_profile')
UNION ALL
SELECT 'fn create_restaurant',    EXISTS(SELECT 1 FROM information_schema.routines WHERE routine_name='create_restaurant_user')
UNION ALL
SELECT 'settings seed',           (SELECT COUNT(*)::text FROM settings) || ' filas';
