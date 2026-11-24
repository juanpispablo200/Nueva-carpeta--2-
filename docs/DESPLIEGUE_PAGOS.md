# Despliegue: pagos, tipos de negocio y retención de datos

Todo esto lo ejecutas tú en tu proyecto de Supabase. Haz una copia de seguridad antes
(Database → Backups) y prueba primero en un proyecto de pruebas.

## 1. Habilitar extensiones (una vez)
Supabase → **Database → Extensions**: habilita **pg_cron** y **pg_net** (si no lo está).
Sin `pg_cron`, las migraciones 22 y 23 se aplican igual pero **la cancelación a los 30
minutos y la retención no correrán** (verás un NOTICE de aviso). Habilítala y vuelve a
ejecutar la migración 22 (y la 23): es idempotente.

## 2. Aplicar las migraciones, en orden
SQL Editor → pegar y ejecutar, una por una: `…19`, `…20`, `…21`, `…22`, `…23`.
Cada una termina con un bloque de verificación (`✅ … aplicada`). Si imprime `FALLA: …`, no sigas.

Antes de la 22, confirma que las notificaciones siguen configuradas (ya lo usabas):
`app.edge_function_url` y `app.service_role_key` (ver `…005_push_notifications.sql`).

## 3. Funciones de borde
```
supabase functions deploy delete-account        --project-ref <TU_PROJECT_REF>
supabase functions deploy purge-storage-queue   --project-ref <TU_PROJECT_REF>
supabase functions delete process-kushki-payment --project-ref <TU_PROJECT_REF>   # si estaba desplegada
supabase secrets unset KUSHKI_PRIVATE_KEY --project-ref <TU_PROJECT_REF>          # ya no se usa
```
`purge-storage-queue` solo acepta la service_role key (la llama pg_cron).

## 4. Verificar los trabajos programados
```sql
select jobname, schedule, active from cron.job where jobname like 'jagoo-%';
-- Esperado: jagoo-cancel-expired-transfers (* * * * *), jagoo-data-retention (0 8 * * *), jagoo-purge-storage (15 * * * *)
select * from cron.job_run_details order by start_time desc limit 10;
```

## 5. Configurar desde el panel admin
- **Ajustes**: minutos para subir comprobante (30), datos bancarios, costo de envío por defecto.
- **Finanzas**: reparto global (debe sumar 100; los valores iniciales 85/5/10 son un ejemplo),
  reparto y pedido mínimo por tipo, zonas de envío, liquidación del efectivo de repartidores.
- **Restaurantes (negocios)**: tipo, número de permiso (opcional), activar/desactivar.

El IVA no se toca: la migración y el panel no lo exponen.

## 6. Pruebas
1. SQL: pega `supabase/tests/01_pagos_y_pedidos.sql` en el SQL Editor de un proyecto de PRUEBAS.
   Termina con ROLLBACK (no guarda nada). Debes ver `OK T1 … OK T11`.
2. App: `.\gradlew :app:testDebugUnitTest` (24 pruebas) y `:app:compileDebugKotlin :driver:compileDebugKotlin`.
3. Web: `cd web && npx tsc --noEmit`.
4. A mano (necesitas un dispositivo, `google-services.json` y datos de prueba):
   - Pedido en efectivo: "¿con cuánto pagas?" → cambio correcto; el negocio lo acepta.
   - Transferencia: sin subir comprobante, a los 30 min el pedido pasa a Cancelado (con notificación).
   - Transferencia con comprobante: el admin verifica en Pagos → el pedido pasa a Aceptado.
   - Licorería: fecha de nacimiento una sola vez; el driver ve "Verificar cédula" y no puede entregar sin confirmarla.
   - Farmacia: producto con receta pide foto; el negocio aprueba o rechaza antes de aceptar.
   - Carrito de dos negocios: aparece el aviso de vaciar.
   - Eliminar cuenta con una receta subida: la foto desaparece del bucket.

## 7. Retención: qué hace exactamente
Ver el encabezado de `20240401000023_data_retention.sql`. La política de privacidad
(sección 9) describe lo mismo. Si cambias un plazo, cambia los dos.

## Importante: pedidos y comprobantes antiguos
- Los comprobantes anteriores a esta versión guardaron una URL completa; el panel los sigue abriendo.
  Si el bucket `payment-proofs` era público, ahora es privado (los enlaces públicos viejos dejan de funcionar).
- El cliente nuevo ya no inserta pedidos directamente: usa `create_order()`. Una versión antigua de
  la app no podrá crear pedidos. Publica la app nueva antes (o junto) a aplicar la migración 22.
