-- ============================================================
-- JAGOO — Rol repartidor, tablas y estados extendidos
-- Ejecutar DESPUÉS de 003_rls.sql
-- ============================================================
SET client_encoding = 'UTF8';

-- 1. Agregar rol 'repartidor' al CHECK de profiles
ALTER TABLE public.profiles
  DROP CONSTRAINT IF EXISTS profiles_role_check;

ALTER TABLE public.profiles
  ADD CONSTRAINT profiles_role_check
  CHECK (role IN ('customer','restaurant','admin','repartidor'));

-- 2. Tabla repartidores (perfil operativo)
CREATE TABLE IF NOT EXISTS public.repartidores (
    id              UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    vehicle_type    TEXT NOT NULL DEFAULT 'moto',   -- moto, bici, carro
    plate           TEXT NOT NULL DEFAULT '',
    is_available    BOOLEAN NOT NULL DEFAULT FALSE,  -- toggle disponible/no disponible
    current_lat     NUMERIC(10,7),
    current_lng     NUMERIC(10,7),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Device tokens para push (FCM)
CREATE TABLE IF NOT EXISTS public.device_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    token       TEXT NOT NULL,
    platform    TEXT NOT NULL DEFAULT 'android',  -- android, ios, web
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, token)
);
CREATE INDEX IF NOT EXISTS idx_device_tokens_user ON public.device_tokens(user_id);

-- 4. Extender orders con repartidor y nuevos estados
ALTER TABLE public.orders
  ADD COLUMN IF NOT EXISTS repartidor_id UUID REFERENCES public.repartidores(id) ON DELETE SET NULL;

ALTER TABLE public.orders
  DROP CONSTRAINT IF EXISTS orders_status_check;

ALTER TABLE public.orders
  ADD CONSTRAINT orders_status_check
  CHECK (status IN (
    'pending',    -- cliente confirma, espera restaurante
    'accepted',   -- restaurante aceptó
    'preparing',  -- en cocina
    'ready',      -- listo para recoger (repartidor notificado)
    'picked_up',  -- repartidor recogió
    'on_the_way', -- en camino al cliente
    'delivered',  -- entregado
    'cancelled'
  ));

CREATE INDEX IF NOT EXISTS idx_orders_repartidor ON public.orders(repartidor_id);

-- 5. RLS repartidores ─────────────────────────────────────────

ALTER TABLE public.repartidores ENABLE ROW LEVEL SECURITY;

-- El repartidor solo ve/edita su propia fila
CREATE POLICY "repartidores_own"
  ON public.repartidores FOR ALL
  USING (id = auth.uid() OR public.get_my_role() = 'admin')
  WITH CHECK (id = auth.uid() OR public.get_my_role() = 'admin');

ALTER TABLE public.device_tokens ENABLE ROW LEVEL SECURITY;

-- Cada usuario gestiona sus propios tokens
CREATE POLICY "device_tokens_own"
  ON public.device_tokens FOR ALL
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

-- 6. Actualizar política de orders para repartidores ─────────────
DROP POLICY IF EXISTS "orders_select" ON public.orders;
CREATE POLICY "orders_select"
  ON public.orders FOR SELECT
  USING (
    customer_id   = auth.uid()
    OR restaurant_id = public.get_my_restaurant_id()
    OR repartidor_id  = auth.uid()
    OR public.get_my_role() IN ('admin','repartidor')
  );

-- El repartidor puede actualizar el status de pedidos que le pertenecen
DROP POLICY IF EXISTS "orders_update_status" ON public.orders;
CREATE POLICY "orders_update_status"
  ON public.orders FOR UPDATE
  USING (
    restaurant_id = public.get_my_restaurant_id()
    OR repartidor_id = auth.uid()
    OR (customer_id = auth.uid() AND status = 'pending')
    OR public.get_my_role() = 'admin'
  );

-- Función: asignación atómica de repartidor (evita race condition)
CREATE OR REPLACE FUNCTION public.accept_order(p_order_id UUID, p_repartidor_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  rows_updated INT;
BEGIN
  UPDATE public.orders
  SET repartidor_id = p_repartidor_id,
      status        = 'picked_up',
      updated_at    = NOW()
  WHERE id = p_order_id
    AND status = 'ready'
    AND repartidor_id IS NULL;  -- solo si aún no tiene repartidor

  GET DIAGNOSTICS rows_updated = ROW_COUNT;
  RETURN rows_updated > 0;
END;
$$;

-- Realtime también en repartidores (para tracking de ubicación)
ALTER PUBLICATION supabase_realtime ADD TABLE public.repartidores;
