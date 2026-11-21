-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Fase 2: Tabla coupons + RPC validate_coupon
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Tabla coupons
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS coupons (
  id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  code         text        NOT NULL UNIQUE,
  type         text        NOT NULL CHECK (type IN ('percent', 'fixed')),
  value        numeric     NOT NULL CHECK (value > 0),
  min_order    numeric     NOT NULL DEFAULT 0,
  max_uses     int         NOT NULL DEFAULT 1,
  uses         int         NOT NULL DEFAULT 0,
  active       boolean     NOT NULL DEFAULT true,
  valid_from   timestamptz NOT NULL DEFAULT now(),
  valid_until  timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_coupons_code   ON coupons(code);
CREATE INDEX IF NOT EXISTS idx_coupons_active ON coupons(active) WHERE active = true;

-- RLS: solo admin gestiona cupones; clientes solo validan via RPC
ALTER TABLE coupons ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS coupons_admin ON coupons;
CREATE POLICY coupons_admin ON coupons FOR ALL
  USING  (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'))
  WITH CHECK (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- Seed de cupón de bienvenida para pruebas
INSERT INTO coupons (code, type, value, min_order, max_uses, active, valid_until)
VALUES ('BIENVENIDO', 'fixed', 2.00, 5.00, 500, true, now() + interval '1 year')
ON CONFLICT (code) DO NOTHING;

INSERT INTO coupons (code, type, value, min_order, max_uses, active, valid_until)
VALUES ('JAGOO10', 'percent', 10.00, 10.00, 200, true, now() + interval '6 months')
ON CONFLICT (code) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. RPC validate_coupon(p_code text, p_order_total numeric)
--
--    Llamada desde CartViewModel antes de confirmar pedido.
--    Devuelve la fila del cupón si es válido; lanza excepción si no.
--    NO incrementa el contador — eso lo hace apply_coupon al confirmar el pedido.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION validate_coupon(
  p_code        text,
  p_order_total numeric
)
RETURNS TABLE (
  id          uuid,
  code        text,
  type        text,
  value       numeric,
  min_order   numeric,
  discount    numeric   -- monto calculado de descuento
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_coupon coupons%ROWTYPE;
  v_discount numeric;
BEGIN
  SELECT * INTO v_coupon
  FROM   coupons c
  WHERE  UPPER(c.code) = UPPER(p_code)
    AND  c.active      = true
    AND  c.uses        < c.max_uses
    AND  (c.valid_until IS NULL OR c.valid_until > now())
    AND  (c.valid_from <= now());

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Cupón no válido o expirado';
  END IF;

  IF p_order_total < v_coupon.min_order THEN
    RAISE EXCEPTION 'El pedido mínimo para este cupón es $%', v_coupon.min_order;
  END IF;

  -- Calcular descuento
  IF v_coupon.type = 'percent' THEN
    v_discount := ROUND((p_order_total * v_coupon.value / 100.0)::numeric, 2);
  ELSE
    v_discount := LEAST(v_coupon.value, p_order_total);
  END IF;

  RETURN QUERY SELECT
    v_coupon.id,
    v_coupon.code,
    v_coupon.type,
    v_coupon.value,
    v_coupon.min_order,
    v_discount;
END;
$$;

GRANT EXECUTE ON FUNCTION validate_coupon(text, numeric) TO authenticated;
GRANT EXECUTE ON FUNCTION validate_coupon(text, numeric) TO anon;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC apply_coupon(p_code text, p_order_id uuid)
--
--    Incrementa el contador una vez que el pedido queda confirmado.
--    Llamada desde CartViewModel.confirmOrder() en background.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION apply_coupon(
  p_code     text,
  p_order_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  UPDATE coupons
  SET    uses = uses + 1
  WHERE  UPPER(code) = UPPER(p_code)
    AND  active      = true
    AND  uses        < max_uses;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Cupón no válido o ya agotado';
  END IF;

  -- Registrar uso en audit_log
  INSERT INTO audit_log (user_id, action, table_name, record_id, new_data)
  VALUES (
    auth.uid(),
    'create',
    'coupons',
    p_code,
    jsonb_build_object('order_id', p_order_id, 'code', p_code)
  );
END;
$$;

GRANT EXECUTE ON FUNCTION apply_coupon(text, uuid) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_name = 'coupons'
  ), 'FALLA: tabla coupons no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'validate_coupon'
  ), 'FALLA: función validate_coupon no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'apply_coupon'
  ), 'FALLA: función apply_coupon no existe';

  RAISE NOTICE '✅ FASE 2 coupons: todas las verificaciones pasaron';
END;
$$;
