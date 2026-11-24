# Suite de seguridad (PGlite)

Postgres real en memoria con un mock mínimo de Supabase (`mock.sql`). No toca tu base real.

```
npm install
npm test                 # base real simulada + 04_hardening: debe dar 144/144
npm run test:antes       # solo hasta 03 (estado actual de tu base): muestra lo que corrige la 24
node pagos.mjs           # corre supabase/tests/01_pagos_y_pedidos.sql (T1..T11)
```

Sale con código 1 si algo falla. Si cambias `migrations/20240401000024_hardening.sql`, regenera `deploy/04_hardening.sql`
(es la misma migración envuelta en BEGIN/COMMIT) antes de correr la suite.

Límite: es un mock. `net.http_post` y Vault tienen la firma real, pero no se probó contra un pg_net ni un Supabase reales.
