-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Calificaciones y reseñas
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Tabla ratings — una calificación por pedido entregado
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ratings (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id        uuid        NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
  customer_id     uuid        NOT NULL REFERENCES profiles(id),
  restaurant_id   text        NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
  courier_id      uuid        REFERENCES profiles(id),
  -- Estrellas: 1-5
  restaurant_stars integer    NOT NULL CHECK (restaurant_stars BETWEEN 1 AND 5),
  courier_stars    integer    CHECK (courier_stars BETWEEN 1 AND 5),
  -- Texto opcional
  restaurant_comment text,
  courier_comment    text,
  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ratings_restaurant ON ratings(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_ratings_customer   ON ratings(customer_id);
CREATE INDEX IF NOT EXISTS idx_ratings_courier    ON ratings(courier_id);

ALTER TABLE ratings ENABLE ROW LEVEL SECURITY;

-- Cliente crea y lee sus propias calificaciones
DROP POLICY IF EXISTS ratings_select ON ratings;
CREATE POLICY ratings_select ON ratings
  FOR SELECT
  USING (
    customer_id = auth.uid()
    OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role IN ('admin','restaurant'))
  );

DROP POLICY IF EXISTS ratings_insert ON ratings;
CREATE POLICY ratings_insert ON ratings
  FOR INSERT
  WITH CHECK (customer_id = auth.uid());

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Vista restaurants_avg_rating — promedio visible para la app
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW restaurants_avg_rating AS
SELECT
  restaurant_id,
  ROUND(AVG(restaurant_stars)::numeric, 1) AS avg_stars,
  COUNT(*)                                  AS total_ratings
FROM ratings
GROUP BY restaurant_id;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC submit_rating — cliente califica el pedido
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION submit_rating(
  p_order_id           uuid,
  p_restaurant_stars   integer,
  p_courier_stars      integer  DEFAULT NULL,
  p_restaurant_comment text     DEFAULT NULL,
  p_courier_comment    text     DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_customer_id   uuid;
  v_restaurant_id text;
  v_courier_id    uuid;
  v_status        text;
  v_rating_id     uuid;
BEGIN
  -- Verificar que el pedido es del cliente y está entregado
  SELECT customer_id, restaurant_id, courier_id, status
  INTO   v_customer_id, v_restaurant_id, v_courier_id, v_status
  FROM   orders WHERE id = p_order_id;

  IF v_customer_id IS NULL THEN
    RAISE EXCEPTION 'Pedido no encontrado';
  END IF;
  IF v_customer_id <> auth.uid() THEN
    RAISE EXCEPTION 'No puedes calificar este pedido';
  END IF;
  IF v_status <> 'delivered' THEN
    RAISE EXCEPTION 'Solo puedes calificar pedidos entregados';
  END IF;

  INSERT INTO ratings (
    order_id, customer_id, restaurant_id, courier_id,
    restaurant_stars, courier_stars,
    restaurant_comment, courier_comment
  )
  VALUES (
    p_order_id, auth.uid(), v_restaurant_id, v_courier_id,
    p_restaurant_stars, p_courier_stars,
    p_restaurant_comment, p_courier_comment
  )
  ON CONFLICT (order_id) DO UPDATE
    SET restaurant_stars   = EXCLUDED.restaurant_stars,
        courier_stars      = EXCLUDED.courier_stars,
        restaurant_comment = EXCLUDED.restaurant_comment,
        courier_comment    = EXCLUDED.courier_comment
  RETURNING id INTO v_rating_id;

  RETURN v_rating_id;
END;
$$;

GRANT EXECUTE ON FUNCTION submit_rating(uuid, integer, integer, text, text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. RPC get_order_rating(p_order_id) — obtiene la calificación de un pedido
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION get_order_rating(p_order_id uuid)
RETURNS TABLE (
  restaurant_stars   integer,
  courier_stars      integer,
  restaurant_comment text,
  courier_comment    text
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT restaurant_stars, courier_stars, restaurant_comment, courier_comment
  FROM   ratings
  WHERE  order_id    = p_order_id
    AND  customer_id = auth.uid();
$$;

GRANT EXECUTE ON FUNCTION get_order_rating(uuid) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_name = 'ratings'
  ), 'FALLA: tabla ratings no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.routines WHERE routine_name = 'submit_rating'
  ), 'FALLA: función submit_rating no existe';

  RAISE NOTICE '✅ FASE ratings: todas las verificaciones pasaron';
END;
$$;
