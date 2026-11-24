-- ==========================================================================
-- JAGOO - PASO 0: extensiones (ejecutalo primero y solo)
-- pg_cron: cancelacion de transferencias a los 30 min y retencion de datos.
-- pg_net: llamadas HTTP desde la base (push y purga de archivos).
-- pgcrypto: ya la usan las migraciones antiguas (crypt/gen_salt).
-- Si algo falla aqui, activalas desde Database > Extensions y sigue con el paso 1.
-- ==========================================================================
create extension if not exists pgcrypto with schema extensions;
create extension if not exists pg_net with schema extensions;
create extension if not exists pg_cron with schema pg_catalog;
grant usage on schema cron to postgres;

select 'PASO 0 OK: extensiones listas' as resultado;
