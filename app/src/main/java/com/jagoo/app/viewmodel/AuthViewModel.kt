package com.jagoo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagoo.app.data.UserRole
import com.jagoo.app.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

sealed class DeleteAccountState {
    object Idle    : DeleteAccountState()
    object Loading : DeleteAccountState()
    object Done    : DeleteAccountState()
    data class Error(val message: String) : DeleteAccountState()
}

@Serializable
private data class OrderStatusRow(val status: String = "")

@Serializable
private data class ProfileRole(
    val role: String = UserRole.CUSTOMER,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("full_name") val fullName: String = "",
    val phone: String = ""
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

    fun resetPassword(emailAddress: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (emailAddress.isBlank()) { onError("Ingresa tu email"); return }
        viewModelScope.launch {
            try {
                auth.resetPasswordForEmail(emailAddress.trim())
                onSuccess()
            } catch (e: Exception) {
                onError("No se pudo enviar el correo. Verifica tu email e intenta de nuevo.")
            }
        }
    }

    fun updateProfile(newName: String, newPhone: String, onDone: (Boolean) -> Unit) {
        if (newName.isBlank()) { onDone(false); return }
        viewModelScope.launch {
            try {
                val userId = auth.currentUserOrNull()?.id ?: return@launch
                supabase.from("profiles")
                    .update({
                        set("full_name", newName.trim())
                        set("phone",     newPhone.trim())
                    }) { filter { eq("id", userId) } }
                _fullName.value = newName.trim()
                onDone(true)
            } catch (_: Exception) { onDone(false) }
        }
    }

    fun loadProfile(onResult: (name: String, phone: String) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUserOrNull()?.id ?: return@launch
                val row = supabase.from("profiles")
                    .select { filter { eq("id", userId) } }
                    .decodeSingle<ProfileRole>()
                onResult(row.fullName, row.phone)
            } catch (_: Exception) { onResult("", "") }
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

    private val _deleteState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteState: StateFlow<DeleteAccountState> = _deleteState.asStateFlow()

    fun resetDeleteState() { _deleteState.value = DeleteAccountState.Idle }

    fun deleteAccount() {
        if (_deleteState.value is DeleteAccountState.Loading) return
        viewModelScope.launch {
            _deleteState.value = DeleteAccountState.Loading
            try {
                val userId = auth.currentUserOrNull()?.id
                if (userId == null) {
                    _deleteState.value = DeleteAccountState.Error("Tu sesión expiró. Inicia sesión de nuevo.")
                    return@launch
                }

                // Pre-chequeo de pedidos en curso (el servidor vuelve a validar)
                val active = withTimeout(10_000L) {
                    supabase.from("orders")
                        .select {
                            filter {
                                eq("customer_id", userId)
                                neq("status", "delivered")
                                neq("status", "cancelled")
                            }
                        }
                        .decodeList<OrderStatusRow>()
                }
                if (active.isNotEmpty()) {
                    _deleteState.value = DeleteAccountState.Error(
                        "Tienes pedidos en curso. Podrás eliminar tu cuenta cuando terminen."
                    )
                    return@launch
                }

                val response = withTimeout(30_000L) {
                    supabase.functions.invoke(
                        function = "delete-account",
                        body = buildJsonObject {}
                    )
                }
                if (!response.status.isSuccess()) {
                    _deleteState.value = DeleteAccountState.Error(
                        serverMessage(response.bodyAsText()) ?: "No se pudo eliminar la cuenta. Intenta de nuevo."
                    )
                    return@launch
                }

                try { auth.clearSession() } catch (_: Exception) {}
                _email.value    = ""
                _password.value = ""
                _fullName.value = ""
                _state.value    = AuthState.LoggedOut
                _deleteState.value = DeleteAccountState.Done
            } catch (e: TimeoutCancellationException) {
                _deleteState.value = DeleteAccountState.Error("Sin conexión. Verifica tu internet e intenta de nuevo.")
            } catch (e: Exception) {
                _deleteState.value = DeleteAccountState.Error(friendlyDeleteError(e))
            }
        }
    }

    private fun serverMessage(body: String): String? = try {
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
    } catch (_: Exception) { null }

    private fun friendlyDeleteError(e: Exception): String {
        val msg = e.message ?: ""
        serverMessage(msg.substringAfter("\n", msg).trim())?.let { return it }
        val lower = msg.lowercase()
        return when {
            lower.contains("pedidos_en_curso") || lower.contains("409") ->
                "Tienes pedidos en curso. Podrás eliminar tu cuenta cuando terminen."
            lower.contains("unable to resolve") || lower.contains("network") ||
            lower.contains("timeout") || lower.contains("connect") ->
                "Sin conexión. Verifica tu internet e intenta de nuevo."
            else -> "No se pudo eliminar la cuenta. Intenta de nuevo."
        }
    }

    fun clearError() { _state.value = AuthState.LoggedOut }

    fun setValidationError(message: String) {
        _state.value = AuthState.Error(message)
    }

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
