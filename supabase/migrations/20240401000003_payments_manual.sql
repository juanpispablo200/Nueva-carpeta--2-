-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Fase 3: Pagos con transferencia bancaria + comprobante manual
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Columnas de pago en orders
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS payment_method text DEFAULT 'transfer',
  ADD COLUMN IF NOT EXISTS payment_status text DEFAULT 'pending'
    CHECK (payment_status IN ('pending', 'verifying', 'verified', 'rejected'));

CREATE INDEX IF NOT EXISTS idx_orders_payment_status
  ON orders(payment_status)
  WHERE payment_status IN ('pending', 'verifying');

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Tabla payment_proofs — comprobantes subidos por clientes
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payment_proofs (
  id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id         uuid        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  customer_id      uuid        NOT NULL REFERENCES profiles(id),
  image_url        text        NOT NULL,  -- URL de Supabase Storage o externa
  reference_number text,                  -- número de referencia del banco
  amount           numeric     NOT NULL,
  upload_date      timestamptz NOT NULL DEFAULT now(),
  verified_by      uuid        REFERENCES profiles(id),
  verified_at      timestamptz,
  status           text        NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'verified', 'rejected')),
  rejection_reason text,
  created_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_payment_proofs_order    ON payment_proofs(order_id);
CREATE INDEX IF NOT EXISTS idx_payment_proofs_customer ON payment_proofs(customer_id);
CREATE INDEX IF NOT EXISTS idx_payment_proofs_status   ON payment_proofs(status)
  WHERE status = 'pending';

-- RLS: cliente ve sus propios comprobantes, admin ve todos
ALTER TABLE payment_proofs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS payment_proofs_select ON payment_proofs;
CREATE POLICY payment_proofs_select ON payment_proofs
  FOR SELECT
  USING (
    customer_id = auth.uid()
    OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
  );

DROP POLICY IF EXISTS payment_proofs_insert ON payment_proofs;
CREATE POLICY payment_proofs_insert ON payment_proofs
  FOR INSERT
  WITH CHECK (customer_id = auth.uid());

DROP POLICY IF EXISTS payment_proofs_update_admin ON payment_proofs;
CREATE POLICY payment_proofs_update_admin ON payment_proofs
  FOR UPDATE
  USING (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC upload_payment_proof(p_order_id, p_image_url, p_reference_number, p_amount)
--
--    Cliente sube comprobante después de transferir.
--    Cambia el payment_status del pedido a 'verifying'.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION upload_payment_proof(
  p_order_id         uuid,
  p_image_url        text,
  p_reference_number text DEFAULT NULL,
  p_amount           numeric DEFAULT 0
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_customer_id uuid;
  v_proof_id    uuid;
BEGIN
  -- Verificar que el pedido pertenezca al usuario
  SELECT customer_id INTO v_customer_id
  FROM   orders
  WHERE  id = p_order_id;

  IF v_customer_id IS NULL THEN
    RAISE EXCEPTION 'Pedido no encontrado';
  END IF;

  IF v_customer_id <> auth.uid() THEN
    RAISE EXCEPTION 'No tienes permiso para subir comprobante de este pedido';
  END IF;

  -- Crear registro de comprobante
  INSERT INTO payment_proofs (order_id, customer_id, image_url, reference_number, amount)
  VALUES (p_order_id, auth.uid(), p_image_url, p_reference_number, p_amount)
  RETURNING id INTO v_proof_id;

  -- Cambiar estado del pedido a 'verifying'
  UPDATE orders
  SET    payment_status = 'verifying'
  WHERE  id = p_order_id;

  -- Auditoría
  INSERT INTO audit_log (user_id, action, table_name, record_id, new_data)
  VALUES (
    auth.uid(),
    'create',
    'payment_proofs',
    v_proof_id::text,
    jsonb_build_object(
      'order_id', p_order_id,
      'image_url', p_image_url,
      'reference_number', p_reference_number
    )
  );

  RETURN v_proof_id;
END;
$$;

GRANT EXECUTE ON FUNCTION upload_payment_proof(uuid, text, text, numeric) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. RPC verify_payment_proof(p_proof_id, p_approve, p_rejection_reason)
--
--    Admin aprueba o rechaza el comprobante desde el panel web.
--    Si aprueba: payment_status del pedido → 'verified', status → 'accepted'
--    Si rechaza: payment_status → 'rejected'
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION verify_payment_proof(
  p_proof_id        uuid,
  p_approve         boolean,
  p_rejection_reason text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_order_id uuid;
  v_role     text;
BEGIN
  -- Solo admin puede verificar
  SELECT role INTO v_role FROM profiles WHERE id = auth.uid();
  IF v_role <> 'admin' THEN
    RAISE EXCEPTION 'Solo administradores pueden verificar comprobantes';
  END IF;

  -- Obtener order_id del comprobante
  SELECT order_id INTO v_order_id
  FROM   payment_proofs
  WHERE  id = p_proof_id;

  IF v_order_id IS NULL THEN
    RAISE EXCEPTION 'Comprobante no encontrado';
  END IF;

  IF p_approve THEN
    -- Aprobar
    UPDATE payment_proofs
    SET    status       = 'verified',
           verified_by  = auth.uid(),
           verified_at  = now()
    WHERE  id = p_proof_id;

    UPDATE orders
    SET    payment_status = 'verified',
           status         = 'accepted'  -- el pedido pasa de pending a accepted
    WHERE  id = v_order_id;

    INSERT INTO audit_log (user_id, action, table_name, record_id, new_data)
    VALUES (auth.uid(), 'status_change', 'payment_proofs', p_proof_id::text,
            jsonb_build_object('approved', true, 'order_id', v_order_id));
  ELSE
    -- Rechazar
    UPDATE payment_proofs
    SET    status           = 'rejected',
           verified_by      = auth.uid(),
           verified_at      = now(),
           rejection_reason = p_rejection_reason
    WHERE  id = p_proof_id;

    UPDATE orders
    SET    payment_status = 'rejected'
    WHERE  id = v_order_id;

    INSERT INTO audit_log (user_id, action, table_name, record_id, new_data)
    VALUES (auth.uid(), 'status_change', 'payment_proofs', p_proof_id::text,
            jsonb_build_object('approved', false, 'reason', p_rejection_reason));
  END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION verify_payment_proof(uuid, boolean, text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Configuración de cuenta bancaria en settings
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO settings (key, value, description)
VALUES
  ('bank_name', 'Banco Pichincha', 'Nombre del banco para transferencias')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description)
VALUES
  ('bank_account_type', 'Ahorros', 'Tipo de cuenta bancaria')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description)
VALUES
  ('bank_account_number', '2100123456', 'Número de cuenta bancaria')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description)
VALUES
  ('bank_account_holder', 'Jagoo Delivery S.A.', 'Titular de la cuenta')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description)
VALUES
  ('bank_ruc', '1792345678001', 'RUC de la empresa')
ON CONFLICT (key) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'orders' AND column_name = 'payment_status'
  ), 'FALLA: orders.payment_status no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_name = 'payment_proofs'
  ), 'FALLA: tabla payment_proofs no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'upload_payment_proof'
  ), 'FALLA: función upload_payment_proof no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'verify_payment_proof'
  ), 'FALLA: función verify_payment_proof no existe';

  RAISE NOTICE '✅ FASE 3 pagos manuales: todas las verificaciones pasaron';
END;
$$;
