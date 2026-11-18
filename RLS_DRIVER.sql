-- ══════════════════════════════════════════════════════════════════════════════
-- RLS para app Driver (repartidor) — idempotente
-- Ejecutar en: https://supabase.com/dashboard/project/tjgavwygdotetoatitju/sql/new
-- ══════════════════════════════════════════════════════════════════════════════

-- ── 1. profiles: repartidor puede leer su propio perfil ───────────────────────
DROP POLICY IF EXISTS profiles_select ON profiles;
CREATE POLICY profiles_select ON profiles FOR SELECT
  USING (
    id = auth.uid()
    OR EXISTS (SELECT 1 FROM profiles p2 WHERE p2.id = auth.uid() AND p2.role = 'admin')
  );

-- ── 2. orders: repartidor ve solo sus pedidos asignados ───────────────────────
DROP POLICY IF EXISTS orders_select ON orders;
CREATE POLICY orders_select ON orders FOR SELECT
  USING (
    -- admin ve todo
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    -- restaurante ve sus pedidos
    OR restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
    -- repartidor ve solo los suyos
    OR courier_id = auth.uid()
    -- cliente ve los suyos
    OR customer_id = auth.uid()
  );

-- ── 3. orders: repartidor solo actualiza STATUS de sus pedidos ────────────────
--    El trigger fn_validate_order_transition valida que solo haga ready→on_the_way→delivered
DROP POLICY IF EXISTS orders_update ON orders;
CREATE POLICY orders_update ON orders FOR UPDATE
  USING (
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    OR restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
    OR courier_id = auth.uid()
  )
  WITH CHECK (
    -- admin y restaurante pueden cambiar cualquier campo
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role IN ('admin', 'restaurant'))
    OR restaurant_id IN (SELECT id FROM restaurants WHERE owner_id = auth.uid())
    -- repartidor solo puede actualizar si sigue siendo su pedido (no puede cambiar courier_id)
    OR courier_id = auth.uid()
  );

-- ── 4. Función: repartidor crea entradas en audit_log al cambiar estado ───────
CREATE OR REPLACE FUNCTION fn_log_courier_status_change()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.status <> OLD.status AND OLD.courier_id IS NOT NULL THEN
    INSERT INTO audit_log (user_id, action, entity, entity_id, details)
    VALUES (
      auth.uid(),
      'status_change',
      'orders',
      NEW.id::text,
      jsonb_build_object(
        'from',       OLD.status,
        'to',         NEW.status,
        'courier_id', OLD.courier_id
      )
    );
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_log_courier_status ON orders;
CREATE TRIGGER trg_log_courier_status
  AFTER UPDATE OF status ON orders
  FOR EACH ROW
  EXECUTE FUNCTION fn_log_courier_status_change();

-- ── 5. audit_log: repartidor puede insertar (para registrar sus cambios) ──────
DROP POLICY IF EXISTS audit_log_admin_insert ON audit_log;
DROP POLICY IF EXISTS audit_log_insert ON audit_log;
CREATE POLICY audit_log_insert ON audit_log FOR INSERT
  WITH CHECK (auth.uid() IS NOT NULL);

-- ── 6. Verificar políticas ────────────────────────────────────────────────────
SELECT tablename, policyname, cmd
FROM pg_policies
WHERE tablename IN ('profiles', 'orders', 'audit_log')
ORDER BY tablename, policyname;

SELECT '✅ RLS Driver configurado correctamente' AS resultado;
