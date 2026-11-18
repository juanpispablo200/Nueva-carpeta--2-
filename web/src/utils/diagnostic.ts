import { createClient } from '@supabase/supabase-js'

export async function diagnoseSupabase() {
  const url = 'https://tjgavwygdotetoatitju.supabase.co'
  const key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRqZ2F2d3lnZG90ZXRvYXRpdGp1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTQ4NjA2ODgsImV4cCI6MjExMDQzNjY4OH0.e_xgPLnPAgJgpUCRPcusITh9V2xVkn_O8B_AKs5PtaI'
  
  console.log('🔍 Diagnóstico Supabase')
  console.log('URL:', url)
  console.log('Key length:', key.length)
  
  try {
    const supabase = createClient(url, key)
    
    // Test 1: Verificar conexión básica
    console.log('\n1. Probando conexión básica...')
    const { data: health, error: healthError } = await supabase.from('profiles').select('count')
    if (healthError) console.log('❌ Error de conexión:', healthError.message)
    else console.log('✅ Conexión exitosa')
    
    // Test 2: Verificar estructura de profiles
    console.log('\n2. Verificando estructura de profiles...')
    try {
      const { data: columns, error: columnsError } = await supabase
        .rpc('get_table_columns', { table_name: 'profiles' })
      
      if (!columnsError && Array.isArray(columns)) {
        console.log('✅ Tabla profiles encontrada')
        console.log('Columnas:', columns.map((c: any) => c.column_name).join(', '))
      } else {
        // Intentar método alternativo
        const { data: sample, error: sampleError } = await supabase
          .from('profiles')
          .select('*')
          .limit(1)
        
        if (sampleError) console.log('❌ Error al leer profiles:', sampleError.message)
        else console.log('✅ Profiles leído, muestra:', sample)
      }
    } catch (err: any) {
      console.log('⚠️  RPC no disponible, usando método alternativo')
      const { data: sample, error: sampleError } = await supabase
        .from('profiles')
        .select('*')
        .limit(1)
      
      if (sampleError) console.log('❌ Error al leer profiles:', sampleError.message)
      else console.log('✅ Profiles leído, muestra:', sample)
    }
    
    // Test 3: Verificar usuarios Auth
    console.log('\n3. Verificando autenticación...')
    const { data: { session }, error: sessionError } = await supabase.auth.getSession()
    if (sessionError) console.log('❌ Error de sesión:', sessionError.message)
    else console.log('✅ Auth configurado, sesión:', session ? 'activa' : 'inactiva')
    
    return { success: true }
  } catch (error: any) {
    console.log('❌ Error general:', error.message)
    return { success: false, error: error.message }
  }
}

// Ejecutar si se llama directamente
if (import.meta.env?.MODE === 'development') {
  diagnoseSupabase()
}