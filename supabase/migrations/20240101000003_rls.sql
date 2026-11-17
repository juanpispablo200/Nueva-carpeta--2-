-- ============================================================
-- JAGOO DELIVERY — Seguridad RLS (Row Level Security)
-- Archivo: 003_rls.sql
-- Ejecutar DESPUÉS de 001_schema.sql y 002_seed.sql
--
-- REGLA DE ORO:
--   - customer  → solo ve/crea SUS pedidos y SUS direcciones
--   - restaurant → ve los pedidos de SU restaurante, edita SU menú
--   - admin      → ve y hace todo
--   - anónimo    → solo puede VER restaurantes y platos activos (sin login)
-- ============================================================

SET client_encoding = 'UTF8';


-- ============================================================
-- FUNCIÓN AUXILIAR: obtener el rol del usuario actual
-- Se usa dentro de todas las políticas.
-- SECURITY DEFINER evita recursión infinita al leer profiles.
-- ============================================================
CREATE OR REPLACE FUNCTION public.get_my_role()
RETURNS TEXT
LANGUAGE sql
STABLE          -- el resultado no cambia dentro de una misma query
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT role
    FROM public.profiles
    WHERE id = auth.uid();
$$;

-- ============================================================
-- FUNCIÓN AUXILIAR: obtener el restaurant_id del usuario actual
-- La usa el restaurante para ver solo sus pedidos y platos.
-- ============================================================
CREATE OR REPLACE FUNCTION public.get_my_restaurant_id()
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT id
    FROM public.restaurants
    WHERE owner_id = auth.uid()
    LIMIT 1;
$$;


-- ============================================================
-- ① TABLA: profiles
-- ============================================================
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Cada usuario ve y edita SOLO su propio perfil
CREATE POLICY "profiles_select_own"
    ON public.profiles FOR SELECT
    USING (
        id = auth.uid()
        OR public.get_my_role() = 'admin'
    );

CREATE POLICY "profiles_update_own"
    ON public.profiles FOR UPDATE
    USING (id = auth.uid())
    WITH CHECK (
        -- El usuario no puede cambiarse el rol a sí mismo
        -- Solo el admin puede hacerlo con una query directa
        role = (SELECT role FROM public.profiles WHERE id = auth.uid())
        OR public.get_my_role() = 'admin'
    );

-- El trigger crea el perfil automáticamente; el usuario no puede INSERT manual
-- El admin sí puede hacerlo (para asignar roles)
CREATE POLICY "profiles_insert_admin"
    ON public.profiles FOR INSERT
    WITH CHECK (public.get_my_role() = 'admin');

-- Solo el admin puede eliminar perfiles
CREATE POLICY "profiles_delete_admin"
    ON public.profiles FOR DELETE
    USING (public.get_my_role() = 'admin');


-- ============================================================
-- ② TABLA: restaurants
-- ============================================================
ALTER TABLE public.restaurants ENABLE ROW LEVEL SECURITY;

-- LECTURA: cualquiera (incluso sin login) puede ver restaurantes activos
CREATE POLICY "restaurants_select_public"
    ON public.restaurants FOR SELECT
    USING (TRUE);   -- todos ven todos los restaurantes (el filtro is_open lo hace la app)

-- EDICIÓN: solo el dueño del restaurante o el admin pueden modificarlo
CREATE POLICY "restaurants_update_owner_or_admin"
    ON public.restaurants FOR UPDATE
    USING (
        owner_id = auth.uid()
        OR public.get_my_role() = 'admin'
    );

-- INSERCIÓN: solo el admin puede crear restaurantes nuevos
CREATE POLICY "restaurants_insert_admin"
    ON public.restaurants FOR INSERT
    WITH CHECK (public.get_my_role() = 'admin');

-- ELIMINACIÓN: solo el admin
CREATE POLICY "restaurants_delete_admin"
    ON public.restaurants FOR DELETE
    USING (public.get_my_role() = 'admin');


-- ============================================================
-- ③ TABLA: menu_sections
-- ============================================================
ALTER TABLE public.menu_sections ENABLE ROW LEVEL SECURITY;

-- LECTURA: cualquiera puede ver las secciones (menú público)
CREATE POLICY "menu_sections_select_public"
    ON public.menu_sections FOR SELECT
    USING (TRUE);

-- ESCRITURA: solo el dueño del restaurante correspondiente o el admin
CREATE POLICY "menu_sections_write_owner_or_admin"
    ON public.menu_sections FOR ALL
    USING (
        restaurant_id = public.get_my_restaurant_id()
        OR public.get_my_role() = 'admin'
    )
    WITH CHECK (
        restaurant_id = public.get_my_restaurant_id()
        OR public.get_my_role() = 'admin'
    );


-- ============================================================
-- ④ TABLA: dishes
-- ============================================================
ALTER TABLE public.dishes ENABLE ROW LEVEL SECURITY;

-- LECTURA: cualquiera puede ver platos disponibles
-- (el cliente sin login también puede explorar el menú)
CREATE POLICY "dishes_select_public"
    ON public.dishes FOR SELECT
    USING (TRUE);

-- INSERCIÓN: solo el dueño del restaurante o admin
CREATE POLICY "dishes_insert_owner_or_admin"
    ON public.dishes FOR INSERT
    WITH CHECK (
        restaurant_id = public.get_my_restaurant_id()
        OR public.get_my_role() = 'admin'
    );

-- EDICIÓN: solo el dueño del restaurante o admin
CREATE POLICY "dishes_update_owner_or_admin"
    ON public.dishes FOR UPDATE
    USING (
        restaurant_id = public.get_my_restaurant_id()
        OR public.get_my_role() = 'admin'
    );

-- ELIMINACIÓN: solo el dueño del restaurante o admin
CREATE POLICY "dishes_delete_owner_or_admin"
    ON public.dishes FOR DELETE
    USING (
        restaurant_id = public.get_my_restaurant_id()
        OR public.get_my_role() = 'admin'
    );


-- ============================================================
-- ⑤ TABLA: addresses
-- ============================================================
ALTER TABLE public.addresses ENABLE ROW LEVEL SECURITY;

-- LECTURA: cada customer ve solo SUS direcciones; admin ve todas
CREATE POLICY "addresses_select_own"
    ON public.addresses FOR SELECT
    USING (
        user_id = auth.uid()
        OR public.get_my_role() = 'admin'
    );

-- INSERCIÓN: solo para sí mismo
CREATE POLICY "addresses_insert_own"
    ON public.addresses FOR INSERT
    WITH CHECK (user_id = auth.uid());

-- EDICIÓN: solo las propias
CREATE POLICY "addresses_update_own"
    ON public.addresses FOR UPDATE
    USING (user_id = auth.uid());

-- ELIMINACIÓN: solo las propias o el admin
CREATE POLICY "addresses_delete_own"
    ON public.addresses FOR DELETE
    USING (
        user_id = auth.uid()
        OR public.get_my_role() = 'admin'
    );


-- ============================================================
-- ⑥ TABLA: orders
-- ============================================================
ALTER TABLE public.orders ENABLE ROW LEVEL SECURITY;

-- LECTURA:
--   • customer   → solo SUS pedidos
--   • restaurant → solo pedidos de SU restaurante
--   • admin      → todos
CREATE POLICY "orders_select"
    ON public.orders FOR SELECT
    USING (
        customer_id   = auth.uid()
        OR restaurant_id = public.get_my_restaurant_id()
        OR public.get_my_role() = 'admin'
    );

-- INSERCIÓN: solo el customer crea pedidos (a nombre propio)
CREATE POLICY "orders_insert_customer"
    ON public.orders FOR INSERT
    WITH CHECK (
        customer_id = auth.uid()
        AND public.get_my_role() = 'customer'
    );

-- EDICIÓN del STATUS:
--   • restaurant → puede cambiar: pending→accepted, accepted→preparing,
--                  preparing→on_the_way, on_the_way→delivered, *→cancelled
--   • customer   → solo puede cancelar si aún está en pending
--   • admin      → puede cambiar a cualquier estado
CREATE POLICY "orders_update_status"
    ON public.orders FOR UPDATE
    USING (
        restaurant_id = public.get_my_restaurant_id()
        OR (customer_id = auth.uid() AND status = 'pending')
        OR public.get_my_role() = 'admin'
    );

-- ELIMINACIÓN: solo el admin (nunca se borra un pedido normal)
CREATE POLICY "orders_delete_admin"
    ON public.orders FOR DELETE
    USING (public.get_my_role() = 'admin');


-- ============================================================
-- ⑦ TABLA: order_items
-- ============================================================
ALTER TABLE public.order_items ENABLE ROW LEVEL SECURITY;

-- LECTURA: quien puede ver el pedido puede ver sus ítems
CREATE POLICY "order_items_select"
    ON public.order_items FOR SELECT
    USING (
        -- el customer dueño del pedido
        EXISTS (
            SELECT 1 FROM public.orders
            WHERE orders.id = order_items.order_id
              AND orders.customer_id = auth.uid()
        )
        -- el restaurante del pedido
        OR EXISTS (
            SELECT 1 FROM public.orders
            WHERE orders.id = order_items.order_id
              AND orders.restaurant_id = public.get_my_restaurant_id()
        )
        -- el admin
        OR public.get_my_role() = 'admin'
    );

-- INSERCIÓN: solo cuando se crea el pedido (el customer los inserta)
CREATE POLICY "order_items_insert_customer"
    ON public.order_items FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.orders
            WHERE orders.id = order_items.order_id
              AND orders.customer_id = auth.uid()
        )
    );

-- No se editan ni eliminan ítems de pedidos confirmados
-- Solo el admin en casos excepcionales
CREATE POLICY "order_items_delete_admin"
    ON public.order_items FOR DELETE
    USING (public.get_my_role() = 'admin');


-- ============================================================
-- HABILITAR REALTIME en la tabla orders
-- Permite que la app Android reciba cambios de estado en vivo
-- sin hacer polling (preguntar cada X segundos).
-- ============================================================
ALTER PUBLICATION supabase_realtime ADD TABLE public.orders;


-- ============================================================
-- FIN DEL RLS
-- ============================================================
