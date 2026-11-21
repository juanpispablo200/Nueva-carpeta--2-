-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Favoritos persistentes + perfil editable
-- ══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Tabla favorites
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS favorites (
  id            uuid  PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       uuid  NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  restaurant_id text  NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, restaurant_id)
);

CREATE INDEX IF NOT EXISTS idx_favorites_user ON favorites(user_id);

ALTER TABLE favorites ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS favorites_select ON favorites;
CREATE POLICY favorites_select ON favorites
  FOR SELECT USING (user_id = auth.uid());

DROP POLICY IF EXISTS favorites_insert ON favorites;
CREATE POLICY favorites_insert ON favorites
  FOR INSERT WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS favorites_delete ON favorites;
CREATE POLICY favorites_delete ON favorites
  FOR DELETE USING (user_id = auth.uid());

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Columnas editables en profiles (si no existen)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE profiles
  ADD COLUMN IF NOT EXISTS avatar_url text;

-- Política para que el usuario pueda actualizarse solo nombre y teléfono
DROP POLICY IF EXISTS profiles_update_own ON profiles;
CREATE POLICY profiles_update_own ON profiles
  FOR UPDATE
  USING (id = auth.uid())
  WITH CHECK (
    id       = auth.uid() AND
    -- No puede cambiarse el rol
    role     = (SELECT role FROM profiles WHERE id = auth.uid())
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC toggle_favorite
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION toggle_favorite(p_restaurant_id text)
RETURNS boolean   -- true = ahora es favorito, false = se quitó
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_exists boolean;
BEGIN
  SELECT EXISTS(
    SELECT 1 FROM favorites
    WHERE user_id = auth.uid() AND restaurant_id = p_restaurant_id
  ) INTO v_exists;

  IF v_exists THEN
    DELETE FROM favorites WHERE user_id = auth.uid() AND restaurant_id = p_restaurant_id;
    RETURN false;
  ELSE
    INSERT INTO favorites (user_id, restaurant_id) VALUES (auth.uid(), p_restaurant_id);
    RETURN true;
  END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION toggle_favorite(text) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  ASSERT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'favorites'),
    'FALLA: tabla favorites no existe';
  ASSERT EXISTS (SELECT 1 FROM information_schema.routines WHERE routine_name = 'toggle_favorite'),
    'FALLA: función toggle_favorite no existe';
  RAISE NOTICE '✅ favorites + perfil editable: listo';
END;
$$;
