-- ============================================================================
-- JAGOO — TAREA 1: SQL COMPLETO, IDEMPOTENTE Y CORREGIDO PARA REPARTIDORES
-- ============================================================================
-- Tabla elegida para is_available: PROFILES (profiles.is_available)
-- Columna elegida para el repartidor en orders: COURIER_ID (orders.courier_id)
-- ============================================================================

-- ── 1. RESPALDO DE ORDERS Y ESTANDARIZACIÓN DE COLUMNA COURIER_ID ───────────
DO $$
BEGIN
    -- Crear respaldo de seguridad si existe repartidor_id
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'orders' AND column_name = 'repartidor_id'
    ) THEN
        EXECUTE 'CREATE TABLE IF NOT EXISTS public.orders_backup_repartidor_id AS SELECT id, repartidor_id, courier_id FROM public.orders;';
    END IF;

    -- Agregar courier_id si no existe
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'orders' AND column_name = 'courier_id'
    ) THEN
        ALTER TABLE public.orders ADD COLUMN courier_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL;
    END IF;

    -- Migrar datos de repartidor_id a courier_id si existía
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'orders' AND column_name = 'repartidor_id'
    ) THEN
        UPDATE public.orders SET courier_id = repartidor_id WHERE courier_id IS NULL AND repartidor_id IS NOT NULL;
        ALTER TABLE public.orders DROP COLUMN repartidor_id;
    END IF;

    -- Agregar is_available en profiles si no existe
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'profiles' AND column_name = 'is_available'
    ) THEN
        ALTER TABLE public.profiles ADD COLUMN is_available BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    -- Agregar is_active en profiles si no existe
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'profiles' AND column_name = 'is_active'
    ) THEN
        ALTER TABLE public.profiles ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_orders_courier_id ON public.orders(courier_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON public.orders(status);

-- ── 2. TABLA DEVICE_TOKENS (para notificaciones Push) ──────────────────────
CREATE TABLE IF NOT EXISTS public.device_tokens (
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    token TEXT NOT NULL,
    platform TEXT DEFAULT 'android',
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (user_id, token)
);

ALTER TABLE public.device_tokens ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "users_own_device_tokens" ON public.device_tokens;
CREATE POLICY "users_own_device_tokens"
ON public.device_tokens FOR ALL
USING (user_id = auth.uid())
WITH CHECK (user_id = auth.uid());

-- ── 3. TABLA ORDER_DECLINES (para ignorar/rechazar ofertas) ────────────────
CREATE TABLE IF NOT EXISTS public.order_declines (
    order_id UUID NOT NULL REFERENCES public.orders(id) ON DELETE CASCADE,
    courier_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (order_id, courier_id)
);

ALTER TABLE public.order_declines ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "courier_own_declines" ON public.order_declines;
CREATE POLICY "courier_own_declines"
ON public.order_declines FOR ALL
USING (courier_id = auth.uid())
WITH CHECK (courier_id = auth.uid());

-- ── 4. TABLA AUDIT_LOG ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID,
    actor_id UUID,
    action TEXT NOT NULL,
    details JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE public.audit_log ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "read_own_audit_log" ON public.audit_log;
CREATE POLICY "read_own_audit_log"
ON public.audit_log FOR SELECT
USING (actor_id = auth.uid() OR public.get_my_role() = 'admin');

-- ── 5. FUNCIÓN AUXILIAR DE SEGURIDAD PARA REPARTIDOR ACTIVO Y DISPONIBLE ────
CREATE OR REPLACE FUNCTION public.is_active_available_courier(p_user_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = p_user_id
          AND role = 'repartidor'
          AND COALESCE(is_active, true) = true
          AND COALESCE(is_available, false) = true
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- ── 6. TRIGGER DE VALIDACIÓN DE TRANSICIÓN DE ESTADOS EN ORDERS ───────────
-- Flujo permitido: pending -> accepted/confirmed -> preparing -> ready -> picked_up -> on_the_way -> delivered
-- Cancelled está permitido desde cualquier estado excepto delivered o cancelled
CREATE OR REPLACE FUNCTION public.validate_order_status_transition()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;

    -- Cancelled permitido antes de delivered
    IF NEW.status = 'cancelled' THEN
        IF OLD.status IN ('delivered', 'cancelled') THEN
            RAISE EXCEPTION 'No se puede cancelar un pedido entregado o ya cancelado.';
        END IF;
        RETURN NEW;
    END IF;

    -- Validar transiciones exactas
    CASE OLD.status
        WHEN 'pending' THEN
            IF NEW.status NOT IN ('accepted', 'confirmed', 'preparing') THEN
                RAISE EXCEPTION 'Transición no permitida: % -> %', OLD.status, NEW.status;
            END IF;
        WHEN 'accepted' THEN
            IF NEW.status NOT IN ('confirmed', 'preparing') THEN
                RAISE EXCEPTION 'Transición no permitida: % -> %', OLD.status, NEW.status;
            END IF;
        WHEN 'confirmed' THEN
            IF NEW.status NOT IN ('preparing', 'ready') THEN
                RAISE EXCEPTION 'Transición no permitida: % -> %', OLD.status, NEW.status;
            END IF;
        WHEN 'preparing' THEN
            IF NEW.status NOT IN ('ready') THEN
                RAISE EXCEPTION 'Transición no permitida: % -> %', OLD.status, NEW.status;
            END IF;
        WHEN 'ready' THEN
            IF NEW.status NOT IN ('picked_up', 'on_the_way') THEN
                RAISE EXCEPTION 'Transición no permitida: % -> %', OLD.status, NEW.status;
            END IF;
        WHEN 'picked_up' THEN
            IF NEW.status NOT IN ('on_the_way') THEN
                RAISE EXCEPTION 'Transición no permitida: % -> %', OLD.status, NEW.status;
            END IF;
        WHEN 'on_the_way' THEN
            IF NEW.status NOT IN ('delivered') THEN
                RAISE EXCEPTION 'Transición no permitida: % -> %', OLD.status, NEW.status;
            END IF;
        WHEN 'delivered' THEN
            RAISE EXCEPTION 'El pedido ya fue entregado y no puede cambiar de estado.';
        WHEN 'cancelled' THEN
            RAISE EXCEPTION 'El pedido fue cancelado y no puede cambiar de estado.';
        ELSE
            RAISE EXCEPTION 'Estado no reconocido: %', OLD.status;
    END CASE;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_validate_order_status ON public.orders;
CREATE TRIGGER trg_validate_order_status
BEFORE UPDATE ON public.orders
FOR EACH ROW
EXECUTE FUNCTION public.validate_order_status_transition();

-- ── 7. TRIGGER BEFORE UPDATE PARA RESTRINGIR EDICIONES DE REPARTIDOR ──────
CREATE OR REPLACE FUNCTION public.restrict_courier_updates()
RETURNS TRIGGER AS $$
DECLARE
    v_role TEXT;
    v_is_accepting TEXT;
BEGIN
    v_role := public.get_my_role();

    IF v_role = 'repartidor' THEN
        v_is_accepting := current_setting('app.accepting_order', true);

        -- Si la llamada proviene de accept_order()
        IF v_is_accepting = 'on' THEN
            IF OLD.courier_id IS NOT NULL OR NEW.courier_id != auth.uid() THEN
                RAISE EXCEPTION 'Un repartidor solo puede asignarse a un pedido libre.';
            END IF;
        ELSE
            -- Si NO viene de accept_order, el repartidor no puede cambiar courier_id
            IF OLD.courier_id IS DISTINCT FROM NEW.courier_id THEN
                RAISE EXCEPTION 'Un repartidor no puede cambiar directamente el repartidor asignado.';
            END IF;
        END IF;

        -- Bloquear modificación de cualquier otra columna sensible
        IF OLD.subtotal IS DISTINCT FROM NEW.subtotal OR
           OLD.total IS DISTINCT FROM NEW.total OR
           OLD.customer_id IS DISTINCT FROM NEW.customer_id OR
           OLD.restaurant_id IS DISTINCT FROM NEW.restaurant_id OR
           OLD.delivery_address IS DISTINCT FROM NEW.delivery_address THEN
            RAISE EXCEPTION 'Un repartidor solo puede cambiar el estado (status) de su pedido asignado.';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_restrict_courier_updates ON public.orders;
CREATE TRIGGER trg_restrict_courier_updates
BEFORE UPDATE ON public.orders
FOR EACH ROW
EXECUTE FUNCTION public.restrict_courier_updates();

-- ── 8. FUNCIÓN RPC ATÓMICA ACCEPT_ORDER (SECURITY DEFINER + AUDIT LOG) ────
CREATE OR REPLACE FUNCTION public.accept_order(p_order_id UUID)
RETURNS VOID AS $$
DECLARE
    v_rows_affected INT;
    v_user_id UUID;
    v_is_active BOOLEAN;
    v_is_available BOOLEAN;
    v_role TEXT;
BEGIN
    v_user_id := auth.uid();

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Usuario no autenticado';
    END IF;

    SELECT role, COALESCE(is_active, true), COALESCE(is_available, false)
    INTO v_role, v_is_active, v_is_available
    FROM public.profiles
    WHERE id = v_user_id;

    IF v_role IS DISTINCT FROM 'repartidor' THEN
        RAISE EXCEPTION 'Solo usuarios con rol repartidor pueden aceptar pedidos';
    END IF;

    IF v_is_active = FALSE THEN
        RAISE EXCEPTION 'Tu cuenta de repartidor ha sido desactivada';
    END IF;

    IF v_is_available = FALSE THEN
        RAISE EXCEPTION 'Debes estar en modo Disponible para aceptar pedidos';
    END IF;

    -- Activar flag de sesión temporal para permitir que el trigger acepte el cambio de courier_id de NULL a auth.uid()
    PERFORM set_config('app.accepting_order', 'on', true);

    -- Actualización atómica en orders
    UPDATE public.orders
    SET courier_id = v_user_id,
        status = 'picked_up',
        created_at = NOW()
    WHERE id = p_order_id
      AND courier_id IS NULL
      AND status = 'ready';

    GET DIAGNOSTICS v_rows_affected = ROW_COUNT;

    IF v_rows_affected = 0 THEN
        RAISE EXCEPTION 'Pedido ya tomado por otro repartidor';
    END IF;

    -- Registrar en audit_log
    INSERT INTO public.audit_log (order_id, actor_id, action, details)
    VALUES (
        p_order_id,
        v_user_id,
        'ACCEPT_ORDER',
        jsonb_build_object('courier_id', v_user_id, 'status', 'picked_up')
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

REVOKE ALL ON FUNCTION public.accept_order(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.accept_order(UUID) TO authenticated;

-- ── 9. POLÍTICAS RLS IDEMPOTENTES PARA ORDERS ──────────────────────────────
ALTER TABLE public.orders ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "orders_courier_select" ON public.orders;
CREATE POLICY "orders_courier_select"
ON public.orders FOR SELECT
USING (
    public.get_my_role() IN ('admin', 'restaurant')
    OR customer_id = auth.uid()
    OR (
        public.get_my_role() = 'repartidor'
        AND public.is_active_available_courier(auth.uid())
        AND (
            courier_id = auth.uid()
            OR (
                courier_id IS NULL
                AND status = 'ready'
                AND NOT EXISTS (
                    SELECT 1 FROM public.order_declines od
                    WHERE od.order_id = orders.id AND od.courier_id = auth.uid()
                )
            )
        )
    )
);

DROP POLICY IF EXISTS "orders_courier_update" ON public.orders;
CREATE POLICY "orders_courier_update"
ON public.orders FOR UPDATE
USING (
    public.get_my_role() IN ('admin', 'restaurant')
    OR (
        public.get_my_role() = 'repartidor'
        AND public.is_active_available_courier(auth.uid())
        AND courier_id = auth.uid()
    )
)
WITH CHECK (
    public.get_my_role() IN ('admin', 'restaurant')
    OR (
        public.get_my_role() = 'repartidor'
        AND public.is_active_available_courier(auth.uid())
        AND courier_id = auth.uid()
    )
);

SELECT '✅ TAREA 1 SQL Completo configurado exitosamente' AS resultado;
