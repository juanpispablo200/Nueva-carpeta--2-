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
