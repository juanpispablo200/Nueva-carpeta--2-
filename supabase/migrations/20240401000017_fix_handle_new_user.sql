-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Fix trigger handle_new_user
-- Garantiza que el perfil usa los datos del usuario NUEVO (NEW),
-- nunca auth.uid() ni datos del usuario actual que ejecuta la operación.
-- ══════════════════════════════════════════════════════════════════════════════

-- El trigger corre con SECURITY DEFINER como superusuario,
-- por lo que NEW.id siempre es el ID del usuario recién creado.
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.profiles (id, full_name, phone, role, is_active)
  VALUES (
    NEW.id,                                                        -- ID del usuario nuevo
    COALESCE(NEW.raw_user_meta_data->>'full_name', ''),           -- nombre del formulario
    COALESCE(NEW.raw_user_meta_data->>'phone', ''),               -- teléfono del formulario
    'customer',                                                    -- rol por defecto
    true
  )
  ON CONFLICT (id) DO NOTHING;  -- si la Edge Function ya insertó el perfil, no pisar
  RETURN NEW;
END;
$$;

-- Recrear trigger (por si no existe)
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_new_user();

-- Verificación
DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM information_schema.triggers
    WHERE trigger_name = 'on_auth_user_created'
  ), 'FALLA: trigger on_auth_user_created no existe';
  RAISE NOTICE '✅ handle_new_user corregido — usa datos del usuario nuevo';
END;
$$;
