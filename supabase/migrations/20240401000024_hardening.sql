-- ═════════════════════════════════════════════════════════════════════════════
-- JAGOO — 24: Endurecimiento de seguridad (Fase 1)
--
-- Idempotente. No edita migraciones anteriores.
--
--   C2  change_order_status valida que el pedido sea del que llama
--   C3  triggers de notificación sin app.* ni clave de servicio en la base
--   M1  apply_coupon solo desde create_order
--   M2  validate_coupon cerrada; check_coupon con límite de intentos
--   M3  log_audit cerrada
--   M4  create_restaurant_user eliminada
--   M5  accept_order(uuid, uuid) eliminada; repartidores/device_tokens reconciliadas
--   M6  el repartidor no lee pedidos libres: usa list_available_orders()
--   M7  search_path fijo y EXECUTE solo a quien corresponde
--   A3  get_order_contact(): nombre/teléfono/vehículo reales y seguros
--   A4  no se pueden crear pedidos con el correo sin confirmar
-- ═════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. C3 — Llamar Edge Functions sin guardar la clave de servicio en la base
--
--    • URL de las funciones: tabla settings (no es secreta).
--    • Secreto compartido: Supabase Vault, nombre 'webhook_secret'. Se envía en
--      la cabecera x-webhook-secret y la función lo compara con WEBHOOK_SECRET.
--    • Si falta cualquiera de los dos, no se hace nada y NUNCA se lanza error:
--      marcar un pedido "listo" jamás debe fallar por una notificación.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO public.settings (key, value, description)
VALUES ('edge_functions_url', '',
        'URL base de las Edge Functions, ej: https://TU-PROYECTO.supabase.co/functions/v1')
ON CONFLICT (key) DO NOTHING;

CREATE OR REPLACE FUNCTION public._webhook_secret()
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE v text;
BEGIN
  EXECUTE $q$SELECT decrypted_secret FROM vault.decrypted_secrets
               WHERE name = 'webhook_secret'
               ORDER BY created_at DESC LIMIT 1$q$ INTO v;
  RETURN v;
EXCEPTION WHEN OTHERS THEN
  RETURN NULL;   -- Vault no disponible o secreto no creado
END;
$$;

CREATE OR REPLACE FUNCTION public.call_edge_function(p_fn text, p_payload jsonb DEFAULT '{}'::jsonb)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_url    text;
  v_secret text;
BEGIN
  SELECT value INTO v_url FROM public.settings WHERE key = 'edge_functions_url';
  v_secret := public._webhook_secret();

  IF coalesce(v_url, '') = '' OR coalesce(v_secret, '') = '' THEN
    RAISE WARNING 'call_edge_function(%): falta edge_functions_url o el secreto webhook_secret; no se envió', p_fn;
    RETURN;
  END IF;

  PERFORM net.http_post(
    url     := rtrim(v_url, '/') || '/' || p_fn,
    body    := coalesce(p_payload, '{}'::jsonb),
    headers := jsonb_build_object(
      'Content-Type',     'application/json',
      'x-webhook-secret', v_secret
    )
  );
EXCEPTION WHEN OTHERS THEN
  RAISE WARNING 'call_edge_function(%): %', p_fn, SQLERRM;
END;
$$;

-- Diagnóstico (sin revelar el secreto). Ejecutar en el SQL Editor.
CREATE OR REPLACE FUNCTION public.edge_config_status()
RETURNS jsonb
LANGUAGE sql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
  SELECT jsonb_build_object(
    'edge_functions_url_set', coalesce((SELECT value FROM public.settings WHERE key = 'edge_functions_url'), '') <> '',
    'webhook_secret_set',     coalesce(public._webhook_secret(), '') <> '',
    'pg_net_installed',       EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_net'),
    'pg_cron_installed',      EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron')
  );
$$;

-- Aviso a repartidores: payload mínimo, nunca la fila completa del pedido
CREATE OR REPLACE FUNCTION public.notify_repartidores_on_ready()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  IF NEW.status = 'ready' AND (OLD.status IS NULL OR OLD.status <> 'ready') THEN
    PERFORM public.call_edge_function(
      'notify-repartidores',
      jsonb_build_object(
        'type',   'UPDATE',
        'table',  'orders',
        'record', jsonb_build_object(
          'id',              NEW.id,
          'status',          NEW.status,
          'restaurant_name', NEW.restaurant_name,
          'courier_id',      NEW.courier_id
        )
      )
    );
  END IF;
  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  RAISE WARNING 'notify_repartidores_on_ready: %', SQLERRM;
  RETURN NEW;   -- nunca bloquear el cambio de estado
END;
$$;

-- Aviso al cliente (mismo cuerpo que la migración 22; solo cambia el envío)
CREATE OR REPLACE FUNCTION public.notify_order_status_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
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

  PERFORM public.call_edge_function(
    'send-push-notification',
    jsonb_build_object(
      'user_id',  NEW.customer_id,
      'order_id', NEW.id,
      'type',     v_type,
      'title',    v_title,
      'body',     v_body
    )
  );

  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  RETURN NEW;  -- una notificación fallida no debe bloquear el pedido
END;
$$;

-- La purga de archivos deja de usar app.service_role_key
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
    PERFORM cron.unschedule(jobid) FROM cron.job WHERE jobname = 'jagoo-purge-storage';
    PERFORM cron.schedule(
      'jagoo-purge-storage', '15 * * * *',
      $job$SELECT public.call_edge_function('purge-storage-queue', '{}'::jsonb)$job$
    );
  END IF;
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'No se pudo reprogramar jagoo-purge-storage: %', SQLERRM;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. C2 — change_order_status: el pedido debe ser del que llama
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.change_order_status(
  p_order_id   uuid,
  p_new_status text,
  p_note       text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_uid        uuid := auth.uid();
  v_role       text;
  v_order      public.orders%ROWTYPE;
  v_owns       boolean := false;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'SIN_PERMISO';
  END IF;

  SELECT role INTO v_role
    FROM public.profiles
   WHERE id = v_uid AND COALESCE(is_active, true);
  IF v_role IS NULL THEN
    RAISE EXCEPTION 'SIN_PERMISO';
  END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'PEDIDO_NO_ENCONTRADO';
  END IF;

  -- ¿El pedido es de quien llama, según su rol?
  v_owns := COALESCE(CASE v_role
    WHEN 'admin'      THEN true
    WHEN 'customer'   THEN v_order.customer_id = v_uid
    WHEN 'restaurant' THEN EXISTS (
                             SELECT 1 FROM public.restaurants r
                              WHERE r.id = v_order.restaurant_id AND r.owner_id = v_uid)
    WHEN 'repartidor' THEN v_order.courier_id IS NOT NULL AND v_order.courier_id = v_uid
    ELSE false
  END, false);   -- COALESCE: courier_id NULL daría NULL y NOT NULL no bloquea
  IF NOT v_owns THEN
    RAISE EXCEPTION 'SIN_PERMISO';
  END IF;

  IF v_role <> 'admin' AND NOT EXISTS (
    SELECT 1 FROM public.order_transitions
     WHERE actor_role  = v_role
       AND from_status = v_order.status
       AND to_status   = p_new_status
  ) THEN
    RAISE EXCEPTION 'Transición no permitida: % → % para rol %',
      v_order.status, p_new_status, v_role;
  END IF;

  UPDATE public.orders
     SET status = p_new_status, updated_at = now()
   WHERE id = p_order_id;

  IF p_note IS NOT NULL THEN
    INSERT INTO public.order_events(order_id, from_status, to_status, actor_id, actor_role, note)
    VALUES (p_order_id, v_order.status, p_new_status, v_uid, v_role, left(p_note, 500));
  END IF;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. M1/M2 — Cupones: solo create_order los consume; el cliente usa check_coupon
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.coupon_attempts (
  id         bigserial PRIMARY KEY,
  user_id    uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_coupon_attempts_user ON public.coupon_attempts (user_id, created_at DESC);
ALTER TABLE public.coupon_attempts ENABLE ROW LEVEL SECURITY;   -- sin políticas: nadie la lee
REVOKE ALL ON public.coupon_attempts FROM PUBLIC, anon, authenticated;
DO $$ BEGIN
  REVOKE ALL ON SEQUENCE public.coupon_attempts_id_seq FROM PUBLIC, anon, authenticated;
EXCEPTION WHEN OTHERS THEN NULL; END $$;

-- Devuelve 0 filas si el cupón no sirve (sin RAISE, para que el intento quede
-- registrado). Con 8 intentos fallidos en 10 minutos lanza DEMASIADOS_INTENTOS.
CREATE OR REPLACE FUNCTION public.check_coupon(p_code text, p_order_total numeric)
RETURNS TABLE (
  id        uuid,
  code      text,
  type      text,
  value     numeric,
  min_order numeric,
  discount  numeric
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_uid   uuid := auth.uid();
  v_fails int;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'SIN_PERMISO';
  END IF;

  DELETE FROM public.coupon_attempts ca WHERE ca.created_at < now() - interval '1 day';

  SELECT count(*) INTO v_fails
    FROM public.coupon_attempts ca
   WHERE ca.user_id = v_uid AND ca.created_at > now() - interval '10 minutes';
  IF v_fails >= 8 THEN
    RAISE EXCEPTION 'DEMASIADOS_INTENTOS';
  END IF;

  BEGIN
    RETURN QUERY SELECT * FROM public.validate_coupon(left(coalesce(p_code, ''), 64), p_order_total);
    RETURN;
  EXCEPTION WHEN OTHERS THEN
    INSERT INTO public.coupon_attempts (user_id) VALUES (v_uid);
    RETURN;
  END;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. M4/M5 — Funciones obsoletas o rotas
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT p.oid::regprocedure AS sig
             FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'public' AND p.proname = 'create_restaurant_user'
  LOOP
    EXECUTE 'DROP FUNCTION ' || r.sig;
  END LOOP;
END;
$$;

DROP FUNCTION IF EXISTS public.accept_order(uuid, uuid);

-- accept_order(uuid) pasaba el pedido de 'ready' a 'on_the_way', transición que
-- el trigger de estados NO permite al repartidor (solo ready → picked_up →
-- on_the_way → delivered). Resultado: ningún repartidor podía aceptar pedidos.
-- Ahora el pedido queda 'picked_up' y el repartidor sigue el flujo normal.
CREATE OR REPLACE FUNCTION public.accept_order(p_order_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_role text;
  v_rows int;
BEGIN
  SELECT role INTO v_role
    FROM public.profiles
   WHERE id = auth.uid() AND COALESCE(is_active, true);

  IF v_role IS NULL THEN
    RAISE EXCEPTION 'Usuario no autenticado, sin perfil o cuenta desactivada';
  END IF;
  IF v_role <> 'repartidor' THEN
    RAISE EXCEPTION 'Solo los repartidores pueden aceptar pedidos (rol actual: %)', v_role;
  END IF;

  UPDATE public.orders
     SET courier_id = auth.uid(),
         status     = 'picked_up'
   WHERE id         = p_order_id
     AND status     = 'ready'
     AND courier_id IS NULL;

  GET DIAGNOSTICS v_rows = ROW_COUNT;
  IF v_rows = 0 THEN
    RAISE EXCEPTION 'El pedido ya fue tomado por otro repartidor o no está disponible';
  END IF;

  INSERT INTO public.audit_log (user_id, action, table_name, record_id, new_data)
  VALUES (auth.uid(), 'accept_order', 'orders', p_order_id::text,
          jsonb_build_object('courier_id', auth.uid(), 'new_status', 'picked_up'));
END;
$$;

-- Datos del repartidor: el modelo vigente es profiles
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS vehicle_type text,
  ADD COLUMN IF NOT EXISTS plate        text;

DO $$
BEGIN
  IF to_regclass('public.repartidores') IS NOT NULL THEN
    EXECUTE $q$UPDATE public.profiles p
                   SET vehicle_type = COALESCE(p.vehicle_type, r.vehicle_type),
                       plate        = COALESCE(p.plate, NULLIF(r.plate, ''))
                  FROM public.repartidores r WHERE r.id = p.id$q$;
    EXECUTE $q$COMMENT ON TABLE public.repartidores IS 'OBSOLETA: usar profiles (role=repartidor). No se escribe más aquí.'$q$;
  END IF;
  IF to_regclass('public.device_tokens') IS NOT NULL THEN
    EXECUTE $q$UPDATE public.profiles p SET fcm_token = t.token
                  FROM (SELECT DISTINCT ON (user_id) user_id, token
                          FROM public.device_tokens
                         ORDER BY user_id, created_at DESC) t
                 WHERE t.user_id = p.id AND p.fcm_token IS NULL$q$;
    EXECUTE $q$COMMENT ON TABLE public.device_tokens IS 'OBSOLETA: el token vive en profiles.fcm_token.'$q$;
  END IF;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. A3 — Teléfono del negocio y contacto del pedido
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE public.restaurants ADD COLUMN IF NOT EXISTS phone text;

-- Solo devuelve números marcables; los de relleno o vacíos salen como NULL
CREATE OR REPLACE FUNCTION public._clean_phone(p text)
RETURNS text
LANGUAGE sql
IMMUTABLE
SET search_path = public, pg_temp
AS $$
  SELECT CASE
    WHEN p IS NULL THEN NULL
    WHEN regexp_replace(p, '\D', '', 'g') !~ '^\d{7,15}$' THEN NULL
    WHEN regexp_replace(p, '\D', '', 'g') IN ('593999998888', '593991112222', '0999998888') THEN NULL
    ELSE btrim(p)
  END;
$$;

CREATE OR REPLACE FUNCTION public.get_order_contact(p_order_id uuid)
RETURNS TABLE (
  restaurant_phone text,
  customer_name    text,
  customer_phone   text,
  courier_name     text,
  courier_vehicle  text,
  courier_plate    text
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_uid    uuid := auth.uid();
  v_role   text;
  v_order  public.orders%ROWTYPE;
  v_owner  uuid;
  v_rphone text;
  v_is_admin boolean;
  v_is_cust  boolean;
  v_is_rest  boolean;
  v_is_cour  boolean;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'SIN_PERMISO'; END IF;

  SELECT role INTO v_role FROM public.profiles WHERE id = v_uid AND COALESCE(is_active, true);
  IF v_role IS NULL THEN RAISE EXCEPTION 'SIN_PERMISO'; END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'SIN_PERMISO'; END IF;

  SELECT r.owner_id, public._clean_phone(r.phone) INTO v_owner, v_rphone
    FROM public.restaurants r WHERE r.id = v_order.restaurant_id;

  v_is_admin := v_role = 'admin';
  v_is_cust  := v_order.customer_id = v_uid;
  v_is_rest  := v_owner IS NOT NULL AND v_owner = v_uid;
  v_is_cour  := v_order.courier_id IS NOT NULL AND v_order.courier_id = v_uid;

  IF NOT (v_is_admin OR v_is_cust OR v_is_rest OR v_is_cour) THEN
    RAISE EXCEPTION 'SIN_PERMISO';
  END IF;

  -- Pedido terminado: no se exponen más datos personales
  IF v_order.status IN ('delivered', 'cancelled') AND NOT v_is_admin THEN
    RETURN QUERY SELECT NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text;
    RETURN;
  END IF;

  IF v_rphone IS NULL THEN
    SELECT public._clean_phone(pr.phone) INTO v_rphone FROM public.profiles pr WHERE pr.id = v_owner;
  END IF;

  RETURN QUERY
  SELECT
    v_rphone,
    cu.full_name,
    public._clean_phone(cu.phone),
    co.full_name,
    co.vehicle_type,
    co.plate
  FROM (SELECT 1) x
  LEFT JOIN public.profiles cu ON cu.id = v_order.customer_id
  LEFT JOIN public.profiles co ON co.id = v_order.courier_id;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. M6 — El repartidor no lee pedidos libres; ve una lista mínima
-- ─────────────────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS orders_select ON public.orders;
CREATE POLICY orders_select ON public.orders
  FOR SELECT
  USING (
    public.get_my_role() = 'admin'
    OR customer_id = auth.uid()
    OR courier_id  = auth.uid()
    OR (
      restaurant_id IN (SELECT id FROM public.restaurants WHERE owner_id = auth.uid())
      AND (payment_method = 'cash'
           OR payment_status = 'verified'
           OR prescription_status = 'pending')
    )
  );

-- Sin dirección, sin cliente y sin total hasta aceptar el pedido
CREATE OR REPLACE FUNCTION public.list_available_orders()
RETURNS TABLE (
  id                 uuid,
  restaurant_name    text,
  business_type      text,
  delivery_zone_name text,
  payment_method     text,
  driver_amount      numeric,
  age_check_required boolean,
  created_at         timestamptz
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.profiles p
     WHERE p.id = auth.uid()
       AND p.role = 'repartidor'
       AND COALESCE(p.is_active, true)
       AND p.is_available
  ) THEN
    RETURN;   -- 0 filas: no es repartidor activo y disponible
  END IF;

  RETURN QUERY
  SELECT o.id, o.restaurant_name, o.business_type::text, o.delivery_zone_name,
         o.payment_method, o.driver_amount, o.age_check_required, o.created_at
    FROM public.orders o
   WHERE o.status = 'ready' AND o.courier_id IS NULL
   ORDER BY o.created_at ASC
   LIMIT 50;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. A4 — Sin correo confirmado no hay pedidos (defensa en profundidad)
--    La verificación de correo se activa en Authentication → Providers → Email
--    → "Confirm email" (no se puede cambiar por SQL).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.require_confirmed_email()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM auth.users u
     WHERE u.id = NEW.customer_id
       AND (u.email_confirmed_at IS NOT NULL OR u.phone_confirmed_at IS NOT NULL)
  ) THEN
    RAISE EXCEPTION 'EMAIL_NO_CONFIRMADO';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_orders_require_email ON public.orders;
CREATE TRIGGER trg_orders_require_email
  BEFORE INSERT ON public.orders
  FOR EACH ROW EXECUTE FUNCTION public.require_confirmed_email();

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. M7 — search_path fijo y permisos de ejecución por lista blanca
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
  r        record;
  v_auth   text[] := ARRAY[
    'accept_order', 'admin_report_by_type', 'change_order_status', 'check_coupon',
    'confirm_age_verification', 'create_order', 'get_driver_earnings',
    'get_driver_location', 'get_effective_min_order', 'get_my_restaurant_id',
    'get_my_role', 'get_order_contact', 'get_order_rating', 'get_split_pcts',
    'is_admin', 'is_adult', 'list_available_orders', 'review_prescription',
    'set_birth_date', 'settle_cash_collections', 'submit_rating', 'toggle_favorite',
    'update_driver_location', 'upload_payment_proof', 'verify_payment_proof'
  ];
  v_anon   text[] := ARRAY['get_my_role', 'is_admin', 'get_my_restaurant_id'];
BEGIN
  FOR r IN
    SELECT p.oid::regprocedure AS sig, p.proname,
           (p.prorettype = 'pg_catalog.trigger'::regtype) AS is_trigger,
           (p.proconfig IS NULL OR NOT EXISTS (
              SELECT 1 FROM unnest(p.proconfig) c WHERE c LIKE 'search_path=%')) AS needs_path
      FROM pg_proc p
      JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE n.nspname = 'public'
       AND p.prokind = 'f'
       AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = p.oid AND d.deptype = 'e')
  LOOP
    IF r.needs_path THEN
      BEGIN
        EXECUTE format('ALTER FUNCTION %s SET search_path = public, pg_temp', r.sig);
      EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'search_path no aplicado a %: %', r.sig, SQLERRM;
      END;
    END IF;

    BEGIN
      EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC, anon, authenticated', r.sig);
      EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO service_role', r.sig);
      IF NOT r.is_trigger THEN
        IF r.proname = ANY (v_auth) THEN
          EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated', r.sig);
        END IF;
        IF r.proname = ANY (v_anon) THEN
          EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO anon', r.sig);
        END IF;
      END IF;
    EXCEPTION WHEN OTHERS THEN
      RAISE NOTICE 'permisos no ajustados en %: %', r.sig, SQLERRM;
    END;
  END LOOP;

  -- Funciones futuras: cerradas por defecto
  BEGIN
    ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC, anon, authenticated;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'default privileges no ajustados: %', SQLERRM;
  END;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT NOT has_function_privilege('anon', 'public.apply_coupon(text, uuid)', 'EXECUTE'),
    'FALLA: anon ejecuta apply_coupon';
  ASSERT NOT has_function_privilege('authenticated', 'public.apply_coupon(text, uuid)', 'EXECUTE'),
    'FALLA: authenticated ejecuta apply_coupon';
  ASSERT NOT has_function_privilege('anon', 'public.validate_coupon(text, numeric)', 'EXECUTE'),
    'FALLA: anon ejecuta validate_coupon';
  ASSERT NOT has_function_privilege('authenticated', 'public.validate_coupon(text, numeric)', 'EXECUTE'),
    'FALLA: authenticated ejecuta validate_coupon';
  ASSERT has_function_privilege('authenticated', 'public.check_coupon(text, numeric)', 'EXECUTE'),
    'FALLA: authenticated no ejecuta check_coupon';
  ASSERT NOT has_function_privilege('anon', 'public.check_coupon(text, numeric)', 'EXECUTE'),
    'FALLA: anon ejecuta check_coupon';
  ASSERT NOT has_function_privilege('authenticated', 'public.log_audit(text, text, text, jsonb, jsonb)', 'EXECUTE'),
    'FALLA: authenticated ejecuta log_audit';
  ASSERT NOT EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                      WHERE n.nspname = 'public' AND p.proname = 'create_restaurant_user'),
    'FALLA: create_restaurant_user sigue existiendo';
  ASSERT NOT has_function_privilege('anon', 'public.call_edge_function(text, jsonb)', 'EXECUTE'),
    'FALLA: anon ejecuta call_edge_function';
  ASSERT NOT has_function_privilege('authenticated', 'public.call_edge_function(text, jsonb)', 'EXECUTE'),
    'FALLA: authenticated ejecuta call_edge_function';
  RAISE NOTICE '✅ hardening aplicada. Estado de las notificaciones: %', public.edge_config_status();
END;
$$;
