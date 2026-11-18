-- ══════════════════════════════════════════════════════════════════════════════
-- VERIFICAR REPARTIDORES — estado completo
-- ══════════════════════════════════════════════════════════════════════════════

SELECT
  u.email,
  p.full_name                             AS nombre,
  p.role                                  AS rol,
  p.is_active                             AS activo,
  COUNT(o.id)                             AS pedidos_asignados,
  COUNT(o.id) FILTER (WHERE o.status = 'delivered')   AS entregados,
  COUNT(o.id) FILTER (WHERE o.status = 'on_the_way')  AS en_camino,
  COUNT(o.id) FILTER (WHERE o.status = 'ready')       AS listos_para_recoger,
  COUNT(o.id) FILTER (WHERE o.status = 'cancelled')   AS cancelados
FROM auth.users u
JOIN profiles p ON p.id = u.id
LEFT JOIN orders o ON o.courier_id = p.id
WHERE p.role = 'repartidor'
GROUP BY u.email, p.full_name, p.role, p.is_active
ORDER BY u.email;

-- Repartidores en auth.users sin perfil (deben ser 0)
SELECT '-- Repartidores sin perfil (deben ser 0):' AS info;
SELECT u.email, u.id
FROM auth.users u
LEFT JOIN profiles p ON p.id = u.id
WHERE p.id IS NULL
  AND u.raw_user_meta_data->>'role' = 'repartidor';
