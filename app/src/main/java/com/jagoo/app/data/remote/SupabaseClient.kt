package com.jagoo.app.data.remote

import com.jagoo.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClient {

    // Detectar si las claves están configuradas en local.properties
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
                BuildConfig.SUPABASE_URL != "PEGA_TU_URL_AQUI" &&
                BuildConfig.SUPABASE_ANON_KEY.isNotBlank() &&
                BuildConfig.SUPABASE_ANON_KEY != "PEGA_TU_ANON_KEY_AQUI"

    val client = createSupabaseClient(
        supabaseUrl = if (isConfigured) BuildConfig.SUPABASE_URL
                      else "https://placeholder.supabase.co",
        supabaseKey = if (isConfigured) BuildConfig.SUPABASE_ANON_KEY
                      else "placeholder"
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }
}
