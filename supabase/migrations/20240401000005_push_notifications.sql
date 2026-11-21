-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Fase 6: Notificaciones push (FCM via Edge Function)
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Columna fcm_token en profiles
--    La app cliente y la Driver guardan aquí su token FCM al iniciar sesión.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE profiles
  ADD COLUMN IF NOT EXISTS fcm_token text;

-- Índice para buscar token por user_id rápidamente (lo usa la Edge Function)
CREATE INDEX IF NOT EXISTS idx_profiles_fcm_token
  ON profiles(id)
  WHERE fcm_token IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Tabla notification_log — historial de notificaciones enviadas
--    Útil para debug y para evitar duplicados.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notification_log (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid        REFERENCES profiles(id) ON DELETE SET NULL,
  order_id    uuid        REFERENCES orders(id)   ON DELETE SET NULL,
  type        text        NOT NULL,  -- 'order_status' | 'payment_verified' | 'new_order_driver'
  title       text        NOT NULL,
  body        text        NOT NULL,
  sent_at     timestamptz NOT NULL DEFAULT now(),
  success     boolean     NOT NULL DEFAULT true,
  error_msg   text
);

CREATE INDEX IF NOT EXISTS idx_notif_log_user  ON notification_log(user_id);
CREATE INDEX IF NOT EXISTS idx_notif_log_order ON notification_log(order_id);

ALTER TABLE notification_log ENABLE ROW LEVEL SECURITY;

-- Solo admin puede leer logs completos
DROP POLICY IF EXISTS notif_log_admin ON notification_log;
CREATE POLICY notif_log_admin ON notification_log
  FOR SELECT
  USING (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Función auxiliar notify_order_status_change()
--    Se llama desde el trigger de orders. Invoca la Edge Function via pg_net.
--    pg_net está disponible en Supabase por defecto.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION notify_order_status_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_title      text;
  v_body       text;
  v_type       text := 'order_status';
  v_project_id text;
BEGIN
  -- Solo disparar cuando el status cambia
  IF OLD.status = NEW.status AND OLD.payment_status IS NOT DISTINCT FROM NEW.payment_status THEN
    RETURN NEW;
  END IF;

  -- Obtener URL del proyecto desde settings (o usar variable de entorno)
  SELECT current_setting('app.supabase_url', true) INTO v_project_id;

  -- Construir título y cuerpo según el nuevo estado
  IF NEW.payment_status = 'verified' AND OLD.payment_status = 'verifying' THEN
    v_title := '✅ Pago verificado';
    v_body  := 'Tu pago fue confirmado. Tu pedido está siendo preparado.';
    v_type  := 'payment_verified';
  ELSE
    v_title := CASE NEW.status
      WHEN 'accepted'   THEN '📋 Pedido aceptado'
      WHEN 'preparing'  THEN '👨‍🍳 En preparación'
      WHEN 'ready'      THEN '✅ Pedido listo'
      WHEN 'on_the_way' THEN '🛵 Repartidor en camino'
      WHEN 'delivered'  THEN '🎉 ¡Pedido entregado!'
      WHEN 'cancelled'  THEN '❌ Pedido cancelado'
      ELSE                   '📦 Actualización de pedido'
    END;

    v_body := CASE NEW.status
      WHEN 'accepted'   THEN 'El restaurante aceptó tu pedido.'
      WHEN 'preparing'  THEN 'El restaurante está preparando tu pedido.'
      WHEN 'ready'      THEN 'Tu pedido está listo para ser recogido.'
      WHEN 'on_the_way' THEN 'Tu pedido va en camino. ¡Pronto llegará!'
      WHEN 'delivered'  THEN '¡Tu pedido fue entregado! Que lo disfrutes.'
      WHEN 'cancelled'  THEN 'Tu pedido fue cancelado.'
      ELSE                   'El estado de tu pedido fue actualizado.'
    END;
  END IF;

  -- Invocar la Edge Function send-push-notification via HTTP (pg_net)
  -- La Edge Function se encarga de leer el fcm_token del usuario y enviar a FCM
  PERFORM net.http_post(
    url     := current_setting('app.edge_function_url', true)
               || '/send-push-notification',
    headers := jsonb_build_object(
      'Content-Type',  'application/json',
      'Authorization', 'Bearer ' || current_setting('app.service_role_key', true)
    ),
    body    := jsonb_build_object(
      'user_id',  NEW.customer_id,
      'order_id', NEW.id,
      'type',     v_type,
      'title',    v_title,
      'body',     v_body
    )::text
  );

  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  -- No bloquear la transacción si la notificación falla
  RETURN NEW;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Trigger en orders
-- ─────────────────────────────────────────────────────────────────────────────
DROP TRIGGER IF EXISTS trg_order_status_notify ON orders;
CREATE TRIGGER trg_order_status_notify
  AFTER UPDATE OF status, payment_status ON orders
  FOR EACH ROW
  EXECUTE FUNCTION notify_order_status_change();

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'profiles' AND column_name = 'fcm_token'
  ), 'FALLA: profiles.fcm_token no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_name = 'notification_log'
  ), 'FALLA: tabla notification_log no existe';

  ASSERT EXISTS (
    SELECT 1 FROM information_schema.triggers
    WHERE trigger_name = 'trg_order_status_notify'
  ), 'FALLA: trigger trg_order_status_notify no existe';

  RAISE NOTICE '✅ FASE 6 push notifications: todas las verificaciones pasaron';
END;
$$;
