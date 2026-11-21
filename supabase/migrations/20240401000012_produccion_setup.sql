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
