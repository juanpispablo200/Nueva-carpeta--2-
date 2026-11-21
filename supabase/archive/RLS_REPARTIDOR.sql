-- ══════════════════════════════════════════════════════════════════════════════
-- RLS para repartidor — ejecutar en Supabase SQL Editor
-- ══════════════════════════════════════════════════════════════════════════════

-- 1. Repartidor puede leer su propio perfil
DROP POLICY IF EXISTS profiles_select ON profiles;
CREATE POLICY profiles_select ON profiles FOR SELECT
  USING (
    id = auth.uid()
    OR EXISTS (SELECT 1 FROM profiles p2 WHERE p2.id = auth.uid() AND p2.role = 'admin')
  );

-- 2. Repartidor ve solo sus pedidos asignados
DROP POLICY IF EXISTS orders_select ON orders;
CREATE POLICY orders_select ON orders FOR SELECT
  USING (
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    OR restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
    OR courier_id = auth.uid()
    OR customer_id = auth.uid()
  );

-- 3. Repartidor solo puede cambiar el STATUS de sus pedidos (no otros campos)
--    El trigger fn_validate_order_transition ya valida las transiciones permitidas
DROP POLICY IF EXISTS orders_update ON orders;
CREATE POLICY orders_update ON orders FOR UPDATE
  USING (
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    OR restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
    OR courier_id = auth.uid()
  )
  WITH CHECK (
    -- Repartidor solo puede actualizar status, no courier_id ni otros campos sensibles
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role IN ('admin', 'restaurant'))
    OR courier_id = auth.uid()
  );

-- 4. Verificar
SELECT tablename, policyname, cmd
FROM pg_policies
WHERE tablename IN ('orders', 'profiles')
ORDER BY tablename, policyname;

SELECT '✅ RLS repartidor configurado' AS resultado;