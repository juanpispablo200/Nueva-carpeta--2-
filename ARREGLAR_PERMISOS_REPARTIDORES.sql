-- ══════════════════════════════════════════════════════════════════════════════
-- ARREGLAR REPARTIDORES - Solución completa
-- Ejecutar en: https://supabase.com/dashboard/project/tjgavwygdotetoatitju/sql/new
-- ══════════════════════════════════════════════════════════════════════════════

-- ── PASO 1: Crear perfil para TODOS los repartidores que no lo tienen ─────────
INSERT INTO profiles (id, full_name, phone, role, is_active)
SELECT
  u.id,
  COALESCE(u.raw_user_meta_data->>'full_name', split_part(u.email, '@', 1), 'Repartidor'),
  u.raw_user_meta_data->>'phone',
  'repartidor',
  true
FROM auth.users u
LEFT JOIN profiles p ON p.id = u.id
WHERE p.id IS NULL
  AND (
    u.raw_user_meta_data->>'role' = 'repartidor'
    OR u.raw_app_meta_data->>'role' = 'repartidor'
  )
ON CONFLICT (id) DO NOTHING;

-- ── PASO 2: Corregir perfiles con rol incorrecto ───────────────────────────────
UPDATE profiles
SET role = 'repartidor', is_active = true
WHERE id IN (
  SELECT u.id
  FROM auth.users u
  JOIN profiles p ON p.id = u.id
  WHERE (
    u.raw_user_meta_data->>'role' = 'repartidor'
    OR u.raw_app_meta_data->>'role' = 'repartidor'
  )
  AND p.role != 'repartidor'
  AND p.role NOT IN ('admin')  -- nunca tocar admins
);

-- ── PASO 3: Activar repartidores con is_active = NULL o false ─────────────────
UPDATE profiles
SET is_active = true
WHERE role = 'repartidor'
  AND (is_active IS NULL OR is_active = false);

-- ── PASO 4: Función para crear perfiles sin columna email ─────────────────────
CREATE OR REPLACE FUNCTION create_courier_profile(
  p_id        uuid,
  p_full_name text,
  p_phone     text,
  p_email     text
)
RETURNS void AS $$
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

GRANT EXECUTE ON FUNCTION create_courier_profile(uuid, text, text, text) TO authenticated;

-- ── PASO 5: Políticas RLS correctas ───────────────────────────────────────────

-- profiles: leer perfil propio o admin
DROP POLICY IF EXISTS profiles_select ON profiles;
CREATE POLICY profiles_select ON profiles FOR SELECT
  USING (
    id = auth.uid()
    OR EXISTS (SELECT 1 FROM profiles p2 WHERE p2.id = auth.uid() AND p2.role = 'admin')
  );

-- profiles: insertar propio o admin
DROP POLICY IF EXISTS profiles_insert_admin ON profiles;
DROP POLICY IF EXISTS profiles_insert ON profiles;
CREATE POLICY profiles_insert ON profiles FOR INSERT
  WITH CHECK (
    id = auth.uid()
    OR EXISTS (SELECT 1 FROM profiles p2 WHERE p2.id = auth.uid() AND p2.role = 'admin')
  );

-- profiles: actualizar solo admin
DROP POLICY IF EXISTS profiles_update_admin ON profiles;
DROP POLICY IF EXISTS profiles_update ON profiles;
CREATE POLICY profiles_update ON profiles FOR UPDATE
  USING (
    EXISTS (SELECT 1 FROM profiles p2 WHERE p2.id = auth.uid() AND p2.role = 'admin')
  );

-- ── PASO 6: Verificación final ────────────────────────────────────────────────
SELECT '=== RESULTADO ===' AS info;

SELECT
  COALESCE(p.full_name, '(sin perfil)') AS nombre,
  u.email,
  p.role,
  p.is_active
FROM auth.users u
LEFT JOIN profiles p ON p.id = u.id
WHERE
  u.raw_user_meta_data->>'role' = 'repartidor'
  OR p.role = 'repartidor'
ORDER BY u.created_at DESC;

SELECT '✅ Listo. Los repartidores ya tienen perfil con rol correcto.' AS resultado;
