-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Fix definitivo de RLS y login
-- Pega TODO esto en Supabase SQL Editor y ejecuta de una sola vez.
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. get_my_role() — sin recursión RLS
--    SET LOCAL row_security = off previene que RLS se aplique dentro de la
--    función, cortando la recursión que causaba "Database error querying schema"
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
  SET LOCAL row_security = off;
  SELECT role INTO v_role FROM profiles WHERE id = auth.uid();
  RETURN COALESCE(v_role, 'customer');
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Limpiar TODAS las políticas existentes en profiles para evitar conflictos
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
  pol record;
BEGIN
  FOR pol IN
    SELECT policyname FROM pg_policies WHERE tablename = 'profiles' AND schemaname = 'public'
  LOOP
    EXECUTE format('DROP POLICY IF EXISTS %I ON profiles', pol.policyname);
  END LOOP;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Políticas limpias para profiles
-- ─────────────────────────────────────────────────────────────────────────────

-- Cualquier usuario autenticado puede leer su propio perfil
CREATE POLICY "profiles: leer propio"
  ON profiles FOR SELECT
  USING (id = auth.uid());

-- Admin puede leer todos los perfiles
CREATE POLICY "profiles: admin lee todo"
  ON profiles FOR SELECT
  USING (get_my_role() = 'admin');

-- Cualquier usuario puede insertar su propio perfil (registro)
CREATE POLICY "profiles: insertar propio"
  ON profiles FOR INSERT
  WITH CHECK (id = auth.uid());

-- Cualquier usuario puede actualizar su propio perfil
CREATE POLICY "profiles: actualizar propio"
  ON profiles FOR UPDATE
  USING (id = auth.uid());

-- Admin puede actualizar cualquier perfil
CREATE POLICY "profiles: admin actualiza todo"
  ON profiles FOR UPDATE
  USING (get_my_role() = 'admin');

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Asegurar que RLS está habilitado en profiles
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Verificación — debe devolver las políticas nuevas
-- ─────────────────────────────────────────────────────────────────────────────
SELECT policyname, cmd, qual
FROM pg_policies
WHERE tablename = 'profiles'
ORDER BY policyname;
