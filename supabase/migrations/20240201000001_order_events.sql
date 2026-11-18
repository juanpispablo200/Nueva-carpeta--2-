-- ============================================================
-- order_events: auditoría de cada transición de estado
-- ============================================================
SET client_encoding = 'UTF8';

CREATE TABLE IF NOT EXISTS public.order_events (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL REFERENCES public.orders(id) ON DELETE CASCADE,
    from_status TEXT,
    to_status   TEXT        NOT NULL,
    actor_id    UUID        REFERENCES public.profiles(id) ON DELETE SET NULL,
    actor_role  TEXT,
    note        TEXT,                        -- motivo de cancelación, etc.
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_order_events_order ON public.order_events(order_id);
CREATE INDEX IF NOT EXISTS idx_order_events_actor ON public.order_events(actor_id);

-- ── Transiciones válidas por rol ──────────────────────────────────────────────
-- Tabla de referencia: qué estados puede alcanzar cada rol
CREATE TABLE IF NOT EXISTS public.order_transitions (
    actor_role  TEXT NOT NULL,
    from_status TEXT NOT NULL,
    to_status   TEXT NOT NULL,
    PRIMARY KEY (actor_role, from_status, to_status)
);

INSERT INTO public.order_transitions VALUES
-- customer
('customer',    'pending',    'cancelled'),
-- restaurant
('restaurant',  'pending',    'accepted'),
('restaurant',  'accepted',   'preparing'),
('restaurant',  'preparing',  'ready'),
('restaurant',  'pending',    'cancelled'),
('restaurant',  'accepted',   'cancelled'),
('restaurant',  'preparing',  'cancelled'),
-- repartidor
('repartidor',  'ready',      'picked_up'),
('repartidor',  'picked_up',  'on_the_way'),
('repartidor',  'on_the_way', 'delivered'),
-- admin puede cualquier transición definida arriba o extra
('admin',       'pending',    'accepted'),
('admin',       'pending',    'cancelled'),
('admin',       'accepted',   'preparing'),
('admin',       'accepted',   'cancelled'),
('admin',       'preparing',  'ready'),
('admin',       'preparing',  'cancelled'),
('admin',       'ready',      'picked_up'),
('admin',       'ready',      'cancelled'),
('admin',       'picked_up',  'on_the_way'),
('admin',       'on_the_way', 'delivered'),
('admin',       'on_the_way', 'cancelled'),
('admin',       'delivered',  'cancelled')
ON CONFLICT DO NOTHING;

-- ── Función: valida transición ANTES de aplicarla ─────────────────────────────
CREATE OR REPLACE FUNCTION public.validate_order_transition()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_role TEXT;
BEGIN
    -- Sin cambio de status → no hacer nada
    IF NEW.status = OLD.status THEN RETURN NEW; END IF;

    -- Leer rol del actor actual
    SELECT role INTO v_role FROM public.profiles WHERE id = auth.uid();

    -- admin puede todo sin restricción adicional
    IF v_role = 'admin' THEN RETURN NEW; END IF;

    -- Validar transición en la tabla
    IF NOT EXISTS (
        SELECT 1 FROM public.order_transitions
        WHERE actor_role  = v_role
          AND from_status = OLD.status
          AND to_status   = NEW.status
    ) THEN
        RAISE EXCEPTION 'Transición no permitida: % → % para rol %',
            OLD.status, NEW.status, COALESCE(v_role,'anónimo');
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_order_transition ON public.orders;
CREATE TRIGGER trg_validate_order_transition
    BEFORE UPDATE OF status ON public.orders
    FOR EACH ROW
    EXECUTE FUNCTION public.validate_order_transition();

-- ── Función: registra el evento DESPUÉS de aplicarlo ─────────────────────────
CREATE OR REPLACE FUNCTION public.record_order_event()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_role TEXT;
BEGIN
    IF NEW.status = OLD.status THEN RETURN NEW; END IF;
    SELECT role INTO v_role FROM public.profiles WHERE id = auth.uid();
    INSERT INTO public.order_events(order_id, from_status, to_status, actor_id, actor_role)
    VALUES (NEW.id, OLD.status, NEW.status, auth.uid(), v_role);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_record_order_event ON public.orders;
CREATE TRIGGER trg_record_order_event
    AFTER UPDATE OF status ON public.orders
    FOR EACH ROW
    EXECUTE FUNCTION public.record_order_event();

-- ── RLS order_events ─────────────────────────────────────────────────────────
ALTER TABLE public.order_events  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.order_transitions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "order_events_select" ON public.order_events FOR SELECT
    USING (
        -- cliente: sus pedidos
        EXISTS (SELECT 1 FROM public.orders o
                WHERE o.id = order_id AND o.customer_id = auth.uid())
        -- restaurante: pedidos de su local
        OR EXISTS (SELECT 1 FROM public.orders o
                   WHERE o.id = order_id
                     AND o.restaurant_id = public.get_my_restaurant_id())
        -- repartidor: pedidos asignados a él
        OR EXISTS (SELECT 1 FROM public.orders o
                   WHERE o.id = order_id AND o.repartidor_id = auth.uid())
        -- admin: todo
        OR public.get_my_role() = 'admin'
    );

-- order_transitions es de solo lectura para todos
CREATE POLICY "order_transitions_read" ON public.order_transitions FOR SELECT
    USING (TRUE);

-- Realtime en order_events
ALTER PUBLICATION supabase_realtime ADD TABLE public.order_events;
