-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — GPS Tracking: ubicación en tiempo real del repartidor
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Columnas de ubicación en profiles del repartidor
--    lat/lng actualizadas cada ~5 segundos por la app Driver.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE profiles
  ADD COLUMN IF NOT EXISTS current_lat  double precision,
  ADD COLUMN IF NOT EXISTS current_lng  double precision,
  ADD COLUMN IF NOT EXISTS location_updated_at timestamptz;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. RPC update_driver_location(p_lat, p_lng)
--    Solo el propio repartidor puede actualizar su posición.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_driver_location(
  p_lat double precision,
  p_lng double precision
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  UPDATE profiles
  SET
    current_lat          = p_lat,
    current_lng          = p_lng,
    location_updated_at  = now()
  WHERE id = auth.uid()
    AND role = 'repartidor';

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Solo los repartidores pueden actualizar su ubicación';
  END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION update_driver_location(double precision, double precision) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC get_driver_location(p_order_id)
--    El cliente obtiene la ubicación del repartidor de su pedido activo.
--    Solo el dueño del pedido puede consultar.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION get_driver_location(p_order_id uuid)
RETURNS TABLE (
  lat                double precision,
  lng                double precision,
  location_updated_at timestamptz,
  courier_name       text
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_customer_id uuid;
  v_courier_id  uuid;
BEGIN
  -- Verificar que el pedido pertenece al cliente
  SELECT customer_id, courier_id
  INTO   v_customer_id, v_courier_id
  FROM   orders
  WHERE  id = p_order_id;

  IF v_customer_id IS NULL THEN
    RAISE EXCEPTION 'Pedido no encontrado';
  END IF;

  IF v_customer_id <> auth.uid() THEN
    RAISE EXCEPTION 'No tienes permiso para ver esta información';
  END IF;

  IF v_courier_id IS NULL THEN
    RETURN; -- Sin repartidor asignado aún
  END IF;

  RETURN QUERY
  SELECT
    p.current_lat,
    p.current_lng,
    p.location_updated_at,
    p.full_name
  FROM profiles p
  WHERE p.id = v_courier_id;
END;
$$;

GRANT EXECUTE ON FUNCTION get_driver_location(uuid) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Política RLS para que el cliente pueda ver lat/lng del repartidor asignado
-- ─────────────────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS profiles_location_select ON profiles;
CREATE POLICY profiles_location_select ON profiles
  FOR SELECT
  USING (
    -- Propio perfil
    id = auth.uid()
    -- O es admin
    OR EXISTS (SELECT 1 FROM profiles p2 WHERE p2.id = auth.uid() AND p2.role = 'admin')
    -- O es repartidor asignado a un pedido del cliente autenticado
    OR EXISTS (
      SELECT 1 FROM orders o
      WHERE o.courier_id = profiles.id
        AND o.customer_id = auth.uid()
        AND o.status IN ('on_the_way', 'ready', 'picked_up')
    )
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'profiles' AND column_name = 'current_lat'
  ), 'FALLA: profiles.current_lat no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines
    WHERE routine_name = 'update_driver_location'
  ), 'FALLA: función update_driver_location no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines
    WHERE routine_name = 'get_driver_location'
  ), 'FALLA: función get_driver_location no existe';

  RAISE NOTICE '✅ FASE GPS: todas las verificaciones pasaron';
END;
$$;
