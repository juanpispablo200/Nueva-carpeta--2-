// Corre supabase/tests/01_pagos_y_pedidos.sql (T1..T11) sobre la base real simulada + 04_hardening.
import { PGlite } from '@electric-sql/pglite'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const here = path.dirname(fileURLToPath(import.meta.url))
const sup = path.resolve(here, '..', '..') + path.sep
const read = p => fs.readFileSync(p, 'utf8')
const db = new PGlite()
await db.exec(read(here + path.sep + 'mock.sql'))
const files = fs.readdirSync(sup + 'migrations').filter(f => f.endsWith('.sql')).sort()
for (const f of files.slice(0, files.indexOf('20240301000001_mejoras_web.sql'))) await db.exec(read(sup + 'migrations/' + f))
for (const p of ['01_parte_A.sql', '02_parte_B.sql', '03_parte_C.sql', '04_hardening.sql']) {
  await db.exec(read(sup + 'deploy/' + p))
  console.log('OK  ', p)
}
try {
  await db.exec(read(sup + 'tests/01_pagos_y_pedidos.sql'))
  console.log('TESTS 01_pagos_y_pedidos: completados sin error')
} catch (e) {
  console.log('TESTS FAIL ->', String(e.message).split('\n')[0])
  process.exit(1)
}
