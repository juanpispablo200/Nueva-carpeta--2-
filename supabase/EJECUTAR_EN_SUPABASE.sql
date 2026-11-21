-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — SQL COMPLETO PARA EJECUTAR EN SUPABASE SQL EDITOR
-- Incluye: fix login (RLS), usuarios, favoritos, perfil editable
-- Ejecuta TODO de una vez. Es idempotente (puede ejecutarse varias veces).
-- ══════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';

-- ─────────────────────────────────────────────────────────────────────────────
-- PARTE 1: Fix get_my_role sin recursión (arregla "Database error" en login)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION get_my_role()
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
DECLARE v_role text;
BEGIN
  SET LOCAL row_security = off;
  SELECT role INTO v_role FROM profiles WHERE id = auth.uid();
  RETURN COALESCE(v_role, 'customer');
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- PARTE 2: Limpiar y recrear políticas de profiles
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE pol record;
BEGIN
  FOR pol IN SELECT policyname FROM pg_policies WHERE tablename = 'profiles' AND schemaname = 'public'
  LOOP EXECUTE format('DROP POLICY IF EXISTS %I ON profiles', pol.policyname); END LOOP;
END;
$$;

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "profiles: leer propio"
  ON profiles FOR SELECT USING (id = auth.uid());

CREATE POLICY "profiles: admin lee todo"
  ON profiles FOR SELECT USING (get_my_role() = 'admin');

CREATE POLICY "profiles: insertar propio"
  ON profiles FOR INSERT WITH CHECK (id = auth.uid());

CREATE POLICY "profiles: actualizar propio"
  ON profiles FOR UPDATE
  USING (id = auth.uid())
  WITH CHECK (id = auth.uid() AND role = (SELECT role FROM profiles WHERE id = auth.uid()));

CREATE POLICY "profiles: admin actualiza todo"
  ON profiles FOR UPDATE USING (get_my_role() = 'admin');

-- ─────────────────────────────────────────────────────────────────────────────
-- PARTE 3: Columnas adicionales en profiles
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE profiles
  ADD COLUMN IF NOT EXISTS is_active    boolean     NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS is_available boolean     NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS fcm_token    text,
  ADD COLUMN IF NOT EXISTS current_lat  double precision,
  ADD COLUMN IF NOT EXISTS current_lng  double precision,
  ADD COLUMN IF NOT EXISTS location_updated_at timestamptz,
  ADD COLUMN IF NOT EXISTS avatar_url   text;

-- Columna repartidor en check de rol (si aún usa el check antiguo)
DO $$
BEGIN
  ALTER TABLE profiles DROP CONSTRAINT IF EXISTS profiles_role_check;
  ALTER TABLE profiles ADD CONSTRAINT profiles_role_check
    CHECK (role IN ('customer', 'restaurant', 'admin', 'repartidor'));
EXCEPTION WHEN others THEN NULL;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- PARTE 4: create_restaurant_user RPC
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION create_restaurant_user(
  p_id uuid, p_full_name text, p_phone text DEFAULT NULL, p_restaurant_id uuid DEFAULT NULL
)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  INSERT INTO profiles (id, full_name, phone, role, is_active)
  VALUES (p_id, p_full_name, COALESCE(p_phone, ''), 'restaurant', true)
  ON CONFLICT (id) DO UPDATE
    SET full_name = EXCLUDED.full_name, phone = EXCLUDED.phone,
        role = 'restaurant', is_active = true;
  IF p_restaurant_id IS NOT NULL THEN
    UPDATE restaurants SET owner_id = p_id WHERE id = p_restaurant_id;
  END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION create_restaurant_user(uuid, text, text, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION create_restaurant_user(uuid, text, text, uuid) TO service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- PARTE 5: update_driver_location RPC
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_driver_location(p_lat double precision, p_lng double precision)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  UPDATE profiles SET current_lat = p_lat, current_lng = p_lng,
    location_updated_at = now()
  WHERE id = auth.uid() AND role = 'repartidor';
END;
$$;
GRANT EXECUTE ON FUNCTION update_driver_location(double precision, double precision) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- PARTE 6: Tabla favorites + toggle_favorite RPC
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS favorites (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  restaurant_id text NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, restaurant_id)
);
CREATE INDEX IF NOT EXISTS idx_favorites_user ON favorites(user_id);
ALTER TABLE favorites ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "favorites: leer propios" ON favorites;
CREATE POLICY "favorites: leer propios" ON favorites FOR SELECT USING (user_id = auth.uid());
DROP POLICY IF EXISTS "favorites: insertar propios" ON favorites;
CREATE POLICY "favorites: insertar propios" ON favorites FOR INSERT WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS "favorites: eliminar propios" ON favorites;
CREATE POLICY "favorites: eliminar propios" ON favorites FOR DELETE USING (user_id = auth.uid());

CREATE OR REPLACE FUNCTION toggle_favorite(p_restaurant_id text)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_exists boolean;
BEGIN
  SELECT EXISTS(SELECT 1 FROM favorites WHERE user_id = auth.uid() AND restaurant_id = p_restaurant_id) INTO v_exists;
  IF v_exists THEN
    DELETE FROM favorites WHERE user_id = auth.uid() AND restaurant_id = p_restaurant_id;
    RETURN false;
  ELSE
    INSERT INTO favorites (user_id, restaurant_id) VALUES (auth.uid(), p_restaurant_id);
    RETURN true;
  END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION toggle_favorite(text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- PARTE 7: Usuarios repartidor (desarrollo)
-- ─────────────────────────────────────────────────────────────────────────────

-- Gabriel: gabriel900998@gmail.com / 123456
DO $$
DECLARE v_id uuid;
BEGIN
  SELECT id INTO v_id FROM auth.users WHERE email = 'gabriel900998@gmail.com';
  IF v_id IS NULL THEN
    v_id := gen_random_uuid();
    INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password,
      email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data)
    VALUES (v_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
      'gabriel900998@gmail.com', crypt('123456', gen_salt('bf')), now(), now(), now(),
      '{"provider":"email","providers":["email"]}', '{"full_name":"Gabriel"}');
  ELSE
    UPDATE auth.users SET encrypted_password = crypt('123456', gen_salt('bf')),
      email_confirmed_at = now(), updated_at = now() WHERE id = v_id;
  END IF;
  INSERT INTO profiles (id, full_name, phone, role, is_active, is_available)
  VALUES (v_id, 'Gabriel', '0999000998', 'repartidor', true, false)
  ON CONFLICT (id) DO UPDATE SET role = 'repartidor', is_active = true,
    is_available = false, full_name = 'Gabriel';
  RAISE NOTICE '✅ gabriel900998@gmail.com / 123456';
END;
$$;

-- repartidor@jagoo.test / Repartidor2024!
DO $$
DECLARE v_id uuid;
BEGIN
  SELECT id INTO v_id FROM auth.users WHERE email = 'repartidor@jagoo.test';
  IF v_id IS NULL THEN
    v_id := gen_random_uuid();
    INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password,
      email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data)
    VALUES (v_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
      'repartidor@jagoo.test', crypt('Repartidor2024!', gen_salt('bf')), now(), now(), now(),
      '{"provider":"email","providers":["email"]}', '{"full_name":"Repartidor Test"}');
  ELSE
    UPDATE auth.users SET encrypted_password = crypt('Repartidor2024!', gen_salt('bf')),
      email_confirmed_at = now(), updated_at = now() WHERE id = v_id;
  END IF;
  INSERT INTO profiles (id, full_name, phone, role, is_active, is_available)
  VALUES (v_id, 'Repartidor Test', '0990000001', 'repartidor', true, false)
  ON CONFLICT (id) DO UPDATE SET role = 'repartidor', is_active = true,
    is_available = false, full_name = 'Repartidor Test';
  RAISE NOTICE '✅ repartidor@jagoo.test / Repartidor2024!';
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- VERIFICACIÓN FINAL
-- ─────────────────────────────────────────────────────────────────────────────
SELECT u.email, p.role, p.full_name, p.is_active
FROM auth.users u
JOIN profiles p ON p.id = u.id
ORDER BY p.role, u.email;
