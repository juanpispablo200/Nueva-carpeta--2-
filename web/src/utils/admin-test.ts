import { createClient } from '@supabase/supabase-js'

const supabase = createClient(
  'https://tjgavwygdotetoatitju.supabase.co',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRqZ2F2d3lnZG90ZXRvYXRpdGp1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTQ4NjA2ODgsImV4cCI6MjExMDQzNjY4OH0.e_xgPLnPAgJgpUCRPcusITh9V2xVkn_O8B_AKs5PtaI'
)

export async function testAdminAccess() {
  console.log('🔍 Test de acceso admin')
  
  try {
    // 1. Verificar conexión a profiles
    console.log('1. Probando tabla profiles...')
    const { data: profiles, error: profilesError } = await supabase
      .from('profiles')
      .select('id, role, is_active')
      .limit(3)
    
    if (profilesError) {
      console.log('❌ Error en profiles:', profilesError.message)
      return { success: false, error: 'profiles: ' + profilesError.message }
    }
    
    console.log('✅ Profiles accesible. Registros:', profiles?.length || 0)
    
    // 2. Verificar columnas
    console.log('\n2. Verificando columnas...')
    const testColumns = ['is_active', 'role', 'full_name', 'phone']
    for (const col of testColumns) {
      const { error } = await supabase.from('profiles').select(col).limit(1)
      console.log(`${col}:`, error ? `❌ ${error.message}` : '✅ OK')
    }
    
    // 3. Verificar usuarios existentes
    console.log('\n3. Buscando usuarios admin...')
    const { data: adminUsers, error: adminError } = await supabase
      .from('profiles')
      .select('id, full_name, role, is_active')
      .eq('role', 'admin')
    
    if (adminError) {
      console.log('❌ Error buscando admins:', adminError.message)
    } else {
      console.log(`✅ Admins encontrados: ${adminUsers?.length || 0}`)
      if (adminUsers && adminUsers.length > 0) {
        adminUsers.forEach((user, i) => {
          console.log(`  ${i + 1}. ${user.full_name || 'Sin nombre'} (activo: ${user.is_active})`)
        })
      }
    }
    
    // 4. Test de login con credenciales comunes
    console.log('\n4. Probando credenciales comunes...')
    const testCredentials = [
      { email: 'admin@jagoo.com', password: 'Admin123' },
      { email: 'admin@admin.com', password: 'admin123' },
      { email: 'administrator@jagoo.com', password: 'admin' }
    ]
    
    for (const creds of testCredentials) {
      const { error } = await supabase.auth.signInWithPassword({
        email: creds.email,
        password: creds.password
      })
      
      if (error) {
        console.log(`${creds.email}: ❌ ${error.message}`)
      } else {
        console.log(`${creds.email}: ✅ Login exitoso!`)
        await supabase.auth.signOut()
        break
      }
    }
    
    return { success: true, adminCount: adminUsers?.length || 0 }
    
  } catch (err: any) {
    console.log('❌ Error crítico:', err.message)
    return { success: false, error: err.message }
  }
}

// Si ejecutamos directamente
if (typeof window === 'undefined') {
  testAdminAccess()
    .then(result => {
      console.log('\n🎯 DIAGNÓSTICO COMPLETO:')
      if (!result.success) {
        console.log('Necesitas aplicar la migración o crear usuarios.')
      } else if (result.adminCount === 0) {
        console.log('⚠️  No hay usuarios admin. Crea uno con:')
        console.log(`
-- SQL para crear admin:
INSERT INTO auth.users (...) VALUES (...);
INSERT INTO profiles (...) VALUES (...);
        `)
      } else {
        console.log('✅ Sistema listo para login admin')
      }
    })
    .catch(console.error)
}