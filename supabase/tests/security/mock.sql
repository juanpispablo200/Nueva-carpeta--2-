-- Simulación mínima de Supabase para PGlite (solo pruebas, no se despliega).
-- Las firmas de net.http_post y vault.decrypted_secrets son las reales, para
-- que un error de tipos (como pasar body::text) NO quede oculto.
create publication supabase_realtime;
create role anon nologin;
create role authenticated nologin;
create role service_role nologin;
create schema auth;
create schema storage;
create schema net;
create schema cron;
create schema extensions;
create schema vault;

-- pgcrypto: en Supabase vive en el esquema extensions; las migraciones lo llaman sin esquema
create function public.gen_salt(text) returns text language sql as $$ select 'salt' $$;
create function public.crypt(text,text) returns text language sql as $$ select 'hash' $$;

create table auth.identities (id uuid default gen_random_uuid(), user_id uuid, identity_data jsonb, provider text, provider_id text, last_sign_in_at timestamptz, created_at timestamptz, updated_at timestamptz);
create table auth.users (
  id uuid primary key default gen_random_uuid(), email text, phone text,
  raw_user_meta_data jsonb default '{}', encrypted_password text, instance_id uuid, aud text, role text,
  raw_app_meta_data jsonb default '{}', updated_at timestamptz default now(),
  confirmation_token text, recovery_token text, email_change text, email_change_token_new text,
  is_super_admin boolean, email_confirmed_at timestamptz, phone_confirmed_at timestamptz,
  created_at timestamptz default now());
create function auth.uid() returns uuid language sql stable as $$ select coalesce(nullif(current_setting('request.jwt.claim.sub', true), ''), (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'))::uuid $$;
create function auth.role() returns text language sql stable as $$ select coalesce(nullif(current_setting('request.jwt.claim.role', true), ''), (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role'), 'anon') $$;

create table storage.buckets (id text primary key, name text, public boolean, file_size_limit bigint, allowed_mime_types text[]);
create table storage.objects (id uuid default gen_random_uuid() primary key, bucket_id text, name text, owner uuid);
alter table storage.objects enable row level security;
create function storage.foldername(name text) returns text[] language sql as $$ select string_to_array(name, '/') $$;

-- pg_net real: net.http_post(url, body jsonb, params jsonb, headers jsonb, timeout_milliseconds int)
create table net.calls (id serial primary key, url text, body jsonb, headers jsonb, at timestamptz default now());
create function net.http_post(url text, body jsonb default '{}'::jsonb, params jsonb default '{}'::jsonb,
  headers jsonb default '{"Content-Type": "application/json"}'::jsonb, timeout_milliseconds int default 5000)
  returns bigint language plpgsql as $$
begin
  insert into net.calls(url, body, headers) values (url, body, headers);
  return 1;
end $$;

create table vault.secrets (id uuid default gen_random_uuid() primary key, name text, secret text, created_at timestamptz default now());
create view vault.decrypted_secrets as select id, name, secret as decrypted_secret, created_at from vault.secrets;

create table cron.job (jobid serial primary key, jobname text, schedule text, command text);
create function cron.schedule(n text, s text, c text) returns bigint language sql as $$ insert into cron.job(jobname,schedule,command) values (n,s,c) returning jobid::bigint $$;
create function cron.unschedule(j bigint) returns boolean language sql as $$ delete from cron.job where jobid=j returning true $$;

create table public.settings (key text primary key, value text not null default '', description text, updated_at timestamptz not null default now());

grant usage on schema public, auth, storage to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
alter default privileges in schema public grant all on sequences to anon, authenticated, service_role;
