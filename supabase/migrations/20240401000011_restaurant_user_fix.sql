-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Fixes: create_restaurant_user RPC + login schema fix
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. RPC create_restaurant_user
--    Crea el perfil y vincula el owner_id en restaurants en una sola operación.
--    Llamado desde el panel web después de auth.signUp().
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION create_restaurant_user(
  p_id            uuid,
  p_full_name     text,
  p_phone         text    DEFAULT NULL,
  p_restaurant_id uuid    DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  -- Crear o actualizar perfil con rol restaurant
  INSERT INTO profiles (id, full_name, phone, role, is_active)
  VALUES (p_id, p_full_name, COALESCE(p_phone, ''), 'restaurant', true)
  ON CONFLICT (id) DO UPDATE
    SET full_name = EXCLUDED.full_name,
        phone     = EXCLUDED.phone,
        role      = 'restaurant',
        is_active = true;

  -- Vincular owner_id en el restaurante
  IF p_restaurant_id IS NOT NULL THEN
    UPDATE restaurants
    SET owner_id = p_id
    WHERE id = p_restaurant_id;
  END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION create_restaurant_user(uuid, text, text, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION create_restaurant_user(uuid, text, text, uuid) TO service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Fix: get_my_role sin recursión
--    La política RLS de profiles usa get_my_role(), pero si get_my_role()
--    hace SELECT en profiles con RLS activo → recursión infinita → error.
--    Solución: SET LOCAL row_security = off dentro de la función.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION get_my_role()
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
DECLARE
  v_role text;
BEGIN
  -- Deshabilitar RLS solo para esta consulta interna (evita recursión)
  SET LOCAL row_security = off;
  SELECT role INTO v_role FROM profiles WHERE id = auth.uid();
  RETURN v_role;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Fix política RLS de profiles — usar SECURITY DEFINER para evitar loop
-- ─────────────────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS profiles_select_own ON profiles;
CREATE POLICY profiles_select_own ON profiles
  FOR SELECT
  USING (id = auth.uid());

-- Admin puede ver todos los perfiles
DROP POLICY IF EXISTS profiles_select_admin ON profiles;
CREATE POLICY profiles_select_admin ON profiles
  FOR SELECT
  USING (get_my_role() = 'admin');

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Columna owner_id en restaurants (si no existe)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE restaurants
  ADD COLUMN IF NOT EXISTS owner_id uuid REFERENCES profiles(id) ON DELETE SET NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'create_restaurant_user'
  ), 'FALLA: create_restaurant_user no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'get_my_role'
  ), 'FALLA: get_my_role no existe';

  RAISE NOTICE '✅ restaurant_user_fix: todas las verificaciones pasaron';
  RAISE NOTICE 'Intenta login con admin@jagoo.com / Admin2024!';
END;
$$;
