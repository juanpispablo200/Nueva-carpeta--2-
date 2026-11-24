-- ==========================================================================
-- JAGOO - PASO 3 de 3: eliminar cuenta, sin tarjeta, tipos de negocio, pedidos y pagos, retencion
-- Concatenacion literal de supabase/migrations/ (no se modifico ningun archivo).
-- Va en UNA transaccion: si algo falla, no se guarda nada de ESTA parte.
-- Si falla, copia el PRIMER mensaje de error (no los 'current transaction is aborted').
-- ==========================================================================

BEGIN;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000019_delete_account.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
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


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000020_remove_kushki.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
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


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000021_business_types.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Tipos de negocio (restaurante, tienda, farmacia, licoreria)
--
-- La tabla se sigue llamando `restaurants` (no se renombra para no romper FKs,
-- RLS ni la app). En la interfaz se muestra como "negocio".
--
-- Qué hace:
--   1. enum business_type + columnas en restaurants (tipo, permiso, activo).
--   2. Marcas por producto: is_age_restricted (licorería) y
--      requires_prescription (farmacia), con triggers que impiden usarlas en
--      el tipo equivocado.
--   3. profiles.birth_date, que solo se puede registrar UNA vez (excepto admin).
--   4. Configuración: business_type_config (reparto y pedido mínimo por tipo)
--      y delivery_zones (tarifa de envío por zona; el cliente elige la zona
--      al confirmar el pedido).
--   5. RLS: los clientes solo ven negocios activos; el dueño no puede cambiar
--      tipo, estado, permiso ni dueño de su negocio.
--
-- Idempotente.
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Tipo de negocio
-- ─────────────────────────────────────────────────────────────────────────────
-- get_my_role(): las migraciones 11, 12, 14 y 18 la definen STABLE con `SET LOCAL
-- row_security = off` en el cuerpo, y PostgreSQL lo rechaza al ejecutarla
-- ("SET is not allowed in a non-volatile function"). El ajuste va en la cláusula
-- de la función, que sí está permitida.
CREATE OR REPLACE FUNCTION public.get_my_role()
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = public
SET row_security = off
AS $$
DECLARE
  v_role text;
BEGIN
  SELECT role INTO v_role FROM public.profiles WHERE id = auth.uid();
  RETURN COALESCE(v_role, 'customer');
END;
$$;
DO $$
BEGIN
  CREATE TYPE public.business_type AS ENUM ('restaurante', 'tienda', 'farmacia', 'licoreria');
EXCEPTION WHEN duplicate_object THEN NULL;
END;
$$;

ALTER TABLE public.restaurants
  ADD COLUMN IF NOT EXISTS business_type public.business_type NOT NULL DEFAULT 'restaurante',
  ADD COLUMN IF NOT EXISTS permit_number text,
  ADD COLUMN IF NOT EXISTS is_active    boolean NOT NULL DEFAULT true;

CREATE INDEX IF NOT EXISTS idx_restaurants_type_active
  ON public.restaurants (business_type) WHERE is_active;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Marcas por producto
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE public.dishes
  ADD COLUMN IF NOT EXISTS is_age_restricted     boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS requires_prescription boolean NOT NULL DEFAULT false;

-- Licorería: todo producto es restringido (mayores de 18). Farmacia: puede
-- requerir receta (si no, es de venta libre). Otros tipos: ninguna marca.
CREATE OR REPLACE FUNCTION public.enforce_dish_restrictions()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
  v_type public.business_type;
BEGIN
  SELECT business_type INTO v_type FROM public.restaurants WHERE id = NEW.restaurant_id;

  IF v_type = 'licoreria' THEN
    NEW.is_age_restricted := true;
  ELSIF NEW.is_age_restricted THEN
    RAISE EXCEPTION 'RESTRICCION_INVALIDA: solo los productos de una licorería pueden ser para mayores de 18';
  END IF;

  -- Medicamentos: SOLO venta libre. Jagoo no vende productos que exijan receta médica.
  IF NEW.requires_prescription THEN
    RAISE EXCEPTION 'RESTRICCION_INVALIDA: no se venden productos con receta; Medicamentos solo ofrece venta libre';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_dish_restrictions ON public.dishes;
CREATE TRIGGER trg_dish_restrictions
  BEFORE INSERT OR UPDATE OF restaurant_id, is_age_restricted, requires_prescription
  ON public.dishes
  FOR EACH ROW EXECUTE FUNCTION public.enforce_dish_restrictions();

-- Limpieza: cualquier producto que hubiera quedado marcado como 'con receta' se retira del catálogo
-- (borrado lógico, reversible) y se quita la marca. Idempotente.
UPDATE public.dishes
   SET requires_prescription = false,
       is_available = false,
       deleted_at = COALESCE(deleted_at, now())
 WHERE requires_prescription;

-- Si cambia el tipo del negocio, se recalculan las marcas de sus productos
CREATE OR REPLACE FUNCTION public.sync_dishes_on_type_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NEW.business_type IS DISTINCT FROM OLD.business_type THEN
    UPDATE public.dishes
       SET is_age_restricted     = (NEW.business_type = 'licoreria'),
           requires_prescription = CASE WHEN NEW.business_type = 'farmacia'
                                        THEN requires_prescription ELSE false END
     WHERE restaurant_id = NEW.id;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_dishes_type ON public.restaurants;
CREATE TRIGGER trg_sync_dishes_type
  AFTER UPDATE OF business_type ON public.restaurants
  FOR EACH ROW EXECUTE FUNCTION public.sync_dishes_on_type_change();

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Fecha de nacimiento (una sola vez)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS birth_date date;

CREATE OR REPLACE FUNCTION public.is_adult(p_birth date)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT p_birth IS NOT NULL AND p_birth <= (CURRENT_DATE - INTERVAL '18 years')::date;
$$;

-- SECURITY INVOKER: current_user refleja al cliente (authenticated) cuando
-- hace UPDATE directo, y al dueño de la función cuando viene de un RPC.
CREATE OR REPLACE FUNCTION public.guard_birth_date()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.birth_date IS NOT DISTINCT FROM OLD.birth_date THEN
    RETURN NEW;
  END IF;

  IF NEW.birth_date IS NOT NULL
     AND (NEW.birth_date < DATE '1900-01-01' OR NEW.birth_date > CURRENT_DATE) THEN
    RAISE EXCEPTION 'FECHA_INVALIDA';
  END IF;

  IF current_user IN ('authenticated', 'anon')
     AND OLD.birth_date IS NOT NULL
     AND public.get_my_role() IS DISTINCT FROM 'admin' THEN
    RAISE EXCEPTION 'FECHA_NACIMIENTO_YA_REGISTRADA';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_birth_date ON public.profiles;
CREATE TRIGGER trg_guard_birth_date
  BEFORE UPDATE OF birth_date ON public.profiles
  FOR EACH ROW EXECUTE FUNCTION public.guard_birth_date();

-- RPC: registra la fecha UNA vez y devuelve si es mayor de 18.
-- Si ya existe, no la cambia y devuelve el resultado con la fecha guardada.
CREATE OR REPLACE FUNCTION public.set_birth_date(p_birth date)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_existing date;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'NO_AUTENTICADO';
  END IF;

  SELECT birth_date INTO v_existing FROM public.profiles WHERE id = auth.uid();

  IF v_existing IS NULL THEN
    UPDATE public.profiles SET birth_date = p_birth WHERE id = auth.uid();
    RETURN public.is_adult(p_birth);
  END IF;

  RETURN public.is_adult(v_existing);
END;
$$;

REVOKE ALL ON FUNCTION public.set_birth_date(date) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_birth_date(date) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Configuración: reparto y pedido mínimo por tipo, zonas de envío
-- ─────────────────────────────────────────────────────────────────────────────
-- Valores por defecto globales (el admin los edita en Ajustes). Son EJEMPLOS:
-- ajústalos a tu modelo de negocio. Los tres porcentajes deben sumar 100.
INSERT INTO public.settings (key, value, description) VALUES
  ('split_negocio_pct',    '85', 'Reparto: % del subtotal para el negocio'),
  ('split_driver_pct',     '5',  'Reparto: % del subtotal para el repartidor (el envío va íntegro al repartidor)'),
  ('split_plataforma_pct', '10', 'Reparto: % del subtotal para la plataforma'),
  ('transfer_timeout_minutes', '30', 'Minutos para subir el comprobante de transferencia antes de cancelar el pedido')
ON CONFLICT (key) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.business_type_config (
  business_type   public.business_type PRIMARY KEY,
  pct_negocio     numeric(5,2),
  pct_driver      numeric(5,2),
  pct_plataforma  numeric(5,2),
  min_order       numeric(10,2),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  -- Reparto propio del tipo: o los tres porcentajes (suman 100) o ninguno
  CONSTRAINT business_type_config_split_ck CHECK (
    (pct_negocio IS NULL AND pct_driver IS NULL AND pct_plataforma IS NULL)
    OR (pct_negocio IS NOT NULL AND pct_driver IS NOT NULL AND pct_plataforma IS NOT NULL
        AND pct_negocio >= 0 AND pct_driver >= 0 AND pct_plataforma >= 0
        AND pct_negocio + pct_driver + pct_plataforma = 100)
  ),
  CONSTRAINT business_type_config_min_ck CHECK (min_order IS NULL OR min_order >= 0)
);

INSERT INTO public.business_type_config (business_type)
SELECT t FROM unnest(enum_range(NULL::public.business_type)) AS t
ON CONFLICT DO NOTHING;

ALTER TABLE public.business_type_config ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS btc_select ON public.business_type_config;
CREATE POLICY btc_select ON public.business_type_config
  FOR SELECT USING (auth.role() = 'authenticated');
DROP POLICY IF EXISTS btc_admin_write ON public.business_type_config;
CREATE POLICY btc_admin_write ON public.business_type_config
  FOR ALL USING (public.get_my_role() = 'admin') WITH CHECK (public.get_my_role() = 'admin');

CREATE TABLE IF NOT EXISTS public.delivery_zones (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name        text NOT NULL,
  fee         numeric(10,2) NOT NULL CHECK (fee >= 0),
  is_active   boolean NOT NULL DEFAULT true,
  sort_order  integer NOT NULL DEFAULT 0,
  created_at  timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.delivery_zones ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS zones_select ON public.delivery_zones;
CREATE POLICY zones_select ON public.delivery_zones
  FOR SELECT USING (auth.role() = 'authenticated');
DROP POLICY IF EXISTS zones_admin_write ON public.delivery_zones;
CREATE POLICY zones_admin_write ON public.delivery_zones
  FOR ALL USING (public.get_my_role() = 'admin') WITH CHECK (public.get_my_role() = 'admin');

-- Reparto efectivo (porcentajes) para un tipo: el del tipo si lo define, si no el global
CREATE OR REPLACE FUNCTION public.get_split_pcts(p_type public.business_type)
RETURNS TABLE (pct_negocio numeric, pct_driver numeric, pct_plataforma numeric)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  c public.business_type_config%ROWTYPE;
  v_n numeric; v_d numeric; v_p numeric;
BEGIN
  SELECT * INTO c FROM public.business_type_config WHERE business_type = p_type;
  IF FOUND AND c.pct_negocio IS NOT NULL THEN
    RETURN QUERY SELECT c.pct_negocio, c.pct_driver, c.pct_plataforma;
    RETURN;
  END IF;

  SELECT COALESCE((SELECT value::numeric FROM public.settings WHERE key = 'split_negocio_pct'), 85),
         COALESCE((SELECT value::numeric FROM public.settings WHERE key = 'split_driver_pct'), 5),
         COALESCE((SELECT value::numeric FROM public.settings WHERE key = 'split_plataforma_pct'), 10)
    INTO v_n, v_d, v_p;

  IF v_n + v_d + v_p <> 100 THEN
    RAISE EXCEPTION 'REPARTO_INVALIDO: los porcentajes de reparto deben sumar 100';
  END IF;

  RETURN QUERY SELECT v_n, v_d, v_p;
END;
$$;

REVOKE ALL ON FUNCTION public.get_split_pcts(public.business_type) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_split_pcts(public.business_type) TO authenticated;

-- Pedido mínimo efectivo: el del tipo si lo define, si no el del negocio
CREATE OR REPLACE FUNCTION public.get_effective_min_order(p_restaurant_id text)
RETURNS numeric
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT COALESCE(c.min_order, r.min_order, 0)
    FROM public.restaurants r
    LEFT JOIN public.business_type_config c ON c.business_type = r.business_type
   WHERE r.id = p_restaurant_id;
$$;

REVOKE ALL ON FUNCTION public.get_effective_min_order(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_effective_min_order(text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. RLS
-- ─────────────────────────────────────────────────────────────────────────────
-- Clientes: solo negocios activos. Admin y dueño ven el suyo aunque esté inactivo.
-- Las políticas públicas antiguas (migración 3) permitían leer TODO y se combinan con OR:
-- hay que quitarlas o los negocios inactivos seguirían visibles para los clientes.
DROP POLICY IF EXISTS restaurants_select_public ON public.restaurants;
DROP POLICY IF EXISTS dishes_select_public ON public.dishes;
DROP POLICY IF EXISTS restaurants_select ON public.restaurants;
CREATE POLICY restaurants_select ON public.restaurants
  FOR SELECT
  USING (
    is_active
    OR owner_id = auth.uid()
    OR public.get_my_role() = 'admin'
  );

-- Productos: solo de negocios activos (admin y dueño ven todo lo no borrado)
DROP POLICY IF EXISTS dishes_select ON public.dishes;
CREATE POLICY dishes_select ON public.dishes
  FOR SELECT
  USING (
    deleted_at IS NULL
    AND (
      public.get_my_role() = 'admin'
      OR EXISTS (
        SELECT 1 FROM public.restaurants r
         WHERE r.id = dishes.restaurant_id
           AND (r.is_active OR r.owner_id = auth.uid())
      )
    )
  );

-- El dueño no puede cambiar tipo, estado, permiso ni dueño de su negocio.
-- SECURITY INVOKER: solo aplica a UPDATE directo del cliente.
CREATE OR REPLACE FUNCTION public.guard_restaurant_columns()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon')
     AND public.get_my_role() IS DISTINCT FROM 'admin'
     AND (NEW.business_type IS DISTINCT FROM OLD.business_type
          OR NEW.is_active     IS DISTINCT FROM OLD.is_active
          OR NEW.permit_number IS DISTINCT FROM OLD.permit_number
          OR NEW.owner_id      IS DISTINCT FROM OLD.owner_id
          OR NEW.min_order     IS DISTINCT FROM OLD.min_order) THEN
    RAISE EXCEPTION 'SOLO_ADMIN: tipo, estado, permiso, dueño y pedido mínimo solo los cambia el administrador';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_restaurant_columns ON public.restaurants;
CREATE TRIGGER trg_guard_restaurant_columns
  BEFORE UPDATE ON public.restaurants
  FOR EACH ROW EXECUTE FUNCTION public.guard_restaurant_columns();

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT (SELECT array_agg(e::text ORDER BY e::text) FROM unnest(enum_range(NULL::public.business_type)) e)
         = ARRAY['farmacia','licoreria','restaurante','tienda'],
    'FALLA: valores del enum business_type';
  ASSERT NOT EXISTS (SELECT 1 FROM public.restaurants WHERE business_type IS NULL),
    'FALLA: negocios sin tipo';
  ASSERT public.is_adult((CURRENT_DATE - INTERVAL '18 years')::date)
         AND NOT public.is_adult((CURRENT_DATE - INTERVAL '17 years')::date),
    'FALLA: is_adult';
  RAISE NOTICE '✅ business_types aplicada';
END;
$$;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000022_orders_payments.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Pedidos y pagos (efectivo y transferencia), reparto, edad y receta
--
-- Qué hace:
--   1. Columnas nuevas en orders (pago, cambio, plazo, edad, receta, reparto).
--   2. create_order(): el pedido se crea en el servidor y devuelve el uuid real.
--      Calcula subtotal, envío por zona, descuento, total y reparto.
--      Se cierra el INSERT directo de pedidos/ítems por parte del cliente.
--   3. Reglas por trigger: no se acepta sin pago (efectivo o transferencia
--      verificada) ni sin receta aprobada; no se entrega sin verificar edad.
--   4. Transferencia: comprobante obligatorio; sin comprobante en N minutos
--      (30 por defecto) el pedido se cancela (pg_cron cada minuto).
--   5. Receta (farmacia) y verificación de edad (licorería).
--   6. Efectivo cobrado por el driver y liquidación a la plataforma.
--   7. RLS: el negocio solo ve pedidos de transferencia ya verificados
--      (o de efectivo); bucket privado de recetas.
--   8. Notificaciones, ganancias del driver, reportes por tipo y eliminación
--      de cuenta actualizadas.
--
-- Idempotente. Requiere las migraciones 20 y 21.
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Columnas de orders
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE public.orders
  ADD COLUMN IF NOT EXISTS business_type            public.business_type,
  ADD COLUMN IF NOT EXISTS discount                 numeric(10,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS delivery_zone_name       text,
  ADD COLUMN IF NOT EXISTS cash_tendered            numeric(10,2),
  ADD COLUMN IF NOT EXISTS change_due               numeric(10,2),
  ADD COLUMN IF NOT EXISTS transfer_deadline        timestamptz,
  ADD COLUMN IF NOT EXISTS age_check_required       boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS age_verified_at          timestamptz,
  ADD COLUMN IF NOT EXISTS age_verified_by          uuid REFERENCES public.profiles(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS prescription_required    boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS prescription_status      text NOT NULL DEFAULT 'none',
  ADD COLUMN IF NOT EXISTS prescription_path        text,
  ADD COLUMN IF NOT EXISTS prescription_reviewed_by uuid REFERENCES public.profiles(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS prescription_reviewed_at timestamptz,
  ADD COLUMN IF NOT EXISTS prescription_note        text,
  ADD COLUMN IF NOT EXISTS business_amount          numeric(10,2),
  ADD COLUMN IF NOT EXISTS driver_amount            numeric(10,2),
  ADD COLUMN IF NOT EXISTS platform_amount          numeric(10,2),
  ADD COLUMN IF NOT EXISTS cancel_reason            text,
  ADD COLUMN IF NOT EXISTS refund_required          boolean NOT NULL DEFAULT false;

ALTER TABLE public.orders DROP CONSTRAINT IF EXISTS orders_prescription_status_check;
ALTER TABLE public.orders
  ADD CONSTRAINT orders_prescription_status_check
  CHECK (prescription_status IN ('none', 'pending', 'approved', 'rejected'));

-- Pedidos existentes: tipo del negocio
UPDATE public.orders o
   SET business_type = r.business_type
  FROM public.restaurants r
 WHERE r.id = o.restaurant_id AND o.business_type IS NULL;

CREATE INDEX IF NOT EXISTS idx_orders_transfer_deadline
  ON public.orders (transfer_deadline)
  WHERE payment_method = 'transfer' AND status = 'pending';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Los triggers de transición dejan pasar al "sistema" (cron, service_role):
--    auth.uid() IS NULL. Los clientes anónimos no pueden hacer UPDATE por RLS.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.validate_order_transition()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_role text;
BEGIN
  IF NEW.status = OLD.status THEN RETURN NEW; END IF;
  IF auth.uid() IS NULL THEN RETURN NEW; END IF;

  SELECT role INTO v_role FROM public.profiles WHERE id = auth.uid();
  IF v_role = 'admin' THEN RETURN NEW; END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.order_transitions
    WHERE actor_role = v_role AND from_status = OLD.status AND to_status = NEW.status
  ) THEN
    RAISE EXCEPTION 'Transición no permitida: % → % para rol %',
      OLD.status, NEW.status, COALESCE(v_role, 'anónimo');
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.validate_order_status_transition()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  user_role text;
BEGIN
  IF NEW.status = OLD.status THEN RETURN NEW; END IF;
  IF auth.uid() IS NULL THEN RETURN NEW; END IF;

  SELECT role INTO user_role FROM public.profiles WHERE id = auth.uid();

  IF user_role = 'admin' THEN RETURN NEW; END IF;

  IF user_role = 'restaurant' THEN
    IF (OLD.status = 'pending'   AND NEW.status = 'accepted')  OR
       (OLD.status = 'accepted'  AND NEW.status = 'preparing') OR
       (OLD.status = 'preparing' AND NEW.status = 'ready')     OR
       (NEW.status = 'cancelled') THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Transición no permitida: % → % para rol restaurant', OLD.status, NEW.status;
  END IF;

  IF user_role = 'repartidor' THEN
    IF (OLD.status = 'ready'      AND NEW.status = 'picked_up')  OR
       (OLD.status = 'picked_up'  AND NEW.status = 'on_the_way') OR
       (OLD.status = 'on_the_way' AND NEW.status = 'delivered') THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Transición no permitida: % → % para rol repartidor', OLD.status, NEW.status;
  END IF;

  -- El cliente solo puede cancelar mientras está pendiente (valida order_transitions)
  IF user_role = 'customer' AND OLD.status = 'pending' AND NEW.status = 'cancelled' THEN
    RETURN NEW;
  END IF;

  RAISE EXCEPTION 'Rol % no autorizado para cambiar estados', COALESCE(user_role, 'anónimo');
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Reglas de negocio (aplican a todos, incluido admin)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.enforce_order_business_rules()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  IF NEW.status = OLD.status THEN RETURN NEW; END IF;

  IF NEW.status = 'accepted' THEN
    IF NOT (NEW.payment_method = 'cash' OR NEW.payment_status = 'verified') THEN
      RAISE EXCEPTION 'PAGO_PENDIENTE: el pedido no se puede aceptar hasta verificar el pago';
    END IF;
    IF NEW.prescription_required AND NEW.prescription_status <> 'approved' THEN
      RAISE EXCEPTION 'RECETA_PENDIENTE: el pedido no se puede aceptar sin receta aprobada';
    END IF;
  END IF;

  IF NEW.status = 'delivered' AND NEW.age_check_required AND NEW.age_verified_at IS NULL THEN
    RAISE EXCEPTION 'EDAD_NO_VERIFICADA: el repartidor debe verificar la cédula antes de entregar';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_order_business_rules ON public.orders;
CREATE TRIGGER trg_order_business_rules
  BEFORE UPDATE OF status ON public.orders
  FOR EACH ROW EXECUTE FUNCTION public.enforce_order_business_rules();

-- Columnas protegidas: un UPDATE directo del cliente (negocio o repartidor) no
-- puede tocar importes, pago, edad ni receta. SECURITY INVOKER: current_user
-- es 'authenticated' en UPDATE directo y el dueño de la función en los RPC.
CREATE OR REPLACE FUNCTION public.guard_order_columns()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon')
     AND public.get_my_role() IS DISTINCT FROM 'admin' THEN
    IF (NEW.customer_id, NEW.restaurant_id, NEW.business_type, NEW.subtotal, NEW.delivery_fee,
        NEW.discount, NEW.total, NEW.payment_method, NEW.payment_status, NEW.cash_tendered,
        NEW.change_due, NEW.transfer_deadline, NEW.age_check_required, NEW.age_verified_at,
        NEW.age_verified_by, NEW.prescription_required, NEW.prescription_status,
        NEW.prescription_path, NEW.prescription_reviewed_by, NEW.prescription_reviewed_at,
        NEW.prescription_note, NEW.business_amount, NEW.driver_amount, NEW.platform_amount,
        NEW.cancel_reason, NEW.refund_required)
       IS DISTINCT FROM
       (OLD.customer_id, OLD.restaurant_id, OLD.business_type, OLD.subtotal, OLD.delivery_fee,
        OLD.discount, OLD.total, OLD.payment_method, OLD.payment_status, OLD.cash_tendered,
        OLD.change_due, OLD.transfer_deadline, OLD.age_check_required, OLD.age_verified_at,
        OLD.age_verified_by, OLD.prescription_required, OLD.prescription_status,
        OLD.prescription_path, OLD.prescription_reviewed_by, OLD.prescription_reviewed_at,
        OLD.prescription_note, OLD.business_amount, OLD.driver_amount, OLD.platform_amount,
        OLD.cancel_reason, OLD.refund_required) THEN
      RAISE EXCEPTION 'COLUMNA_PROTEGIDA: ese dato solo se cambia desde las funciones del sistema';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_order_columns ON public.orders;
CREATE TRIGGER trg_guard_order_columns
  BEFORE UPDATE ON public.orders
  FOR EACH ROW EXECUTE FUNCTION public.guard_order_columns();

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. create_order(): crea el pedido en el servidor y devuelve el uuid real
--
--    p_items: [{ "dish_id": "...", "quantity": 2, "unit_price": 5.50,
--                "special_instructions": "", "selected_options_json": [] }]
--    Errores (el mensaje es un código estable que la app traduce):
--      NO_AUTENTICADO · CUENTA_NO_VALIDA · METODO_PAGO_INVALIDO ·
--      NEGOCIO_NO_DISPONIBLE · NEGOCIO_CERRADO · CARRITO_VACIO ·
--      PRODUCTO_NO_DISPONIBLE · CANTIDAD_INVALIDA · PRECIO_INVALIDO ·
--      PEDIDO_MINIMO:<monto> · ZONA_REQUERIDA · ZONA_INVALIDA ·
--      EDAD_NO_VERIFICADA · MENOR_DE_EDAD · EDAD_NO_CONFIRMADA · RECETA_REQUERIDA ·
--      EFECTIVO_INSUFICIENTE · REPARTO_INVALIDO
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.create_order(
  p_restaurant_id     text,
  p_items             jsonb,
  p_delivery_address  text,
  p_zone_id           uuid    DEFAULT NULL,
  p_payment_method    text    DEFAULT 'cash',
  p_cash_tendered     numeric DEFAULT NULL,
  p_coupon_code       text    DEFAULT NULL,
  p_prescription_path text    DEFAULT NULL,
  p_age_confirmed     boolean DEFAULT false
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_uid          uuid := auth.uid();
  v_prof         public.profiles%ROWTYPE;
  v_rest         public.restaurants%ROWTYPE;
  v_item         record;
  v_dish         public.dishes%ROWTYPE;
  v_subtotal     numeric(10,2) := 0;
  v_fee          numeric(10,2) := 0;
  v_discount     numeric(10,2) := 0;
  v_total        numeric(10,2);
  v_change       numeric(10,2);
  v_min          numeric;
  v_age_req      boolean := false;
  v_rx_req       boolean := false;
  v_zone         public.delivery_zones%ROWTYPE;
  v_zone_name    text;
  v_has_zones    boolean;
  v_pct          record;
  v_biz_amt      numeric(10,2);
  v_drv_amt      numeric(10,2);
  v_plat_amt     numeric(10,2);
  v_timeout      integer;
  v_deadline     timestamptz;
  v_order_id     uuid;
  v_count        integer := 0;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'NO_AUTENTICADO'; END IF;

  SELECT * INTO v_prof FROM public.profiles WHERE id = v_uid;
  IF NOT FOUND OR v_prof.role <> 'customer' OR COALESCE(v_prof.is_active, true) = false THEN
    RAISE EXCEPTION 'CUENTA_NO_VALIDA';
  END IF;

  IF p_payment_method IS NULL OR p_payment_method NOT IN ('cash', 'transfer') THEN
    RAISE EXCEPTION 'METODO_PAGO_INVALIDO';
  END IF;

  SELECT * INTO v_rest FROM public.restaurants WHERE id = p_restaurant_id;
  IF NOT FOUND OR NOT v_rest.is_active THEN RAISE EXCEPTION 'NEGOCIO_NO_DISPONIBLE'; END IF;
  IF NOT v_rest.is_open THEN RAISE EXCEPTION 'NEGOCIO_CERRADO'; END IF;

  IF p_items IS NULL OR jsonb_typeof(p_items) <> 'array' OR jsonb_array_length(p_items) = 0 THEN
    RAISE EXCEPTION 'CARRITO_VACIO';
  END IF;

  -- Validar ítems y calcular subtotal
  FOR v_item IN
    SELECT * FROM jsonb_to_recordset(p_items)
      AS x(dish_id text, quantity integer, unit_price numeric)
  LOOP
    v_count := v_count + 1;
    SELECT * INTO v_dish FROM public.dishes
     WHERE id = v_item.dish_id AND restaurant_id = p_restaurant_id
       AND deleted_at IS NULL AND is_available;
    IF NOT FOUND THEN RAISE EXCEPTION 'PRODUCTO_NO_DISPONIBLE'; END IF;
    -- Defensa: no se vende nada que exija receta (el trigger ya lo impide al guardar el producto)
    IF v_dish.requires_prescription THEN RAISE EXCEPTION 'PRODUCTO_NO_DISPONIBLE'; END IF;
    IF v_item.quantity IS NULL OR v_item.quantity < 1 OR v_item.quantity > 50 THEN
      RAISE EXCEPTION 'CANTIDAD_INVALIDA';
    END IF;
    -- El precio unitario incluye opciones: nunca puede ser menor al precio base
    IF v_item.unit_price IS NULL OR v_item.unit_price < v_dish.price THEN
      RAISE EXCEPTION 'PRECIO_INVALIDO';
    END IF;
    v_subtotal := v_subtotal + round(v_item.unit_price * v_item.quantity, 2);
    IF v_dish.is_age_restricted     THEN v_age_req := true; END IF;
    IF v_dish.requires_prescription THEN v_rx_req  := true; END IF;
  END LOOP;

  -- Pedido mínimo (del tipo si lo define; si no, el del negocio)
  v_min := public.get_effective_min_order(p_restaurant_id);
  IF v_subtotal < v_min THEN
    RAISE EXCEPTION 'PEDIDO_MINIMO:%', to_char(v_min, 'FM999990.00');
  END IF;

  -- Edad (licorería)
  IF v_age_req THEN
    IF v_prof.birth_date IS NULL THEN RAISE EXCEPTION 'EDAD_NO_VERIFICADA'; END IF;
    IF NOT public.is_adult(v_prof.birth_date) THEN RAISE EXCEPTION 'MENOR_DE_EDAD'; END IF;
    -- El cliente debe confirmar en el checkout que es mayor de 18 y que mostrará su cédula
    IF NOT COALESCE(p_age_confirmed, false) THEN RAISE EXCEPTION 'EDAD_NO_CONFIRMADA'; END IF;
  END IF;

  -- Receta (farmacia): foto subida a <uid>/...
  IF v_rx_req THEN
    IF p_prescription_path IS NULL
       OR left(p_prescription_path, length(v_uid::text) + 1) <> v_uid::text || '/' THEN
      RAISE EXCEPTION 'RECETA_REQUERIDA';
    END IF;
  END IF;

  -- Envío: por zona. Si no hay zonas configuradas, tarifa por defecto de settings.
  SELECT EXISTS (SELECT 1 FROM public.delivery_zones WHERE is_active) INTO v_has_zones;
  IF v_has_zones THEN
    IF p_zone_id IS NULL THEN RAISE EXCEPTION 'ZONA_REQUERIDA'; END IF;
    SELECT * INTO v_zone FROM public.delivery_zones WHERE id = p_zone_id AND is_active;
    IF NOT FOUND THEN RAISE EXCEPTION 'ZONA_INVALIDA'; END IF;
    v_fee := v_zone.fee;
    v_zone_name := v_zone.name;
  ELSE
    -- Sin zonas: tarifa propia del negocio (restaurants.delivery_fee); si no tiene, la de ajustes
    v_fee := v_rest.delivery_fee;
    IF v_fee IS NULL THEN
      SELECT COALESCE((SELECT value::numeric FROM public.settings WHERE key = 'default_delivery_fee'), 0) INTO v_fee;
    END IF;
  END IF;

  -- Cupón (se valida en el servidor)
  IF p_coupon_code IS NOT NULL AND length(trim(p_coupon_code)) > 0 THEN
    SELECT c.discount INTO v_discount FROM public.validate_coupon(trim(p_coupon_code), v_subtotal + v_fee) c;
  END IF;

  v_total := GREATEST(v_subtotal + v_fee - COALESCE(v_discount, 0), 0);

  -- Efectivo: "paga con"
  IF p_payment_method = 'cash' THEN
    IF p_cash_tendered IS NULL OR p_cash_tendered < v_total THEN
      RAISE EXCEPTION 'EFECTIVO_INSUFICIENTE';
    END IF;
    v_change := round(p_cash_tendered - v_total, 2);
  END IF;

  -- Transferencia: plazo para subir el comprobante
  IF p_payment_method = 'transfer' THEN
    SELECT COALESCE((SELECT value::integer FROM public.settings WHERE key = 'transfer_timeout_minutes'), 30)
      INTO v_timeout;
    v_deadline := now() + make_interval(mins => v_timeout);
  END IF;

  -- Reparto: % sobre el subtotal; el envío va íntegro al repartidor;
  -- la plataforma recibe el resto (absorbe el descuento del cupón).
  SELECT * INTO v_pct FROM public.get_split_pcts(v_rest.business_type);
  v_biz_amt  := round(v_subtotal * v_pct.pct_negocio / 100, 2);
  v_drv_amt  := round(v_subtotal * v_pct.pct_driver  / 100, 2) + v_fee;
  v_plat_amt := v_total - v_biz_amt - v_drv_amt;

  INSERT INTO public.orders (
    customer_id, restaurant_id, restaurant_name, restaurant_logo_url,
    subtotal, delivery_fee, discount, total, status, delivery_address,
    business_type, delivery_zone_name,
    payment_method, payment_status, cash_tendered, change_due, transfer_deadline,
    age_check_required, prescription_required, prescription_status, prescription_path,
    business_amount, driver_amount, platform_amount
  ) VALUES (
    v_uid, v_rest.id, v_rest.name, COALESCE(v_rest.logo_url, ''),
    v_subtotal, v_fee, COALESCE(v_discount, 0), v_total, 'pending', p_delivery_address,
    v_rest.business_type, v_zone_name,
    p_payment_method, 'pending', p_cash_tendered, v_change, v_deadline,
    v_age_req, v_rx_req, CASE WHEN v_rx_req THEN 'pending' ELSE 'none' END,
    CASE WHEN v_rx_req THEN p_prescription_path ELSE NULL END,
    v_biz_amt, v_drv_amt, v_plat_amt
  ) RETURNING id INTO v_order_id;

  INSERT INTO public.order_items
    (order_id, dish_id, dish_name, unit_price, quantity, selected_options_json, special_instructions)
  SELECT v_order_id, d.id, d.name, x.unit_price, x.quantity,
         COALESCE(x.selected_options_json, '[]'::jsonb), COALESCE(x.special_instructions, '')
    FROM jsonb_to_recordset(p_items)
      AS x(dish_id text, quantity integer, unit_price numeric,
           selected_options_json jsonb, special_instructions text)
    JOIN public.dishes d ON d.id = x.dish_id AND d.restaurant_id = p_restaurant_id;

  IF p_coupon_code IS NOT NULL AND length(trim(p_coupon_code)) > 0 THEN
    PERFORM public.apply_coupon(trim(p_coupon_code), v_order_id);
  END IF;

  RETURN jsonb_build_object(
    'order_id',          v_order_id,
    'subtotal',          v_subtotal,
    'delivery_fee',      v_fee,
    'discount',          COALESCE(v_discount, 0),
    'total',             v_total,
    'change_due',        v_change,
    'transfer_deadline', v_deadline,
    'age_check_required', v_age_req,
    'prescription_required', v_rx_req
  );
END;
$$;

REVOKE ALL ON FUNCTION public.create_order(text, jsonb, text, uuid, text, numeric, text, text, boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_order(text, jsonb, text, uuid, text, numeric, text, text, boolean) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Transferencia: comprobante obligatorio y verificación
-- ─────────────────────────────────────────────────────────────────────────────
-- p_image_url ahora guarda la RUTA del archivo en el bucket privado
-- payment-proofs (<uid>/<archivo>), no una URL pública.
CREATE OR REPLACE FUNCTION public.upload_payment_proof(
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
  v_order    public.orders%ROWTYPE;
  v_proof_id uuid;
BEGIN
  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Pedido no encontrado'; END IF;
  IF v_order.customer_id IS DISTINCT FROM auth.uid() THEN
    RAISE EXCEPTION 'No tienes permiso para subir comprobante de este pedido';
  END IF;

  IF v_order.payment_method <> 'transfer' THEN RAISE EXCEPTION 'PEDIDO_NO_ES_TRANSFERENCIA'; END IF;
  IF v_order.status <> 'pending' THEN RAISE EXCEPTION 'PEDIDO_NO_PAGABLE'; END IF;
  IF v_order.payment_status NOT IN ('pending', 'rejected') THEN RAISE EXCEPTION 'COMPROBANTE_YA_ENVIADO'; END IF;

  IF p_image_url IS NULL
     OR left(p_image_url, length(auth.uid()::text) + 1) <> auth.uid()::text || '/' THEN
    RAISE EXCEPTION 'RUTA_COMPROBANTE_INVALIDA';
  END IF;

  INSERT INTO public.payment_proofs (order_id, customer_id, image_url, reference_number, amount)
  VALUES (p_order_id, auth.uid(), p_image_url, p_reference_number,
          CASE WHEN COALESCE(p_amount, 0) > 0 THEN p_amount ELSE v_order.total END)
  RETURNING id INTO v_proof_id;

  UPDATE public.orders SET payment_status = 'verifying' WHERE id = p_order_id;

  INSERT INTO public.audit_log (user_id, action, table_name, record_id, new_data)
  VALUES (auth.uid(), 'create', 'payment_proofs', v_proof_id::text,
          jsonb_build_object('order_id', p_order_id, 'reference_number', p_reference_number));

  RETURN v_proof_id;
END;
$$;

GRANT EXECUTE ON FUNCTION public.upload_payment_proof(uuid, text, text, numeric) TO authenticated;

-- Al verificar: el pedido pasa a accepted (si no necesita receta o ya está
-- aprobada). Si se rechaza, el cliente tiene otro plazo para subir uno nuevo.
CREATE OR REPLACE FUNCTION public.verify_payment_proof(
  p_proof_id         uuid,
  p_approve          boolean,
  p_rejection_reason text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_order_id uuid;
  v_order    public.orders%ROWTYPE;
  v_timeout  integer;
BEGIN
  IF public.get_my_role() IS DISTINCT FROM 'admin' THEN
    RAISE EXCEPTION 'Solo administradores pueden verificar comprobantes';
  END IF;

  SELECT order_id INTO v_order_id FROM public.payment_proofs WHERE id = p_proof_id;
  IF v_order_id IS NULL THEN RAISE EXCEPTION 'Comprobante no encontrado'; END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = v_order_id FOR UPDATE;
  IF v_order.status = 'cancelled' THEN RAISE EXCEPTION 'PEDIDO_CANCELADO'; END IF;

  IF p_approve THEN
    UPDATE public.payment_proofs
       SET status = 'verified', verified_by = auth.uid(), verified_at = now()
     WHERE id = p_proof_id;

    UPDATE public.orders
       SET payment_status    = 'verified',
           transfer_deadline = NULL,
           status = CASE
             WHEN status = 'pending'
              AND (NOT prescription_required OR prescription_status = 'approved')
             THEN 'accepted' ELSE status END
     WHERE id = v_order_id;

    INSERT INTO public.audit_log (user_id, action, table_name, record_id, new_data)
    VALUES (auth.uid(), 'status_change', 'payment_proofs', p_proof_id::text,
            jsonb_build_object('approved', true, 'order_id', v_order_id));
  ELSE
    SELECT COALESCE((SELECT value::integer FROM public.settings WHERE key = 'transfer_timeout_minutes'), 30)
      INTO v_timeout;

    UPDATE public.payment_proofs
       SET status = 'rejected', verified_by = auth.uid(), verified_at = now(),
           rejection_reason = p_rejection_reason
     WHERE id = p_proof_id;

    UPDATE public.orders
       SET payment_status    = 'rejected',
           transfer_deadline = now() + make_interval(mins => v_timeout)
     WHERE id = v_order_id;

    INSERT INTO public.audit_log (user_id, action, table_name, record_id, new_data)
    VALUES (auth.uid(), 'status_change', 'payment_proofs', p_proof_id::text,
            jsonb_build_object('approved', false, 'reason', p_rejection_reason));
  END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION public.verify_payment_proof(uuid, boolean, text) TO authenticated;

-- Cancelación automática: transferencia sin comprobante (o con comprobante
-- rechazado y sin reemplazo) pasado el plazo.
CREATE OR REPLACE FUNCTION public.cancel_expired_transfer_orders()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_n integer;
BEGIN
  WITH expired AS (
    UPDATE public.orders
       SET status = 'cancelled', cancel_reason = 'TRANSFER_TIMEOUT'
     WHERE payment_method = 'transfer'
       AND status = 'pending'
       AND payment_status IN ('pending', 'rejected')
       AND transfer_deadline IS NOT NULL
       AND transfer_deadline < now()
    RETURNING id
  )
  SELECT count(*) INTO v_n FROM expired;
  RETURN v_n;
END;
$$;

REVOKE ALL ON FUNCTION public.cancel_expired_transfer_orders() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.cancel_expired_transfer_orders() TO service_role;

-- Programar cada minuto con pg_cron (si no está habilitada, ver docs/DESPLIEGUE_PAGOS.md)
DO $$
BEGIN
  BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_cron;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'No se pudo crear pg_cron desde SQL: habilítala en Database → Extensions.';
  END;

  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
    PERFORM cron.unschedule(jobid) FROM cron.job WHERE jobname = 'jagoo-cancel-expired-transfers';
    PERFORM cron.schedule(
      'jagoo-cancel-expired-transfers',
      '* * * * *',
      'SELECT public.cancel_expired_transfer_orders()'
    );
  ELSE
    RAISE NOTICE '⚠ pg_cron NO está habilitada: la cancelación automática no correrá hasta habilitarla y reaplicar esta migración.';
  END IF;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Receta (farmacia)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.review_prescription(
  p_order_id uuid,
  p_approve  boolean,
  p_note     text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_order public.orders%ROWTYPE;
  v_role  text := public.get_my_role();
BEGIN
  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Pedido no encontrado'; END IF;

  IF NOT (
    v_role = 'admin'
    OR EXISTS (SELECT 1 FROM public.restaurants r
                WHERE r.id = v_order.restaurant_id AND r.owner_id = auth.uid())
  ) THEN
    RAISE EXCEPTION 'No tienes permiso para revisar esta receta';
  END IF;

  IF NOT v_order.prescription_required OR v_order.prescription_status <> 'pending' THEN
    RAISE EXCEPTION 'RECETA_NO_PENDIENTE';
  END IF;
  IF v_order.status <> 'pending' THEN RAISE EXCEPTION 'PEDIDO_NO_PENDIENTE'; END IF;

  IF p_approve THEN
    UPDATE public.orders
       SET prescription_status      = 'approved',
           prescription_reviewed_by = auth.uid(),
           prescription_reviewed_at = now(),
           prescription_note        = p_note
     WHERE id = p_order_id;
  ELSE
    UPDATE public.orders
       SET prescription_status      = 'rejected',
           prescription_reviewed_by = auth.uid(),
           prescription_reviewed_at = now(),
           prescription_note        = p_note,
           status                   = 'cancelled',
           cancel_reason            = 'RECETA_RECHAZADA',
           refund_required          = (payment_method = 'transfer' AND payment_status = 'verified')
     WHERE id = p_order_id;
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.review_prescription(uuid, boolean, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.review_prescription(uuid, boolean, text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. Verificación de edad al entregar (licorería)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.confirm_age_verification(p_order_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_order public.orders%ROWTYPE;
BEGIN
  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Pedido no encontrado'; END IF;

  IF NOT (v_order.courier_id = auth.uid() OR public.get_my_role() = 'admin') THEN
    RAISE EXCEPTION 'Solo el repartidor asignado puede verificar la edad';
  END IF;
  IF NOT v_order.age_check_required THEN RAISE EXCEPTION 'EDAD_NO_REQUERIDA'; END IF;
  IF v_order.status NOT IN ('ready', 'picked_up', 'on_the_way') THEN
    RAISE EXCEPTION 'PEDIDO_NO_EN_ENTREGA';
  END IF;

  UPDATE public.orders
     SET age_verified_at = now(), age_verified_by = auth.uid()
   WHERE id = p_order_id;

  INSERT INTO public.order_events (order_id, from_status, to_status, actor_id, actor_role, note)
  VALUES (p_order_id, v_order.status, v_order.status, auth.uid(),
          public.get_my_role(), 'EDAD_VERIFICADA');
END;
$$;

REVOKE ALL ON FUNCTION public.confirm_age_verification(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.confirm_age_verification(uuid) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. Efectivo cobrado por el driver y liquidación
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.cash_collections (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id         uuid NOT NULL UNIQUE REFERENCES public.orders(id) ON DELETE CASCADE,
  driver_id        uuid REFERENCES public.profiles(id) ON DELETE SET NULL,
  amount_collected numeric(10,2) NOT NULL,   -- total cobrado al cliente
  driver_share     numeric(10,2) NOT NULL,   -- lo que se queda el driver
  due_to_platform  numeric(10,2) NOT NULL,   -- lo que debe entregar a la plataforma
  status           text NOT NULL DEFAULT 'collected' CHECK (status IN ('collected', 'settled')),
  collected_at     timestamptz NOT NULL DEFAULT now(),
  settled_at       timestamptz,
  settled_by       uuid REFERENCES public.profiles(id) ON DELETE SET NULL,
  note             text
);

CREATE INDEX IF NOT EXISTS idx_cash_collections_driver
  ON public.cash_collections (driver_id, status);

ALTER TABLE public.cash_collections ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS cash_collections_select ON public.cash_collections;
CREATE POLICY cash_collections_select ON public.cash_collections
  FOR SELECT USING (driver_id = auth.uid() OR public.get_my_role() = 'admin');
REVOKE INSERT, UPDATE, DELETE ON public.cash_collections FROM anon, authenticated;

CREATE OR REPLACE FUNCTION public.record_cash_collection()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NEW.status = 'delivered' AND OLD.status <> 'delivered' AND NEW.payment_method = 'cash' THEN
    INSERT INTO public.cash_collections
      (order_id, driver_id, amount_collected, driver_share, due_to_platform)
    VALUES
      (NEW.id, NEW.courier_id, NEW.total,
       COALESCE(NEW.driver_amount, 0), NEW.total - COALESCE(NEW.driver_amount, 0))
    ON CONFLICT (order_id) DO NOTHING;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_record_cash_collection ON public.orders;
CREATE TRIGGER trg_record_cash_collection
  AFTER UPDATE OF status ON public.orders
  FOR EACH ROW EXECUTE FUNCTION public.record_cash_collection();

CREATE OR REPLACE FUNCTION public.settle_cash_collections(p_ids uuid[], p_note text DEFAULT NULL)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_n integer;
BEGIN
  IF public.get_my_role() IS DISTINCT FROM 'admin' THEN
    RAISE EXCEPTION 'Solo administradores pueden liquidar efectivo';
  END IF;

  WITH done AS (
    UPDATE public.cash_collections
       SET status = 'settled', settled_at = now(), settled_by = auth.uid(), note = p_note
     WHERE id = ANY (p_ids) AND status = 'collected'
    RETURNING id
  )
  SELECT count(*) INTO v_n FROM done;
  RETURN v_n;
END;
$$;

REVOKE ALL ON FUNCTION public.settle_cash_collections(uuid[], text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.settle_cash_collections(uuid[], text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. RLS de pedidos y comprobantes
-- ─────────────────────────────────────────────────────────────────────────────
-- Los pedidos solo se crean con create_order(): se cierran los INSERT directos.
DO $$
DECLARE pol record;
BEGIN
  FOR pol IN
    SELECT policyname, tablename FROM pg_policies
     WHERE schemaname = 'public'
       AND ((tablename IN ('orders', 'order_items') AND cmd = 'INSERT')
         OR (tablename = 'payment_proofs' AND cmd = 'INSERT'))
  LOOP
    EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', pol.policyname, pol.tablename);
  END LOOP;
END;
$$;

CREATE POLICY orders_insert_admin ON public.orders
  FOR INSERT WITH CHECK (public.get_my_role() = 'admin');
CREATE POLICY order_items_insert_admin ON public.order_items
  FOR INSERT WITH CHECK (public.get_my_role() = 'admin');

-- El negocio solo ve pedidos de efectivo, de transferencia ya verificada, o con
-- receta por revisar. Un pedido de transferencia sin pagar no le llega.
DROP POLICY IF EXISTS orders_select ON public.orders;
CREATE POLICY orders_select ON public.orders
  FOR SELECT
  USING (
    public.get_my_role() = 'admin'
    OR customer_id = auth.uid()
    OR courier_id  = auth.uid()
    OR (
      -- el repartidor ve los pedidos listos sin asignar
      public.get_my_role() = 'repartidor'
      AND status = 'ready'
      AND courier_id IS NULL
    )
    OR (
      restaurant_id IN (SELECT id FROM public.restaurants WHERE owner_id = auth.uid())
      AND (payment_method = 'cash'
           OR payment_status = 'verified'
           OR prescription_status = 'pending')
    )
  );

-- Ítems: visibles si el pedido es visible (hereda la RLS de orders)
DROP POLICY IF EXISTS order_items_select ON public.order_items;
CREATE POLICY order_items_select ON public.order_items
  FOR SELECT
  USING (EXISTS (SELECT 1 FROM public.orders o WHERE o.id = order_items.order_id));

-- Storage: comprobantes solo en la carpeta propia
DROP POLICY IF EXISTS storage_payment_proofs_insert ON storage.objects;
CREATE POLICY storage_payment_proofs_insert
  ON storage.objects FOR INSERT
  WITH CHECK (
    bucket_id = 'payment-proofs'
    AND auth.role() = 'authenticated'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

-- Storage: bucket privado de recetas
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('prescriptions', 'prescriptions', false, 5242880,
        ARRAY['image/jpeg', 'image/png', 'image/webp'])
ON CONFLICT (id) DO UPDATE SET public = false;

DROP POLICY IF EXISTS storage_prescriptions_insert ON storage.objects;
CREATE POLICY storage_prescriptions_insert
  ON storage.objects FOR INSERT
  WITH CHECK (
    bucket_id = 'prescriptions'
    AND auth.role() = 'authenticated'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

-- Lee: el dueño de la carpeta, el admin y el negocio del pedido que la referencia
DROP POLICY IF EXISTS storage_prescriptions_select ON storage.objects;
CREATE POLICY storage_prescriptions_select
  ON storage.objects FOR SELECT
  USING (
    bucket_id = 'prescriptions'
    AND (
      (storage.foldername(name))[1] = auth.uid()::text
      OR public.get_my_role() = 'admin'
      OR EXISTS (
        SELECT 1 FROM public.orders o
          JOIN public.restaurants r ON r.id = o.restaurant_id
         WHERE o.prescription_path = storage.objects.name
           AND r.owner_id = auth.uid()
      )
    )
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. Notificaciones (texto neutro: "negocio", no "restaurante")
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.notify_order_status_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_title text;
  v_body  text;
  v_type  text := 'order_status';
BEGIN
  IF OLD.status = NEW.status
     AND OLD.payment_status IS NOT DISTINCT FROM NEW.payment_status
     AND OLD.prescription_status IS NOT DISTINCT FROM NEW.prescription_status THEN
    RETURN NEW;
  END IF;

  IF NEW.customer_id IS NULL THEN RETURN NEW; END IF;

  IF OLD.status <> NEW.status THEN
    IF NEW.status = 'cancelled' AND NEW.cancel_reason = 'TRANSFER_TIMEOUT' THEN
      v_title := '⏱ Pedido cancelado';
      v_body  := 'No recibimos el comprobante de transferencia a tiempo.';
      v_type  := 'order_cancelled';
    ELSIF NEW.status = 'cancelled' AND NEW.cancel_reason = 'RECETA_RECHAZADA' THEN
      v_title := '❌ Receta no aprobada';
      v_body  := 'El negocio no aprobó tu receta y el pedido fue cancelado.';
      v_type  := 'prescription_rejected';
    ELSE
      v_title := CASE NEW.status
        WHEN 'accepted'   THEN '📋 Pedido aceptado'
        WHEN 'preparing'  THEN '👨‍🍳 En preparación'
        WHEN 'ready'      THEN '✅ Pedido listo'
        WHEN 'picked_up'  THEN '🛵 Repartidor retiró tu pedido'
        WHEN 'on_the_way' THEN '🛵 Repartidor en camino'
        WHEN 'delivered'  THEN '🎉 ¡Pedido entregado!'
        WHEN 'cancelled'  THEN '❌ Pedido cancelado'
        ELSE                   '📦 Actualización de pedido'
      END;
      v_body := CASE NEW.status
        WHEN 'accepted'   THEN 'El negocio aceptó tu pedido.'
        WHEN 'preparing'  THEN 'El negocio está preparando tu pedido.'
        WHEN 'ready'      THEN 'Tu pedido está listo para ser recogido.'
        WHEN 'picked_up'  THEN 'Tu pedido ya está con el repartidor.'
        WHEN 'on_the_way' THEN 'Tu pedido va en camino. ¡Pronto llegará!'
        WHEN 'delivered'  THEN '¡Tu pedido fue entregado!'
        WHEN 'cancelled'  THEN 'Tu pedido fue cancelado.'
        ELSE                   'El estado de tu pedido fue actualizado.'
      END;
    END IF;
  ELSIF OLD.payment_status IS DISTINCT FROM NEW.payment_status THEN
    IF NEW.payment_status = 'verified' THEN
      v_title := '✅ Pago verificado';
      v_body  := 'Tu pago fue confirmado.';
      v_type  := 'payment_verified';
    ELSIF NEW.payment_status = 'rejected' THEN
      v_title := '⚠ Comprobante rechazado';
      v_body  := 'Sube un comprobante válido antes de que venza el plazo.';
      v_type  := 'payment_rejected';
    ELSE
      RETURN NEW;
    END IF;
  ELSE
    IF NEW.prescription_status = 'approved' THEN
      v_title := '✅ Receta aprobada';
      v_body  := 'El negocio aprobó tu receta.';
      v_type  := 'prescription_approved';
    ELSE
      RETURN NEW;
    END IF;
  END IF;

  PERFORM net.http_post(
    url     := current_setting('app.edge_function_url', true) || '/send-push-notification',
    headers := jsonb_build_object(
      'Content-Type',  'application/json',
      'Authorization', 'Bearer ' || current_setting('app.service_role_key', true)
    ),
    body    := jsonb_build_object(
      'user_id',  NEW.customer_id,
      'order_id', NEW.id,
      'type',     v_type,
      'title',    v_title,
      'body',     v_body
    )::text
  );

  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  RETURN NEW;  -- una notificación fallida no debe bloquear el pedido
END;
$$;

DROP TRIGGER IF EXISTS trg_order_status_notify ON public.orders;
CREATE TRIGGER trg_order_status_notify
  AFTER UPDATE OF status, payment_status, prescription_status ON public.orders
  FOR EACH ROW EXECUTE FUNCTION public.notify_order_status_change();

-- ─────────────────────────────────────────────────────────────────────────────
-- 11. Ganancias del driver (según el reparto de cada pedido)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.get_driver_earnings(
  p_driver_id uuid,
  p_period    text DEFAULT 'all'
)
RETURNS TABLE (
  total_deliveries bigint,
  fee_per_delivery numeric,
  total_earnings   numeric,
  period           text
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_legacy_fee numeric;
  v_from       timestamptz;
BEGIN
  IF auth.uid() IS DISTINCT FROM p_driver_id AND public.get_my_role() IS DISTINCT FROM 'admin' THEN
    RAISE EXCEPTION 'No tienes permiso para ver las ganancias de este repartidor';
  END IF;

  SELECT COALESCE((SELECT value::numeric FROM public.settings WHERE key = 'driver_fee_per_delivery'), 1.50)
    INTO v_legacy_fee;

  v_from := CASE p_period
    WHEN 'today' THEN date_trunc('day',   now())
    WHEN 'week'  THEN date_trunc('week',  now())
    WHEN 'month' THEN date_trunc('month', now())
    ELSE TIMESTAMPTZ '-infinity'
  END;

  RETURN QUERY
  SELECT COUNT(*)::bigint,
         CASE WHEN COUNT(*) > 0
              THEN round(SUM(COALESCE(o.driver_amount, v_legacy_fee)) / COUNT(*), 2)
              ELSE 0::numeric END,
         COALESCE(SUM(COALESCE(o.driver_amount, v_legacy_fee)), 0)::numeric,
         p_period
    FROM public.orders o
   WHERE o.courier_id = p_driver_id
     AND o.status = 'delivered'
     AND o.created_at >= v_from;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_driver_earnings(uuid, text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 12. Reporte por tipo de negocio (solo admin)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.admin_report_by_type(
  p_from timestamptz DEFAULT NULL,
  p_to   timestamptz DEFAULT NULL
)
RETURNS TABLE (
  business_type    public.business_type,
  orders_total     bigint,
  orders_delivered bigint,
  orders_cancelled bigint,
  gross_total      numeric,
  business_amount  numeric,
  driver_amount    numeric,
  platform_amount  numeric
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF public.get_my_role() IS DISTINCT FROM 'admin' THEN
    RAISE EXCEPTION 'Solo administradores';
  END IF;

  RETURN QUERY
  SELECT t.t,
         COUNT(o.id),
         COUNT(o.id) FILTER (WHERE o.status = 'delivered'),
         COUNT(o.id) FILTER (WHERE o.status = 'cancelled'),
         COALESCE(SUM(o.total)           FILTER (WHERE o.status = 'delivered'), 0)::numeric,
         COALESCE(SUM(o.business_amount) FILTER (WHERE o.status = 'delivered'), 0)::numeric,
         COALESCE(SUM(o.driver_amount)   FILTER (WHERE o.status = 'delivered'), 0)::numeric,
         COALESCE(SUM(o.platform_amount) FILTER (WHERE o.status = 'delivered'), 0)::numeric
    FROM unnest(enum_range(NULL::public.business_type)) AS t(t)
    LEFT JOIN public.orders o
           ON o.business_type = t.t
          AND (p_from IS NULL OR o.created_at >= p_from)
          AND (p_to   IS NULL OR o.created_at <  p_to)
   GROUP BY t.t
   ORDER BY t.t;
END;
$$;

REVOKE ALL ON FUNCTION public.admin_report_by_type(timestamptz, timestamptz) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_report_by_type(timestamptz, timestamptz) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 13. Eliminación de cuenta: archivo de tarjeta, recetas y fecha de nacimiento
--     (reemplaza la versión de la migración 19)
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
  IF p_user_id IS NULL THEN RAISE EXCEPTION 'USUARIO_NO_ENCONTRADO'; END IF;
  IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id = p_user_id) THEN
    RAISE EXCEPTION 'USUARIO_NO_ENCONTRADO';
  END IF;

  SELECT role INTO v_role FROM public.profiles WHERE id = p_user_id;
  IF v_role IS NOT NULL AND v_role <> 'customer' THEN RAISE EXCEPTION 'ROL_NO_PERMITIDO'; END IF;

  IF EXISTS (
    SELECT 1 FROM public.orders
     WHERE customer_id = p_user_id AND status NOT IN ('delivered', 'cancelled')
  ) THEN
    RAISE EXCEPTION 'PEDIDOS_EN_CURSO';
  END IF;

  UPDATE public.order_items
     SET special_instructions = ''
   WHERE order_id IN (SELECT id FROM public.orders WHERE customer_id = p_user_id);

  -- Recetas: la ruta se anula (la Edge Function borra los archivos)
  UPDATE public.orders
     SET customer_id       = NULL,
         delivery_address  = NULL,
         prescription_path = NULL,
         prescription_note = NULL
   WHERE customer_id = p_user_id;

  IF to_regclass('public.payment_proofs') IS NOT NULL THEN
    UPDATE public.payment_proofs
       SET customer_id = NULL, image_url = '[eliminado]', reference_number = NULL
     WHERE customer_id = p_user_id;
  END IF;

  IF to_regclass('public.card_payments_archive') IS NOT NULL THEN
    UPDATE public.card_payments_archive
       SET customer_id = NULL, kushki_token = '[eliminado]', kushki_response = NULL
     WHERE customer_id = p_user_id;
  END IF;

  IF to_regclass('public.ratings') IS NOT NULL THEN
    UPDATE public.ratings
       SET customer_id = NULL, restaurant_comment = NULL, courier_comment = NULL
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

  IF to_regclass('public.notification_log') IS NOT NULL THEN
    DELETE FROM public.notification_log WHERE user_id = p_user_id;
  END IF;

  DELETE FROM public.addresses WHERE user_id = p_user_id;

  -- Perfil (incluye fecha de nacimiento) y cuenta de acceso
  DELETE FROM auth.users WHERE id = p_user_id;
END;
$$;

REVOKE ALL ON FUNCTION public.delete_customer_account(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.delete_customer_account(uuid) TO service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 14. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (SELECT 1 FROM information_schema.routines
                 WHERE routine_schema = 'public' AND routine_name = 'create_order'),
    'FALLA: create_order no existe';
  ASSERT NOT has_function_privilege('anon', 'public.create_order(text,jsonb,text,uuid,text,numeric,text,text,boolean)', 'EXECUTE'),
    'FALLA: anon puede ejecutar create_order';
  ASSERT NOT has_function_privilege('authenticated', 'public.cancel_expired_transfer_orders()', 'EXECUTE'),
    'FALLA: authenticated puede ejecutar cancel_expired_transfer_orders';
  ASSERT NOT has_function_privilege('authenticated', 'public.delete_customer_account(uuid)', 'EXECUTE'),
    'FALLA: authenticated puede ejecutar delete_customer_account';
  ASSERT NOT EXISTS (
    SELECT 1 FROM pg_policies
     WHERE schemaname = 'public' AND tablename IN ('orders', 'order_items')
       AND cmd = 'INSERT' AND policyname NOT LIKE '%_admin'
  ), 'FALLA: quedan políticas INSERT abiertas en orders/order_items';
  RAISE NOTICE '✅ orders_payments aplicada';
END;
$$;


-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- >>> 20240401000023_data_retention.sql
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Retención automática de datos
--
-- Lo que hace este job es EXACTAMENTE lo que declara la Política de Privacidad
-- (web/src/pages/PrivacyPolicyPage.tsx, sección 9). Si cambias un plazo aquí,
-- cámbialo también en la política.
--
--   Dato                                   Plazo            Acción
--   ───────────────────────────────────────────────────────────────────────────
--   Foto de receta                         30 días tras     Se borra el archivo y
--                                          entregar/cancelar la ruta del pedido
--   Comprobante de transferencia (foto)    365 días         Se borra el archivo,
--                                                           se vacía la referencia
--   Datos personales del pedido            730 días         Se anonimizan (cliente,
--                                                           dirección, notas)
--   Importes, fechas y reparto             Se conservan     Obligación contable
--                                                           (REVISAR plazo legal)
--   Historial de notificaciones            90 días          Se borra
--   Última posición del repartidor         24 horas         Se borra
--
-- Los archivos de Storage no se borran por SQL: el job los pone en
-- storage_deletion_queue y la Edge Function purge-storage-queue los elimina
-- con la API de Storage.
--
-- Idempotente.
-- ══════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.storage_deletion_queue (
  id         bigserial PRIMARY KEY,
  bucket     text NOT NULL,
  path       text NOT NULL,
  queued_at  timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz,
  attempts   integer NOT NULL DEFAULT 0,
  UNIQUE (bucket, path)
);

CREATE INDEX IF NOT EXISTS idx_storage_queue_pending
  ON public.storage_deletion_queue (queued_at) WHERE deleted_at IS NULL;

ALTER TABLE public.storage_deletion_queue ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.storage_deletion_queue FROM anon, authenticated;

CREATE OR REPLACE FUNCTION public.run_data_retention()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_rx      integer := 0;
  v_proofs  integer := 0;
  v_orders  integer := 0;
  v_notifs  integer := 0;
  v_loc     integer := 0;
BEGIN
  -- 1. Recetas: 30 días después de entregar o cancelar
  WITH due AS (
    SELECT id, prescription_path FROM public.orders
     WHERE prescription_path IS NOT NULL
       AND status IN ('delivered', 'cancelled')
       AND updated_at < now() - interval '30 days'
  ), queued AS (
    INSERT INTO public.storage_deletion_queue (bucket, path)
    SELECT 'prescriptions', prescription_path FROM due
    ON CONFLICT (bucket, path) DO NOTHING
    RETURNING 1
  ), cleared AS (
    UPDATE public.orders o
       SET prescription_path = NULL, prescription_note = NULL
      FROM due WHERE o.id = due.id
    RETURNING 1
  )
  SELECT count(*) INTO v_rx FROM cleared;

  -- 2. Comprobantes: 365 días
  WITH due AS (
    SELECT id, image_url FROM public.payment_proofs
     WHERE created_at < now() - interval '365 days'
       AND image_url <> '[eliminado]'
  ), queued AS (
    INSERT INTO public.storage_deletion_queue (bucket, path)
    SELECT 'payment-proofs', image_url FROM due
     WHERE image_url NOT LIKE 'http%'
    ON CONFLICT (bucket, path) DO NOTHING
    RETURNING 1
  ), cleared AS (
    UPDATE public.payment_proofs p
       SET image_url = '[eliminado]', reference_number = NULL
      FROM due WHERE p.id = due.id
    RETURNING 1
  )
  SELECT count(*) INTO v_proofs FROM cleared;

  -- 3. Pedidos: 730 días → se anonimizan los datos personales
  WITH old_orders AS (
    SELECT id FROM public.orders
     WHERE created_at < now() - interval '730 days'
       AND status IN ('delivered', 'cancelled')
       AND (customer_id IS NOT NULL OR delivery_address IS NOT NULL)
  ), items AS (
    UPDATE public.order_items i
       SET special_instructions = ''
      FROM old_orders WHERE i.order_id = old_orders.id
    RETURNING 1
  ), anon AS (
    UPDATE public.orders o
       SET customer_id = NULL, delivery_address = NULL, prescription_note = NULL
      FROM old_orders WHERE o.id = old_orders.id
    RETURNING 1
  )
  SELECT count(*) INTO v_orders FROM anon;

  -- 4. Notificaciones: 90 días
  IF to_regclass('public.notification_log') IS NOT NULL THEN
    WITH d AS (
      DELETE FROM public.notification_log WHERE sent_at < now() - interval '90 days' RETURNING 1
    )
    SELECT count(*) INTO v_notifs FROM d;
  END IF;

  -- 5. Última posición conocida del repartidor: 24 horas
  WITH l AS (
    UPDATE public.profiles
       SET current_lat = NULL, current_lng = NULL
     WHERE current_lat IS NOT NULL
       AND (location_updated_at IS NULL OR location_updated_at < now() - interval '24 hours')
    RETURNING 1
  )
  SELECT count(*) INTO v_loc FROM l;

  RETURN jsonb_build_object(
    'prescriptions_cleared', v_rx,
    'proofs_cleared',        v_proofs,
    'orders_anonymized',     v_orders,
    'notifications_deleted', v_notifs,
    'locations_cleared',     v_loc
  );
END;
$$;

REVOKE ALL ON FUNCTION public.run_data_retention() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.run_data_retention() TO service_role;

-- Programación: retención diaria 08:00 UTC (03:00 Ecuador) y purga de archivos cada hora
DO $$
BEGIN
  BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_cron;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'No se pudo crear pg_cron desde SQL: habilítala en Database → Extensions.';
  END;

  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
    PERFORM cron.unschedule(jobid) FROM cron.job WHERE jobname = 'jagoo-data-retention';
    PERFORM cron.schedule('jagoo-data-retention', '0 8 * * *', 'SELECT public.run_data_retention()');

    -- La purga llama a la Edge Function con pg_net (mismos ajustes app.* que las notificaciones push)
    PERFORM cron.unschedule(jobid) FROM cron.job WHERE jobname = 'jagoo-purge-storage';
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_net') THEN
      PERFORM cron.schedule(
        'jagoo-purge-storage',
        '15 * * * *',
        $job$SELECT net.http_post(
               url     := current_setting('app.edge_function_url', true) || '/purge-storage-queue',
               headers := jsonb_build_object(
                 'Content-Type',  'application/json',
                 'Authorization', 'Bearer ' || current_setting('app.service_role_key', true)),
               body    := '{}'::text)$job$
      );
    ELSE
      RAISE NOTICE 'pg_net no está habilitada: programa purge-storage-queue desde el panel de Supabase.';
    END IF;
  ELSE
    RAISE NOTICE '⚠ pg_cron NO está habilitada: la retención no correrá hasta habilitarla y reaplicar esta migración.';
  END IF;
END;
$$;

DO $$
BEGIN
  ASSERT NOT has_function_privilege('authenticated', 'public.run_data_retention()', 'EXECUTE'),
    'FALLA: authenticated puede ejecutar run_data_retention';
  RAISE NOTICE '✅ data_retention aplicada';
END;
$$;

COMMIT;

select 'PASO 3 OK: parte C aplicada' as resultado;
