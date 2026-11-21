package com.jagoo.driver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagoo.driver.data.UserRole
import com.jagoo.driver.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class DriverAuthState {
    object Idle       : DriverAuthState()
    object Loading    : DriverAuthState()
    object Repartidor : DriverAuthState()
    data class Error(val message: String) : DriverAuthState()
    object LoggedOut  : DriverAuthState()
}

@Serializable
private data class DriverProfileRole(
    val role: String = UserRole.REPARTIDOR,
    @SerialName("is_active") val isActive: Boolean = true
)

class DriverAuthViewModel(application: Application) : AndroidViewModel(application) {

    private val supabase = SupabaseClient.client
    private val auth     = supabase.auth

    private val _state    = MutableStateFlow<DriverAuthState>(DriverAuthState.LoggedOut)
    val state: StateFlow<DriverAuthState> = _state.asStateFlow()

    private val _email    = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    fun onEmailChange(v: String)    { _email.value    = v }
    fun onPasswordChange(v: String) { _password.value = v }

    init { checkExistingSession() }

    private fun checkExistingSession() {
        viewModelScope.launch {
            _state.value = DriverAuthState.Loading
            try {
                withTimeout(5_000L) {
                    auth.refreshCurrentSession()
                }
                resolveDriverRole()
            } catch (_: Exception) {
                _state.value = DriverAuthState.LoggedOut
            }
        }
    }

    fun validateSessionOnResume() {
        if (auth.currentUserOrNull() == null) return
        viewModelScope.launch {
            try {
                withTimeout(5_000L) {
                    auth.refreshCurrentSession()
                }
                resolveDriverRole()
            } catch (_: Exception) {
                resolveDriverRole()
            }
        }
    }

    fun login() {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _state.value = DriverAuthState.Error("Ingresa tu email y contraseña de repartidor")
            return
        }
        viewModelScope.launch {
            _state.value = DriverAuthState.Loading
            try {
                withTimeout(10_000L) {
                    auth.signInWith(Email) {
                        email    = _email.value.trim()
                        password = _password.value
                    }
                }
                resolveDriverRole()
            } catch (e: TimeoutCancellationException) {
                _state.value = DriverAuthState.Error("Sin conexión. Verifica tu internet")
            } catch (e: Exception) {
                _state.value = DriverAuthState.Error(friendlyError(e))
            }
        }
    }

    // ── Crear / Registrar Repartidor en Supabase ─────────────────────────────
    fun registerDriver() {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _state.value = DriverAuthState.Error("Completa email y contraseña para registrarte")
            return
        }
        if (_password.value.length < 6) {
            _state.value = DriverAuthState.Error("La contraseña debe tener al menos 6 caracteres")
            return
        }
        viewModelScope.launch {
            _state.value = DriverAuthState.Loading
            try {
                withTimeout(10_000L) {
                    auth.signUpWith(Email) {
                        email    = _email.value.trim()
                        password = _password.value
                        data     = kotlinx.serialization.json.buildJsonObject {
                            put("full_name", kotlinx.serialization.json.JsonPrimitive("Repartidor Jagoo"))
                            put("role", kotlinx.serialization.json.JsonPrimitive("repartidor"))
                        }
                    }
                }
                try {
                    withTimeout(8_000L) {
                        auth.signInWith(Email) {
                            email    = _email.value.trim()
                            password = _password.value
                        }
                    }
                } catch (_: Exception) {}

                val userId = auth.currentUserOrNull()?.id
                if (userId != null) {
                    try {
                        supabase.from("profiles").upsert(mapOf(
                            "id" to userId,
                            "full_name" to "Repartidor Jagoo",
                            "role" to "repartidor",
                            "is_active" to true,
                            "is_available" to true
                        ))
                    } catch (_: Exception) {}
                }

                resolveDriverRole()
            } catch (e: TimeoutCancellationException) {
                _state.value = DriverAuthState.Error("Sin conexión. Verifica tu internet")
            } catch (e: Exception) {
                _state.value = DriverAuthState.Error(friendlyError(e))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try { auth.signOut() } catch (_: Exception) {}
            _state.value    = DriverAuthState.LoggedOut
            _email.value    = ""
            _password.value = ""
        }
    }

    private suspend fun resolveDriverRole() {
        val userId = auth.currentUserOrNull()?.id ?: run {
            _state.value = DriverAuthState.LoggedOut
            return
        }
        try {
            val profile = withTimeout(8_000L) {
                supabase.from("profiles")
                    .select { filter { eq("id", userId) } }
                    .decodeSingle<DriverProfileRole>()
            }

            if (!profile.isActive) {
                try { auth.signOut() } catch (_: Exception) {}
                _state.value = DriverAuthState.Error("Tu cuenta de repartidor ha sido desactivada")
                return
            }

            if (profile.role != UserRole.REPARTIDOR) {
                try { auth.signOut() } catch (_: Exception) {}
                _state.value = DriverAuthState.Error("Acceso exclusivo para repartidores Jagoo")
                return
            }

            _state.value = DriverAuthState.Repartidor
        } catch (e: Exception) {
            try { auth.signOut() } catch (_: Exception) {}
            _state.value = DriverAuthState.Error("Tu cuenta ha sido desactivada o no posee el rol autorizado")
        }
    }

    fun clearError() { _state.value = DriverAuthState.LoggedOut }

    private fun friendlyError(e: Exception): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            msg.contains("invalid login credentials") ||
            msg.contains("invalid login")            -> "Email o contraseña incorrectos. Si es tu primera vez, pulsa '¿No existe tu cuenta? Registrar como Repartidor'"
            msg.contains("email not confirmed")      -> "Tu email requiere confirmación. Desactiva 'Confirm email' en Supabase Dashboard ➔ Auth ➔ Providers ➔ Email"
            msg.contains("already registered") ||
            msg.contains("already in use")          -> "Este email ya está registrado. Ingresa tu contraseña y pulsa 'Ingresar como Repartidor'"
            msg.contains("user not found")           -> "El usuario no existe. Pulsa '¿No existe tu cuenta? Registrar como Repartidor'"
            msg.contains("network") ||
            msg.contains("unable to resolve") ||
            msg.contains("timeout") ||
            msg.contains("connection")              -> "Sin conexión a internet"
            else                                     -> e.message ?: "Credenciales incorrectas o error de conexión"
        }
    }
}
