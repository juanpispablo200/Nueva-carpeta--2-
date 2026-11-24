-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Pruebas SQL: pagos, cancelación automática, RLS por tipo de negocio,
-- edad (licorería) y receta (farmacia).
--
-- Cómo correrlas (en una base de PRUEBA o de staging, nunca con datos reales
-- que no quieras tocar; todo ocurre dentro de una transacción que termina en
-- ROLLBACK, pero igual conviene un entorno de pruebas):
--   SQL Editor de Supabase → pegar el archivo completo → Run.
--   Cada prueba imprime "OK ..." (NOTICE). Si una falla, el script se detiene
--   con "FALLA: ..." y no se guarda nada.
--
-- Requiere las migraciones 20 a 23 aplicadas.
-- Usa UUID fijos de prueba (prefijo 00000000-0000-0000-0000-0000000000xx).
-- ══════════════════════════════════════════════════════════════════════════════
BEGIN;

-- Contexto "sistema": sin usuario autenticado (auth.uid() = NULL)
CREATE FUNCTION pg_temp.sys() RETURNS void LANGUAGE plpgsql AS $f$
BEGIN
  PERFORM set_config('request.jwt.claims', '', true);
  PERFORM set_config('request.jwt.claim.sub', '', true);
END;
$f$;

-- ── Datos de prueba ──────────────────────────────────────────────────────────
-- Usuarios: cliente adulto A (a1), cliente menor M (a2), cliente sin fecha S (a3),
-- dueño de restaurante (b1), dueño de farmacia (b2), dueño de licorería (b3),
-- repartidor (c1), admin (d1)
DO $$
DECLARE
  u record;
BEGIN
  FOR u IN SELECT * FROM (VALUES
    ('00000000-0000-0000-0000-0000000000a1'::uuid, 'test-a1@jagoo.test', 'customer'),
    ('00000000-0000-0000-0000-0000000000a2'::uuid, 'test-a2@jagoo.test', 'customer'),
    ('00000000-0000-0000-0000-0000000000a3'::uuid, 'test-a3@jagoo.test', 'customer'),
    ('00000000-0000-0000-0000-0000000000b1'::uuid, 'test-b1@jagoo.test', 'restaurant'),
    ('00000000-0000-0000-0000-0000000000b2'::uuid, 'test-b2@jagoo.test', 'restaurant'),
    ('00000000-0000-0000-0000-0000000000b3'::uuid, 'test-b3@jagoo.test', 'restaurant'),
    ('00000000-0000-0000-0000-0000000000c1'::uuid, 'test-c1@jagoo.test', 'repartidor'),
    ('00000000-0000-0000-0000-0000000000d1'::uuid, 'test-d1@jagoo.test', 'admin')
  ) AS x(id, email, role)
  LOOP
    INSERT INTO auth.users (id, email) VALUES (u.id, u.email) ON CONFLICT (id) DO NOTHING;
    INSERT INTO public.profiles (id, full_name, phone, role, is_active)
    VALUES (u.id, 'Prueba ' || u.email, '', u.role, true)
    ON CONFLICT (id) DO UPDATE SET role = EXCLUDED.role, is_active = true;
  END LOOP;

  -- Fechas de nacimiento (como superusuario)
  UPDATE public.profiles SET birth_date = (CURRENT_DATE - INTERVAL '30 years')::date
   WHERE id = '00000000-0000-0000-0000-0000000000a1';
  UPDATE public.profiles SET birth_date = (CURRENT_DATE - INTERVAL '16 years')::date
   WHERE id = '00000000-0000-0000-0000-0000000000a2';

  -- Sin zonas activas (se prueba aparte) y tarifa por defecto conocida
  UPDATE public.delivery_zones SET is_active = false;
  INSERT INTO public.settings (key, value) VALUES ('default_delivery_fee', '2.50')
    ON CONFLICT (key) DO UPDATE SET value = '2.50';
  INSERT INTO public.settings (key, value) VALUES ('split_negocio_pct', '85'), ('split_driver_pct', '5'), ('split_plataforma_pct', '10')
    ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
  UPDATE public.business_type_config SET pct_negocio = NULL, pct_driver = NULL, pct_plataforma = NULL, min_order = NULL;

  -- Negocios
  INSERT INTO public.restaurants (id, name, category, business_type, owner_id, min_order, is_open, is_active)
  VALUES
    ('t-rest', 'Test Restaurante', 'Comida', 'restaurante', '00000000-0000-0000-0000-0000000000b1', 5, true, true),
    ('t-farm', 'Test Farmacia',    'Salud',  'farmacia',    '00000000-0000-0000-0000-0000000000b2', 5, true, true),
    ('t-lico', 'Test Licoreria',   'Licor',  'licoreria',   '00000000-0000-0000-0000-0000000000b3', 5, true, true),
    ('t-off',  'Test Inactivo',    'Comida', 'tienda',      NULL,                                   5, true, false)
  ON CONFLICT (id) DO UPDATE SET business_type = EXCLUDED.business_type, owner_id = EXCLUDED.owner_id,
                                 is_active = EXCLUDED.is_active, is_open = true;

  -- Productos
  INSERT INTO public.dishes (id, restaurant_id, section_name, name, price)
  VALUES ('t-d-rest', 't-rest', 'Platos', 'Plato prueba', 6),
         ('t-d-lico', 't-lico', 'Licores', 'Ron prueba', 10),
         ('t-d-farm-otc', 't-farm', 'Venta libre', 'Analgésico', 6),
         ('t-d-off', 't-off', 'Varios', 'Producto inactivo', 6)
  ON CONFLICT (id) DO NOTHING;
  INSERT INTO public.dishes (id, restaurant_id, section_name, name, price, requires_prescription)
  VALUES ('t-d-farm-rx', 't-farm', 'Con receta', 'Antibiótico', 12, true)
  ON CONFLICT (id) DO UPDATE SET requires_prescription = true;
END;
$$;

-- ══════════════════════════════════════════════════════════════════════════════
-- T1. Restricciones por tipo de producto
-- ══════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
  v_err text;
BEGIN
  ASSERT (SELECT is_age_restricted FROM public.dishes WHERE id = 't-d-lico'),
    'FALLA T1: todo producto de licorería debe quedar restringido (+18)';
  ASSERT NOT (SELECT is_age_restricted FROM public.dishes WHERE id = 't-d-rest'),
    'FALLA T1: un producto de restaurante no debe estar restringido';

  BEGIN
    UPDATE public.dishes SET is_age_restricted = true WHERE id = 't-d-rest';
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  ASSERT v_err LIKE 'RESTRICCION_INVALIDA%', 'FALLA T1: +18 en un restaurante debió rechazarse';

  v_err := NULL;
  BEGIN
    UPDATE public.dishes SET requires_prescription = true WHERE id = 't-d-lico';
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  ASSERT v_err LIKE 'RESTRICCION_INVALIDA%', 'FALLA T1: receta en una licorería debió rechazarse';

  RAISE NOTICE 'OK T1 restricciones por tipo de producto';
END;
$$;

-- ══════════════════════════════════════════════════════════════════════════════
-- T2. Pedido en efectivo: se crea en el servidor, cambio y reparto
-- ══════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
  v_res   jsonb;
  v_o     public.orders%ROWTYPE;
  v_err   text;
BEGIN
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);
  PERFORM set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a1', true);
  EXECUTE 'SET LOCAL ROLE authenticated';

  v_res := public.create_order(
    't-rest',
    '[{"dish_id":"t-d-rest","quantity":2,"unit_price":6,"special_instructions":"","selected_options_json":[]}]'::jsonb,
    'Calle Prueba 123', NULL, 'cash', 20, NULL, NULL);

  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();

  SELECT * INTO v_o FROM public.orders WHERE id = (v_res->>'order_id')::uuid;
  ASSERT v_o.subtotal = 12.00 AND v_o.delivery_fee = 2.50 AND v_o.total = 14.50, 'FALLA T2: importes (' || v_o.total || ')';
  ASSERT v_o.change_due = 5.50, 'FALLA T2: cambio esperado 5.50, fue ' || COALESCE(v_o.change_due::text, 'NULL');
  ASSERT v_o.status = 'pending' AND v_o.payment_method = 'cash', 'FALLA T2: estado inicial';
  ASSERT v_o.business_type = 'restaurante', 'FALLA T2: tipo de negocio no quedó en el pedido';
  ASSERT v_o.business_amount + v_o.driver_amount + v_o.platform_amount = v_o.total,
    'FALLA T2: el reparto no suma el total';
  -- 85% de 12 = 10.20; 5% de 12 = 0.60 + envío 2.50 = 3.10; plataforma = 14.50 - 10.20 - 3.10 = 1.20
  ASSERT v_o.business_amount = 10.20 AND v_o.driver_amount = 3.10 AND v_o.platform_amount = 1.20,
    'FALLA T2: reparto esperado 10.20 / 3.10 / 1.20, fue ' || v_o.business_amount || ' / ' || v_o.driver_amount || ' / ' || v_o.platform_amount;
  ASSERT (SELECT count(*) FROM public.order_items WHERE order_id = v_o.id) = 1, 'FALLA T2: ítems no creados';

  -- Efectivo insuficiente
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  BEGIN
    PERFORM public.create_order('t-rest',
      '[{"dish_id":"t-d-rest","quantity":2,"unit_price":6}]'::jsonb, 'x', NULL, 'cash', 10, NULL, NULL);
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT v_err = 'EFECTIVO_INSUFICIENTE', 'FALLA T2: debió rechazar efectivo insuficiente (' || COALESCE(v_err, 'sin error') || ')';

  RAISE NOTICE 'OK T2 pedido en efectivo, cambio y reparto';
END;
$$;

-- ══════════════════════════════════════════════════════════════════════════════
-- T3. Validaciones de create_order: mínimo, precio, negocio inactivo, zona
-- ══════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
  v_err text;
  v_zone uuid;
  v_res jsonb;
BEGIN
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';

  BEGIN  -- mínimo 5: 1 x 4.00 con precio base 6 → PRECIO_INVALIDO primero
    PERFORM public.create_order('t-rest', '[{"dish_id":"t-d-rest","quantity":1,"unit_price":4}]'::jsonb, 'x', NULL, 'cash', 50, NULL, NULL);
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  ASSERT v_err = 'PRECIO_INVALIDO', 'FALLA T3: precio menor al base (' || COALESCE(v_err, 'sin error') || ')';

  v_err := NULL;
  BEGIN
    PERFORM public.create_order('t-off', '[{"dish_id":"t-d-off","quantity":1,"unit_price":6}]'::jsonb, 'x', NULL, 'cash', 50, NULL, NULL);
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  ASSERT v_err = 'NEGOCIO_NO_DISPONIBLE', 'FALLA T3: negocio inactivo (' || COALESCE(v_err, 'sin error') || ')';

  v_err := NULL;
  BEGIN
    PERFORM public.create_order('t-rest', '[{"dish_id":"t-d-rest","quantity":1,"unit_price":6}]'::jsonb, 'x', NULL, 'tarjeta', NULL, NULL, NULL);
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  ASSERT v_err = 'METODO_PAGO_INVALIDO', 'FALLA T3: la tarjeta ya no es un método válido (' || COALESCE(v_err, 'sin error') || ')';

  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();

  -- Pedido mínimo por tipo (tiene prioridad sobre el del negocio)
  UPDATE public.business_type_config SET min_order = 20 WHERE business_type = 'restaurante';
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  v_err := NULL;
  BEGIN
    PERFORM public.create_order('t-rest', '[{"dish_id":"t-d-rest","quantity":2,"unit_price":6}]'::jsonb, 'x', NULL, 'cash', 50, NULL, NULL);
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT v_err LIKE 'PEDIDO_MINIMO:%', 'FALLA T3: pedido mínimo del tipo (' || COALESCE(v_err, 'sin error') || ')';
  UPDATE public.business_type_config SET min_order = NULL WHERE business_type = 'restaurante';

  -- Zonas: con zona activa, es obligatoria y fija la tarifa
  INSERT INTO public.delivery_zones (name, fee, is_active) VALUES ('Zona prueba', 3.75, true) RETURNING id INTO v_zone;
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  v_err := NULL;
  BEGIN
    PERFORM public.create_order('t-rest', '[{"dish_id":"t-d-rest","quantity":1,"unit_price":6}]'::jsonb, 'x', NULL, 'cash', 50, NULL, NULL);
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  ASSERT v_err = 'ZONA_REQUERIDA', 'FALLA T3: sin zona debió fallar (' || COALESCE(v_err, 'sin error') || ')';
  v_res := public.create_order('t-rest', '[{"dish_id":"t-d-rest","quantity":1,"unit_price":6}]'::jsonb, 'x', v_zone, 'cash', 50, NULL, NULL);
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT (v_res->>'delivery_fee')::numeric = 3.75, 'FALLA T3: la tarifa debe venir de la zona';
  UPDATE public.delivery_zones SET is_active = false;

  RAISE NOTICE 'OK T3 validaciones de create_order (precio, negocio, método, mínimo, zona)';
END;
$$;

-- ══════════════════════════════════════════════════════════════════════════════
-- T4. Transferencia: oculta al negocio, no se acepta sin pago, cancelación a tiempo
-- ══════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
  v_res    jsonb;
  v_id     uuid;
  v_err    text;
  v_n      integer;
  v_proof  uuid;
  v_seen   integer;
BEGIN
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  v_res := public.create_order('t-rest', '[{"dish_id":"t-d-rest","quantity":1,"unit_price":6}]'::jsonb, 'x', NULL, 'transfer', NULL, NULL, NULL);
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  v_id := (v_res->>'order_id')::uuid;

  ASSERT (SELECT transfer_deadline FROM public.orders WHERE id = v_id) > now() + interval '29 minutes',
    'FALLA T4: el plazo debe ser de ~30 minutos';

  -- El negocio NO ve el pedido de transferencia sin pagar
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  SELECT count(*) INTO v_seen FROM public.orders WHERE id = v_id;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT v_seen = 0, 'FALLA T4: el negocio ve un pedido de transferencia sin pagar';

  -- Ni siquiera como superusuario se puede aceptar sin pago (regla por trigger)
  BEGIN
    UPDATE public.orders SET status = 'accepted' WHERE id = v_id;
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  ASSERT v_err LIKE 'PAGO_PENDIENTE%', 'FALLA T4: aceptar sin pago debió fallar (' || COALESCE(v_err, 'sin error') || ')';

  -- Comprobante con ruta ajena: rechazado. Con ruta propia: pasa a 'verifying'
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  v_err := NULL;
  BEGIN
    PERFORM public.upload_payment_proof(v_id, '00000000-0000-0000-0000-0000000000zz/x.jpg', NULL, 0);
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  ASSERT v_err = 'RUTA_COMPROBANTE_INVALIDA', 'FALLA T4: ruta ajena debió rechazarse (' || COALESCE(v_err, 'sin error') || ')';
  v_proof := public.upload_payment_proof(v_id, '00000000-0000-0000-0000-0000000000a1/' || v_id || '.jpg', 'REF1', 0);
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT (SELECT payment_status FROM public.orders WHERE id = v_id) = 'verifying', 'FALLA T4: debe quedar en verifying';

  -- Con comprobante en revisión NO se cancela automáticamente
  UPDATE public.orders SET transfer_deadline = now() - interval '1 minute' WHERE id = v_id;
  v_n := public.cancel_expired_transfer_orders();
  ASSERT (SELECT status FROM public.orders WHERE id = v_id) = 'pending',
    'FALLA T4: un pedido con comprobante en revisión no debe cancelarse';

  -- El admin verifica → accepted
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000d1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  PERFORM public.verify_payment_proof(v_proof, true, NULL);
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT (SELECT status FROM public.orders WHERE id = v_id) = 'accepted', 'FALLA T4: al verificar debe pasar a accepted';
  ASSERT (SELECT payment_status FROM public.orders WHERE id = v_id) = 'verified', 'FALLA T4: payment_status verified';

  RAISE NOTICE 'OK T4 transferencia: oculta, sin aceptar sin pago, verificación → accepted';
END;
$$;

DO $$
DECLARE
  v_res jsonb;
  v_id  uuid;
  v_n   integer;
  v_id2 uuid;
BEGIN
  -- Pedido de transferencia sin comprobante y con plazo vencido → se cancela
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  v_res := public.create_order('t-rest', '[{"dish_id":"t-d-rest","quantity":1,"unit_price":6}]'::jsonb, 'x', NULL, 'transfer', NULL, NULL, NULL);
  v_id := (v_res->>'order_id')::uuid;
  v_res := public.create_order('t-rest', '[{"dish_id":"t-d-rest","quantity":1,"unit_price":6}]'::jsonb, 'x', NULL, 'transfer', NULL, NULL, NULL);
  v_id2 := (v_res->>'order_id')::uuid;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();

  -- v_id vence; v_id2 sigue dentro del plazo
  UPDATE public.orders SET transfer_deadline = now() - interval '1 minute' WHERE id = v_id;

  v_n := public.cancel_expired_transfer_orders();
  ASSERT v_n >= 1, 'FALLA T4b: debió cancelar al menos un pedido vencido';
  ASSERT (SELECT status FROM public.orders WHERE id = v_id) = 'cancelled', 'FALLA T4b: el pedido vencido debe estar cancelled';
  ASSERT (SELECT cancel_reason FROM public.orders WHERE id = v_id) = 'TRANSFER_TIMEOUT', 'FALLA T4b: motivo TRANSFER_TIMEOUT';
  ASSERT (SELECT status FROM public.orders WHERE id = v_id2) = 'pending', 'FALLA T4b: el pedido dentro del plazo no debe cancelarse';

  -- Idempotente: una segunda corrida no vuelve a tocar nada
  ASSERT public.cancel_expired_transfer_orders() = 0, 'FALLA T4b: la cancelación debe ser idempotente';

  -- Un usuario normal NO puede ejecutar la cancelación masiva
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  BEGIN
    PERFORM public.cancel_expired_transfer_orders();
    EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
    RAISE EXCEPTION 'FALLA T4b: authenticated pudo ejecutar cancel_expired_transfer_orders';
  EXCEPTION WHEN insufficient_privilege THEN
    EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  END;

  RAISE NOTICE 'OK T4b cancelación automática (vencido sí, vigente no, idempotente, sin permiso para usuarios)';
END;
$$;

-- ══════════════════════════════════════════════════════════════════════════════
-- T5. Efectivo: el negocio acepta; pedido protegido contra UPDATE directo
-- ══════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
  v_res  jsonb;
  v_id   uuid;
  v_err  text;
  v_seen integer;
BEGIN
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  v_res := public.create_order('t-rest', '[{"dish_id":"t-d-rest","quantity":1,"unit_price":6}]'::jsonb, 'x', NULL, 'cash', 10, NULL, NULL);
  v_id := (v_res->>'order_id')::uuid;

  -- Un cliente no puede insertar pedidos directamente (solo create_order)
  BEGIN
    INSERT INTO public.orders (customer_id, restaurant_id, restaurant_name, subtotal, delivery_fee, total, status)
    VALUES ('00000000-0000-0000-0000-0000000000a1', 't-rest', 'x', 1, 0, 1, 'pending');
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT v_err IS NOT NULL, 'FALLA T5: el cliente pudo insertar un pedido directo';

  -- El negocio dueño lo ve (efectivo) y puede aceptarlo
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  SELECT count(*) INTO v_seen FROM public.orders WHERE id = v_id;
  ASSERT v_seen = 1, 'FALLA T5: el negocio debe ver el pedido en efectivo';

  -- No puede cambiar el total ni el método de pago
  v_err := NULL;
  BEGIN
    UPDATE public.orders SET total = 0.01 WHERE id = v_id;
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  ASSERT v_err LIKE 'COLUMNA_PROTEGIDA%', 'FALLA T5: el negocio no debe poder cambiar el total (' || COALESCE(v_err, 'sin error') || ')';

  UPDATE public.orders SET status = 'accepted' WHERE id = v_id;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT (SELECT status FROM public.orders WHERE id = v_id) = 'accepted', 'FALLA T5: el negocio debe poder aceptar efectivo';

  -- Otro negocio (farmacia) NO ve pedidos del restaurante
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b2","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  SELECT count(*) INTO v_seen FROM public.orders WHERE restaurant_id = 't-rest';
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT v_seen = 0, 'FALLA T5: otro negocio ve pedidos ajenos';

  RAISE NOTICE 'OK T5 efectivo, protección de columnas y aislamiento entre negocios';
END;
$$;

-- ══════════════════════════════════════════════════════════════════════════════
-- T6. RLS por tipo de negocio: productos y negocios
-- ══════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
  v_n   integer;
  v_err text;
BEGIN
  -- Un cliente no ve negocios inactivos ni sus productos
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  SELECT count(*) INTO v_n FROM public.restaurants WHERE id = 't-off';
  ASSERT v_n = 0, 'FALLA T6: el cliente ve un negocio inactivo';
  SELECT count(*) INTO v_n FROM public.dishes WHERE restaurant_id = 't-off';
  ASSERT v_n = 0, 'FALLA T6: el cliente ve productos de un negocio inactivo';
  SELECT count(*) INTO v_n FROM public.restaurants WHERE id = 't-lico';
  ASSERT v_n = 1, 'FALLA T6: el cliente debe ver negocios activos';
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();

  -- El dueño de la licorería NO puede editar productos de la farmacia
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b3","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  UPDATE public.dishes SET price = 1 WHERE restaurant_id = 't-farm';
  GET DIAGNOSTICS v_n = ROW_COUNT;
  ASSERT v_n = 0, 'FALLA T6: un negocio modificó productos de otro';

  -- No puede cambiar su propio tipo ni activarse/desactivarse
  BEGIN
    UPDATE public.restaurants SET business_type = 'farmacia' WHERE id = 't-lico';
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT v_err LIKE 'SOLO_ADMIN%', 'FALLA T6: el dueño no debe poder cambiar el tipo de su negocio (' || COALESCE(v_err, 'sin error') || ')';

  -- El admin sí puede, y los productos se recalculan
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000d1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  UPDATE public.restaurants SET business_type = 'tienda' WHERE id = 't-lico';
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT NOT (SELECT is_age_restricted FROM public.dishes WHERE id = 't-d-lico'),
    'FALLA T6: al dejar de ser licorería, sus productos ya no deben ser +18';
  UPDATE public.restaurants SET business_type = 'licoreria' WHERE id = 't-lico';

  RAISE NOTICE 'OK T6 RLS por tipo de negocio';
END;
$$;

-- ══════════════════════════════════════════════════════════════════════════════
-- T7. Edad (licorería)
-- ══════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
  v_err   text;
  v_res   jsonb;
  v_id    uuid;
  v_adult boolean;
BEGIN
  -- Sin fecha de nacimiento
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a3","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  BEGIN
    PERFORM public.create_order('t-lico', '[{"dish_id":"t-d-lico","quantity":1,"unit_price":10}]'::jsonb, 'x', NULL, 'cash', 20, NULL, NULL);
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT v_err = 'EDAD_NO_VERIFICADA', 'FALLA T7: sin fecha debió fallar (' || COALESCE(v_err, 'sin error') || ')';

  -- Menor de edad
  v_err := NULL;
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a2","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  BEGIN
    PERFORM public.create_order('t-lico', '[{"dish_id":"t-d-lico","quantity":1,"unit_price":10}]'::jsonb, 'x', NULL, 'cash', 20, NULL, NULL);
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT v_err = 'MENOR_DE_EDAD', 'FALLA T7: un menor debió ser rechazado (' || COALESCE(v_err, 'sin error') || ')';

  -- La fecha se registra UNA vez: el cliente sin fecha la fija y luego no puede cambiarla
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a3","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  v_adult := public.set_birth_date((CURRENT_DATE - INTERVAL '25 years')::date);
  ASSERT v_adult, 'FALLA T7: set_birth_date debió devolver true para 25 años';
  v_adult := public.set_birth_date((CURRENT_DATE - INTERVAL '10 years')::date);  -- intento de cambio: se ignora
  ASSERT v_adult, 'FALLA T7: la fecha original no debe cambiarse';
  v_err := NULL;
  BEGIN
    UPDATE public.profiles SET birth_date = (CURRENT_DATE - INTERVAL '40 years')::date
     WHERE id = '00000000-0000-0000-0000-0000000000a3';
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT v_err = 'FECHA_NACIMIENTO_YA_REGISTRADA', 'FALLA T7: no se puede cambiar la fecha dos veces (' || COALESCE(v_err, 'sin error') || ')';

  -- Pedido de un adulto → exige verificación al entregar
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  v_res := public.create_order('t-lico', '[{"dish_id":"t-d-lico","quantity":1,"unit_price":10}]'::jsonb, 'x', NULL, 'cash', 20, NULL, NULL);
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  v_id := (v_res->>'order_id')::uuid;
  ASSERT (SELECT age_check_required FROM public.orders WHERE id = v_id), 'FALLA T7: el pedido debe exigir verificación de edad';

  -- Avanzar el pedido hasta on_the_way con el repartidor asignado (contexto sistema)
  UPDATE public.orders SET status = 'accepted' WHERE id = v_id;
  UPDATE public.orders SET status = 'preparing' WHERE id = v_id;
  UPDATE public.orders SET status = 'ready' WHERE id = v_id;
  UPDATE public.orders SET courier_id = '00000000-0000-0000-0000-0000000000c1', status = 'picked_up' WHERE id = v_id;
  UPDATE public.orders SET status = 'on_the_way' WHERE id = v_id;

  -- El repartidor no puede entregar sin verificar la cédula
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  v_err := NULL;
  BEGIN
    UPDATE public.orders SET status = 'delivered' WHERE id = v_id;
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  ASSERT v_err LIKE 'EDAD_NO_VERIFICADA%', 'FALLA T7: entregar sin verificar edad debió fallar (' || COALESCE(v_err, 'sin error') || ')';

  -- Otro repartidor / cliente no puede confirmar; el asignado sí
  PERFORM public.confirm_age_verification(v_id);
  UPDATE public.orders SET status = 'delivered' WHERE id = v_id;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();

  ASSERT (SELECT age_verified_at FROM public.orders WHERE id = v_id) IS NOT NULL
     AND (SELECT age_verified_by FROM public.orders WHERE id = v_id) = '00000000-0000-0000-0000-0000000000c1',
    'FALLA T7: la verificación debe quedar registrada en el pedido';
  ASSERT (SELECT status FROM public.orders WHERE id = v_id) = 'delivered', 'FALLA T7: debe poder entregarse tras verificar';

  -- Efectivo entregado → queda registro de lo cobrado por el driver
  ASSERT EXISTS (SELECT 1 FROM public.cash_collections WHERE order_id = v_id AND status = 'collected'
                   AND driver_id = '00000000-0000-0000-0000-0000000000c1'),
    'FALLA T7: debe registrarse el efectivo cobrado por el driver';

  RAISE NOTICE 'OK T7 edad: fecha única, menor rechazado, verificación del driver, efectivo registrado';
END;
$$;

-- ══════════════════════════════════════════════════════════════════════════════
-- T8. Efectivo del driver: liquidación solo por admin
-- ══════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
  v_ids uuid[];
  v_n   integer;
BEGIN
  SELECT array_agg(id) INTO v_ids FROM public.cash_collections WHERE status = 'collected';

  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  BEGIN
    PERFORM public.settle_cash_collections(v_ids, 'intento del driver');
    EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
    RAISE EXCEPTION 'FALLA T8: un driver pudo liquidar su propio efectivo';
  EXCEPTION WHEN OTHERS THEN
    EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
    ASSERT SQLERRM LIKE 'Solo administradores%', 'FALLA T8: error inesperado ' || SQLERRM;
  END;

  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000d1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  v_n := public.settle_cash_collections(v_ids, 'liquidado en prueba');
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT v_n >= 1, 'FALLA T8: el admin debe poder liquidar';

  RAISE NOTICE 'OK T8 liquidación de efectivo solo por admin';
END;
$$;

-- ══════════════════════════════════════════════════════════════════════════════
-- T9. Receta (farmacia)
-- ══════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
  v_err  text;
  v_res  jsonb;
  v_id   uuid;
  v_id2  uuid;
BEGIN
  -- Sin receta
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  BEGIN
    PERFORM public.create_order('t-farm', '[{"dish_id":"t-d-farm-rx","quantity":1,"unit_price":12}]'::jsonb, 'x', NULL, 'cash', 20, NULL, NULL);
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  ASSERT v_err = 'RECETA_REQUERIDA', 'FALLA T9: sin receta debió fallar (' || COALESCE(v_err, 'sin error') || ')';

  -- Con una ruta de receta ajena
  v_err := NULL;
  BEGIN
    PERFORM public.create_order('t-farm', '[{"dish_id":"t-d-farm-rx","quantity":1,"unit_price":12}]'::jsonb, 'x', NULL, 'cash', 20, NULL,
                                '00000000-0000-0000-0000-0000000000a2/receta.jpg');
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  ASSERT v_err = 'RECETA_REQUERIDA', 'FALLA T9: receta en carpeta ajena debió rechazarse';

  -- Venta libre: no pide receta
  v_res := public.create_order('t-farm', '[{"dish_id":"t-d-farm-otc","quantity":1,"unit_price":6}]'::jsonb, 'x', NULL, 'cash', 20, NULL, NULL);
  ASSERT NOT (v_res->>'prescription_required')::boolean, 'FALLA T9: producto de venta libre no debe pedir receta';

  -- Con receta propia
  v_res := public.create_order('t-farm', '[{"dish_id":"t-d-farm-rx","quantity":1,"unit_price":12}]'::jsonb, 'x', NULL, 'cash', 20, NULL,
                               '00000000-0000-0000-0000-0000000000a1/receta1.jpg');
  v_id := (v_res->>'order_id')::uuid;
  v_res := public.create_order('t-farm', '[{"dish_id":"t-d-farm-rx","quantity":1,"unit_price":12}]'::jsonb, 'x', NULL, 'cash', 20, NULL,
                               '00000000-0000-0000-0000-0000000000a1/receta2.jpg');
  v_id2 := (v_res->>'order_id')::uuid;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT (SELECT prescription_status FROM public.orders WHERE id = v_id) = 'pending', 'FALLA T9: receta debe quedar pendiente';

  -- Sin receta aprobada el pedido no avanza (ni como dueño del negocio)
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b2","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  v_err := NULL;
  BEGIN
    UPDATE public.orders SET status = 'accepted' WHERE id = v_id;
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  ASSERT v_err LIKE 'RECETA_PENDIENTE%', 'FALLA T9: aceptar sin receta aprobada debió fallar (' || COALESCE(v_err, 'sin error') || ')';

  -- Otro negocio no puede revisar la receta
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b1","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  v_err := NULL;
  BEGIN
    PERFORM public.review_prescription(v_id, true, NULL);
  EXCEPTION WHEN OTHERS THEN v_err := SQLERRM; END;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT v_err LIKE 'No tienes permiso%', 'FALLA T9: otro negocio no debe revisar recetas (' || COALESCE(v_err, 'sin error') || ')';

  -- El negocio dueño aprueba → puede aceptar
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b2","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  PERFORM public.review_prescription(v_id, true, 'ok');
  UPDATE public.orders SET status = 'accepted' WHERE id = v_id;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT (SELECT status FROM public.orders WHERE id = v_id) = 'accepted', 'FALLA T9: con receta aprobada debe poder aceptar';

  -- Rechazar la receta cancela el pedido
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b2","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  PERFORM public.review_prescription(v_id2, false, 'ilegible');
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT (SELECT status FROM public.orders WHERE id = v_id2) = 'cancelled'
     AND (SELECT cancel_reason FROM public.orders WHERE id = v_id2) = 'RECETA_RECHAZADA',
    'FALLA T9: rechazar la receta debe cancelar el pedido';

  RAISE NOTICE 'OK T9 receta: requerida, solo la revisa su negocio, sin aprobar no avanza, rechazo cancela';
END;
$$;

-- ══════════════════════════════════════════════════════════════════════════════
-- T10. Cliente: sin acceso directo a datos de otros
-- ══════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
  v_n integer;
BEGIN
  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a2","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  SELECT count(*) INTO v_n FROM public.orders WHERE customer_id <> '00000000-0000-0000-0000-0000000000a2';
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT v_n = 0, 'FALLA T10: un cliente ve pedidos de otros';

  PERFORM set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a2","role":"authenticated"}', true);
  EXECUTE 'SET LOCAL ROLE authenticated';
  SELECT count(*) INTO v_n FROM public.cash_collections;
  EXECUTE 'RESET ROLE'; PERFORM pg_temp.sys();
  ASSERT v_n = 0, 'FALLA T10: un cliente ve cobros en efectivo';

  RAISE NOTICE 'OK T10 aislamiento del cliente';
END;
$$;

-- ══════════════════════════════════════════════════════════════════════════════
-- T11. Retención de datos
-- ══════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
  v_id     uuid;
  v_report jsonb;
BEGIN
  -- Pedido entregado hace 31 días con receta → la ruta pasa a la cola y se limpia
  SELECT id INTO v_id FROM public.orders WHERE prescription_path IS NOT NULL AND status = 'accepted' LIMIT 1;
  UPDATE public.orders SET status = 'cancelled', cancel_reason = 'PRUEBA' WHERE id = v_id;
  -- updated_at lo actualiza un trigger: se fuerza deshabilitándolo solo en esta transacción
  ALTER TABLE public.orders DISABLE TRIGGER on_order_updated;
  UPDATE public.orders SET updated_at = now() - interval '31 days' WHERE id = v_id;
  ALTER TABLE public.orders ENABLE TRIGGER on_order_updated;

  v_report := public.run_data_retention();
  ASSERT (v_report->>'prescriptions_cleared')::int >= 1, 'FALLA T11: la receta vencida debió limpiarse';
  ASSERT (SELECT prescription_path FROM public.orders WHERE id = v_id) IS NULL, 'FALLA T11: la ruta de receta debe quedar vacía';
  ASSERT EXISTS (SELECT 1 FROM public.storage_deletion_queue WHERE bucket = 'prescriptions' AND deleted_at IS NULL),
    'FALLA T11: el archivo debe quedar en la cola de borrado';
  ASSERT (SELECT total FROM public.orders WHERE id = v_id) > 0, 'FALLA T11: los importes se conservan';

  RAISE NOTICE 'OK T11 retención: receta a la cola de borrado, importes conservados';
END;
$$;

ROLLBACK;
