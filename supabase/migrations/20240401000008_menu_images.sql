-- ══════════════════════════════════════════════════════════════════════════════
-- JAGOO — Bucket Storage para imágenes del menú
-- Idempotente
-- ══════════════════════════════════════════════════════════════════════════════

-- Bucket público para imágenes de platos (se sirven directamente al cliente)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'menu-images',
  'menu-images',
  true,         -- público: la URL se puede usar directamente en <img>
  3145728,      -- 3 MB por imagen
  ARRAY['image/jpeg','image/png','image/webp','image/gif']
)
ON CONFLICT (id) DO NOTHING;

-- Política: restaurante autenticado puede subir imágenes
DROP POLICY IF EXISTS menu_images_insert ON storage.objects;
CREATE POLICY menu_images_insert
  ON storage.objects FOR INSERT
  WITH CHECK (
    bucket_id = 'menu-images'
    AND auth.role() = 'authenticated'
  );

-- Política: todo el mundo puede leer (bucket público igual, pero es buena práctica)
DROP POLICY IF EXISTS menu_images_select ON storage.objects;
CREATE POLICY menu_images_select
  ON storage.objects FOR SELECT
  USING (bucket_id = 'menu-images');

-- Política: solo el que subió o admin puede borrar
DROP POLICY IF EXISTS menu_images_delete ON storage.objects;
CREATE POLICY menu_images_delete
  ON storage.objects FOR DELETE
  USING (
    bucket_id = 'menu-images'
    AND (
      owner = auth.uid()
      OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin')
    )
  );

DO $$
BEGIN
  ASSERT EXISTS (
    SELECT 1 FROM storage.buckets WHERE id = 'menu-images'
  ), 'FALLA: bucket menu-images no existe';
  RAISE NOTICE '✅ FASE menu-images: bucket creado correctamente';
END;
$$;
