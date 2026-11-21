-- ══════════════════════════════════════════════════════════════════════════════
-- REPARAR REPARTIDORES EXISTENTES (idempotente)
-- Corrige: perfil faltante, rol incorrecto, is_active NULL
-- NO toca admins ni restaurantes
-- ══════════════════════════════════════════════════════════════════════════════

-- 1. Crear perfil para repartidores que están en auth.users pero NO en profiles
--    Detectamos por user_metadata.role = 'repartidor'
INSERT INTO profiles (id, full_name, phone, role, is_active)
SELECT
  u.id,
  COALESCE(u.raw_user_meta_data->>'full_name', u.email, 'Repartidor'),
  u.raw_user_meta_data->>'phone',
  'repartidor',
  true
FROM auth.users u
LEFT JOIN profiles p ON p.id = u.id
WHERE p.id IS NULL
  AND (
    u.raw_user_meta_data->>'role' = 'repartidor'
    OR u.raw_app_meta_data->>'role' = 'repartidor'
  )
ON CONFLICT (id) DO NOTHING;

-- 2. Corregir perfiles donde el rol está mal escrito
--    (ej: 'courier', 'delivery', 'driver' → 'repartidor')
UPDATE profiles
SET role = 'repartidor'
WHERE role IN ('courier', 'delivery', 'driver')
  AND role NOT IN ('admin', 'restaurant');

-- 3. Asegurar is_active = true para repartidores activos con NULL
UPDATE profiles
SET is_active = true
WHERE role = 'repartidor'
  AND is_active IS NULL;

-- 4. Verificación final
SELECT
  'Repartidores con perfil correcto' AS check,
  COUNT(*)                           AS cantidad
FROM profiles
WHERE role = 'repartidor'
UNION ALL
SELECT
  'Repartidores activos',
  COUNT(*)
FROM profiles
WHERE role = 'repartidor' AND is_active = true
UNION ALL
SELECT
  'Auth sin perfil (quedan pendientes)',
  COUNT(*)
FROM auth.users u
LEFT JOIN profiles p ON p.id = u.id
WHERE p.id IS NULL
  AND u.raw_user_meta_data->>'role' = 'repartidor';

SELECT '✅ Reparación completada' AS resultado;