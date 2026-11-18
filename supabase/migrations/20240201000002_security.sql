-- ============================================================
-- Seguridad: funciones auxiliares con SECURITY DEFINER
-- ============================================================
SET client_encoding = 'UTF8';

-- Agregar rol repartidor al CHECK si no existe ya
ALTER TABLE public.profiles
    DROP CONSTRAINT IF EXISTS profiles_role_check;
ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_role_check
    CHECK (role IN ('customer','restaurant','admin','repartidor'));

-- ── is_admin() ────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role = 'admin'
    );
$$;

-- ── get_my_role() (reemplaza la versión anterior si existe) ───────────────────
CREATE OR REPLACE FUNCTION public.get_my_role()
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT role FROM public.profiles WHERE id = auth.uid();
$$;

-- ── get_my_restaurant_id() ────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.get_my_restaurant_id()
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT id FROM public.restaurants
    WHERE owner_id = auth.uid()
    LIMIT 1;
$$;

-- ── Función segura: cambiar estado de un pedido ───────────────────────────────
-- Los paneles web deben llamar a esta función en vez de hacer UPDATE directo.
-- Valida la transición, registra el evento y actualiza en un solo paso.
CREATE OR REPLACE FUNCTION public.change_order_status(
    p_order_id  UUID,
    p_new_status TEXT,
    p_note      TEXT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_role      TEXT;
    v_old_status TEXT;
BEGIN
    SELECT role INTO v_role FROM public.profiles WHERE id = auth.uid();
    SELECT status INTO v_old_status FROM public.orders WHERE id = p_order_id;

    -- Validar transición
    IF v_role <> 'admin' AND NOT EXISTS (
        SELECT 1 FROM public.order_transitions
        WHERE actor_role = v_role
          AND from_status = v_old_status
          AND to_status   = p_new_status
    ) THEN
        RAISE EXCEPTION 'Transición no permitida: % → % para rol %',
            v_old_status, p_new_status, COALESCE(v_role,'anónimo');
    END IF;

    UPDATE public.orders
    SET status = p_new_status, updated_at = NOW()
    WHERE id = p_order_id;

    -- Guardar nota si hay (cancelación, etc.)
    IF p_note IS NOT NULL THEN
        INSERT INTO public.order_events(order_id, from_status, to_status, actor_id, actor_role, note)
        VALUES (p_order_id, v_old_status, p_new_status, auth.uid(), v_role, p_note);
    END IF;
END;
$$;

-- ── Repartidor desactivado: no puede entrar ───────────────────────────────────
-- RLS en repartidores: añadir campo is_active y política
ALTER TABLE public.repartidores
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Un repartidor desactivado no puede leer pedidos disponibles
DROP POLICY IF EXISTS "orders_select" ON public.orders;
CREATE POLICY "orders_select" ON public.orders FOR SELECT
    USING (
        customer_id  = auth.uid()
        OR restaurant_id = public.get_my_restaurant_id()
        OR (
            repartidor_id = auth.uid()
            AND EXISTS (SELECT 1 FROM public.repartidores r
                        WHERE r.id = auth.uid() AND r.is_active = TRUE)
        )
        OR (
            -- repartidor activo ve pedidos ready sin asignar
            public.get_my_role() = 'repartidor'
            AND status = 'ready'
            AND repartidor_id IS NULL
            AND EXISTS (SELECT 1 FROM public.repartidores r
                        WHERE r.id = auth.uid() AND r.is_active = TRUE
                          AND r.is_available = TRUE)
        )
        OR public.get_my_role() = 'admin'
    );
