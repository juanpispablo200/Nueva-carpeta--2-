-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Retención automática de datos
--
-- Lo que hace este job es EXACTAMENTE lo que declara la Política de Privacidad
-- (web/src/pages/PrivacyPolicyPage.tsx, sección 9). Si cambias un plazo aquí,
-- cámbialo también en la política.
--
--   Dato                                   Plazo            Acción
--   ───────────────────────────────────────────────────────────────────────────
--   Foto de receta                         30 días tras     Se borra el archivo y
--                                          entregar/cancelar la ruta del pedido
--   Comprobante de transferencia (foto)    365 días         Se borra el archivo,
--                                                           se vacía la referencia
--   Datos personales del pedido            730 días         Se anonimizan (cliente,
--                                                           dirección, notas)
--   Importes, fechas y reparto             Se conservan     Obligación contable
--                                                           (REVISAR plazo legal)
--   Historial de notificaciones            90 días          Se borra
--   Última posición del repartidor         24 horas         Se borra
--
-- Los archivos de Storage no se borran por SQL: el job los pone en
-- storage_deletion_queue y la Edge Function purge-storage-queue los elimina
-- con la API de Storage.
--
-- Idempotente.
-- ══════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.storage_deletion_queue (
  id         bigserial PRIMARY KEY,
  bucket     text NOT NULL,
  path       text NOT NULL,
  queued_at  timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz,
  attempts   integer NOT NULL DEFAULT 0,
  UNIQUE (bucket, path)
);

CREATE INDEX IF NOT EXISTS idx_storage_queue_pending
  ON public.storage_deletion_queue (queued_at) WHERE deleted_at IS NULL;

ALTER TABLE public.storage_deletion_queue ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.storage_deletion_queue FROM anon, authenticated;

CREATE OR REPLACE FUNCTION public.run_data_retention()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_rx      integer := 0;
  v_proofs  integer := 0;
  v_orders  integer := 0;
  v_notifs  integer := 0;
  v_loc     integer := 0;
BEGIN
  -- 1. Recetas: 30 días después de entregar o cancelar
  WITH due AS (
    SELECT id, prescription_path FROM public.orders
     WHERE prescription_path IS NOT NULL
       AND status IN ('delivered', 'cancelled')
       AND updated_at < now() - interval '30 days'
  ), queued AS (
    INSERT INTO public.storage_deletion_queue (bucket, path)
    SELECT 'prescriptions', prescription_path FROM due
    ON CONFLICT (bucket, path) DO NOTHING
    RETURNING 1
  ), cleared AS (
    UPDATE public.orders o
       SET prescription_path = NULL, prescription_note = NULL
      FROM due WHERE o.id = due.id
    RETURNING 1
  )
  SELECT count(*) INTO v_rx FROM cleared;

  -- 2. Comprobantes: 365 días
  WITH due AS (
    SELECT id, image_url FROM public.payment_proofs
     WHERE created_at < now() - interval '365 days'
       AND image_url <> '[eliminado]'
  ), queued AS (
    INSERT INTO public.storage_deletion_queue (bucket, path)
    SELECT 'payment-proofs', image_url FROM due
     WHERE image_url NOT LIKE 'http%'
    ON CONFLICT (bucket, path) DO NOTHING
    RETURNING 1
  ), cleared AS (
    UPDATE public.payment_proofs p
       SET image_url = '[eliminado]', reference_number = NULL
      FROM due WHERE p.id = due.id
    RETURNING 1
  )
  SELECT count(*) INTO v_proofs FROM cleared;

  -- 3. Pedidos: 730 días → se anonimizan los datos personales
  WITH old_orders AS (
    SELECT id FROM public.orders
     WHERE created_at < now() - interval '730 days'
       AND status IN ('delivered', 'cancelled')
       AND (customer_id IS NOT NULL OR delivery_address IS NOT NULL)
  ), items AS (
    UPDATE public.order_items i
       SET special_instructions = ''
      FROM old_orders WHERE i.order_id = old_orders.id
    RETURNING 1
  ), anon AS (
    UPDATE public.orders o
       SET customer_id = NULL, delivery_address = NULL, prescription_note = NULL
      FROM old_orders WHERE o.id = old_orders.id
    RETURNING 1
  )
  SELECT count(*) INTO v_orders FROM anon;

  -- 4. Notificaciones: 90 días
  IF to_regclass('public.notification_log') IS NOT NULL THEN
    WITH d AS (
      DELETE FROM public.notification_log WHERE sent_at < now() - interval '90 days' RETURNING 1
    )
    SELECT count(*) INTO v_notifs FROM d;
  END IF;

  -- 5. Última posición conocida del repartidor: 24 horas
  WITH l AS (
    UPDATE public.profiles
       SET current_lat = NULL, current_lng = NULL
     WHERE current_lat IS NOT NULL
       AND (location_updated_at IS NULL OR location_updated_at < now() - interval '24 hours')
    RETURNING 1
  )
  SELECT count(*) INTO v_loc FROM l;

  RETURN jsonb_build_object(
    'prescriptions_cleared', v_rx,
    'proofs_cleared',        v_proofs,
    'orders_anonymized',     v_orders,
    'notifications_deleted', v_notifs,
    'locations_cleared',     v_loc
  );
END;
$$;

REVOKE ALL ON FUNCTION public.run_data_retention() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.run_data_retention() TO service_role;

-- Programación: retención diaria 08:00 UTC (03:00 Ecuador) y purga de archivos cada hora
DO $$
BEGIN
  BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_cron;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'No se pudo crear pg_cron desde SQL: habilítala en Database → Extensions.';
  END;

  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
    PERFORM cron.unschedule(jobid) FROM cron.job WHERE jobname = 'jagoo-data-retention';
    PERFORM cron.schedule('jagoo-data-retention', '0 8 * * *', 'SELECT public.run_data_retention()');

    -- La purga llama a la Edge Function con pg_net (mismos ajustes app.* que las notificaciones push)
    PERFORM cron.unschedule(jobid) FROM cron.job WHERE jobname = 'jagoo-purge-storage';
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_net') THEN
      PERFORM cron.schedule(
        'jagoo-purge-storage',
        '15 * * * *',
        $job$SELECT net.http_post(
               url     := current_setting('app.edge_function_url', true) || '/purge-storage-queue',
               headers := jsonb_build_object(
                 'Content-Type',  'application/json',
                 'Authorization', 'Bearer ' || current_setting('app.service_role_key', true)),
               body    := '{}'::text)$job$
      );
    ELSE
      RAISE NOTICE 'pg_net no está habilitada: programa purge-storage-queue desde el panel de Supabase.';
    END IF;
  ELSE
    RAISE NOTICE '⚠ pg_cron NO está habilitada: la retención no correrá hasta habilitarla y reaplicar esta migración.';
  END IF;
END;
$$;

DO $$
BEGIN
  ASSERT NOT has_function_privilege('authenticated', 'public.run_data_retention()', 'EXECUTE'),
    'FALLA: authenticated puede ejecutar run_data_retention';
  RAISE NOTICE '✅ data_retention aplicada';
END;
$$;
