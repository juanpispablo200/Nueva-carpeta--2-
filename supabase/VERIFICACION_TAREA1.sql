-- ============================================================================
-- JAGOO — PRUEBAS AUTOMÁTICAS DE COMPORTAMIENTO (TAREA 1)
-- ============================================================================
-- Ejecuta este script en Supabase SQL Editor.
-- Crea usuarios y pedidos de prueba dentro de un bloque controlado y evalúa
-- exactamente las 9 pruebas de comportamiento a-i.
-- ============================================================================

DROP TABLE IF EXISTS public.tarea1_test_results;
CREATE TEMP TABLE tarea1_test_results (
    paso TEXT PRIMARY KEY,
    descripcion TEXT NOT NULL,
    resultado BOOLEAN NOT NULL,
    detalle TEXT
);

DO $$
DECLARE
    -- IDs de prueba
    v_driver_a UUID := 'a1111111-1111-1111-1111-111111111111';
    v_driver_b UUID := 'b2222222-2222-2222-2222-222222222222';
    v_driver_inactive UUID := 'c3333333-3333-3333-3333-333333333333';
    v_driver_unavailable UUID := 'd4444444-4444-4444-4444-444444444444';

    v_rest_id UUID := 'e5555555-5555-5555-5555-555555555555';
    v_cust_id UUID := 'f6666666-6666-6666-6666-666666666666';

    v_order_1 UUID := '91111111-1111-1111-1111-111111111111';
    v_order_2 UUID := '92222222-2222-2222-2222-222222222222';
    v_order_3 UUID := '93333333-3333-3333-3333-333333333333';
    v_order_b UUID := '94444444-4444-4444-4444-444444444444';

    v_res_a BOOLEAN := FALSE;
    v_res_b BOOLEAN := FALSE;
    v_res_c BOOLEAN := FALSE;
    v_res_d BOOLEAN := FALSE;
    v_res_e BOOLEAN := FALSE;
    v_res_f BOOLEAN := FALSE;
    v_res_g BOOLEAN := FALSE;
    v_res_h BOOLEAN := FALSE;
    v_res_i BOOLEAN := FALSE;

    v_err_msg TEXT;
    v_audit_count INT;
    v_status TEXT;
    v_courier UUID;
BEGIN
    -- ── CONFIGURACIÓN INICIAL DE PRUEBA ─────────────────────────────────────
    DELETE FROM public.audit_log WHERE order_id IN (v_order_1, v_order_2, v_order_3, v_order_b);
    DELETE FROM public.orders WHERE id IN (v_order_1, v_order_2, v_order_3, v_order_b);
    DELETE FROM public.profiles WHERE id IN (v_driver_a, v_driver_b, v_driver_inactive, v_driver_unavailable, v_cust_id);

    -- Insertar perfiles de prueba
    INSERT INTO public.profiles (id, full_name, role, is_active, is_available) VALUES
    (v_driver_a, 'Repartidor A', 'repartidor', true, true),
    (v_driver_b, 'Repartidor B', 'repartidor', true, true),
    (v_driver_inactive, 'Repartidor Inactivo', 'repartidor', false, true),
    (v_driver_unavailable, 'Repartidor Ocupado', 'repartidor', true, false),
    (v_cust_id, 'Cliente Test', 'customer', true, false);

    -- Insertar pedido listo libre
    INSERT INTO public.orders (id, customer_id, restaurant_id, restaurant_name, subtotal, delivery_fee, total, status, courier_id) VALUES
    (v_order_1, v_cust_id, v_rest_id, 'Restaurante Test', 10.00, 1.50, 11.50, 'ready', NULL),
    (v_order_2, v_cust_id, v_rest_id, 'Restaurante Test', 15.00, 1.50, 16.50, 'ready', NULL),
    (v_order_3, v_cust_id, v_rest_id, 'Restaurante Test', 20.00, 1.50, 21.50, 'ready', NULL),
    (v_order_b, v_cust_id, v_rest_id, 'Restaurante Test', 25.00, 1.50, 26.50, 'picked_up', v_driver_b);

    -- ── PRUEBA A: Repartidor activo y disponible acepta pedido ready libre ───
    PERFORM set_config('request.jwt.claims', jsonb_build_object('sub', v_driver_a::text, 'role', 'authenticated')::text, true);
    PERFORM set_config('role', 'authenticated', true);

    BEGIN
        PERFORM public.accept_order(v_order_1);

        SELECT status, courier_id INTO v_status, v_courier FROM public.orders WHERE id = v_order_1;
        SELECT count(*) INTO v_audit_count FROM public.audit_log WHERE order_id = v_order_1 AND action = 'ACCEPT_ORDER';

        IF v_status = 'picked_up' AND v_courier = v_driver_a AND v_audit_count > 0 THEN
            v_res_a := TRUE;
        END IF;
    EXCEPTION WHEN OTHERS THEN
        v_res_a := FALSE;
    END;

    -- ── PRUEBA B: Otro repartidor intenta aceptar el mismo pedido ────────────
    PERFORM set_config('request.jwt.claims', jsonb_build_object('sub', v_driver_b::text, 'role', 'authenticated')::text, true);

    BEGIN
        PERFORM public.accept_order(v_order_1);
        v_res_b := FALSE; -- Si no falla, la prueba es falsa
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_err_msg = MESSAGE_TEXT;
        IF v_err_msg LIKE '%ya tomado%' OR v_err_msg LIKE '%policy%' THEN
            v_res_b := TRUE;
        END IF;
    END;

    -- ── PRUEBA C: Repartidor con is_active = false falla ──────────────────────
    PERFORM set_config('request.jwt.claims', jsonb_build_object('sub', v_driver_inactive::text, 'role', 'authenticated')::text, true);

    BEGIN
        PERFORM public.accept_order(v_order_2);
        v_res_c := FALSE;
    EXCEPTION WHEN OTHERS THEN
        v_res_c := TRUE;
    END;

    -- ── PRUEBA D: Repartidor con is_available = false falla ───────────────────
    PERFORM set_config('request.jwt.claims', jsonb_build_object('sub', v_driver_unavailable::text, 'role', 'authenticated')::text, true);

    BEGIN
        PERFORM public.accept_order(v_order_2);
        v_res_d := FALSE;
    EXCEPTION WHEN OTHERS THEN
        v_res_d := TRUE;
    END;

    -- ── PRUEBA E: Repartidor intenta cambiar total de su pedido ──────────────
    PERFORM set_config('request.jwt.claims', jsonb_build_object('sub', v_driver_a::text, 'role', 'authenticated')::text, true);

    BEGIN
        UPDATE public.orders SET total = 999.99 WHERE id = v_order_1;
        v_res_e := FALSE;
    EXCEPTION WHEN OTHERS THEN
        v_res_e := TRUE;
    END;

    -- ── PRUEBA F: Repartidor intenta cambiar courier_id directamente ─────────
    BEGIN
        UPDATE public.orders SET courier_id = v_driver_b WHERE id = v_order_1;
        v_res_f := FALSE;
    EXCEPTION WHEN OTHERS THEN
        v_res_f := TRUE;
    END;

    -- ── PRUEBA G: Repartidor avanza picked_up -> on_the_way -> delivered ────
    BEGIN
        UPDATE public.orders SET status = 'on_the_way' WHERE id = v_order_1;
        UPDATE public.orders SET status = 'delivered' WHERE id = v_order_1;
        SELECT status INTO v_status FROM public.orders WHERE id = v_order_1;
        IF v_status = 'delivered' THEN
            v_res_g := TRUE;
        END IF;
    EXCEPTION WHEN OTHERS THEN
        v_res_g := FALSE;
    END;

    -- ── PRUEBA H: Repartidor intenta saltar picked_up -> delivered ───────────
    -- Preparamos order_3 en picked_up
    UPDATE public.orders SET courier_id = v_driver_a, status = 'picked_up' WHERE id = v_order_3;

    BEGIN
        UPDATE public.orders SET status = 'delivered' WHERE id = v_order_3;
        v_res_h := FALSE;
    EXCEPTION WHEN OTHERS THEN
        v_res_h := TRUE;
    END;

    -- ── PRUEBA I: Repartidor A no puede modificar pedidos de B ───────────────
    BEGIN
        UPDATE public.orders SET status = 'on_the_way' WHERE id = v_order_b;
        v_res_i := FALSE;
    EXCEPTION WHEN OTHERS THEN
        v_res_i := TRUE;
    END;

    -- ── GUARDAR RESULTADOS DE VERIFICACIÓN ──────────────────────────────────
    INSERT INTO tarea1_test_results VALUES
    ('a', 'Repartidor activo y disponible acepta pedido ready (courier_id + picked_up + audit_log)', v_res_a, 'OK'),
    ('b', 'Segundo repartidor falla al aceptar pedido ya tomado ("Pedido ya tomado por otro repartidor")', v_res_b, 'OK'),
    ('c', 'Repartidor inactivo (is_active = false) falla al intentar accept_order', v_res_c, 'OK'),
    ('d', 'Repartidor no disponible (is_available = false) falla al intentar accept_order', v_res_d, 'OK'),
    ('e', 'Repartidor intenta cambiar precio/total de su pedido -> Falla por trigger', v_res_e, 'OK'),
    ('f', 'Repartidor intenta cambiar courier_id directamente -> Falla por trigger', v_res_f, 'OK'),
    ('g', 'Repartidor avanza estado en secuencia correcta (picked_up -> on_the_way -> delivered)', v_res_g, 'OK'),
    ('h', 'Repartidor intenta salto inválido (picked_up -> delivered sin pasar por on_the_way) -> Falla por trigger', v_res_h, 'OK'),
    ('i', 'Repartidor A no puede modificar ni interferir en los pedidos del Repartidor B', v_res_i, 'OK');

    -- Limpieza de prueba
    DELETE FROM public.audit_log WHERE order_id IN (v_order_1, v_order_2, v_order_3, v_order_b);
    DELETE FROM public.orders WHERE id IN (v_order_1, v_order_2, v_order_3, v_order_b);
    DELETE FROM public.profiles WHERE id IN (v_driver_a, v_driver_b, v_driver_inactive, v_driver_unavailable, v_cust_id);

END $$;

SELECT paso, descripcion, resultado FROM tarea1_test_results ORDER BY paso;
