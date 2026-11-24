-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Retiro del pago con tarjeta (Kushki)
--
-- Métodos de pago que quedan: 'cash' (efectivo) y 'transfer' (transferencia).
--
-- Qué hace:
--   1. Los pedidos históricos con payment_method='card' pasan a 'transfer'
--      (la constraint ya no admite 'card'). No se borra ningún pedido.
--   2. kushki_transactions se renombra a card_payments_archive y queda solo
--      lectura para admin. Conserva los importes históricos para contabilidad
--      (los datos personales ya se anonimizan en delete_customer_account).
--   3. Se elimina el RPC register_kushki_charge y los settings kushki_*.
--
-- La Edge Function process-kushki-payment se elimina del repo; si ya estaba
-- desplegada: supabase functions delete process-kushki-payment
-- Idempotente.
-- ══════════════════════════════════════════════════════════════════════════════

-- 1. Pedidos históricos con tarjeta → transfer; constraint solo cash/transfer
UPDATE public.orders SET payment_method = 'transfer' WHERE payment_method = 'card';

ALTER TABLE public.orders DROP CONSTRAINT IF EXISTS orders_payment_method_check;
ALTER TABLE public.orders
  ADD CONSTRAINT orders_payment_method_check
  CHECK (payment_method IN ('cash', 'transfer'));

-- 2. Archivar la tabla de transacciones
DO $$
BEGIN
  IF to_regclass('public.kushki_transactions') IS NOT NULL
     AND to_regclass('public.card_payments_archive') IS NULL THEN
    ALTER TABLE public.kushki_transactions RENAME TO card_payments_archive;
  END IF;
END;
$$;

DO $$
BEGIN
  IF to_regclass('public.card_payments_archive') IS NOT NULL THEN
    DROP POLICY IF EXISTS kushki_tx_select ON public.card_payments_archive;
    DROP POLICY IF EXISTS card_archive_admin_select ON public.card_payments_archive;
    CREATE POLICY card_archive_admin_select ON public.card_payments_archive
      FOR SELECT USING (public.get_my_role() = 'admin');
    REVOKE INSERT, UPDATE, DELETE ON public.card_payments_archive FROM anon, authenticated;
    COMMENT ON TABLE public.card_payments_archive IS
      'Histórico de cobros con tarjeta (Kushki, retirado). Solo lectura para admin.';
  END IF;
END;
$$;

-- 3. Retirar RPC y settings
DROP FUNCTION IF EXISTS public.register_kushki_charge(uuid, text, numeric, text, jsonb);
DELETE FROM public.settings WHERE key IN ('kushki_public_key', 'kushki_enabled');

-- 4. Verificación
DO $$
BEGIN
  ASSERT NOT EXISTS (SELECT 1 FROM public.orders WHERE payment_method NOT IN ('cash', 'transfer')),
    'FALLA: quedan pedidos con payment_method distinto de cash/transfer';
  ASSERT NOT EXISTS (
    SELECT 1 FROM information_schema.routines
    WHERE routine_schema = 'public' AND routine_name = 'register_kushki_charge'
  ), 'FALLA: register_kushki_charge sigue existiendo';
  ASSERT NOT EXISTS (SELECT 1 FROM public.settings WHERE key LIKE 'kushki%'),
    'FALLA: quedan settings de kushki';
  RAISE NOTICE '✅ remove_kushki aplicada';
END;
$$;
