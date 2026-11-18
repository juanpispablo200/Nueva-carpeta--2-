package com.jagoo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagoo.app.data.UserRole
import com.jagoo.app.data.remote.SupabaseClient
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

sealed class AuthState {
    object Idle       : AuthState()
    object Loading    : AuthState()
    object Customer   : AuthState()
    data class WrongRole(val role: String) : AuthState()
    data class Error(val message: String)  : AuthState()
    object LoggedOut  : AuthState()
}

@Serializable
private data class ProfileRole(
    val role: String = UserRole.CUSTOMER,
    @SerialName("is_active") val isActive: Boolean = true
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val supabase = SupabaseClient.client
    private val auth     = supabase.auth

    private val _state    = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _email    = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _fullName = MutableStateFlow("")
    val fullName: StateFlow<String> = _fullName.asStateFlow()

    fun onEmailChange(v: String)    { _email.value    = v }
    fun onPasswordChange(v: String) { _password.value = v }
    fun onFullNameChange(v: String) { _fullName.value = v }

    init { checkExistingSession() }

    private fun checkExistingSession() {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            try {
                withTimeout(5_000L) {
                    auth.refreshCurrentSession()
                }
                resolveRole()
            } catch (e: TimeoutCancellationException) {
                _state.value = AuthState.LoggedOut
            } catch (e: Exception) {
                _state.value = AuthState.LoggedOut
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
                resolveRole()
            } catch (_: Exception) {
                resolveRole()
            }
        }
    }

    fun login() {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _state.value = AuthState.Error("Completa email y contraseña")
            return
        }
        viewModelScope.launch {
            _state.value = AuthState.Loading
            try {
                withTimeout(10_000L) {
                    auth.signInWith(Email) {
                        email    = _email.value.trim()
                        password = _password.value
                    }
                }
                resolveRole()
            } catch (e: TimeoutCancellationException) {
                _state.value = AuthState.Error("Sin conexión. Verifica tu internet")
            } catch (e: Exception) {
                _state.value = AuthState.Error(friendlyError(e))
            }
        }
    }

    fun register() {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _state.value = AuthState.Error("Completa email y contraseña")
            return
        }
        if (_password.value.length < 6) {
            _state.value = AuthState.Error("La contraseña debe tener al menos 6 caracteres")
            return
        }
        viewModelScope.launch {
            _state.value = AuthState.Loading
            try {
                withTimeout(10_000L) {
                    auth.signUpWith(Email) {
                        email    = _email.value.trim()
                        password = _password.value
                        data     = kotlinx.serialization.json.buildJsonObject {
                            put("full_name", kotlinx.serialization.json.JsonPrimitive(_fullName.value.trim()))
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

                resolveRole()
            } catch (e: TimeoutCancellationException) {
                _state.value = AuthState.Error("Sin conexión. Verifica tu internet")
            } catch (e: Exception) {
                _state.value = AuthState.Error(friendlyError(e))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try { auth.signOut() } catch (_: Exception) {}
            _state.value    = AuthState.LoggedOut
            _email.value    = ""
            _password.value = ""
            _fullName.value = ""
        }
    }

    private suspend fun resolveRole() {
        val userId = auth.currentUserOrNull()?.id ?: run {
            _state.value = AuthState.LoggedOut
            return
        }
        try {
            val profile = withTimeout(8_000L) {
                supabase.from("profiles")
                    .select { filter { eq("id", userId) } }
                    .decodeSingle<ProfileRole>()
            }

            if (!profile.isActive) {
                try { auth.signOut() } catch (_: Exception) {}
                _state.value = AuthState.Error("Tu cuenta ha sido desactivada")
                return
            }

            if (profile.role == UserRole.REPARTIDOR) {
                try { auth.signOut() } catch (_: Exception) {}
                _state.value = AuthState.Error("Acceso reservado a la aplicación Jagoo Driver")
                return
            }

            _state.value = when (profile.role) {
                UserRole.CUSTOMER -> AuthState.Customer
                else              -> AuthState.WrongRole(profile.role)
            }
        } catch (e: Exception) {
            try { auth.signOut() } catch (_: Exception) {}
            _state.value = AuthState.Error("Tu cuenta ha sido desactivada")
        }
    }

    fun clearError() { _state.value = AuthState.LoggedOut }

    private fun friendlyError(e: Exception): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            msg.contains("invalid login credentials") ||
            msg.contains("invalid login")            -> "Email o contraseña incorrectos"
            msg.contains("user already registered") ||
            msg.contains("already registered")      -> "Este email ya está registrado"
            msg.contains("email not confirmed")      -> "Confirma tu email antes de entrar"
            msg.contains("network") ||
            msg.contains("unable to resolve") ||
            msg.contains("timeout") ||
            msg.contains("connection")              -> "Sin conexión a internet"
            msg.contains("rate limit")               -> "Demasiados intentos. Espera un momento"
            msg.contains("password")                 -> "Contraseña muy corta (mínimo 6 caracteres)"
            msg.contains("signup")                   -> "No se pudo crear la cuenta. Intenta de nuevo"
            else                                     -> "Error al conectar. Intenta de nuevo"
        }
    }
}
