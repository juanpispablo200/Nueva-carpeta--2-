-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Crear usuario repartidor: gabriel900998@gmail.com
-- ══════════════════════════════════════════════════════════════════════════════

DO $$
DECLARE
  v_id uuid;
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
      crypt('123456', gen_salt('bf')),
      now(), now(), now(),
      '{"provider":"email","providers":["email"]}',
      '{"full_name":"Gabriel"}'
    );
    RAISE NOTICE 'Usuario creado: gabriel900998@gmail.com';
  ELSE
    UPDATE auth.users
    SET encrypted_password = crypt('123456', gen_salt('bf')),
        email_confirmed_at = now(),
        updated_at         = now()
    WHERE id = v_id;
    RAISE NOTICE 'Contraseña actualizada: gabriel900998@gmail.com';
  END IF;

  INSERT INTO profiles (id, full_name, phone, role, is_active, is_available)
  VALUES (v_id, 'Gabriel', '', 'repartidor', true, false)
  ON CONFLICT (id) DO UPDATE
    SET full_name    = 'Gabriel',
        role         = 'repartidor',
        is_active    = true,
        is_available = false;

  RAISE NOTICE '✅ Repartidor listo';
END;
$$;

-- Verificar
SELECT u.email, p.role, p.full_name, p.is_active
FROM auth.users u
JOIN profiles p ON p.id = u.id
WHERE u.email = 'gabriel900998@gmail.com';
