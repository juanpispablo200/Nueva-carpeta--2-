-- ══════════════════════════════════════════════════════════════════════════════
-- RESET COMPLETO JAGOO - Limpiar todo y dejar solo admin + restaurantes básicos
-- ══════════════════════════════════════════════════════════════════════════════

-- ── 1. ELIMINAR DATOS EXISTENTES (en orden correcto para evitar errores FK) ────
-- Desactivar triggers temporalmente para evitar errores
SET session_replication_role = 'replica';

-- Limpiar en orden inverso de dependencias
DELETE FROM order_events;
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM dishes;
DELETE FROM restaurants;
DELETE FROM audit_log;
DELETE FROM profiles WHERE email != 'admin@jagoo.com'; -- Mantener solo admin
DELETE FROM auth.users WHERE email != 'admin@jagoo.com'; -- Mantener solo admin

-- Reactivar triggers
SET session_replication_role = 'origin';

-- ── 2. CREAR/USAR USUARIO ADMIN SI NO EXISTE ───────────────────────────────────
DO $$
DECLARE
  admin_id uuid;
BEGIN
  -- Verificar si ya existe el admin
  SELECT id INTO admin_id FROM auth.users WHERE email = 'admin@jagoo.com';
  
  IF admin_id IS NULL THEN
    -- Crear nuevo usuario admin
    INSERT INTO auth.users (
      id, instance_id, email, encrypted_password, email_confirmed_at,
      raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
      confirmation_token, email_change_token, recovery_token
    ) VALUES (
      gen_random_uuid(),
      '00000000-0000-0000-0000-000000000000',
      'admin@jagoo.com',
      crypt('Admin123', gen_salt('bf')),
      NOW(),
      '{"provider":"email","providers":["email"]}',
      '{"role":"admin"}',
      NOW(),
      NOW(),
      '',
      '',
      ''
    ) RETURNING id INTO admin_id;
    
    RAISE NOTICE 'Usuario admin creado con ID: %', admin_id;
  ELSE
    RAISE NOTICE 'Usuario admin ya existe con ID: %', admin_id;
    -- Actualizar contraseña por si acaso
    UPDATE auth.users 
    SET encrypted_password = crypt('Admin123', gen_salt('bf'))
    WHERE id = admin_id;
  END IF;
  
  -- Asegurar perfil admin
  INSERT INTO profiles (id, full_name, phone, role, is_active, email)
  VALUES (
    admin_id,
    'Administrador Principal',
    '+593999999999',
    'admin',
    true,
    'admin@jagoo.com'
  )
  ON CONFLICT (id) DO UPDATE 
  SET 
    full_name = 'Administrador Principal',
    phone = '+593999999999',
    role = 'admin',
    is_active = true,
    email = 'admin@jagoo.com';
    
  RAISE NOTICE 'Perfil admin configurado';
END $$;

-- ── 3. CREAR RESTAURANTES DE EJEMPLO ──────────────────────────────────────────
DO $$
DECLARE
  rest1_id uuid := gen_random_uuid();
  rest2_id uuid := gen_random_uuid();
  rest3_id uuid := gen_random_uuid();
BEGIN
  -- Restaurante 1: Hamburguesas
  INSERT INTO restaurants (id, name, description, address, phone, rating, delivery_time, image_url, is_active, owner_id)
  VALUES (
    rest1_id,
    'Burger Master',
    'Las mejores hamburguesas artesanales de la ciudad',
    'Av. Amazonas y Naciones Unidas, Quito',
    '+593 2 2345678',
    4.5,
    '25-35 min',
    'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400',
    true,
    (SELECT id FROM auth.users WHERE email = 'admin@jagoo.com')
  );
  
  -- Restaurante 2: Pizza
  INSERT INTO restaurants (id, name, description, address, phone, rating, delivery_time, image_url, is_active, owner_id)
  VALUES (
    rest2_id,
    'Pizza Italia',
    'Auténtica pizza italiana con ingredientes frescos',
    'Av. 6 de Diciembre y Colón, Quito',
    '+593 2 3456789',
    4.7,
    '30-40 min',
    'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w-400',
    true,
    (SELECT id FROM auth.users WHERE email = 'admin@jagoo.com')
  );
  
  -- Restaurante 3: Sushi
  INSERT INTO restaurants (id, name, description, address, phone, rating, delivery_time, image_url, is_active, owner_id)
  VALUES (
    rest3_id,
    'Sushi Express',
    'Sushi fresco preparado al momento',
    'Av. González Suárez y Eloy Alfaro, Quito',
    '+593 2 4567890',
    4.3,
    '20-30 min',
    'https://images.unsplash.com/photo-1579584425555-c3ce17fd4351?w=400',
    true,
    (SELECT id FROM auth.users WHERE email = 'admin@jagoo.com')
  );
  
  RAISE NOTICE '3 restaurantes creados con IDs: %, %, %', rest1_id, rest2_id, rest3_id;
  
  -- ── 4. CREAR PLATOS PARA CADA RESTAURANTE ───────────────────────────────────
  
  -- Platos para Burger Master
  INSERT INTO dishes (id, restaurant_id, restaurant_name, section_name, name, description, price, image_url, is_available, is_popular)
  VALUES
    (gen_random_uuid(), rest1_id, 'Burger Master', 'Hamburguesas', 'Clásica', 'Carne 150g, lechuga, tomate, cebolla, queso', 8.99, 'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=300', true, true),
    (gen_random_uuid(), rest1_id, 'Burger Master', 'Hamburguesas', 'Doble Queso', 'Doble carne, doble queso, tocino', 12.99, 'https://images.unsplash.com/photo-1586190848861-99aa4a171e90?w=300', true, false),
    (gen_random_uuid(), rest1_id, 'Burger Master', 'Acompañamientos', 'Papas Fritas', 'Porción grande con salsa', 3.99, 'https://images.unsplash.com/photo-1541592106381-b31e9677c0e5?w=300', true, true),
    (gen_random_uuid(), rest1_id, 'Burger Master', 'Bebidas', 'Gaseosa 500ml', 'Coca-Cola, Fanta, Sprite', 2.50, 'https://images.unsplash.com/photo-1622483767028-3f66f32aef97?w=300', true, false);
  
  -- Platos para Pizza Italia
  INSERT INTO dishes (id, restaurant_id, restaurant_name, section_name, name, description, price, image_url, is_available, is_popular)
  VALUES
    (gen_random_uuid(), rest2_id, 'Pizza Italia', 'Pizzas', 'Margherita', 'Salsa de tomate, mozzarella, albahaca', 10.99, 'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=300', true, true),
    (gen_random_uuid(), rest2_id, 'Pizza Italia', 'Pizzas', 'Pepperoni', 'Salsa de tomate, mozzarella, pepperoni', 13.99, 'https://images.unsplash.com/photo-1628840042765-356cda07504e?w=300', true, true),
    (gen_random_uuid(), rest2_id, 'Pizza Italia', 'Pizzas', 'Hawaiana', 'Jamón, piña, mozzarella', 12.99, 'https://images.unsplash.com/photo-1604068549290-dea0e4a305ca?w=300', true, false),
    (gen_random_uuid(), rest2_id, 'Pizza Italia', 'Bebidas', 'Vino Tinto', 'Copa de vino tinto de la casa', 5.99, 'https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?w=300', true, false);
  
  -- Platos para Sushi Express
  INSERT INTO dishes (id, restaurant_id, restaurant_name, section_name, name, description, price, image_url, is_available, is_popular)
  VALUES
    (gen_random_uuid(), rest3_id, 'Sushi Express', 'Rolls', 'California Roll', 'Cangrejo, aguacate, pepino', 9.99, 'https://images.unsplash.com/photo-1579584425555-c3ce17fd4351?w=300', true, true),
    (gen_random_uuid(), rest3_id, 'Sushi Express', 'Rolls', 'Salmon Roll', 'Salmón fresco, aguacate', 11.99, 'https://images.unsplash.com/photo-1611143669185-af226c5e8a0e?w=300', true, true),
    (gen_random_uuid(), rest3_id, 'Sushi Express', 'Sashimi', 'Sashimi Mixto', '12 piezas de sashimi variado', 15.99, 'https://images.unsplash.com/photo-1553621042-f6e147245754?w=300', true, false),
    (gen_random_uuid(), rest3_id, 'Sushi Express', 'Bebidas', 'Té Verde', 'Té verde japonés caliente', 2.99, 'https://images.unsplash.com/photo-1561047029-3000c68339ca?w=300', true, false);
  
  RAISE NOTICE '12 platos creados para los 3 restaurantes';
END $$;

-- ── 5. VERIFICAR QUE TODO ESTÉ CORRECTO ───────────────────────────────────────
SELECT '====== VERIFICACIÓN FINAL ======' as mensaje;

-- Usuarios
SELECT 'Usuarios totales:' as tipo, COUNT(*) as cantidad FROM auth.users
UNION ALL
SELECT 'Usuarios admin:' as tipo, COUNT(*) as cantidad FROM auth.users WHERE email = 'admin@jagoo.com'
UNION ALL
SELECT 'Perfiles activos:' as tipo, COUNT(*) as cantidad FROM profiles WHERE is_active = true
UNION ALL
SELECT 'Perfiles admin:' as tipo, COUNT(*) as cantidad FROM profiles WHERE role = 'admin';

-- Restaurantes y platos
SELECT '====== RESTAURANTES Y PLATOS ======' as mensaje;

SELECT 'Restaurantes activos:' as tipo, COUNT(*) as cantidad FROM restaurants WHERE is_active = true
UNION ALL
SELECT 'Platos disponibles:' as tipo, COUNT(*) as cantidad FROM dishes WHERE is_available = true
UNION ALL
SELECT 'Platos populares:' as tipo, COUNT(*) as cantidad FROM dishes WHERE is_popular = true;

-- Mostrar credenciales
SELECT '====== CREDENCIALES DE ACCESO ======' as mensaje;
SELECT '🎯 ADMIN PANEL' as tipo, 'http://localhost:5173/login' as valor
UNION ALL
SELECT '📧 Email' as tipo, 'admin@jagoo.com' as valor
UNION ALL
SELECT '🔐 Contraseña' as tipo, 'Admin123' as valor
UNION ALL
SELECT '👤 Rol' as tipo, 'admin (acceso completo)' as valor;

SELECT '====== RESET COMPLETADO ======' as mensaje;
SELECT '✅ Base de datos reseteada exitosamente' as resultado;
SELECT '✅ Solo usuario admin conservado' as resultado;
SELECT '✅ 3 restaurantes de ejemplo creados' as resultado;
SELECT '✅ 12 platos de ejemplo creados' as resultado;
SELECT '✅ Credenciales listas para usar' as resultado;