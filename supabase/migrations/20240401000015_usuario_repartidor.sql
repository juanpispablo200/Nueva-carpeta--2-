-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Crear usuarios repartidor (solo desarrollo/staging)
-- ⚠️  SOLO PARA ENTORNOS DE DESARROLLO. No ejecutar en producción con estas
--     credenciales. En producción, crea los usuarios desde el Dashboard de
--     Supabase (Authentication → Users → Add user) con contraseñas seguras.
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- Repartidor de prueba
-- Email: repartidor@jagoo.test
-- Contraseña: CAMBIAR antes de usar en staging
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
  v_id   uuid;
  -- ⚠️ DESARROLLO ÚNICAMENTE — reemplazar con contraseña segura en staging
  v_pass text := 'Repartidor2024!';
BEGIN
  SELECT id INTO v_id FROM auth.users WHERE email = 'repartidor@jagoo.test';

  IF v_id IS NULL THEN
    v_id := gen_random_uuid();
    INSERT INTO auth.users (
      id, instance_id, aud, role, email,
      encrypted_password, email_confirmed_at,
      created_at, updated_at,
      raw_app_meta_data, raw_user_meta_data
    ) VALUES (
      v_id,
      '00000000-0000-0000-0000-000000000000',
      'authenticated', 'authenticated',
      'repartidor@jagoo.test',
      crypt(v_pass, gen_salt('bf')),
      now(), now(), now(),
      '{"provider":"email","providers":["email"]}',
      '{"full_name":"Repartidor Test"}'
    );
    RAISE NOTICE 'Creado: repartidor@jagoo.test';
  ELSE
    UPDATE auth.users
    SET encrypted_password = crypt(v_pass, gen_salt('bf')),
        email_confirmed_at = now(),
        updated_at         = now()
    WHERE id = v_id;
    RAISE NOTICE 'Actualizado: repartidor@jagoo.test';
  END IF;

  INSERT INTO profiles (id, full_name, phone, role, is_active, is_available)
  VALUES (v_id, 'Repartidor Test', '0990000001', 'repartidor', true, false)
  ON CONFLICT (id) DO UPDATE
    SET role         = 'repartidor',
        is_active    = true,
        is_available = false,
        full_name    = 'Repartidor Test';

  RAISE NOTICE '✅ [DEV] repartidor@jagoo.test lista';
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- Gabriel (usuario de prueba personal)
-- Email: gabriel900998@gmail.com
-- ⚠️  Contraseña débil — solo desarrollo local
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
  v_id   uuid;
  -- ⚠️ DESARROLLO ÚNICAMENTE
  v_pass text := '123456';
BEGIN
  SELECT id INTO v_id FROM auth.users WHERE email = 'gabriel900998@gmail.com';

  IF v_id IS NULL THEN
    v_id := gen_random_uuid();
    INSERT INTO auth.users (
      id, instance_id, aud, role, email,
      encrypted_password, email_confirmed_at,
      created_at, updated_at,
      raw_app_meta_data, raw_user_meta_data
    ) VALUES (
      v_id,
      '00000000-0000-0000-0000-000000000000',
      'authenticated', 'authenticated',
      'gabriel900998@gmail.com',
      crypt(v_pass, gen_salt('bf')),
      now(), now(), now(),
      '{"provider":"email","providers":["email"]}',
      '{"full_name":"Gabriel"}'
    );
    RAISE NOTICE 'Creado: gabriel900998@gmail.com';
  ELSE
    UPDATE auth.users
    SET encrypted_password = crypt(v_pass, gen_salt('bf')),
        email_confirmed_at = now(),
        updated_at         = now()
    WHERE id = v_id;
    RAISE NOTICE 'Actualizado: gabriel900998@gmail.com';
  END IF;

  INSERT INTO profiles (id, full_name, phone, role, is_active, is_available)
  VALUES (v_id, 'Gabriel', '0999000998', 'repartidor', true, false)
  ON CONFLICT (id) DO UPDATE
    SET role         = 'repartidor',
        is_active    = true,
        is_available = false,
        full_name    = 'Gabriel';

  RAISE NOTICE '✅ [DEV] gabriel900998@gmail.com lista';
END;
$$;

-- Verificación
SELECT u.email, p.role, p.full_name, p.is_active
FROM auth.users u
JOIN profiles p ON p.id = u.id
WHERE p.role = 'repartidor'
ORDER BY u.created_at;
