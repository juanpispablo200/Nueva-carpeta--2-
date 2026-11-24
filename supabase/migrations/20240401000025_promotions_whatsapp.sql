-- ═════════════════════════════════════════════════════════════════════════════
-- JAGOO — 25: Promociones por negocio y pedido por WhatsApp (Fase 4)
--
-- Idempotente. No edita migraciones anteriores.
--
--   • promotions: cada negocio gestiona las suyas (porcentaje, fechas, activa).
--   • El descuento lo calcula create_order en el servidor y lo paga el negocio
--     (el reparto de driver/negocio/plataforma se hace sobre el subtotal ya con promo).
--   • list_active_promotions(): datos reales para el banner del home.
--   • get_restaurant_whatsapp(): número para el pedido alternativo por WhatsApp.
--     No se entrega para licorerías ni farmacias (saltaría la verificación de edad).
-- ═════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Tabla promotions y RLS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.promotions (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id    text NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
  title            text NOT NULL,
  discount_percent integer NOT NULL,
  starts_at        timestamptz NOT NULL DEFAULT now(),
  ends_at          timestamptz NOT NULL,
  is_active        boolean NOT NULL DEFAULT true,
  created_at       timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT promotions_title_ck   CHECK (char_length(btrim(title)) BETWEEN 3 AND 60),
  CONSTRAINT promotions_percent_ck CHECK (discount_percent BETWEEN 1 AND 70),
  CONSTRAINT promotions_dates_ck   CHECK (ends_at > starts_at)
);

CREATE INDEX IF NOT EXISTS idx_promotions_restaurant ON public.promotions (restaurant_id);
CREATE INDEX IF NOT EXISTS idx_promotions_active
  ON public.promotions (ends_at) WHERE is_active;

ALTER TABLE public.promotions ENABLE ROW LEVEL SECURITY;

-- El cliente NO lee la tabla: usa list_active_promotions() / get_restaurant_promo().
DROP POLICY IF EXISTS promotions_select ON public.promotions;
CREATE POLICY promotions_select ON public.promotions
  FOR SELECT USING (
    public.get_my_role() = 'admin'
    OR restaurant_id IN (SELECT id FROM public.restaurants WHERE owner_id = auth.uid())
  );

DROP POLICY IF EXISTS promotions_write ON public.promotions;
CREATE POLICY promotions_write ON public.promotions
  FOR ALL
  USING (
    public.get_my_role() = 'admin'
    OR restaurant_id IN (SELECT id FROM public.restaurants WHERE owner_id = auth.uid())
  )
  WITH CHECK (
    public.get_my_role() = 'admin'
    OR restaurant_id IN (SELECT id FROM public.restaurants WHERE owner_id = auth.uid())
  );

REVOKE ALL ON public.promotions FROM PUBLIC, anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.promotions TO authenticated;

-- Un negocio no puede mover una promoción a otro negocio
CREATE OR REPLACE FUNCTION public.guard_promotion_columns()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
  IF NEW.restaurant_id IS DISTINCT FROM OLD.restaurant_id THEN
    RAISE EXCEPTION 'PROMO_NEGOCIO_NO_CAMBIA';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_promotion_columns ON public.promotions;
CREATE TRIGGER trg_guard_promotion_columns
  BEFORE UPDATE ON public.promotions
  FOR EACH ROW EXECUTE FUNCTION public.guard_promotion_columns();

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Lecturas para la app del cliente (solo promociones vigentes)
-- ─────────────────────────────────────────────────────────────────────────────
-- Mayor porcentaje vigente de un negocio (0 si no hay). Interna: la usa create_order.
CREATE OR REPLACE FUNCTION public._active_promo_percent(p_restaurant_id text)
RETURNS integer
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
  SELECT COALESCE(MAX(p.discount_percent), 0)::integer
    FROM public.promotions p
    JOIN public.restaurants r ON r.id = p.restaurant_id
   WHERE p.restaurant_id = p_restaurant_id
     AND p.is_active AND r.is_active
     AND now() >= p.starts_at AND now() < p.ends_at;
$$;

CREATE OR REPLACE FUNCTION public.get_restaurant_promo(p_restaurant_id text)
RETURNS TABLE (title text, discount_percent integer, ends_at timestamptz)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
  SELECT p.title, p.discount_percent, p.ends_at
    FROM public.promotions p
    JOIN public.restaurants r ON r.id = p.restaurant_id
   WHERE p.restaurant_id = p_restaurant_id
     AND p.is_active AND r.is_active
     AND now() >= p.starts_at AND now() < p.ends_at
     AND auth.uid() IS NOT NULL
   ORDER BY p.discount_percent DESC
   LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public.list_active_promotions()
RETURNS TABLE (
  id               uuid,
  restaurant_id    text,
  restaurant_name  text,
  business_type    text,
  logo_url         text,
  cover_url        text,
  title            text,
  discount_percent integer,
  ends_at          timestamptz
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
  SELECT p.id, r.id, r.name, r.business_type::text, r.logo_url, r.cover_url,
         p.title, p.discount_percent, p.ends_at
    FROM public.promotions p
    JOIN public.restaurants r ON r.id = p.restaurant_id
   WHERE p.is_active AND r.is_active
     AND now() >= p.starts_at AND now() < p.ends_at
     AND auth.uid() IS NOT NULL
   ORDER BY p.discount_percent DESC, p.ends_at ASC
   LIMIT 20;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. WhatsApp: número del negocio en formato internacional (solo móviles de Ecuador)
--    NULL para licorería y farmacia: ahí el pedido debe pasar por la app
--    (verificación de edad y receta).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.get_restaurant_whatsapp(p_restaurant_id text)
RETURNS text
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_r      public.restaurants%ROWTYPE;
  v_phone  text;
  v_digits text;
BEGIN
  IF auth.uid() IS NULL THEN RETURN NULL; END IF;

  SELECT * INTO v_r FROM public.restaurants WHERE id = p_restaurant_id AND is_active;
  IF NOT FOUND THEN RETURN NULL; END IF;
  IF v_r.business_type IN ('licoreria', 'farmacia') THEN RETURN NULL; END IF;

  v_phone := public._clean_phone(v_r.phone);
  IF v_phone IS NULL AND v_r.owner_id IS NOT NULL THEN
    SELECT public._clean_phone(pr.phone) INTO v_phone FROM public.profiles pr WHERE pr.id = v_r.owner_id;
  END IF;
  IF v_phone IS NULL THEN RETURN NULL; END IF;

  v_digits := regexp_replace(v_phone, '\D', '', 'g');
  IF v_digits ~ '^09\d{8}$'   THEN v_digits := '593' || substr(v_digits, 2); END IF;
  IF v_digits ~ '^5939\d{8}$' THEN RETURN v_digits; END IF;
  RETURN NULL;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. create_order: aplica la promoción vigente (copia de la 22 con 5 cambios)
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
  v_promo        numeric(10,2) := 0;
  v_promo_pct    integer := 0;
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

  -- Promoción vigente del negocio (la fija y la paga el negocio; se calcula en el servidor)
  v_promo_pct := public._active_promo_percent(p_restaurant_id);
  IF v_promo_pct > 0 THEN
    v_promo := round(v_subtotal * v_promo_pct / 100, 2);
  END IF;

  -- Cupón (se valida en el servidor, sobre el total ya con la promoción)
  IF p_coupon_code IS NOT NULL AND length(trim(p_coupon_code)) > 0 THEN
    SELECT c.discount INTO v_discount FROM public.validate_coupon(trim(p_coupon_code), v_subtotal - v_promo + v_fee) c;
  END IF;

  v_total := GREATEST(v_subtotal - v_promo + v_fee - COALESCE(v_discount, 0), 0);

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
  v_biz_amt  := round((v_subtotal - v_promo) * v_pct.pct_negocio / 100, 2);
  v_drv_amt  := round((v_subtotal - v_promo) * v_pct.pct_driver  / 100, 2) + v_fee;
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
    v_subtotal, v_fee, v_promo + COALESCE(v_discount, 0), v_total, 'pending', p_delivery_address,
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
    'discount',          v_promo + COALESCE(v_discount, 0),
    'promo_discount',    v_promo,
    'total',             v_total,
    'change_due',        v_change,
    'transfer_deadline', v_deadline,
    'age_check_required', v_age_req,
    'prescription_required', v_rx_req
  );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Permisos (lista blanca, igual que en la 24)
-- ─────────────────────────────────────────────────────────────────────────────
REVOKE ALL ON FUNCTION public._active_promo_percent(text)        FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.guard_promotion_columns()          FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.get_restaurant_promo(text)         FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.list_active_promotions()           FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_restaurant_whatsapp(text)      FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.create_order(text, jsonb, text, uuid, text, numeric, text, text, boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public._active_promo_percent(text)     TO service_role;
GRANT EXECUTE ON FUNCTION public.guard_promotion_columns()       TO service_role;
GRANT EXECUTE ON FUNCTION public.get_restaurant_promo(text)      TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_active_promotions()        TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_restaurant_whatsapp(text)   TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_order(text, jsonb, text, uuid, text, numeric, text, text, boolean) TO authenticated, service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT has_function_privilege('authenticated', 'public.list_active_promotions()', 'EXECUTE'),
    'FALLA: authenticated no ejecuta list_active_promotions';
  ASSERT NOT has_function_privilege('anon', 'public.list_active_promotions()', 'EXECUTE'),
    'FALLA: anon ejecuta list_active_promotions';
  ASSERT NOT has_function_privilege('authenticated', 'public._active_promo_percent(text)', 'EXECUTE'),
    'FALLA: authenticated ejecuta _active_promo_percent';
  ASSERT NOT has_table_privilege('anon', 'public.promotions', 'SELECT'),
    'FALLA: anon lee promotions';
  RAISE NOTICE '✅ promociones y WhatsApp aplicados';
END;
$$;
