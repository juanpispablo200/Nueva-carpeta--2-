-- Actualizar función sin columna email
CREATE OR REPLACE FUNCTION create_courier_profile(
  p_id uuid,
  p_full_name text,
  p_phone text,
  p_email text
)
RETURNS void AS $$
BEGIN
  INSERT INTO profiles (id, full_name, phone, role, is_active)
  VALUES (p_id, p_full_name, p_phone, 'repartidor', true)
  ON CONFLICT (id) DO UPDATE SET
    full_name = p_full_name,
    phone = p_phone,
    role = 'repartidor',
    is_active = true;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

SELECT '✅ Función actualizada sin columna email' as resultado;