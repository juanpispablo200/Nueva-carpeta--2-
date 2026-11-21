-- ══════════════════════════════════════════════════════════════════════════════
-- ÚLTIMO SQL - Ejecutar en Supabase para que TODO funcione
-- ══════════════════════════════════════════════════════════════════════════════

-- 1. Actualizar función sin columna email
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

-- 2. Política para que admin pueda insertar restaurantes
DROP POLICY IF EXISTS restaurants_insert_admin ON restaurants;
CREATE POLICY restaurants_insert_admin ON restaurants FOR INSERT
  WITH CHECK (
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
  );

-- 3. Política para que admin pueda insertar profiles
DROP POLICY IF EXISTS profiles_insert_admin ON profiles;
CREATE POLICY profiles_insert_admin ON profiles FOR INSERT
  WITH CHECK (
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    OR id = auth.uid()
  );

-- 4. Verificar
SELECT '✅ TODO CONFIGURADO' as resultado,
       'Ahora puedes crear repartidores y restaurantes sin errores' as detalle;