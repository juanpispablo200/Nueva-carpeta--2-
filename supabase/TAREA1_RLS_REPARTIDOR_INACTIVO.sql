-- ============================================================================
-- JAGOO — TAREA 1: RLS y Seguridad para Repartidores (Idempotente)
-- ============================================================================
-- Un repartidor inactivo (is_active = false) no puede leer ni actualizar nada.
-- ============================================================================

-- 1. Función auxiliar para verificar si el usuario es un repartidor activo
CREATE OR REPLACE FUNCTION public.is_active_repartidor(p_user_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = p_user_id
          AND role = 'repartidor'
          AND COALESCE(is_active, true) = true
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 2. Asegurar que la función del rol actual verifique is_active
CREATE OR REPLACE FUNCTION public.get_my_role()
RETURNS TEXT AS $$
DECLARE
    v_role TEXT;
    v_active BOOLEAN;
BEGIN
    SELECT role, COALESCE(is_active, true)
    INTO v_role, v_active
    FROM public.profiles
    WHERE id = auth.uid();

    -- Si la cuenta está desactivada, retornar 'inactive'
    IF v_active = false THEN
        RETURN 'inactive';
    END IF;

    RETURN v_role;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. Actualizar políticas en PROFILES
DROP POLICY IF EXISTS "repartidor_read_own_profile" ON public.profiles;
CREATE POLICY "repartidor_read_own_profile"
ON public.profiles FOR SELECT
USING (
    id = auth.uid()
    AND COALESCE(is_active, true) = true
);

-- 4. Actualizar políticas en REPARTIDORES
DROP POLICY IF EXISTS "repartidores_own" ON public.repartidores;
CREATE POLICY "repartidores_own"
ON public.repartidores FOR ALL
USING (
    id = auth.uid()
    AND public.is_active_repartidor(auth.uid())
)
WITH CHECK (
    id = auth.uid()
    AND public.is_active_repartidor(auth.uid())
);

-- 5. Actualizar políticas en ORDERS para repartidores activos únicamente
DROP POLICY IF EXISTS "orders_repartidor_select" ON public.orders;
CREATE POLICY "orders_repartidor_select"
ON public.orders FOR SELECT
USING (
    public.is_active_repartidor(auth.uid())
    AND (
        repartidor_id = auth.uid()
        OR (repartidor_id IS NULL AND status = 'ready')
    )
);

DROP POLICY IF EXISTS "orders_repartidor_update" ON public.orders;
CREATE POLICY "orders_repartidor_update"
ON public.orders FOR UPDATE
USING (
    public.is_active_repartidor(auth.uid())
    AND (
        repartidor_id = auth.uid()
        OR (repartidor_id IS NULL AND status = 'ready')
    )
)
WITH CHECK (
    public.is_active_repartidor(auth.uid())
);

SELECT '✅ TAREA 1: RLS y Seguridad de Repartidores Inactivos configurado correctamente' AS resultado;
