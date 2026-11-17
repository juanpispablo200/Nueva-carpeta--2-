-- ============================================================
-- JAGOO DELIVERY — Supabase Schema
-- Archivo: 001_schema.sql
-- Ejecutar en: Supabase → SQL Editor
-- ============================================================

-- ✅ Forzar codificación UTF-8 para evitar caracteres raros (ñ, tildes)
SET client_encoding = 'UTF8';


-- ============================================================
-- 1. TABLA: profiles
--    Reemplaza las tablas "roles" y "users" del SQLite.
--    Cada fila está enlazada a un usuario de auth.users.
--    El rol lo asigna el sistema, nunca el usuario.
-- ============================================================
CREATE TABLE IF NOT EXISTS public.profiles (
    id          UUID        PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name   TEXT        NOT NULL DEFAULT '',
    phone       TEXT        NOT NULL DEFAULT '',
    -- rol posible: 'customer', 'restaurant', 'admin'
    -- todos los nuevos usuarios entran como 'customer' (ver trigger abajo)
    role        TEXT        NOT NULL DEFAULT 'customer'
                            CHECK (role IN ('customer', 'restaurant', 'admin')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Índice para buscar perfiles por rol rápidamente (lo usa el admin)
CREATE INDEX IF NOT EXISTS idx_profiles_role ON public.profiles(role);


-- ============================================================
-- 2. TRIGGER: crear perfil automáticamente al registrarse
--    Cuando alguien se registra con Supabase Auth,
--    se crea su fila en profiles con role = 'customer'.
--    El usuario NUNCA puede elegir su propio rol.
-- ============================================================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER           -- corre con permisos de superusuario
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.profiles (id, full_name, phone, role)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'full_name', ''),
        COALESCE(NEW.raw_user_meta_data->>'phone', ''),
        'customer'          -- siempre customer, sin excepción
    );
    RETURN NEW;
END;
$$;

-- Conectar el trigger al evento de nuevo usuario en Auth
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_user();


-- ============================================================
-- 3. TABLA: restaurants
--    Equivalente a la tabla "restaurants" del SQLite.
-- ============================================================
CREATE TABLE IF NOT EXISTS public.restaurants (
    id              TEXT        PRIMARY KEY,   -- ej: "r1", "r2"
    name            TEXT        NOT NULL,
    category        TEXT        NOT NULL,
    description     TEXT        NOT NULL DEFAULT '',
    logo_url        TEXT        NOT NULL DEFAULT '',
    cover_url       TEXT        NOT NULL DEFAULT '',
    brand_color     TEXT        NOT NULL DEFAULT '#FFD600',
    rating          NUMERIC(3,1) NOT NULL DEFAULT 0.0,
    review_count    INTEGER     NOT NULL DEFAULT 0,
    delivery_time   TEXT        NOT NULL DEFAULT '30-40 min',
    delivery_fee    NUMERIC(10,2) NOT NULL DEFAULT 1.50,
    min_order       NUMERIC(10,2) NOT NULL DEFAULT 5.00,
    is_open         BOOLEAN     NOT NULL DEFAULT TRUE,
    is_featured     BOOLEAN     NOT NULL DEFAULT FALSE,
    is_offer        BOOLEAN     NOT NULL DEFAULT FALSE,
    offer_text      TEXT,
    -- id del usuario (profiles) que administra este restaurante
    owner_id        UUID        REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- ============================================================
-- 4. TABLA: menu_sections
--    Equivalente a "menu_sections" del SQLite.
--    Agrupa los platos por sección (ej: "Hamburguesas", "Postres")
-- ============================================================
CREATE TABLE IF NOT EXISTS public.menu_sections (
    id              TEXT        PRIMARY KEY,
    restaurant_id   TEXT        NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
    name            TEXT        NOT NULL,
    sort_order      INTEGER     NOT NULL DEFAULT 0,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE
);


-- ============================================================
-- 5. TABLA: dishes
--    Equivalente a "dishes" + "option_groups" + "options" del SQLite.
--    Los grupos de opciones se guardan como JSON para simplificar.
-- ============================================================
CREATE TABLE IF NOT EXISTS public.dishes (
    id              TEXT        PRIMARY KEY,
    restaurant_id   TEXT        NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
    restaurant_name TEXT        NOT NULL DEFAULT '',
    section_name    TEXT        NOT NULL,
    name            TEXT        NOT NULL,
    description     TEXT        NOT NULL DEFAULT '',
    price           NUMERIC(10,2) NOT NULL,
    image_url       TEXT        NOT NULL DEFAULT '',
    is_available    BOOLEAN     NOT NULL DEFAULT TRUE,
    is_popular      BOOLEAN     NOT NULL DEFAULT FALSE,
    is_offer        BOOLEAN     NOT NULL DEFAULT FALSE,
    offer_badge     TEXT,
    -- Grupos de opciones guardados como JSON
    -- Ejemplo: [{"id":"og1","title":"Término","isRequired":true,"maxSelection":1,
    --            "options":[{"id":"opt1","name":"Medio","price":0}]}]
    option_groups   JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Índice para filtrar platos por restaurante rápidamente
CREATE INDEX IF NOT EXISTS idx_dishes_restaurant ON public.dishes(restaurant_id);


-- ============================================================
-- 6. TABLA: addresses
--    Equivalente a "addresses" del SQLite.
--    Cada dirección pertenece a un usuario (customer).
-- ============================================================
CREATE TABLE IF NOT EXISTS public.addresses (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    label           TEXT        NOT NULL DEFAULT 'Casa',   -- "Casa", "Trabajo", etc.
    full_address    TEXT        NOT NULL,
    is_primary      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Índice para buscar direcciones de un usuario
CREATE INDEX IF NOT EXISTS idx_addresses_user ON public.addresses(user_id);


-- ============================================================
-- 7. TABLA: orders
--    Equivalente a "orders" del SQLite.
--    status sigue el ciclo de vida real de un pedido.
-- ============================================================
CREATE TABLE IF NOT EXISTS public.orders (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- El cliente que hizo el pedido
    customer_id     UUID        NOT NULL REFERENCES public.profiles(id),
    -- El restaurante al que se le hizo el pedido
    restaurant_id   TEXT        NOT NULL REFERENCES public.restaurants(id),
    restaurant_name TEXT        NOT NULL,
    restaurant_logo_url TEXT    NOT NULL DEFAULT '',
    subtotal        NUMERIC(10,2) NOT NULL,
    delivery_fee    NUMERIC(10,2) NOT NULL,
    total           NUMERIC(10,2) NOT NULL,
    -- Ciclo de vida del pedido (en orden):
    -- pending     → recibido, esperando que el restaurante acepte
    -- accepted    → restaurante aceptó
    -- preparing   → en cocina
    -- on_the_way  → repartidor en camino
    -- delivered   → entregado
    -- cancelled   → cancelado
    status          TEXT        NOT NULL DEFAULT 'pending'
                                CHECK (status IN (
                                    'pending','accepted','preparing',
                                    'on_the_way','delivered','cancelled'
                                )),
    -- Dirección de entrega (texto simple, sin FK para no bloquear)
    delivery_address TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Índices para las dos consultas más frecuentes
CREATE INDEX IF NOT EXISTS idx_orders_customer    ON public.orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_restaurant  ON public.orders(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_orders_status      ON public.orders(status);

-- Trigger que actualiza updated_at automáticamente al cambiar el status
CREATE OR REPLACE FUNCTION public.update_order_timestamp()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_order_updated ON public.orders;
CREATE TRIGGER on_order_updated
    BEFORE UPDATE ON public.orders
    FOR EACH ROW
    EXECUTE FUNCTION public.update_order_timestamp();


-- ============================================================
-- 8. TABLA: order_items
--    Equivalente a "order_items" del SQLite.
--    unit_price incluye el precio base + opciones seleccionadas.
-- ============================================================
CREATE TABLE IF NOT EXISTS public.order_items (
    id                      UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                UUID    NOT NULL REFERENCES public.orders(id) ON DELETE CASCADE,
    dish_id                 TEXT    NOT NULL,
    dish_name               TEXT    NOT NULL,
    -- precio unitario EN EL MOMENTO del pedido (incluye opciones)
    -- guardado aquí para que no cambie si el restaurante edita el precio después
    unit_price              NUMERIC(10,2) NOT NULL,
    quantity                INTEGER NOT NULL CHECK (quantity > 0),
    -- opciones elegidas guardadas como JSON
    -- Ejemplo: [{"optionGroupId":"og1","optionName":"Término Medio","optionPrice":0}]
    selected_options_json   JSONB   NOT NULL DEFAULT '[]'::jsonb,
    special_instructions    TEXT    NOT NULL DEFAULT ''
);

-- Índice para recuperar todos los ítems de un pedido
CREATE INDEX IF NOT EXISTS idx_order_items_order ON public.order_items(order_id);


-- ============================================================
-- FIN DEL SCHEMA
-- ============================================================
