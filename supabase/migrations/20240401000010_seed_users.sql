-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Seed de usuarios admin y restaurante
-- Idempotente: crea el usuario si no existe, actualiza contraseña si ya existe
-- ══════════════════════════════════════════════════════════════════════════════

DO $$
DECLARE
  v_admin_id      uuid;
  v_rest_id       uuid;
BEGIN

  -- ── 1. ADMIN ──────────────────────────────────────────────────────────────

  SELECT id INTO v_admin_id
  FROM auth.users
  WHERE email = 'admin@jagoo.com';

  IF v_admin_id IS NULL THEN
    -- Crear usuario admin
    v_admin_id := gen_random_uuid();

    INSERT INTO auth.users (
      id, instance_id, email, encrypted_password,
      email_confirmed_at, created_at, updated_at,
      raw_app_meta_data, raw_user_meta_data,
      is_super_admin, role, aud
    ) VALUES (
      v_admin_id,
      '00000000-0000-0000-0000-000000000000',
      'admin@jagoo.com',
      crypt('Admin2024!', gen_salt('bf')),
      now(), now(), now(),
      '{"provider":"email","providers":["email"]}',
      '{"full_name":"Administrador Jagoo"}',
      false, 'authenticated', 'authenticated'
    );

    RAISE NOTICE 'Admin creado: admin@jagoo.com / Admin2024!';
  ELSE
    -- Actualizar contraseña
    UPDATE auth.users
    SET
      encrypted_password = crypt('Admin2024!', gen_salt('bf')),
      email_confirmed_at = COALESCE(email_confirmed_at, now()),
      updated_at         = now()
    WHERE id = v_admin_id;

    RAISE NOTICE 'Admin actualizado: admin@jagoo.com / Admin2024!';
  END IF;

  -- Asegurar perfil con rol admin
  INSERT INTO public.profiles (id, full_name, phone, role, is_active)
  VALUES (v_admin_id, 'Administrador Jagoo', '', 'admin', true)
  ON CONFLICT (id) DO UPDATE
    SET role      = 'admin',
        is_active = true;


  -- ── 2. RESTAURANTE ────────────────────────────────────────────────────────

  SELECT id INTO v_rest_id
  FROM auth.users
  WHERE email = 'restaurante@jagoo.com';

  IF v_rest_id IS NULL THEN
    v_rest_id := gen_random_uuid();

    INSERT INTO auth.users (
      id, instance_id, email, encrypted_password,
      email_confirmed_at, created_at, updated_at,
      raw_app_meta_data, raw_user_meta_data,
      is_super_admin, role, aud
    ) VALUES (
      v_rest_id,
      '00000000-0000-0000-0000-000000000000',
      'restaurante@jagoo.com',
      crypt('Rest2024!', gen_salt('bf')),
      now(), now(), now(),
      '{"provider":"email","providers":["email"]}',
      '{"full_name":"Restaurante Demo"}',
      false, 'authenticated', 'authenticated'
    );

    RAISE NOTICE 'Restaurante creado: restaurante@jagoo.com / Rest2024!';
  ELSE
    UPDATE auth.users
    SET
      encrypted_password = crypt('Rest2024!', gen_salt('bf')),
      email_confirmed_at = COALESCE(email_confirmed_at, now()),
      updated_at         = now()
    WHERE id = v_rest_id;

    RAISE NOTICE 'Restaurante actualizado: restaurante@jagoo.com / Rest2024!';
  END IF;

  -- Asegurar perfil con rol restaurant
  INSERT INTO public.profiles (id, full_name, phone, role, is_active)
  VALUES (v_rest_id, 'Restaurante Demo', '', 'restaurant', true)
  ON CONFLICT (id) DO UPDATE
    SET role      = 'restaurant',
        is_active = true;


  -- ── 3. Resumen ─────────────────────────────────────────────────────────────
  RAISE NOTICE '';
  RAISE NOTICE '✅ Usuarios listos:';
  RAISE NOTICE '   Admin:       admin@jagoo.com       / Admin2024!';
  RAISE NOTICE '   Restaurante: restaurante@jagoo.com / Rest2024!';

END;
$$;

-- Verificar resultado
SELECT
  u.email,
  p.role,
  p.is_active,
  u.email_confirmed_at IS NOT NULL AS email_confirmado
FROM auth.users u
JOIN public.profiles p ON p.id = u.id
WHERE u.email IN ('admin@jagoo.com', 'restaurante@jagoo.com')
ORDER BY p.role;
