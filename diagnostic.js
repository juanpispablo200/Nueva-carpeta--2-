const { createClient } = require('@supabase/supabase-js');

const supabaseUrl = 'https://tjgavwygdotetoatitju.supabase.co';
const supabaseKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRqZ2F2d3lnZG90ZXRvYXRpdGp1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTQ4NjA2ODgsImV4cCI6MjExMDQzNjY4OH0.e_xgPLnPAgJgpUCRPcusITh9V2xVkn_O8B_AKs5PtaI';

const supabase = createClient(supabaseUrl, supabaseKey);

async function diagnose() {
  console.log('🔍 Diagnóstico Supabase - Jagoo');
  console.log('================================\n');

  // 1. Test básico de conexión
  console.log('1. Probando conexión básica...');
  try {
    const { data, error } = await supabase.from('profiles').select('id').limit(1);
    if (error) {
      console.log('❌ Error:', error.message);
      if (error.message.includes('is_active')) {
        console.log('⚠️  Falta columna is_active - Necesita migración');
      }
    } else {
      console.log('✅ Conexión exitosa');
    }
  } catch (err) {
    console.log('❌ Error crítico:', err.message);
  }

  // 2. Verificar columnas de profiles
  console.log('\n2. Verificando estructura de profiles...');
  try {
    // Intentamos varias consultas para ver qué columnas existen
    const tests = [
      { name: 'columna is_active', query: 'SELECT is_active FROM profiles LIMIT 1' },
      { name: 'columna role', query: 'SELECT role FROM profiles LIMIT 1' },
      { name: 'columna full_name', query: 'SELECT full_name FROM profiles LIMIT 1' }
    ];

    for (const test of tests) {
      const { error } = await supabase.from('profiles').select(test.name.split(' ')[1]).limit(1);
      if (error) {
        console.log(`❌ ${test.name}: ${error.message}`);
      } else {
        console.log(`✅ ${test.name}: Existe`);
      }
    }
  } catch (err) {
    console.log('Error en verificación:', err.message);
  }

  // 3. Verificar si hay usuarios
  console.log('\n3. Verificando usuarios existentes...');
  try {
    const { data: users, error } = await supabase.auth.admin.listUsers();
    if (error) {
      console.log('❌ Error al listar usuarios:', error.message);
    } else {
      console.log(`✅ Usuarios en auth: ${users.users.length}`);
      if (users.users.length > 0) {
        console.log('Primeros 3 usuarios:');
        users.users.slice(0, 3).forEach((user, i) => {
          console.log(`  ${i + 1}. ${user.email} (${user.id})`);
        });
      }
    }
  } catch (err) {
    console.log('❌ Error en auth:', err.message);
  }

  // 4. Verificar tabla audit_log
  console.log('\n4. Verificando tabla audit_log...');
  try {
    const { error } = await supabase.from('audit_log').select('id').limit(1);
    if (error) {
      console.log('❌ audit_log:', error.message);
      console.log('⚠️  La tabla audit_log no existe - Necesita migración');
    } else {
      console.log('✅ Tabla audit_log existe');
    }
  } catch (err) {
    console.log('Error:', err.message);
  }

  console.log('\n================================');
  console.log('🎯 CONCLUSIÓN:');
  console.log('Si ves errores sobre columnas o tablas faltantes,');
  console.log('necesitas aplicar APLICAR_ESTA_MIGRACION.sql en Supabase');
  console.log('URL: https://supabase.com/dashboard/project/tjgavwygdotetoatitju/sql/new');
}

diagnose().catch(console.error);