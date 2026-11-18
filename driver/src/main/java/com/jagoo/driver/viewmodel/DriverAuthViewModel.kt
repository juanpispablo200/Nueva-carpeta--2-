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
                _state.value = DriverAuthState.Error("Credenciales incorrectas o error de conexión")
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
}
