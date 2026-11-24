-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — SQL COMPLETO PARA EJECUTAR EN SUPABASE SQL EDITOR
-- Incluye: fix login, usuarios sin perfil, permisos RLS seguros
-- Idempotente — puede ejecutarse varias veces sin problema
-- ══════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Fix get_my_role sin recursión (arregla "Database error" en login)
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
-- 2. Limpiar y recrear políticas de profiles sin conflictos
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE pol record;
BEGIN
  FOR pol IN SELECT policyname FROM pg_policies
    WHERE tablename = 'profiles' AND schemaname = 'public'
  LOOP
    EXECUTE format('DROP POLICY IF EXISTS %I ON profiles', pol.policyname);
  END LOOP;
END;
$$;

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "profiles: leer propio"
  ON profiles FOR SELECT USING (id = auth.uid());

CREATE POLICY "profiles: admin lee todo"
  ON profiles FOR SELECT USING (get_my_role() = 'admin');

CREATE POLICY "profiles: insertar propio"
  ON profiles FOR INSERT WITH CHECK (id = auth.uid());

-- El usuario puede actualizar su nombre y teléfono pero NO su rol
CREATE POLICY "profiles: actualizar propio"
  ON profiles FOR UPDATE
  USING (id = auth.uid())
  WITH CHECK (
    id = auth.uid() AND
    role = (SELECT role FROM profiles WHERE id = auth.uid())
  );

CREATE POLICY "profiles: admin actualiza todo"
  ON profiles FOR UPDATE USING (get_my_role() = 'admin');

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Columnas necesarias en profiles (idempotente)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE profiles
  ADD COLUMN IF NOT EXISTS is_active    boolean     NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS is_available boolean     NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS fcm_token    text,
  ADD COLUMN IF NOT EXISTS current_lat  double precision,
  ADD COLUMN IF NOT EXISTS current_lng  double precision,
  ADD COLUMN IF NOT EXISTS location_updated_at timestamptz,
  ADD COLUMN IF NOT EXISTS avatar_url   text;

DO $$
BEGIN
  ALTER TABLE profiles DROP CONSTRAINT IF EXISTS profiles_role_check;
  ALTER TABLE profiles ADD CONSTRAINT profiles_role_check
    CHECK (role IN ('customer', 'restaurant', 'admin', 'repartidor'));
EXCEPTION WHEN others THEN NULL;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Crear perfil para todos los usuarios auth que no lo tengan
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO profiles (id, full_name, phone, role, is_active)
SELECT
  u.id,
  COALESCE(u.raw_user_meta_data->>'full_name', split_part(u.email, '@', 1)),
  COALESCE(u.raw_user_meta_data->>'phone', ''),
  CASE
    WHEN u.email = 'admin@jagoo.com'         THEN 'admin'
    WHEN u.email LIKE '%repartidor%'         THEN 'repartidor'
    WHEN u.email = 'gabriel900998@gmail.com' THEN 'repartidor'
    WHEN u.email LIKE '%restaurante%'        THEN 'restaurant'
    ELSE 'customer'
  END,
  true
FROM auth.users u
LEFT JOIN profiles p ON p.id = u.id
WHERE p.id IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Trigger handle_new_user corregido
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.profiles (id, full_name, phone, role, is_active)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'full_name', ''),
    COALESCE(NEW.raw_user_meta_data->>'phone', ''),
    'customer',
    true
  )
  ON CONFLICT (id) DO NOTHING;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_new_user();

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. RPCs necesarias
-- ─────────────────────────────────────────────────────────────────────────────

-- update_driver_location
CREATE OR REPLACE FUNCTION update_driver_location(
  p_lat double precision, p_lng double precision
)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  UPDATE profiles
  SET current_lat = p_lat, current_lng = p_lng, location_updated_at = now()
  WHERE id = auth.uid() AND role = 'repartidor' AND is_active = true;
END;
$$;
GRANT EXECUTE ON FUNCTION update_driver_location(double precision, double precision) TO authenticated;

-- toggle_favorite
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
  SELECT EXISTS(SELECT 1 FROM favorites
    WHERE user_id = auth.uid() AND restaurant_id = p_restaurant_id) INTO v_exists;
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
-- 7. Cambiar contraseña admin (garantiza acceso)
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE auth.users
SET encrypted_password = crypt('Admin2024!', gen_salt('bf')),
    email_confirmed_at = now(),
    updated_at = now()
WHERE email = 'admin@jagoo.com';

INSERT INTO profiles (id, full_name, phone, role, is_active)
SELECT id, 'Administrador Jagoo', '', 'admin', true
FROM auth.users WHERE email = 'admin@jagoo.com'
ON CONFLICT (id) DO UPDATE SET role = 'admin', is_active = true;

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. Verificación final
-- ─────────────────────────────────────────────────────────────────────────────
SELECT
  u.email,
  p.full_name,
  p.role,
  p.is_active,
  u.email_confirmed_at IS NOT NULL AS confirmado
FROM auth.users u
JOIN profiles p ON p.id = u.id
ORDER BY p.role, u.email;
