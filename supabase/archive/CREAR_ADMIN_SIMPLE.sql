-- ══════════════════════════════════════════════════════════════════════════════
-- CREAR USUARIO ADMIN SIMPLE - Sin errores
-- Copia y pega TODO este SQL en Supabase SQL Editor
-- ══════════════════════════════════════════════════════════════════════════════

-- Paso 1: Eliminar usuario admin si existe (para empezar limpio)
DELETE FROM profiles WHERE email = 'admin@jagoo.com';
DELETE FROM auth.users WHERE email = 'admin@jagoo.com';

-- Paso 2: Crear usuario en auth.users
INSERT INTO auth.users (
  instance_id,
  id,
  aud,
  role,
  email,
  encrypted_password,
  email_confirmed_at,
  invited_at,
  confirmation_token,
  confirmation_sent_at,
  recovery_token,
  recovery_sent_at,
  email_change_token_new,
  email_change,
  email_change_sent_at,
  last_sign_in_at,
  raw_app_meta_data,
  raw_user_meta_data,
  is_super_admin,
  created_at,
  updated_at,
  phone,
  phone_confirmed_at,
  phone_change,
  phone_change_token,
  phone_change_sent_at,
  email_change_token_current,
  email_change_confirm_status,
  banned_until,
  reauthentication_token,
  reauthentication_sent_at
) VALUES (
  '00000000-0000-0000-0000-000000000000',
  gen_random_uuid(),
  'authenticated',
  'authenticated',
  'admin@jagoo.com',
  crypt('Admin123', gen_salt('bf')),
  NOW(),
  NULL,
  '',
  NULL,
  '',
  NULL,
  '',
  '',
  NULL,
  NULL,
  '{"provider":"email","providers":["email"]}',
  '{}',
  NULL,
  NOW(),
  NOW(),
  NULL,
  NULL,
  '',
  '',
  NULL,
  '',
  0,
  NULL,
  '',
  NULL
);

-- Paso 3: Obtener el ID del usuario recién creado y crear perfil
INSERT INTO profiles (id, full_name, phone, role, is_active, email)
SELECT 
  id,
  'Administrador Principal',
  '+593999999999',
  'admin',
  true,
  'admin@jagoo.com'
FROM auth.users 
WHERE email = 'admin@jagoo.com';

-- Paso 4: Verificar que se creó correctamente
SELECT 
  'Usuario Admin Creado Exitosamente' as mensaje,
  u.email,
  u.id,
  p.full_name,
  p.role,
  p.is_active
FROM auth.users u
JOIN profiles p ON u.id = p.id
WHERE u.email = 'admin@jagoo.com';

-- ══════════════════════════════════════════════════════════════════════════════
-- CREDENCIALES DE ACCESO:
-- Email: admin@jagoo.com
-- Contraseña: Admin123
-- URL: http://localhost:5173/login
-- ══════════════════════════════════════════════════════════════════════════════