// Suite de seguridad de Jagoo sobre PGlite (Postgres real en memoria + mock de Supabase).
//   npm install && node suite.mjs                  -> con endurecimiento (24)
//   node suite.mjs --sin-hardening                 -> estado ANTES de la 24 (para ver qué falla)
// Sale con código 1 si algún caso falla.
import { PGlite } from '@electric-sql/pglite'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const here = path.dirname(fileURLToPath(import.meta.url))
const sup = path.resolve(here, '..', '..') + path.sep
const read = p => fs.readFileSync(p, 'utf8')
const SIN = process.argv.includes('--sin-hardening')

const db = new PGlite()
await db.exec(read(here + path.sep + 'mock.sql'))

// Estado real del Supabase del usuario: las 7 primeras migraciones + partes A, B, C (+ 04)
const files = fs.readdirSync(sup + 'migrations').filter(f => f.endsWith('.sql')).sort()
for (const f of files.slice(0, files.indexOf('20240301000001_mejoras_web.sql'))) await db.exec(read(sup + 'migrations/' + f))
const parts = ['01_parte_A.sql', '02_parte_B.sql', '03_parte_C.sql']
if (!SIN) parts.push('04_hardening.sql', '05_promociones.sql')
for (const p of parts) await db.exec(read(sup + 'deploy/' + p))

const q = async sql => (await db.query(sql)).rows

// ── Datos ──────────────────────────────────────────────────────────────────────
const U = {
  A: '00000000-0000-0000-0000-00000000000a', B: '00000000-0000-0000-0000-00000000000b',
  O1: '00000000-0000-0000-0000-0000000000c1', O2: '00000000-0000-0000-0000-0000000000c2',
  K1: '00000000-0000-0000-0000-0000000000d1', K2: '00000000-0000-0000-0000-0000000000d2',
  AD: '00000000-0000-0000-0000-0000000000ad', NC: '00000000-0000-0000-0000-0000000000ee',
}
const role = { A: 'customer', B: 'customer', O1: 'restaurant', O2: 'restaurant', K1: 'repartidor', K2: 'repartidor', AD: 'admin', NC: 'customer' }
const phone = { A: '0990000001', B: '0990000002', O1: '0990000003', O2: '0990000004', K1: '0990000005', K2: '0990000006', AD: '0990000007', NC: '0990000008' }
for (const [k, id] of Object.entries(U)) {
  await db.exec(`insert into auth.users(id,email,email_confirmed_at) values ('${id}','${k}@t.test', ${k === 'NC' ? 'null' : 'now()'});
    insert into public.profiles(id,full_name,phone,role,is_active) values ('${id}','Persona ${k}','${phone[k]}','${role[k]}',true)
      on conflict (id) do update set role='${role[k]}', is_active=true, phone='${phone[k]}', full_name='Persona ${k}';`)
}
await db.exec(`update public.profiles set is_available = true where id in ('${U.K1}','${U.K2}')`)
try { await db.exec(`update public.profiles set vehicle_type='moto', plate='ABC123' where id='${U.K2}'`) } catch { }
try { await db.exec(`alter table public.restaurants add column if not exists phone text`) } catch { }
const oid = s => `10000000-0000-0000-0000-${s.padStart(12, '0')}`
const oA = oid('a'), oB = oid('b'), oK2 = oid('e2'), oFree = oid('f0'), oActive = oid('e3'), oDone = oid('e4'), oPend = oid('e5'), oPend2 = oid('e6')
await db.exec(`
  insert into public.restaurants(id,name,category,business_type,owner_id,is_open,is_active) values
   ('ra','Negocio A','x','restaurante','${U.O1}',true,true),('rb','Negocio B','x','restaurante','${U.O2}',true,true) on conflict do nothing;
  update public.restaurants set phone='0991234567' where id='rb';
  insert into public.dishes(id,restaurant_id,section_name,name,price) values ('da','ra','s','Plato A',5),('db','rb','s','Plato B',5) on conflict do nothing;
  insert into public.delivery_zones(name, fee) values ('Centro', 1.5);
  insert into public.orders(id,customer_id,restaurant_id,restaurant_name,subtotal,delivery_fee,total,status,delivery_address,payment_method,payment_status) values
   ('${oA}','${U.A}','ra','Negocio A',5,1,6,'pending','Casa de A','cash','pending'),
   ('${oB}','${U.B}','rb','Negocio B',5,1,6,'pending','Casa de B','cash','pending'),
   ('${oPend}','${U.B}','rb','Negocio B',5,1,6,'pending','Casa de B','cash','pending'),
   ('${oPend2}','${U.B}','rb','Negocio B',5,1,6,'pending','Casa de B','cash','pending');
  insert into public.orders(id,customer_id,restaurant_id,restaurant_name,subtotal,delivery_fee,total,status,delivery_address,payment_method,payment_status,courier_id) values
   ('${oK2}','${U.B}','rb','Negocio B',5,1,6,'ready','Casa de B (k2)','cash','pending','${U.K2}'),
   ('${oActive}','${U.B}','rb','Negocio B',5,1,6,'on_the_way','Casa de B (activo)','cash','pending','${U.K2}'),
   ('${oDone}','${U.B}','rb','Negocio B',5,1,6,'delivered','Casa de B (hecho)','cash','pending','${U.K2}');
  insert into public.orders(id,customer_id,restaurant_id,restaurant_name,subtotal,delivery_fee,total,status,delivery_address,payment_method,payment_status,delivery_zone_name,driver_amount) values
   ('${oFree}','${U.B}','rb','Negocio B',5,1,6,'ready','Casa de B (libre)','cash','pending','Centro',1.2);
  insert into public.order_items(order_id,dish_id,dish_name,unit_price,quantity) values ('${oB}','db','Plato B',5,1);
  insert into public.addresses(id,user_id,label,full_address,is_primary) values (gen_random_uuid(),'${U.B}','Casa','Direccion privada de B',true);
  insert into public.payment_proofs(order_id,customer_id,image_url,amount) values ('${oB}','${U.B}','${U.B}/x.jpg',6);
  insert into public.coupons(code,type,value,min_order,max_uses,active,valid_until) values ('SECRETO10','fixed',2,0,100,true,now()+interval '1 day') on conflict do nothing;
`)

// ── Ayudantes ──────────────────────────────────────────────────────────────────
async function asRole(who, fn, { commit = false } = {}) {
  await db.exec('BEGIN')
  try {
    if (who === 'anon') await db.exec(`select set_config('request.jwt.claims','',false); set role anon`)
    else if (who === 'svc') await db.exec(`select set_config('request.jwt.claims','{"role":"service_role"}',false); set role service_role`)
    else await db.exec(`select set_config('request.jwt.claims','{"sub":"${U[who]}","role":"authenticated"}',false); set role authenticated`)
    const run = async sql => { const r = await db.query(sql); return r.rows }
    return await fn(run)
  } finally {
    try { await db.exec('reset role') } catch { }
    try { await db.exec(commit ? 'commit' : 'rollback') } catch { }
  }
}
async function as(who, sql) {
  try { return await asRole(who, async run => { const rows = await run(sql); return { ok: true, n: rows.length, rows } }) }
  catch (e) { return { ok: false, err: String(e.message).split('\n')[0] } }
}
const results = []
// exp: 'vacio' (0 filas o error = bien), 'error' (debe fallar), 'filas' (debe devolver algo)
async function T(sev, name, who, sql, exp = 'vacio') {
  const r = await as(who, sql)
  let pass
  if (exp === 'vacio') pass = !r.ok || r.n === 0
  else if (exp === 'error') pass = !r.ok
  else pass = r.ok && r.n > 0
  results.push({ sev, name, pass, det: r.ok ? `filas=${r.n}` : `error: ${r.err.slice(0, 70)}` })
}
async function C(sev, name, fn) {
  try { const [pass, det] = await fn(); results.push({ sev, name, pass, det: det ?? '' }) }
  catch (e) { results.push({ sev, name, pass: false, det: 'excepción: ' + String(e.message).split('\n')[0].slice(0, 90) }) }
}
const errOf = async (who, sql) => { const r = await as(who, sql); return r.ok ? null : r.err }

// ══ 1. Lectura entre clientes ══════════════════════════════════════════════════
await T('CRITICO', 'Cliente A lee pedidos de B', 'A', `select id from orders where customer_id='${U.B}'`)
await T('CRITICO', 'Cliente A lee items de pedidos de B', 'A', `select id from order_items where order_id='${oB}'`)
await T('CRITICO', 'Cliente A lee direcciones de B', 'A', `select id from addresses where user_id='${U.B}'`)
await T('CRITICO', 'Cliente A lee comprobantes de B', 'A', `select id from payment_proofs where customer_id='${U.B}'`)
await T('ALTO', 'Cliente A lee perfil (telefono) de B', 'A', `select id from profiles where id='${U.B}'`)
await T('ALTO', 'Cliente A lee cobros en efectivo', 'A', `select id from cash_collections`)
await T('ALTO', 'Cliente A lee auditoria', 'A', `select id from audit_log`)
await T('MEDIO', 'Cliente A lista cupones (codigos)', 'A', `select code from coupons`)
await T('ALTO', 'Cliente A lee eventos de pedidos de B', 'A', `select id from order_events where order_id='${oB}'`)
// ══ 2. Escritura entre clientes ════════════════════════════════════════════════
await T('CRITICO', 'Cliente A cambia total de su pedido', 'A', `update orders set total=0.01 where id='${oA}' returning id`)
await T('CRITICO', 'Cliente A edita pedido de B', 'A', `update orders set status='cancelled' where id='${oB}' returning id`)
await T('CRITICO', 'Cliente A se pone rol admin', 'A', `update profiles set role='admin' where id='${U.A}' returning id`)
await T('ALTO', 'Cliente A edita otro perfil', 'A', `update profiles set full_name='x' where id='${U.B}' returning id`)
await T('ALTO', 'Cliente A inserta pedido directo (salta create_order)', 'A', `insert into orders(customer_id,restaurant_id,restaurant_name,subtotal,delivery_fee,total,status) values ('${U.A}','ra','x',1,0,0.01,'pending') returning id`, 'error')
await T('ALTO', 'Cliente A escribe en settings', 'A', `update settings set value='0' where key='default_delivery_fee' returning key`)
await T('CRITICO', 'Cliente A cambia la URL de las Edge Functions (settings)', 'A', `update settings set value='https://evil.test' where key='edge_functions_url' returning key`)
await T('ALTO', 'Cliente A edita productos', 'A', `update dishes set price=0.01 returning id`)
await T('ALTO', 'Cliente A edita negocios', 'A', `update restaurants set name='x' returning id`)
// ══ 3. RPC con IDOR / escalada (C2) ════════════════════════════════════════════
await T('CRITICO', 'C2 change_order_status: cliente A cancela pedido AJENO', 'A', `select change_order_status('${oB}','cancelled','x')`, 'error')
await T('CRITICO', 'C2 change_order_status: negocio O1 avanza pedido de OTRO negocio', 'O1', `select change_order_status('${oB}','accepted')`, 'error')
await T('ALTO', 'C2 change_order_status: repartidor K1 mueve pedido de K2', 'K1', `select change_order_status('${oActive}','delivered')`, 'error')
await T('ALTO', 'C2 change_order_status: anonimo', 'anon', `select change_order_status('${oA}','cancelled')`, 'error')
await T('ALTO', 'C2 change_order_status: repartidor K1 sobre pedido libre', 'K1', `select change_order_status('${oFree}','picked_up')`, 'error')
await T('ALTO', 'RPC accept_order: cliente toma un pedido', 'A', `select accept_order('${oFree}')`, 'error')
await T('ALTO', 'RPC accept_order: negocio toma un pedido', 'O1', `select accept_order('${oFree}')`, 'error')
await T('ALTO', 'RPC admin_report_by_type: cliente', 'A', `select * from admin_report_by_type()`, 'error')
await T('ALTO', 'RPC settle_cash_collections: repartidor liquida', 'K1', `select settle_cash_collections(array[gen_random_uuid()])`, 'error')
await T('ALTO', 'RPC verify_payment_proof: cliente aprueba su pago', 'A', `select verify_payment_proof(gen_random_uuid(), true)`, 'error')
await T('ALTO', 'RPC review_prescription: negocio ajeno', 'O1', `select review_prescription('${oB}', true)`, 'error')
await T('ALTO', 'RPC get_driver_earnings de otro repartidor', 'K1', `select * from get_driver_earnings('${U.K2}')`, 'error')
await T('ALTO', 'RPC get_driver_location de pedido ajeno', 'A', `select * from get_driver_location('${oK2}')`, 'vacio')
await T('ALTO', 'RPC confirm_age_verification: repartidor ajeno', 'K1', `select confirm_age_verification('${oK2}')`, 'error')
await T('ALTO', 'RPC upload_payment_proof en pedido ajeno', 'A', `select upload_payment_proof('${oB}', '${U.A}/x.jpg')`, 'error')
await T('MEDIO', 'RPC submit_rating en pedido ajeno', 'A', `select submit_rating('${oB}', 5)`, 'error')
// ══ 4. Cupones (M1/M2) ═════════════════════════════════════════════════════════
await T('ALTO', 'M1 apply_coupon: cliente gasta usos de un cupon', 'A', `select apply_coupon('SECRETO10','${oA}')`, 'error')
await T('ALTO', 'M1 apply_coupon: anonimo', 'anon', `select apply_coupon('SECRETO10','${oA}')`, 'error')
await T('MEDIO', 'M2 validate_coupon: anonimo adivina codigos', 'anon', `select * from validate_coupon('SECRETO10', 50)`, 'error')
await T('MEDIO', 'M2 validate_coupon: cliente directo (sin limite de intentos)', 'A', `select * from validate_coupon('SECRETO10', 50)`, 'error')
await T('MEDIO', 'M2 check_coupon: anonimo', 'anon', `select * from check_coupon('SECRETO10', 50)`, 'error')
await T('FUNCIONA', 'M2 check_coupon: cupon valido devuelve 1 fila', 'A', `select * from check_coupon('SECRETO10', 50)`, 'filas')
await T('FUNCIONA', 'M2 check_coupon: cupon invalido devuelve 0 filas (sin error)', 'A', `select * from check_coupon('NOEXISTE', 50)`, 'vacio')
await C('MEDIO', 'M2 check_coupon: 8 fallos en 10 min bloquean con DEMASIADOS_INTENTOS', async () => {
  const e = await asRole('B', async run => {
    for (let i = 0; i < 8; i++) await run(`select * from check_coupon('MALO${i}', 10)`)
    try { await run(`select * from check_coupon('SECRETO10', 50)`); return null } catch (x) { return String(x.message) }
  }, { commit: true })
  await db.exec(`delete from public.coupon_attempts`)
  return [!!e && e.includes('DEMASIADOS_INTENTOS'), e ?? 'no bloqueó']
})
await C('MEDIO', 'M2 coupon_attempts no se puede leer ni escribir como cliente', async () => {
  const a = await as('A', `select * from coupon_attempts`), b = await as('A', `insert into coupon_attempts(user_id) values ('${U.A}')`)
  return [(!a.ok || a.n === 0) && !b.ok, `${a.ok ? 'filas=' + a.n : a.err} / ${b.ok ? 'insert ok' : 'insert denegado'}`]
})
await C('FUNCIONA', 'M1 create_order con cupon sigue funcionando y consume un uso', async () => {
  const before = (await q(`select uses from coupons where code='SECRETO10'`))[0].uses
  const z = (await q(`select id from delivery_zones limit 1`))[0].id
  const r = await asRole('A', run => run(`select create_order('ra', '[{"dish_id":"da","quantity":1,"unit_price":5}]'::jsonb, 'Calle 1', '${z}', 'cash', 50, 'SECRETO10', null, false) as r`), { commit: false })
  const used = await asRole('A', async run => {
    await run(`select create_order('ra', '[{"dish_id":"da","quantity":1,"unit_price":5}]'::jsonb, 'Calle 1', '${z}', 'cash', 50, 'SECRETO10', null, false)`)
    await db.exec('reset role')
    return (await q(`select uses from coupons where code='SECRETO10'`))[0].uses
  })
  return [r.length === 1 && Number(used) === Number(before) + 1, `usos ${before} -> ${used}`]
})
// ══ 5. Auditoría, funciones obsoletas (M3/M4/M5) ═══════════════════════════════
await T('MEDIO', 'M3 log_audit: cliente escribe auditoria', 'A', `select log_audit('create','x','y')`, 'error')
await T('MEDIO', 'M3 log_audit: anonimo', 'anon', `select log_audit('create','x','y')`, 'error')
await T('CRITICO', 'M4 create_restaurant_user: cliente se vuelve dueno de negocio', 'A', `select create_restaurant_user('${U.A}','Hack','','rb')`, 'error')
await C('ALTO', 'M4 create_restaurant_user ya no existe', async () => {
  const n = (await q(`select count(*)::int n from pg_proc where proname='create_restaurant_user'`))[0].n
  return [n === 0, `sobrecargas=${n}`]
})
await C('ALTO', 'M5 accept_order(uuid, uuid) retirada; queda solo accept_order(uuid)', async () => {
  const r = await q(`select pg_get_function_identity_arguments(oid) a from pg_proc where proname='accept_order'`)
  return [r.length === 1 && r[0].a === 'p_order_id uuid', JSON.stringify(r.map(x => x.a))]
})
await C('MEDIO', 'M5 datos de repartidor copiados a profiles (vehiculo y token)', async () => {
  const col = await q(`select column_name c from information_schema.columns where table_name='profiles' and column_name in ('vehicle_type','plate')`)
  return [col.length === 2, col.map(x => x.c).join(',')]
})
// ══ 6. Negocios y repartidores ═════════════════════════════════════════════════
await T('CRITICO', 'Negocio O1 lee pedidos de OTRO negocio', 'O1', `select id from orders where restaurant_id='rb'`)
await T('CRITICO', 'Negocio O1 lee items de OTRO negocio', 'O1', `select id from order_items where order_id='${oB}'`)
await T('ALTO', 'Negocio O1 lee comprobantes', 'O1', `select id from payment_proofs`)
await T('ALTO', 'Negocio O1 edita productos de OTRO negocio', 'O1', `update dishes set price=0.01 where restaurant_id='rb' returning id`)
await T('ALTO', 'Negocio O1 inserta producto en OTRO negocio', 'O1', `insert into dishes(id,restaurant_id,section_name,name,price) values ('zz','rb','s','x',1) returning id`, 'error')
await T('ALTO', 'Negocio O1 edita OTRO negocio', 'O1', `update restaurants set is_open=false where id='rb' returning id`)
await T('ALTO', 'Negocio O1 edita pedido de OTRO negocio (UPDATE directo)', 'O1', `update orders set status='cancelled' where restaurant_id='rb' returning id`)
await T('ALTO', 'Negocio O1 cambia su propio tipo/estado', 'O1', `update restaurants set business_type='licoreria' where id='ra' returning id`, 'error')
await T('ALTO', 'Negocio O1 lee perfiles de otros', 'O1', `select id from profiles where id<>'${U.O1}'`)
await T('CRITICO', 'Repartidor K1 lee pedidos ya asignados a OTRO', 'K1', `select id from orders where courier_id='${U.K2}'`)
await T('ALTO', 'Repartidor K1 edita pedido de OTRO (UPDATE directo)', 'K1', `update orders set status='picked_up' where courier_id='${U.K2}' returning id`)
await T('ALTO', 'Repartidor K1 se autoasigna un pedido libre (UPDATE directo)', 'K1', `update orders set courier_id='${U.K1}' where id='${oFree}' returning id`)
await T('ALTO', 'Repartidor K1 lee comprobantes', 'K1', `select id from payment_proofs`)
await T('ALTO', 'Repartidor K1 lee perfiles de clientes', 'K1', `select id from profiles where role='customer'`)
await T('MEDIO', 'M6 repartidor K1 ya NO ve direcciones de pedidos libres', 'K1', `select delivery_address from orders where id='${oFree}'`, 'vacio')
await T('MEDIO', 'M6 repartidor K1 ya NO ve el total de pedidos libres', 'K1', `select total from orders where status='ready' and courier_id is null`, 'vacio')
await T('FUNCIONA', 'M6 list_available_orders: repartidor disponible ve el pedido libre', 'K1', `select * from list_available_orders()`, 'filas')
await C('ALTO', 'M6 list_available_orders: solo columnas minimas (sin direccion, total ni cliente)', async () => {
  const r = await as('K1', `select * from list_available_orders()`)
  const k = Object.keys(r.rows?.[0] ?? {}).sort().join(',')
  const esperado = 'age_check_required,business_type,created_at,delivery_zone_name,driver_amount,id,payment_method,restaurant_name'
  return [r.ok && k === esperado, k]
})
await T('ALTO', 'M6 list_available_orders: cliente recibe 0 filas', 'A', `select * from list_available_orders()`, 'vacio')
await T('ALTO', 'M6 list_available_orders: anonimo', 'anon', `select * from list_available_orders()`, 'error')
await C('ALTO', 'M6 list_available_orders: repartidor NO disponible recibe 0 filas', async () => {
  await db.exec(`update profiles set is_available=false where id='${U.K1}'`)
  const r = await as('K1', `select * from list_available_orders()`)
  await db.exec(`update profiles set is_available=true where id='${U.K1}'`)
  return [r.ok && r.n === 0, `filas=${r.n}`]
})
await C('ALTO', 'M6 tras accept_order el repartidor ve el pedido completo', async () => {
  const r = await asRole('K1', async run => { await run(`select accept_order('${oFree}')`); return run(`select delivery_address, total from orders where id='${oFree}'`) })
  return [r.length === 1 && r[0].delivery_address === 'Casa de B (libre)', JSON.stringify(r[0] ?? null)]
})
// ══ 7. Anónimo ═════════════════════════════════════════════════════════════════
for (const t of ['orders', 'order_items', 'profiles', 'addresses', 'payment_proofs', 'settings', 'coupons', 'audit_log', 'cash_collections', 'delivery_zones'])
  await T(t === 'settings' || t === 'delivery_zones' ? 'MEDIO' : 'CRITICO', `ANONIMO lee ${t}`, 'anon', `select * from ${t}`)
// ══ 8. A3 get_order_contact ════════════════════════════════════════════════════
const contact = async (who, id) => { const r = await as(who, `select * from get_order_contact('${id}')`); return r }
await C('ALTO', 'A3 repartidor asignado ve telefono del negocio y nombre/telefono del cliente', async () => {
  const r = await contact('K2', oActive), x = r.rows?.[0]
  return [r.ok && x.restaurant_phone === '0991234567' && x.customer_name === 'Persona B' && x.customer_phone === '0990000002', JSON.stringify(x ?? r.err)]
})
await C('ALTO', 'A3 cliente ve nombre, vehiculo y placa del repartidor', async () => {
  const r = await contact('B', oActive), x = r.rows?.[0]
  return [r.ok && x.courier_name === 'Persona K2' && x.courier_vehicle === 'moto' && x.courier_plate === 'ABC123', JSON.stringify(x ?? r.err)]
})
await C('ALTO', 'A3 dueno del negocio ve cliente y repartidor', async () => {
  const r = await contact('O2', oActive), x = r.rows?.[0]
  return [r.ok && x.customer_phone === '0990000002' && x.courier_name === 'Persona K2', JSON.stringify(x ?? r.err)]
})
await T('FUNCIONA', 'A3 admin ve el contacto', 'AD', `select * from get_order_contact('${oActive}')`, 'filas')
await T('CRITICO', 'A3 cliente A pide contacto de pedido ajeno', 'A', `select * from get_order_contact('${oActive}')`, 'error')
await T('CRITICO', 'A3 negocio O1 pide contacto de pedido de otro negocio', 'O1', `select * from get_order_contact('${oActive}')`, 'error')
await T('CRITICO', 'A3 repartidor K1 pide contacto de pedido de K2', 'K1', `select * from get_order_contact('${oActive}')`, 'error')
await T('CRITICO', 'A3 anonimo pide contacto', 'anon', `select * from get_order_contact('${oActive}')`, 'error')
await C('ALTO', 'A3 pedido entregado: el repartidor ya no recibe datos personales', async () => {
  const r = await contact('K2', oDone), x = r.rows?.[0]
  return [r.ok && Object.values(x).every(v => v === null), JSON.stringify(x ?? r.err)]
})
await C('ALTO', 'A3 telefono falso o vacio sale NULL (nunca se puede marcar)', async () => {
  await db.exec(`update profiles set phone='+593 99 999 8888' where id='${U.B}'; update restaurants set phone='' where id='rb'; update profiles set phone='' where id='${U.O2}'`)
  const x = (await contact('K2', oActive)).rows?.[0]
  await db.exec(`update profiles set phone='${phone.B}' where id='${U.B}'; update restaurants set phone='0991234567' where id='rb'; update profiles set phone='${phone.O2}' where id='${U.O2}'`)
  return [x && x.customer_phone === null && x.restaurant_phone === null, JSON.stringify(x)]
})
// ══ 9. A4 correo confirmado ════════════════════════════════════════════════════
await C('CRITICO', 'A4 create_order con correo SIN confirmar -> EMAIL_NO_CONFIRMADO', async () => {
  const z = (await q(`select id from delivery_zones limit 1`))[0].id
  const e = await errOf('NC', `select create_order('ra', '[{"dish_id":"da","quantity":1,"unit_price":5}]'::jsonb, 'Calle 1', '${z}', 'cash', 50, null, null, false)`)
  return [!!e && e.includes('EMAIL_NO_CONFIRMADO'), e ?? 'creó el pedido']
})
await C('CRITICO', 'A4 INSERT directo en orders con correo sin confirmar -> EMAIL_NO_CONFIRMADO', async () => {
  let e = null
  try { await db.exec(`insert into orders(customer_id,restaurant_id,restaurant_name,subtotal,delivery_fee,total,status,payment_method) values ('${U.NC}','ra','x',1,0,1,'pending','cash')`) } catch (x) { e = String(x.message) }
  return [!!e && e.includes('EMAIL_NO_CONFIRMADO'), e ?? 'insertó']
})
await C('FUNCIONA', 'A4 create_order con correo confirmado funciona', async () => {
  const z = (await q(`select id from delivery_zones limit 1`))[0].id
  const e = await errOf('A', `select create_order('ra', '[{"dish_id":"da","quantity":1,"unit_price":5}]'::jsonb, 'Calle 1', '${z}', 'cash', 50, null, null, false)`)
  return [e === null, e ?? 'ok']
})
// ══ 10. Caminos legítimos de change_order_status ═══════════════════════════════
await T('FUNCIONA', 'C2 cliente A cancela SU pedido pendiente', 'A', `select change_order_status('${oA}','cancelled')`, 'filas')
await T('FUNCIONA', 'C2 negocio O1 acepta SU pedido', 'O1', `select change_order_status('${oA}','accepted')`, 'filas')
await T('FUNCIONA', 'C2 admin mueve cualquier pedido', 'AD', `select change_order_status('${oB}','cancelled')`, 'filas')
await C('FUNCIONA', 'C2 repartidor K2 entrega SU pedido en camino', async () => {
  const r = await as('K2', `select change_order_status('${oActive}','delivered')`)
  return [r.ok, r.ok ? 'ok' : r.err]
})
await T('FUNCIONA', 'Cliente A lee SU pedido', 'A', `select id from orders where id='${oA}'`, 'filas')
await T('FUNCIONA', 'Negocio O1 lee SUS pedidos', 'O1', `select id from orders where restaurant_id='ra'`, 'filas')
await T('FUNCIONA', 'Repartidor K2 lee SU pedido asignado', 'K2', `select id from orders where id='${oK2}'`, 'filas')
await T('FUNCIONA', 'Admin lee todos los pedidos', 'AD', `select id from orders`, 'filas')
await T('FUNCIONA', 'Cliente ejecuta get_my_role() (lo usa RLS)', 'A', `select get_my_role()`, 'filas')
await T('FUNCIONA', 'Cliente ejecuta get_effective_min_order', 'A', `select get_effective_min_order('ra')`, 'filas')
// ══ 11. C3 notificaciones sin configuración ═══════════════════════════════════
const calls = async () => (await q(`select count(*)::int n from net.calls`))[0].n
await C('CRITICO', 'C3 marcar "listo" SIN configuracion no lanza error ni envia nada', async () => {
  const n0 = await calls()
  await db.exec(`update orders set status='ready' where id='${oPend}'`)
  return [(await calls()) === n0, `llamadas=${await calls()}`]
})
if (!SIN) {
  await C('ALTO', 'C3 call_edge_function sin configuracion no falla', async () => {
    await db.exec(`select public.call_edge_function('x', '{}'::jsonb)`)
    return [true, '']
  })
  await C('ALTO', 'C3 edge_config_status informa que falta configurar', async () => {
    const s = (await q(`select public.edge_config_status() s`))[0].s
    return [s.edge_functions_url_set === false && s.webhook_secret_set === false, JSON.stringify(s)]
  })
  await db.exec(`update settings set value='https://proj.supabase.co/functions/v1' where key='edge_functions_url';
                 insert into vault.secrets(name, secret) values ('webhook_secret', 'S3CRETO');`)
  await C('CRITICO', 'C3 con configuracion: "listo" llama a notify-repartidores con secreto y payload minimo', async () => {
    await db.exec(`delete from net.calls`)
    await db.exec(`update orders set status='ready' where id='${oPend2}'`)
    const rows = await q(`select url, body, headers from net.calls order by id`)
    const nr = rows.find(r => r.url.endsWith('/notify-repartidores'))
    if (!nr) return [false, 'no llamó: ' + JSON.stringify(rows.map(r => r.url))]
    const rec = Object.keys(nr.body.record).sort().join(',')
    const ok = nr.url === 'https://proj.supabase.co/functions/v1/notify-repartidores'
      && nr.headers['x-webhook-secret'] === 'S3CRETO' && !JSON.stringify(nr.headers).includes('Authorization')
      && rec === 'courier_id,id,restaurant_name,status' && !JSON.stringify(nr.body).includes('Casa de B')
    return [ok, `${nr.url} record=${rec}`]
  })
  await C('ALTO', 'C3 el aviso al cliente usa send-push-notification con secreto', async () => {
    const r = (await q(`select url, headers, body from net.calls where url like '%/send-push-notification' limit 1`))[0]
    return [!!r && r.headers['x-webhook-secret'] === 'S3CRETO' && r.body.user_id === U.B, r ? r.url : 'sin llamada']
  })
  await C('CRITICO', 'C3 si pg_net falla, marcar "listo" sigue funcionando', async () => {
    await db.exec(`create or replace function net.http_post(url text, body jsonb default '{}', params jsonb default '{}', headers jsonb default '{}', timeout_milliseconds int default 5000) returns bigint language plpgsql as $$ begin raise exception 'boom'; end $$`)
    let e = null
    try { await db.exec(`update orders set status='pending' where id='${oPend2}'; update orders set status='accepted' where id='${oPend2}'; update orders set status='ready' where id='${oPend2}'`) } catch (x) { e = String(x.message) }
    await db.exec(`create or replace function net.http_post(url text, body jsonb default '{}', params jsonb default '{}', headers jsonb default '{"Content-Type":"application/json"}', timeout_milliseconds int default 5000) returns bigint language plpgsql as $$ begin insert into net.calls(url, body, headers) values (url, body, headers); return 1; end $$`)
    return [e === null, e ?? 'ok']
  })
}
await C('CRITICO', 'C3 ninguna funcion ni tarea usa app.service_role_key / app.supabase_url / app.edge_function_url', async () => {
  const f = await q(`select p.proname n from pg_proc p join pg_namespace s on s.oid=p.pronamespace where s.nspname='public' and p.prosrc ~ 'service_role_key|app\\.supabase_url|app\\.edge_function_url'`)
  const j = await q(`select jobname from cron.job where command ~ 'service_role_key|app\\.'`)
  return [f.length === 0 && j.length === 0, [...f.map(x => x.n), ...j.map(x => 'cron:' + x.jobname)].join(',')]
})
// ══ 12. M7 permisos de funciones ═══════════════════════════════════════════════
const fnq = `select p.proname n, p.oid, p.proconfig cfg from pg_proc p join pg_namespace s on s.oid=p.pronamespace
  where s.nspname='public' and p.prokind='f' and p.proname not in ('gen_salt','crypt') and not exists (select 1 from pg_depend d where d.objid=p.oid and d.deptype='e')`
await C('ALTO', 'M7 anonimo solo ejecuta get_my_role, is_admin y get_my_restaurant_id', async () => {
  const x = (await q(`select n from (${fnq}) f where has_function_privilege('anon', oid, 'EXECUTE')`)).map(r => r.n).filter(n => !['get_my_role', 'is_admin', 'get_my_restaurant_id'].includes(n))
  return [x.length === 0, x.join(',')]
})
await C('ALTO', 'M7 authenticated solo ejecuta la lista blanca', async () => {
  const ok = new Set(['accept_order', 'admin_report_by_type', 'change_order_status', 'check_coupon', 'confirm_age_verification', 'create_order', 'get_driver_earnings', 'get_driver_location', 'get_effective_min_order', 'get_my_restaurant_id', 'get_my_role', 'get_order_contact', 'get_order_rating', 'get_restaurant_promo', 'get_restaurant_whatsapp', 'get_split_pcts', 'is_admin', 'is_adult', 'list_active_promotions', 'list_available_orders', 'review_prescription', 'set_birth_date', 'settle_cash_collections', 'submit_rating', 'toggle_favorite', 'update_driver_location', 'upload_payment_proof', 'verify_payment_proof'])
  const x = (await q(`select n from (${fnq}) f where has_function_privilege('authenticated', oid, 'EXECUTE')`)).map(r => r.n).filter(n => !ok.has(n))
  return [x.length === 0, [...new Set(x)].join(',')]
})
await C('MEDIO', 'M7 toda funcion de public tiene search_path fijo', async () => {
  const x = (await q(fnq)).filter(r => !(r.cfg ?? []).some(c => c.startsWith('search_path='))).map(r => r.n)
  return [x.length === 0, [...new Set(x)].join(',')]
})
await C('MEDIO', 'M7 service_role conserva EXECUTE (Edge Functions y cron)', async () => {
  const x = (await q(`select n from (${fnq}) f where not has_function_privilege('service_role', oid, 'EXECUTE')`)).map(r => r.n)
  return [x.length === 0, x.join(',')]
})

// ══ 13. Promociones y WhatsApp (Fase 4) ════════════════════════════════════════
if (!SIN) {
  const FUT = `now() + interval '7 days'`
  // RLS: cada negocio gestiona solo las suyas
  await C('CRITICO', 'P1 negocio O1 crea promocion en SU negocio', async () => {
    const r = await as('O1', `insert into promotions(restaurant_id,title,discount_percent,ends_at) values ('ra','Promo A',20,${FUT}) returning id`)
    return [r.ok && r.n === 1, r.ok ? 'ok' : r.err]
  })
  await db.exec(`insert into promotions(restaurant_id,title,discount_percent,ends_at) values ('ra','Promo A',20,${FUT})`)
  await T('CRITICO', 'P2 negocio O1 NO crea promocion en OTRO negocio', 'O1', `insert into promotions(restaurant_id,title,discount_percent,ends_at) values ('rb','Hack',50,${FUT}) returning id`, 'error')
  await T('CRITICO', 'P3 negocio O2 NO edita promocion de OTRO negocio', 'O2', `update promotions set discount_percent=70 where restaurant_id='ra' returning id`)
  await T('CRITICO', 'P4 negocio O2 NO borra promocion de OTRO negocio', 'O2', `delete from promotions where restaurant_id='ra' returning id`)
  await T('CRITICO', 'P5 negocio O1 NO mueve su promocion a otro negocio', 'O1', `update promotions set restaurant_id='rb' where restaurant_id='ra' returning id`, 'error')
  await T('ALTO', 'P6 cliente NO lee la tabla promotions', 'A', `select * from promotions`)
  await T('ALTO', 'P7 cliente NO inserta promociones', 'A', `insert into promotions(restaurant_id,title,discount_percent,ends_at) values ('ra','Hack',50,${FUT})`, 'error')
  await T('ALTO', 'P8 anonimo NO lee promotions', 'anon', `select * from promotions`, 'error')
  await T('MEDIO', 'P9 porcentaje 0 y 90 rechazados', 'O1', `insert into promotions(restaurant_id,title,discount_percent,ends_at) values ('ra','Cero',0,${FUT})`, 'error')
  await T('MEDIO', 'P10 porcentaje 90 rechazado', 'O1', `insert into promotions(restaurant_id,title,discount_percent,ends_at) values ('ra','Noventa',90,${FUT})`, 'error')
  await T('MEDIO', 'P11 fin antes del inicio rechazado', 'O1', `insert into promotions(restaurant_id,title,discount_percent,starts_at,ends_at) values ('ra','Fechas',10,now(),now() - interval '1 day')`, 'error')
  await T('FUNCIONA', 'P12 negocio O1 ve SUS promociones', 'O1', `select * from promotions where restaurant_id='ra'`, 'filas')
  await T('FUNCIONA', 'P13 admin ve todas', 'AD', `select * from promotions`, 'filas')
  // Lectura para el cliente: solo vigentes y activas
  await db.exec(`insert into promotions(restaurant_id,title,discount_percent,starts_at,ends_at) values ('rb','Vencida',50,now() - interval '3 days', now() - interval '1 day');
                 insert into promotions(restaurant_id,title,discount_percent,ends_at,is_active) values ('rb','Apagada',50,${FUT},false);
                 insert into promotions(restaurant_id,title,discount_percent,starts_at,ends_at) values ('rb','Futura',50,now() + interval '2 days',${FUT})`)
  await C('ALTO', 'P14 list_active_promotions: solo la vigente y activa (no vencida, apagada ni futura)', async () => {
    const r = await as('A', `select title from list_active_promotions()`)
    return [r.ok && r.rows.length === 1 && r.rows[0].title === 'Promo A', JSON.stringify(r.rows ?? r.err)]
  })
  await T('ALTO', 'P15 list_active_promotions: anonimo denegado', 'anon', `select * from list_active_promotions()`, 'error')
  await C('ALTO', 'P16 get_restaurant_promo devuelve la vigente de ese negocio y nada del otro', async () => {
    const a = await as('A', `select discount_percent from get_restaurant_promo('ra')`), b = await as('A', `select * from get_restaurant_promo('rb')`)
    return [a.ok && a.rows[0]?.discount_percent === 20 && b.ok && b.n === 0, `ra=${JSON.stringify(a.rows)} rb=${b.n}`]
  })
  await T('ALTO', 'P17 cliente NO ejecuta _active_promo_percent (interna)', 'A', `select _active_promo_percent('ra')`, 'error')
  // create_order aplica la promo en el servidor
  const zone = (await q(`select id from delivery_zones limit 1`))[0].id
  const mk = (coupon = 'null') => `select create_order('ra', '[{"dish_id":"da","quantity":1,"unit_price":5}]'::jsonb, 'Calle 1', '${zone}', 'cash', 50, ${coupon}, null, false) as r`
  await C('CRITICO', 'P18 create_order aplica 20% sobre el subtotal: subtotal 5, promo 1.00, envio 1.50 -> total 5.50', async () => {
    const r = await as('A', mk()), j = r.rows?.[0]?.r
    return [r.ok && Number(j.discount) === 1 && Number(j.promo_discount) === 1 && Number(j.total) === 5.5, JSON.stringify(j ?? r.err)]
  })
  await C('CRITICO', 'P19 create_order promo + cupon de $2: descuento 3.00, total 3.50', async () => {
    const r = await as('A', mk(`'SECRETO10'`)), j = r.rows?.[0]?.r
    return [r.ok && Number(j.discount) === 3 && Number(j.total) === 3.5, JSON.stringify(j ?? r.err)]
  })
  await C('ALTO', 'P20 el negocio paga la promo: su parte se calcula sobre el subtotal ya descontado', async () => {
    const o = await asRole('A', async run => {
      const j = (await run(mk()))[0].r
      await db.exec('reset role')
      return (await q(`select subtotal, discount, total, business_amount, driver_amount, platform_amount from orders where id='${j.order_id}'`))[0]
    })
    const sum = Number(o.business_amount) + Number(o.driver_amount) + Number(o.platform_amount)
    return [Math.abs(sum - Number(o.total)) < 0.011 && Number(o.discount) === 1, JSON.stringify(o)]
  })
  await C('ALTO', 'P21 sin promocion vigente create_order no descuenta (negocio rb)', async () => {
    const j = (await asRole('B', run => run(`select create_order('rb', '[{"dish_id":"db","quantity":1,"unit_price":5}]'::jsonb, 'Calle 1', '${zone}', 'cash', 50, null, null, false) as r`)))[0].r
    return [Number(j.discount) === 0 && Number(j.total) === 6.5, JSON.stringify(j)]
  })
  // WhatsApp
  await db.exec(`insert into restaurants(id,name,category,business_type,owner_id,is_open,is_active,phone) values ('rl','Licoreria','x','licoreria','${U.O2}',true,true,'0991234567') on conflict do nothing`)
  const wa = async (who, id) => (await as(who, `select get_restaurant_whatsapp('${id}') w`)).rows?.[0]?.w
  await C('ALTO', 'W1 whatsapp: telefono del negocio en formato internacional', async () => { const w = await wa('A', 'rb'); return [w === '593991234567', String(w)] })
  await C('ALTO', 'W2 whatsapp: sin telefono del negocio usa el del dueno', async () => { const w = await wa('A', 'ra'); return [w === '593990000003', String(w)] })
  await C('CRITICO', 'W3 whatsapp: licoreria devuelve NULL (no se salta la verificacion de edad)', async () => { const w = await wa('A', 'rl'); return [w === null, String(w)] })
  await C('ALTO', 'W4 whatsapp: numero falso devuelve NULL', async () => {
    await db.exec(`update restaurants set phone='+593 99 999 8888' where id='rb'; update profiles set phone='' where id='${U.O2}'`)
    const w = await wa('A', 'rb')
    await db.exec(`update restaurants set phone='0991234567' where id='rb'; update profiles set phone='${phone.O2}' where id='${U.O2}'`)
    return [w === null, String(w)]
  })
  await T('FUNCIONA', 'W6 negocio O1 actualiza el telefono de SU negocio', 'O1', "update restaurants set phone='0991112233' where id='ra' returning id", 'filas')
  await T('ALTO', 'W7 negocio O1 NO cambia el telefono de OTRO negocio', 'O1', "update restaurants set phone='0990000000' where id='rb' returning id")
  await T('ALTO', 'W5 whatsapp: anonimo denegado', 'anon', `select get_restaurant_whatsapp('rb')`, 'error')
}

// ── Informe ────────────────────────────────────────────────────────────────────
const order = { CRITICO: 0, ALTO: 1, MEDIO: 2, FUNCIONA: 3 }
results.sort((a, b) => order[a.sev] - order[b.sev])
for (const r of results) console.log(`${r.pass ? '[ok]   ' : '[FALLA]'} ${r.sev.padEnd(8)} ${r.name} -> ${r.det}`)
const fails = results.filter(r => !r.pass)
console.log(`\nMODO: ${SIN ? 'SIN hardening (estado actual de tu base)' : 'CON hardening (24)'}`)
console.log(`RESUMEN: ${results.length - fails.length}/${results.length} correctos; fallas: ${fails.length} (criticas ${fails.filter(f => f.sev === 'CRITICO').length}, altas ${fails.filter(f => f.sev === 'ALTO').length}, medias ${fails.filter(f => f.sev === 'MEDIO').length}, de funcionamiento ${fails.filter(f => f.sev === 'FUNCIONA').length})`)
process.exit(fails.length ? 1 : 0)
