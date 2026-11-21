-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Kushki: pasarela de pago con tarjeta
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Ampliar el CHECK de payment_method en orders
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE orders
  DROP CONSTRAINT IF EXISTS orders_payment_method_check;

ALTER TABLE orders
  ADD CONSTRAINT orders_payment_method_check
  CHECK (payment_method IN ('transfer', 'cash', 'card'));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Tabla kushki_transactions — log de cada cobro intentado
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS kushki_transactions (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id        uuid        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  customer_id     uuid        NOT NULL REFERENCES profiles(id),
  kushki_token    text        NOT NULL,  -- token de un solo uso del SDK
  amount          numeric     NOT NULL,
  currency        text        NOT NULL DEFAULT 'USD',
  status          text        NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'approved', 'declined', 'error')),
  kushki_response jsonb,                 -- respuesta completa de Kushki API
  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_kushki_tx_order ON kushki_transactions(order_id);

ALTER TABLE kushki_transactions ENABLE ROW LEVEL SECURITY;

-- Solo admin ve todas las transacciones; cliente solo las suyas
DROP POLICY IF EXISTS kushki_tx_select ON kushki_transactions;
CREATE POLICY kushki_tx_select ON kushki_transactions
  FOR SELECT
  USING (
    customer_id = auth.uid()
    OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC register_kushki_charge(p_order_id, p_token, p_amount)
--    Llamado SOLO desde la Edge Function (con service_role).
--    Registra el intento y actualiza orders si fue aprobado.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION register_kushki_charge(
  p_order_id       uuid,
  p_token          text,
  p_amount         numeric,
  p_status         text,
  p_kushki_response jsonb DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_customer_id uuid;
  v_tx_id       uuid;
BEGIN
  SELECT customer_id INTO v_customer_id FROM orders WHERE id = p_order_id;
  IF v_customer_id IS NULL THEN
    RAISE EXCEPTION 'Pedido no encontrado';
  END IF;

  INSERT INTO kushki_transactions
    (order_id, customer_id, kushki_token, amount, status, kushki_response)
  VALUES
    (p_order_id, v_customer_id, p_token, p_amount, p_status, p_kushki_response)
  RETURNING id INTO v_tx_id;

  IF p_status = 'approved' THEN
    UPDATE orders
    SET
      payment_method = 'card',
      payment_status = 'verified'
    WHERE id = p_order_id;
  END IF;

  RETURN v_tx_id;
END;
$$;

-- Solo service_role puede llamar esta función
REVOKE ALL ON FUNCTION register_kushki_charge(uuid, text, numeric, text, jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION register_kushki_charge(uuid, text, numeric, text, jsonb) TO service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Setting: clave pública de Kushki (la app Android la necesita para tokenizar)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO settings (key, value, description)
VALUES ('kushki_public_key', 'TU_KUSHKI_PUBLIC_KEY', 'Clave pública de Kushki (sandbox o producción)')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description)
VALUES ('kushki_enabled', 'false', 'Activar cobro con tarjeta Kushki (true/false)')
ON CONFLICT (key) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_name = 'kushki_transactions'
  ), 'FALLA: tabla kushki_transactions no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'register_kushki_charge'
  ), 'FALLA: función register_kushki_charge no existe';

  RAISE NOTICE '✅ FASE Kushki: todas las verificaciones pasaron';
END;
$$;
