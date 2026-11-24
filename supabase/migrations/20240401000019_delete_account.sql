-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Eliminación de cuenta de cliente (requisito de Google Play)
--
-- Qué hace:
--   1. Permite anonimizar pedidos y registros contables: las columnas customer_id
--      pasan a ser NULL y sus claves foráneas a ON DELETE SET NULL.
--   2. Crea delete_customer_account(uuid): anonimiza lo que se conserva por
--      obligación contable, borra lo demás y elimina la cuenta de auth.users
--      (en cascada: profiles, favorites, addresses) en UNA sola transacción.
--   3. La función SOLO la puede ejecutar service_role (Edge Function delete-account).
--      Un usuario autenticado NO puede llamarla: podría borrar cuentas ajenas.
--
-- Idempotente. No edita migraciones anteriores.
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. customer_id nullable + ON DELETE SET NULL
--    (orders, payment_proofs, kushki_transactions, ratings)
--    Se omiten las tablas que no existan en este entorno.
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
  t      record;
  v_con  text;
BEGIN
  FOR t IN
    SELECT * FROM (VALUES
      ('orders',              'customer_id'),
      ('payment_proofs',      'customer_id'),
      ('kushki_transactions', 'customer_id'),
      ('ratings',             'customer_id')
    ) AS x(tbl, col)
  LOOP
    IF to_regclass('public.' || t.tbl) IS NULL THEN
      RAISE NOTICE 'Tabla % no existe, se omite', t.tbl;
      CONTINUE;
    END IF;

    -- Quitar la FK actual hacia profiles (cualquiera sea su nombre)
    FOR v_con IN
      SELECT c.conname
      FROM   pg_constraint c
      JOIN   pg_attribute  a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
      WHERE  c.contype  = 'f'
        AND  c.conrelid = to_regclass('public.' || t.tbl)
        AND  a.attname  = t.col
    LOOP
      EXECUTE format('ALTER TABLE public.%I DROP CONSTRAINT %I', t.tbl, v_con);
    END LOOP;

    EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I DROP NOT NULL', t.tbl, t.col);
    EXECUTE format(
      'ALTER TABLE public.%I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES public.profiles(id) ON DELETE SET NULL',
      t.tbl, t.tbl || '_' || t.col || '_fkey', t.col
    );
  END LOOP;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. delete_customer_account(p_user_id)
--    Errores con código estable (la Edge Function los traduce al español):
--      USUARIO_NO_ENCONTRADO · ROL_NO_PERMITIDO · PEDIDOS_EN_CURSO
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.delete_customer_account(p_user_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_role text;
BEGIN
  IF p_user_id IS NULL THEN
    RAISE EXCEPTION 'USUARIO_NO_ENCONTRADO';
  END IF;

  -- La cuenta debe existir en auth
  IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id = p_user_id) THEN
    RAISE EXCEPTION 'USUARIO_NO_ENCONTRADO';
  END IF;

  -- Solo clientes. Admin, restaurante y repartidor los gestiona el administrador.
  SELECT role INTO v_role FROM public.profiles WHERE id = p_user_id;
  IF v_role IS NOT NULL AND v_role <> 'customer' THEN
    RAISE EXCEPTION 'ROL_NO_PERMITIDO';
  END IF;

  -- No se elimina con pedidos en curso
  IF EXISTS (
    SELECT 1 FROM public.orders
    WHERE customer_id = p_user_id
      AND status NOT IN ('delivered', 'cancelled')
  ) THEN
    RAISE EXCEPTION 'PEDIDOS_EN_CURSO';
  END IF;

  -- ── Anonimizar (se conserva el registro contable, sin datos personales) ────
  UPDATE public.order_items
     SET special_instructions = ''
   WHERE order_id IN (SELECT id FROM public.orders WHERE customer_id = p_user_id);

  UPDATE public.orders
     SET customer_id      = NULL,
         delivery_address = NULL
   WHERE customer_id = p_user_id;

  IF to_regclass('public.payment_proofs') IS NOT NULL THEN
    UPDATE public.payment_proofs
       SET customer_id      = NULL,
           image_url        = '[eliminado]',
           reference_number = NULL
     WHERE customer_id = p_user_id;
  END IF;

  IF to_regclass('public.kushki_transactions') IS NOT NULL THEN
    UPDATE public.kushki_transactions
       SET customer_id     = NULL,
           kushki_token    = '[eliminado]',
           kushki_response = NULL
     WHERE customer_id = p_user_id;
  END IF;

  IF to_regclass('public.ratings') IS NOT NULL THEN
    UPDATE public.ratings
       SET customer_id        = NULL,
           restaurant_comment = NULL,
           courier_comment    = NULL
     WHERE customer_id = p_user_id;
  END IF;

  IF to_regclass('public.order_events') IS NOT NULL THEN
    UPDATE public.order_events SET note = NULL WHERE actor_id = p_user_id;
  END IF;

  IF to_regclass('public.audit_log') IS NOT NULL THEN
    UPDATE public.audit_log
       SET old_data = NULL, new_data = NULL, ip_address = NULL, user_agent = NULL
     WHERE user_id = p_user_id;
  END IF;

  -- ── Borrar ────────────────────────────────────────────────────────────────
  IF to_regclass('public.notification_log') IS NOT NULL THEN
    DELETE FROM public.notification_log WHERE user_id = p_user_id;
  END IF;

  DELETE FROM public.addresses WHERE user_id = p_user_id;

  -- Cuenta de acceso. En cascada: sesiones, identidades, profiles y favorites.
  DELETE FROM auth.users WHERE id = p_user_id;
END;
$$;

-- Solo la Edge Function (service_role) puede ejecutarla
REVOKE ALL ON FUNCTION public.delete_customer_account(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.delete_customer_account(uuid) FROM anon;
REVOKE ALL ON FUNCTION public.delete_customer_account(uuid) FROM authenticated;
GRANT  EXECUTE ON FUNCTION public.delete_customer_account(uuid) TO service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines
    WHERE routine_schema = 'public' AND routine_name = 'delete_customer_account'
  ), 'FALLA: delete_customer_account no existe';

  ASSERT NOT has_function_privilege('authenticated', 'public.delete_customer_account(uuid)', 'EXECUTE'),
    'FALLA: authenticated puede ejecutar delete_customer_account (riesgo de borrar cuentas ajenas)';
  ASSERT NOT has_function_privilege('anon', 'public.delete_customer_account(uuid)', 'EXECUTE'),
    'FALLA: anon puede ejecutar delete_customer_account';

  ASSERT (SELECT is_nullable = 'YES' FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'orders' AND column_name = 'customer_id'),
    'FALLA: orders.customer_id sigue siendo NOT NULL';

  RAISE NOTICE '✅ delete_account: migración aplicada y permisos verificados';
END;
$$;
