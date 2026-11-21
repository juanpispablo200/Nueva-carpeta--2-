-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Fase 4: Tabla settings, ganancias en backend, bucket Storage
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Tabla settings (clave-valor de configuración del sistema)
--    Usada por: ganancias del driver, datos bancarios, tarifa de envío, etc.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS settings (
  key         text PRIMARY KEY,
  value       text NOT NULL DEFAULT '',
  description text,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

-- RLS: solo admin escribe; autenticados pueden leer
ALTER TABLE settings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS settings_read ON settings;
CREATE POLICY settings_read ON settings
  FOR SELECT USING (auth.role() = 'authenticated');

DROP POLICY IF EXISTS settings_write ON settings;
CREATE POLICY settings_write ON settings
  FOR ALL
  USING  (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'))
  WITH CHECK (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- Seed de valores por defecto (idempotente con ON CONFLICT)
INSERT INTO settings (key, value, description) VALUES
  ('driver_fee_per_delivery', '1.50',          'Ganancia del repartidor por entrega ($)')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('default_delivery_fee',   '2.50',          'Tarifa de envío cobrada al cliente ($)')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('platform_name',          'Jagoo',         'Nombre de la plataforma')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('bank_name',              'Banco Pichincha','Nombre del banco para transferencias')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('bank_account_type',      'Ahorros',       'Tipo de cuenta bancaria')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('bank_account_number',    '2100123456',    'Número de cuenta bancaria')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('bank_account_holder',    'Jagoo Delivery S.A.', 'Titular de la cuenta')
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
  ('bank_ruc',               '1792345678001', 'RUC de la empresa')
ON CONFLICT (key) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. RPC get_driver_earnings(p_driver_id uuid, p_period text)
--
--    Calcula ganancias del repartidor en servidor usando la tarifa de settings.
--    p_period: 'today' | 'week' | 'month' | 'all'
--    Reemplaza el cálculo hardcodeado de 1.50 * entregas.size en Kotlin.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION get_driver_earnings(
  p_driver_id uuid,
  p_period    text DEFAULT 'all'
)
RETURNS TABLE (
  total_deliveries  bigint,
  fee_per_delivery  numeric,
  total_earnings    numeric,
  period            text
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_fee          numeric;
  v_from         timestamptz;
  v_caller_id    uuid;
  v_caller_role  text;
BEGIN
  -- Solo el propio repartidor o admin puede ver las ganancias
  v_caller_id := auth.uid();
  SELECT role INTO v_caller_role FROM profiles WHERE id = v_caller_id;

  IF v_caller_id <> p_driver_id AND v_caller_role <> 'admin' THEN
    RAISE EXCEPTION 'No tienes permiso para ver las ganancias de este repartidor';
  END IF;

  -- Leer tarifa desde settings
  SELECT value::numeric INTO v_fee
  FROM settings WHERE key = 'driver_fee_per_delivery';

  v_fee := COALESCE(v_fee, 1.50);  -- fallback si no existe

  -- Calcular fecha de inicio según período
  v_from := CASE p_period
    WHEN 'today' THEN date_trunc('day',   now())
    WHEN 'week'  THEN date_trunc('week',  now())
    WHEN 'month' THEN date_trunc('month', now())
    ELSE          TIMESTAMPTZ '-infinity'
  END;

  RETURN QUERY
  SELECT
    COUNT(*)::bigint           AS total_deliveries,
    v_fee                      AS fee_per_delivery,
    COUNT(*) * v_fee           AS total_earnings,
    p_period                   AS period
  FROM orders
  WHERE courier_id = p_driver_id
    AND status     = 'delivered'
    AND created_at >= v_from;
END;
$$;

GRANT EXECUTE ON FUNCTION get_driver_earnings(uuid, text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Bucket de Supabase Storage para comprobantes de pago
--
--    Se crea con SQL directamente en storage.buckets.
--    public = false → solo usuarios autenticados acceden.
--    file_size_limit = 5 MB, mime types = imágenes.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'payment-proofs',
  'payment-proofs',
  false,
  5242880,   -- 5 MB
  ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/heic']
)
ON CONFLICT (id) DO NOTHING;

-- Política: cliente autenticado puede subir sus propios comprobantes
DROP POLICY IF EXISTS storage_payment_proofs_insert ON storage.objects;
CREATE POLICY storage_payment_proofs_insert
  ON storage.objects FOR INSERT
  WITH CHECK (
    bucket_id = 'payment-proofs'
    AND auth.role() = 'authenticated'
  );

-- Política: el dueño del archivo y admin pueden leerlo
DROP POLICY IF EXISTS storage_payment_proofs_select ON storage.objects;
CREATE POLICY storage_payment_proofs_select
  ON storage.objects FOR SELECT
  USING (
    bucket_id = 'payment-proofs'
    AND (
      auth.uid()::text = (storage.foldername(name))[1]
      OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    )
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_name = 'settings'
  ), 'FALLA: tabla settings no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'get_driver_earnings'
  ), 'FALLA: función get_driver_earnings no existe';

  ASSERT EXISTS (
    SELECT 1 FROM storage.buckets WHERE id = 'payment-proofs'
  ), 'FALLA: bucket payment-proofs no existe';

  RAISE NOTICE '✅ FASE 4 earnings+storage: todas las verificaciones pasaron';
END;
$$;
