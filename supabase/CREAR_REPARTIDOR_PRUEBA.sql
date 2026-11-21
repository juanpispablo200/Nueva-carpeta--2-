-- ============================================================================
-- JAGOO — CREAR USUARIO REPARTIDOR DE PRUEBA EN SUPABASE
-- ============================================================================
-- Ejecuta este script en Supabase SQL Editor para crear o actualizar el usuario.
--
-- Credenciales de inicio de sesión:
--   Email:       carlos@jagoo.com   (o repartidor@jagoo.com)
--   Contraseña:  Repartidor123!     (o Repartidor123)
--   Rol:         repartidor
--   Estado:      is_active = true | is_available = true
-- ============================================================================

DO $$
DECLARE
    v_user_carlos UUID := '11111111-2222-3333-4444-555555555555';
    v_user_repartidor UUID := '22222222-3333-4444-5555-666666666666';
    v_encrypted_p1 TEXT := crypt('Repartidor123!', gen_salt('bf'));
    v_encrypted_p2 TEXT := crypt('Repartidor123', gen_salt('bf'));
BEGIN
    -- 1. Crear / actualizar carlos@jagoo.com (con Repartidor123! y Repartidor123)
    INSERT INTO auth.users (
        id, instance_id, email, encrypted_password, email_confirmed_at,
        raw_app_meta_data, raw_user_meta_data, role, aud, created_at, updated_at
    )
    VALUES (
        v_user_carlos, '00000000-0000-0000-0000-000000000000', 'carlos@jagoo.com',
        v_encrypted_p1, NOW(), '{"provider": "email", "providers": ["email"]}'::jsonb,
        '{"full_name": "Carlos Repartidor", "role": "repartidor"}'::jsonb,
        'authenticated', 'authenticated', NOW(), NOW()
    )
    ON CONFLICT (id) DO UPDATE SET
        encrypted_password = v_encrypted_p1,
        email_confirmed_at = NOW(),
        raw_user_meta_data = '{"full_name": "Carlos Repartidor", "role": "repartidor"}'::jsonb;

    INSERT INTO public.profiles (id, full_name, phone, role, is_active, is_available)
    VALUES (v_user_carlos, 'Carlos Repartidor', '+593 99 888 7777', 'repartidor', true, true)
    ON CONFLICT (id) DO UPDATE SET
        full_name = 'Carlos Repartidor', role = 'repartidor', is_active = true, is_available = true;

    -- 2. Crear / actualizar repartidor@jagoo.com
    INSERT INTO auth.users (
        id, instance_id, email, encrypted_password, email_confirmed_at,
        raw_app_meta_data, raw_user_meta_data, role, aud, created_at, updated_at
    )
    VALUES (
        v_user_repartidor, '00000000-0000-0000-0000-000000000000', 'repartidor@jagoo.com',
        v_encrypted_p1, NOW(), '{"provider": "email", "providers": ["email"]}'::jsonb,
        '{"full_name": "Repartidor Jagoo", "role": "repartidor"}'::jsonb,
        'authenticated', 'authenticated', NOW(), NOW()
    )
    ON CONFLICT (id) DO UPDATE SET
        encrypted_password = v_encrypted_p1,
        email_confirmed_at = NOW(),
        raw_user_meta_data = '{"full_name": "Repartidor Jagoo", "role": "repartidor"}'::jsonb;

    INSERT INTO public.profiles (id, full_name, phone, role, is_active, is_available)
    VALUES (v_user_repartidor, 'Repartidor Jagoo', '+593 99 111 2222', 'repartidor', true, true)
    ON CONFLICT (id) DO UPDATE SET
        full_name = 'Repartidor Jagoo', role = 'repartidor', is_active = true, is_available = true;

END $$;

SELECT '✅ REPARTIDORES CREADOS' AS resultado,
       'Puedes ingresar con email: carlos@jagoo.com o repartidor@jagoo.com con contraseña: Repartidor123!' AS instrucciones;
