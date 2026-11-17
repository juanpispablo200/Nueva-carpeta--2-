-- ============================================================
-- Webhook: disparar Edge Function cuando orders.status = 'ready'
-- Ejecutar en SQL Editor después de desplegar la Edge Function
-- ============================================================

-- Supabase Webhooks se configuran en Dashboard → Database → Webhooks
-- O via la API de gestión. Este SQL documenta la configuración esperada.

-- Alternativa: crear un trigger PostgreSQL que llame a la función via pg_net
-- (requiere extensión pg_net, disponible en Supabase)

CREATE OR REPLACE FUNCTION public.notify_repartidores_on_ready()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  -- Solo disparar cuando status cambia A 'ready'
  IF NEW.status = 'ready' AND (OLD.status IS NULL OR OLD.status <> 'ready') THEN
    -- Invocar la Edge Function de forma asíncrona via pg_net
    PERFORM net.http_post(
      url     := current_setting('app.supabase_url') || '/functions/v1/notify-repartidores',
      headers := jsonb_build_object(
        'Content-Type',  'application/json',
        'Authorization', 'Bearer ' || current_setting('app.service_role_key')
      ),
      body    := jsonb_build_object(
        'type',       'UPDATE',
        'table',      'orders',
        'record',     row_to_json(NEW)
      )::text
    );
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_order_ready ON public.orders;
CREATE TRIGGER on_order_ready
  AFTER UPDATE ON public.orders
  FOR EACH ROW
  EXECUTE FUNCTION public.notify_repartidores_on_ready();

-- NOTA: configurar las variables en Supabase Dashboard:
--   ALTER DATABASE postgres SET app.supabase_url = 'https://TUPROYECTO.supabase.co';
--   ALTER DATABASE postgres SET app.service_role_key = 'eyJ...';
