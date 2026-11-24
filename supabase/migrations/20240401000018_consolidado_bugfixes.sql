-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Consolidado bugfixes
-- Aplica: fix login, perfiles faltantes, trigger, RPCs, seguridad
-- Idempotente — puede ejecutarse múltiples veces sin problema
-- ══════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. get_my_role sin recursión RLS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION get_my_role()
RETURNS text LANGUAGE plpgsql SECURITY DEFINER STABLE
SET search_path = public AS $$
DECLARE v_role text;
BEGIN
  SET LOCAL row_security = off;
  SELECT role INTO v_role FROM profiles WHERE id = auth.uid();
  RETURN COALESCE(v_role, 'customer');
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Políticas RLS de profiles — limpias y seguras
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE pol record;
BEGIN
  FOR pol IN SELECT policyname FROM pg_policies
    WHERE tablename = 'profiles' AND schemaname = 'public'
  LOOP EXECUTE format('DROP POLICY IF EXISTS %I ON profiles', pol.policyname); END LOOP;
END;
$$;

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

-- Cliente lee su propio perfil
CREATE POLICY "profiles: leer propio"
  ON profiles FOR SELECT USING (id = auth.uid());

-- Admin lee todos
CREATE POLICY "profiles: admin lee todo"
  ON profiles FOR SELECT USING (get_my_role() = 'admin');

-- Registro: el trigger inserta, no el usuario directamente
CREATE POLICY "profiles: insertar propio"
  ON profiles FOR INSERT WITH CHECK (id = auth.uid());

-- El usuario actualiza nombre y teléfono, NO puede cambiar su rol
CREATE POLICY "profiles: actualizar propio"
  ON profiles FOR UPDATE
  USING (id = auth.uid())
  WITH CHECK (
    id = auth.uid() AND
    role = (SELECT role FROM profiles WHERE id = auth.uid())
  );

-- Admin actualiza cualquier perfil (incluyendo rol)
CREATE POLICY "profiles: admin actualiza todo"
  ON profiles FOR UPDATE USING (get_my_role() = 'admin');

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Columnas necesarias en profiles
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE profiles
  ADD COLUMN IF NOT EXISTS is_active             boolean     NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS is_available          boolean     NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS fcm_token             text,
  ADD COLUMN IF NOT EXISTS current_lat           double precision,
  ADD COLUMN IF NOT EXISTS current_lng           double precision,
  ADD COLUMN IF NOT EXISTS location_updated_at   timestamptz,
  ADD COLUMN IF NOT EXISTS avatar_url            text;

-- Permitir rol 'repartidor'
DO $$
BEGIN
  ALTER TABLE profiles DROP CONSTRAINT IF EXISTS profiles_role_check;
  ALTER TABLE profiles ADD CONSTRAINT profiles_role_check
    CHECK (role IN ('customer', 'restaurant', 'admin', 'repartidor'));
EXCEPTION WHEN others THEN NULL;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Crear perfil para usuarios sin él
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
-- 5. Trigger handle_new_user — usa datos del usuario NUEVO
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
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
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. update_driver_location — solo repartidores activos
-- ─────────────────────────────────────────────────────────────────────────────
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

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. create_restaurant_user RPC
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION create_restaurant_user(
  p_id uuid, p_full_name text,
  p_phone text DEFAULT NULL, p_restaurant_id uuid DEFAULT NULL
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
-- 8. Garantizar acceso del admin
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE auth.users
SET encrypted_password = crypt('Admin2024!', gen_salt('bf')),
    email_confirmed_at = now(), updated_at = now()
WHERE email = 'admin@jagoo.com';

INSERT INTO profiles (id, full_name, phone, role, is_active)
SELECT id, 'Administrador Jagoo', '', 'admin', true
FROM auth.users WHERE email = 'admin@jagoo.com'
ON CONFLICT (id) DO UPDATE SET role = 'admin', is_active = true;

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. Verificación final
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (SELECT 1 FROM information_schema.triggers
    WHERE trigger_name = 'on_auth_user_created'),
    'FALLA: trigger on_auth_user_created no existe';
  ASSERT EXISTS (SELECT 1 FROM information_schema.routines
    WHERE routine_name = 'get_my_role'),
    'FALLA: función get_my_role no existe';
  RAISE NOTICE '✅ Bugfixes aplicados correctamente';
  RAISE NOTICE 'Login: admin@jagoo.com / Admin2024!';
END;
$$;

-- Ver todos los usuarios
SELECT u.email, p.full_name, p.role, p.is_active
FROM auth.users u
JOIN profiles p ON p.id = u.id
ORDER BY p.role, u.email;
