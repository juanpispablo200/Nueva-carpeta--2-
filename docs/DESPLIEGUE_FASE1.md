# Despliegue de la Fase 1 en tu Supabase real

Proyecto: `tjgavwygdotetoatitju`. Hoy tu base solo tiene las 7 primeras migraciones, por eso la app muestra
"El servidor de Jagoo aún no está actualizado… falta la función create_order". Esta guía lo arregla y aplica el endurecimiento.

Todo se hace en **Supabase → SQL Editor** salvo los pasos marcados "terminal".

## Antes de empezar

- No ejecutes `aplicar_en_produccion.sql` (está obsoleto).
- No ejecutes las migraciones `010_seed_users`, `013_crear_repartidor`, `015_usuario_repartidor`: crean cuentas con contraseñas escritas en el repo.
- Cada paso es una transacción: si falla, no queda nada a medias. Si falla, copia **el primer** mensaje de error.

## 1. Base de datos (SQL Editor, en este orden)

Pega y ejecuta cada archivo completo. Cada uno termina con una fila `PASO N OK`.

| Orden | Archivo | Resultado esperado |
|---|---|---|
| 1 | `supabase/deploy/00_extensiones.sql` | extensiones `pg_net`, `pg_cron`, `pgcrypto` activas |
| 2 | `supabase/deploy/01_parte_A.sql` | `PASO 1 OK` |
| 3 | `supabase/deploy/02_parte_B.sql` | `PASO 2 OK` |
| 4 | `supabase/deploy/03_parte_C.sql` | `PASO 3 OK` |
| 5 | `supabase/deploy/04_hardening.sql` | `PASO 4 OK` y `estado_notificaciones` con `false` (aún no configuras el secreto) |

Si `00_extensiones` falla por permisos, activa `pg_net` y `pg_cron` en **Database → Extensions** y repite.

Verificación (debe dar `tablas_nuevas = 3` y `create_order = 1`):

```sql
select
  (select count(*) from information_schema.tables
    where table_schema='public' and table_name in ('settings','coupons','payment_proofs')) as tablas_nuevas,
  (select count(*) from pg_proc where proname='create_order') as create_order,
  (select count(*) from pg_proc where proname in ('check_coupon','get_order_contact','list_available_orders','call_edge_function')) as funciones_fase1;
```
`funciones_fase1` debe ser `4`.

## 2. Secreto compartido entre la base y las Edge Functions

Genera un valor largo (PowerShell):

```powershell
[guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
```

Guárdalo en un gestor de contraseñas. Nunca en el repo.

**SQL Editor** (cambia `PEGA_EL_VALOR`):

```sql
select vault.create_secret('PEGA_EL_VALOR', 'webhook_secret', 'Secreto base de datos -> Edge Functions');

update public.settings
   set value = 'https://tjgavwygdotetoatitju.supabase.co/functions/v1'
 where key = 'edge_functions_url';

select public.edge_config_status();
```
`edge_config_status` debe mostrar `edge_functions_url_set: true` y `webhook_secret_set: true`.

Si ejecutas `vault.create_secret` dos veces con el mismo nombre, queda el más reciente; borra el viejo desde **Project Settings → Vault**.

## 3. Edge Functions (terminal, dentro de la carpeta del proyecto)

```powershell
supabase login
supabase link --project-ref tjgavwygdotetoatitju
```

Crea un archivo `supabase/.env.functions.local` **fuera de git** (el patrón `.env.*.local` ya lo ignora; confírmalo con `git status`) con:

```
WEBHOOK_SECRET=PEGA_EL_MISMO_VALOR
FIREBASE_PROJECT_ID=tu-proyecto-firebase
FIREBASE_CLIENT_EMAIL=firebase-adminsdk-xxxx@tu-proyecto.iam.gserviceaccount.com
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nMIIE...\n-----END PRIVATE KEY-----\n"
```

La clave privada va en una sola línea con `\n` literales (así la lee `send-push-notification`). `SUPABASE_URL` y `SUPABASE_SERVICE_ROLE_KEY` los inyecta Supabase solos.

```powershell
supabase secrets set --env-file supabase/.env.functions.local
supabase functions deploy create-user
supabase functions deploy delete-account
supabase functions deploy send-push-notification --no-verify-jwt
supabase functions deploy notify-repartidores --no-verify-jwt
supabase functions deploy purge-storage-queue --no-verify-jwt
```

Borra `supabase/.env.functions.local` al terminar.

Verificación:

```powershell
supabase functions list
# Sin secreto debe responder 401 (si responde 200, la función está abierta):
curl.exe -i -X POST https://tjgavwygdotetoatitju.supabase.co/functions/v1/send-push-notification -H "Content-Type: application/json" -d "{}"
```

## 4. Authentication (Dashboard)

1. **Authentication → Providers → Email → Confirm email: ON.** No se puede cambiar por SQL. Es lo que evita cuentas con correos ajenos.
2. **Authentication → URL Configuration:** Site URL = tu dominio del panel (`web.base.url`). Redirect URLs: agrega `<tu dominio>/email-confirmado` y `<tu dominio>/reset-password`.
3. (Recomendado) **Authentication → Rate Limits**: deja los valores por defecto o más estrictos.

Mientras no tengas dominio público, el enlace de confirmación no llevará a una página útil: configura `web.base.url` en `local.properties` cuando publiques el panel.

## 5. Probar de punta a punta (con teléfono)

1. Registra un cliente nuevo: debe aparecer "Revisa tu correo" y **no** entrar a la app. Abre el enlace y luego inicia sesión.
2. Crea un pedido en efectivo: debe crearse (ya no sale `SERVER_OUTDATED`).
3. Con el negocio, avanza el pedido hasta "listo". Debe funcionar aunque no hayas hecho el paso 2 o 3 (las notificaciones fallan en silencio).
4. Con la app Driver (cuenta de repartidor disponible): el pedido aparece en la lista con "Ganas $x" y la zona, sin dirección. Al aceptar se ve la dirección, el teléfono del cliente y los productos.
5. Revisa que llegue el push (requiere pasos 2 y 3 completos y `google-services.json`).

Diagnóstico si no llega el push:

```sql
select public.edge_config_status();
select * from public.notification_log order by created_at desc limit 5;
```

## Quién ejecuta qué

| Paso | Quién |
|---|---|
| 1 SQL, 2 secreto, 4 Dashboard | Tú, en el panel de Supabase |
| 3 Edge Functions | Tú, en la terminal con `supabase login` (requiere tu cuenta) |
| Probar en teléfono | Tú |

## Revertir

El endurecimiento (`04`) no borra datos de usuario. Elimina solo `create_restaurant_user` y `accept_order(uuid, uuid)` (rotas) y cierra permisos. Las tablas `repartidores` y `device_tokens` se conservan marcadas como obsoletas.

