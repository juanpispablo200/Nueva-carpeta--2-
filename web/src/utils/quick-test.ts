import { createClient } from '@supabase/supabase-js'

async function quickTest() {
  console.log('🔍 Test rápido de Supabase')
  
  const supabase = createClient(
    'https://tjgavwygdotetoatitju.supabase.co',
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRqZ2F2d3lnZG90ZXRvYXRpdGp1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTQ4NjA2ODgsImV4cCI6MjExMDQzNjY4OH0.e_xgPLnPAgJgpUCRPcusITh9V2xVkn_O8B_AKs5PtaI'
  )

  try {
    // Test 1: Verificar conexión
    const { error } = await supabase.from('profiles').select('id').limit(1)
    
    if (error) {
      console.log('❌ Error:', error.message)
      
      if (error.message.includes('is_active')) {
        console.log('\n⚠️  DIAGNÓSTICO:')
        console.log('Falta la columna "is_active" en la tabla "profiles"')
        console.log('Esto significa que LA MIGRACIÓN NO ESTÁ APLICADA')
      } else if (error.message.includes('does not exist')) {
        console.log('\n⚠️  DIAGNÓSTICO:')
        console.log('La tabla "profiles" no existe o no está accesible')
      }
      
      return {
        success: false,
        error: error.message,
        solution: 'Aplicar migración APLICAR_ESTA_MIGRACION.sql'
      }
    }
    
    // Test 2: Verificar columnas específicas
    console.log('✅ Conexión exitosa')
    
    const columnTests = [
      { name: 'is_active', desc: 'Columna para cuentas activas' },
      { name: 'role', desc: 'Rol del usuario' },
      { name: 'full_name', desc: 'Nombre completo' }
    ]
    
    for (const test of columnTests) {
      const { error } = await supabase.from('profiles').select(test.name).limit(1)
      console.log(`${test.name} (${test.desc}):`, error ? '❌ FALTA' : '✅ OK')
    }
    
    return { success: true }
    
  } catch (err: any) {
    console.log('❌ Error crítico:', err.message)
    return { success: false, error: err.message }
  }
}

// Ejecutar si estamos en Node.js
if (typeof window === 'undefined') {
  quickTest()
}

export { quickTest }