-- ==========================================================================
-- JAGOO - PASO 2 de 3: arreglos de login, perfiles y favoritos (sin cuentas de prueba)
-- Concatenacion literal de supabase/migrations/ (no se modifico ningun archivo).
-- Va en UNA transaccion: si algo falla, no se guarda nada de ESTA parte.
-- Si falla, copia el PRIMER mensaje de error (no los 'current transaction is aborted').
-- ==========================================================================

BEGIN;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000011_restaurant_user_fix.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
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


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000012_produccion_setup.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Setup de producción: fix login + limpieza + usuarios finales
-- Ejecutar en Supabase SQL Editor
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- PARTE 1: Fix get_my_role (elimina recursión RLS → resuelve "Database error")
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
  RETURN v_role;
END;
$$;

-- Políticas de profiles limpias
DROP POLICY IF EXISTS profiles_select_own   ON profiles;
DROP POLICY IF EXISTS profiles_select_admin ON profiles;
DROP POLICY IF EXISTS profiles_location_select ON profiles;

CREATE POLICY profiles_select_own ON profiles
  FOR SELECT USING (id = auth.uid());

CREATE POLICY profiles_select_admin ON profiles
  FOR SELECT USING (get_my_role() = 'admin');

-- ─────────────────────────────────────────────────────────────────────────────
-- PARTE 2: RPC create_restaurant_user (vincula owner_id)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION create_restaurant_user(
  p_id            uuid,
  p_full_name     text,
  p_phone         text DEFAULT NULL,
  p_restaurant_id uuid DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO profiles (id, full_name, phone, role, is_active)
  VALUES (p_id, p_full_name, COALESCE(p_phone, ''), 'restaurant', true)
  ON CONFLICT (id) DO UPDATE
    SET full_name = EXCLUDED.full_name,
        phone     = EXCLUDED.phone,
        role      = 'restaurant',
        is_active = true;

  IF p_restaurant_id IS NOT NULL THEN
    UPDATE restaurants SET owner_id = p_id WHERE id = p_restaurant_id;
  END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION create_restaurant_user(uuid, text, text, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION create_restaurant_user(uuid, text, text, uuid) TO service_role;

ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS owner_id uuid REFERENCES profiles(id) ON DELETE SET NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- PARTE 3: Respaldo de usuarios existentes (para ver antes de limpiar)
-- ─────────────────────────────────────────────────────────────────────────────

-- Ejecuta esto PRIMERO y revisa el resultado:
SELECT
  u.id,
  u.email,
  u.created_at::date AS fecha,
  p.role,
  p.full_name,
  p.is_active
FROM auth.users u
LEFT JOIN profiles p ON p.id = u.id
ORDER BY u.created_at;

-- ─────────────────────────────────────────────────────────────────────────────
-- PARTE 4: Limpieza de datos de prueba
-- DESCOMENTA Y EJECUTA SOLO DESPUÉS DE REVISAR EL SELECT DE ARRIBA
-- ─────────────────────────────────────────────────────────────────────────────

/*
-- Eliminar pedidos de prueba (mantiene restaurantes y catálogo)
DELETE FROM order_items  WHERE order_id IN (SELECT id FROM orders WHERE created_at < '2025-01-01');
DELETE FROM orders       WHERE created_at < '2025-01-01';
DELETE FROM payment_proofs;
DELETE FROM kushki_transactions;
DELETE FROM notification_log;
DELETE FROM ratings;

-- Eliminar usuarios de prueba (conserva los de producción)
-- IMPORTANTE: reemplaza los emails de producción que NO quieres borrar
DELETE FROM auth.users
WHERE email NOT IN (
  'ADMIN1@EMAIL.COM',      -- ← reemplazar
  'ADMIN2@EMAIL.COM',      -- ← reemplazar
  'RESTAURANTE1@EMAIL.COM', -- ← reemplazar
  'DRIVER1@EMAIL.COM'       -- ← reemplazar
);
*/

-- ─────────────────────────────────────────────────────────────────────────────
-- PARTE 5: Crear usuarios de producción
-- Reemplaza los placeholders con los emails y contraseñas reales.
-- Ejecuta bloque por bloque.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Administrador 1 ──────────────────────────────────────────────────────────
/*
DO $$
DECLARE v_id uuid;
BEGIN
  SELECT id INTO v_id FROM auth.users WHERE email = 'ADMIN1@EMAIL.COM';
  IF v_id IS NULL THEN
    v_id := gen_random_uuid();
    INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password,
      email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data)
    VALUES (v_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
      'ADMIN1@EMAIL.COM', crypt('CONTRASEÑA_ADMIN1', gen_salt('bf')),
      now(), now(), now(),
      '{"provider":"email","providers":["email"]}',
      '{"full_name":"Admin Principal"}');
  ELSE
    UPDATE auth.users SET encrypted_password = crypt('CONTRASEÑA_ADMIN1', gen_salt('bf')),
      email_confirmed_at = now() WHERE id = v_id;
  END IF;
  INSERT INTO profiles (id, full_name, phone, role, is_active)
  VALUES (v_id, 'Admin Principal', '', 'admin', true)
  ON CONFLICT (id) DO UPDATE SET role = 'admin', is_active = true;
  RAISE NOTICE 'Admin 1 listo: ADMIN1@EMAIL.COM';
END; $$;
*/

-- ── Administrador 2 ──────────────────────────────────────────────────────────
/*
DO $$
DECLARE v_id uuid;
BEGIN
  SELECT id INTO v_id FROM auth.users WHERE email = 'ADMIN2@EMAIL.COM';
  IF v_id IS NULL THEN
    v_id := gen_random_uuid();
    INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password,
      email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data)
    VALUES (v_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
      'ADMIN2@EMAIL.COM', crypt('CONTRASEÑA_ADMIN2', gen_salt('bf')),
      now(), now(), now(),
      '{"provider":"email","providers":["email"]}',
      '{"full_name":"Admin 2"}');
  ELSE
    UPDATE auth.users SET encrypted_password = crypt('CONTRASEÑA_ADMIN2', gen_salt('bf')),
      email_confirmed_at = now() WHERE id = v_id;
  END IF;
  INSERT INTO profiles (id, full_name, phone, role, is_active)
  VALUES (v_id, 'Admin 2', '', 'admin', true)
  ON CONFLICT (id) DO UPDATE SET role = 'admin', is_active = true;
  RAISE NOTICE 'Admin 2 listo: ADMIN2@EMAIL.COM';
END; $$;
*/

-- ── Driver (repartidor) ──────────────────────────────────────────────────────
/*
DO $$
DECLARE v_id uuid;
BEGIN
  SELECT id INTO v_id FROM auth.users WHERE email = 'DRIVER@EMAIL.COM';
  IF v_id IS NULL THEN
    v_id := gen_random_uuid();
    INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password,
      email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data)
    VALUES (v_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
      'DRIVER@EMAIL.COM', crypt('CONTRASEÑA_DRIVER', gen_salt('bf')),
      now(), now(), now(),
      '{"provider":"email","providers":["email"]}',
      '{"full_name":"Repartidor Jagoo"}');
  ELSE
    UPDATE auth.users SET encrypted_password = crypt('CONTRASEÑA_DRIVER', gen_salt('bf')),
      email_confirmed_at = now() WHERE id = v_id;
  END IF;
  INSERT INTO profiles (id, full_name, phone, role, is_active, is_available)
  VALUES (v_id, 'Repartidor Jagoo', 'TELEFONO_DRIVER', 'repartidor', true, false)
  ON CONFLICT (id) DO UPDATE SET role = 'repartidor', is_active = true;
  RAISE NOTICE 'Driver listo: DRIVER@EMAIL.COM';
END; $$;
*/

-- ── Restaurante (un bloque por cada restaurante) ─────────────────────────────
/*
DO $$
DECLARE
  v_id uuid;
  v_rest_id uuid;
BEGIN
  -- Obtener el ID del restaurante (ejecuta SELECT * FROM restaurants; para verlos)
  SELECT id INTO v_rest_id FROM restaurants WHERE name = 'NOMBRE_DEL_RESTAURANTE';

  SELECT id INTO v_id FROM auth.users WHERE email = 'RESTAURANTE1@EMAIL.COM';
  IF v_id IS NULL THEN
    v_id := gen_random_uuid();
    INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password,
      email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data)
    VALUES (v_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
      'RESTAURANTE1@EMAIL.COM', crypt('CONTRASEÑA_REST1', gen_salt('bf')),
      now(), now(), now(),
      '{"provider":"email","providers":["email"]}',
      '{"full_name":"Nombre Restaurante"}');
  ELSE
    UPDATE auth.users SET encrypted_password = crypt('CONTRASEÑA_REST1', gen_salt('bf')),
      email_confirmed_at = now() WHERE id = v_id;
  END IF;

  PERFORM create_restaurant_user(v_id, 'Nombre Restaurante', 'TELEFONO', v_rest_id);
  RAISE NOTICE 'Restaurante listo: RESTAURANTE1@EMAIL.COM vinculado a %', v_rest_id;
END; $$;
*/

-- ─────────────────────────────────────────────────────────────────────────────
-- PARTE 6: Verificación final
-- ─────────────────────────────────────────────────────────────────────────────
SELECT
  u.email,
  p.role,
  p.full_name,
  p.is_active,
  u.email_confirmed_at IS NOT NULL AS confirmado
FROM auth.users u
JOIN profiles p ON p.id = u.id
ORDER BY p.role, u.email;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000014_fix_rls_login.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
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


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000016_favorites_profile.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Favoritos persistentes + perfil editable
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Tabla favorites
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS favorites (
  id            uuid  PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       uuid  NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  restaurant_id text  NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, restaurant_id)
);

CREATE INDEX IF NOT EXISTS idx_favorites_user ON favorites(user_id);

ALTER TABLE favorites ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS favorites_select ON favorites;
CREATE POLICY favorites_select ON favorites
  FOR SELECT USING (user_id = auth.uid());

DROP POLICY IF EXISTS favorites_insert ON favorites;
CREATE POLICY favorites_insert ON favorites
  FOR INSERT WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS favorites_delete ON favorites;
CREATE POLICY favorites_delete ON favorites
  FOR DELETE USING (user_id = auth.uid());

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Columnas editables en profiles (si no existen)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE profiles
  ADD COLUMN IF NOT EXISTS avatar_url text;

-- Política para que el usuario pueda actualizarse solo nombre y teléfono
DROP POLICY IF EXISTS profiles_update_own ON profiles;
CREATE POLICY profiles_update_own ON profiles
  FOR UPDATE
  USING (id = auth.uid())
  WITH CHECK (
    id       = auth.uid() AND
    -- No puede cambiarse el rol
    role     = (SELECT role FROM profiles WHERE id = auth.uid())
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC toggle_favorite
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION toggle_favorite(p_restaurant_id text)
RETURNS boolean   -- true = ahora es favorito, false = se quitó
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_exists boolean;
BEGIN
  SELECT EXISTS(
    SELECT 1 FROM favorites
    WHERE user_id = auth.uid() AND restaurant_id = p_restaurant_id
  ) INTO v_exists;

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
-- 4. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'favorites'),
    'FALLA: tabla favorites no existe';
  ASSERT EXISTS (SELECT 1 FROM information_schema.routines WHERE routine_name = 'toggle_favorite'),
    'FALLA: función toggle_favorite no existe';
  RAISE NOTICE '✅ favorites + perfil editable: listo';
END;
$$;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000017_fix_handle_new_user.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Fix trigger handle_new_user
-- Garantiza que el perfil usa los datos del usuario NUEVO (NEW),
-- nunca auth.uid() ni datos del usuario actual que ejecuta la operación.
-- ══════════════════════════════════════════════════════════════════════════════

-- El trigger corre con SECURITY DEFINER como superusuario,
-- por lo que NEW.id siempre es el ID del usuario recién creado.
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.profiles (id, full_name, phone, role, is_active)
  VALUES (
    NEW.id,                                                        -- ID del usuario nuevo
    COALESCE(NEW.raw_user_meta_data->>'full_name', ''),           -- nombre del formulario
    COALESCE(NEW.raw_user_meta_data->>'phone', ''),               -- teléfono del formulario
    'customer',                                                    -- rol por defecto
    true
  )
  ON CONFLICT (id) DO NOTHING;  -- si la Edge Function ya insertó el perfil, no pisar
  RETURN NEW;
END;
$$;

-- Recrear trigger (por si no existe)
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_new_user();

-- Verificación
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.triggers
    WHERE trigger_name = 'on_auth_user_created'
  ), 'FALLA: trigger on_auth_user_created no existe';
  RAISE NOTICE '✅ handle_new_user corregido — usa datos del usuario nuevo';
END;
$$;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000018_consolidado_bugfixes.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
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

COMMIT;

select 'PASO 2 OK: parte B aplicada' as resultado;
