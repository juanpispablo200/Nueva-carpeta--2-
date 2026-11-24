package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.PillShape
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.AuthState
import com.jagoo.app.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(
    authViewModel: AuthViewModel,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val state    by authViewModel.state.collectAsState()
    val email    by authViewModel.email.collectAsState()
    val password by authViewModel.password.collectAsState()
    val fullName by authViewModel.fullName.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }

    // Cuando el registro es exitoso, navegar a Home
    LaunchedEffect(state) {
        if (state is AuthState.Customer) onRegisterSuccess()
    }

    // Cuenta creada: hay que confirmar el correo antes de entrar
    if (state is AuthState.ConfirmEmail) {
        ConfirmEmailNotice(
            email = (state as AuthState.ConfirmEmail).email,
            onGoToLogin = { authViewModel.clearError(); onNavigateToLogin() }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = JagooSpacing.xl, vertical = JagooSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            // ── Header con botón atrás ────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { authViewModel.clearError(); onNavigateToLogin() },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = JagooGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(JagooSpacing.lg))

            Text(
                text = "Crear cuenta",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = JagooTextPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Regístrate gratis y empieza a pedir",
                fontFamily = PoppinsFontFamily,
                fontSize = 14.sp,
                color = JagooTextMuted,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(JagooSpacing.lg))

            // ── Nombre completo ───────────────────────────────────────────
            OutlinedTextField(
                value = fullName,
                onValueChange = { authViewModel.onFullNameChange(it) },
                label = { Text("Nombre completo", fontFamily = PoppinsFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = JagooGreen)
                },
                singleLine = true,
                colors = textFieldColorsRegister(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(JagooSpacing.sm))

            // ── Email ─────────────────────────────────────────────────────
            OutlinedTextField(
                value = email,
                onValueChange = { authViewModel.onEmailChange(it) },
                label = { Text("Email", fontFamily = PoppinsFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = JagooGreen)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                colors = textFieldColorsRegister(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(JagooSpacing.sm))

            // ── Contraseña ────────────────────────────────────────────────
            OutlinedTextField(
                value = password,
                onValueChange = { authViewModel.onPasswordChange(it) },
                label = { Text("Contraseña (mín. 6 caracteres)", fontFamily = PoppinsFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = JagooGreen)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                                          else Icons.Default.Visibility,
                            contentDescription = "Mostrar contraseña",
                            tint = JagooTextMuted
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                colors = textFieldColorsRegister(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(JagooSpacing.md))

            // ── Error ─────────────────────────────────────────────────────
            if (state is AuthState.Error) {
                Text(
                    text = (state as AuthState.Error).message,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = Color(0xFFD32F2F),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(JagooSpacing.sm))
            }

            // ── Nota: el rol siempre es customer ──────────────────────────
            Text(
                text = "Al registrarte, tu cuenta será de tipo Cliente.\nSi eres restaurante o administrador, accede desde la página web.",
                fontFamily = PoppinsFontFamily,
                fontSize = 11.sp,
                color = JagooTextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Al crear tu cuenta aceptas los Términos y la Política de privacidad.",
                fontFamily = PoppinsFontFamily,
                fontSize = 11.sp,
                color = JagooTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = JagooSpacing.sm)
            )
            com.jagoo.app.ui.components.LegalLinks()

            Spacer(modifier = Modifier.height(JagooSpacing.sm))

            // ── Botón Crear cuenta ────────────────────────────────────────
            Button(
                onClick = {
                    if (state is AuthState.Error) authViewModel.clearError()
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        authViewModel.setValidationError("Ingresa un email válido")
                        return@Button
                    }
                    authViewModel.register()
                },
                enabled = state !is AuthState.Loading,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JagooYellow,
                    disabledContainerColor = JagooYellow.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (state is AuthState.Loading) {
                    CircularProgressIndicator(
                        color = JagooGreen,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Crear cuenta",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = JagooGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(JagooSpacing.sm))

            TextButton(onClick = { authViewModel.clearError(); onNavigateToLogin() }) {
                Text(
                    text = "¿Ya tienes cuenta? Inicia sesión",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 14.sp,
                    color = JagooGreen
                )
            }
        }
    }
}

@Composable
private fun ConfirmEmailNotice(email: String, onGoToLogin: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(JagooBackground).padding(JagooSpacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = JagooGreen,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(JagooSpacing.lg))
            Text(
                text = "Revisa tu correo",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = JagooTextPrimary
            )
            Spacer(modifier = Modifier.height(JagooSpacing.sm))
            Text(
                text = "Te enviamos un enlace de confirmación a $email. Ábrelo para activar tu cuenta y luego inicia sesión. Si no lo ves, revisa la carpeta de spam.",
                fontFamily = PoppinsFontFamily,
                fontSize = 14.sp,
                color = JagooTextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(JagooSpacing.xl))
            Button(
                onClick = onGoToLogin,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    text = "Ir a iniciar sesión",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = JagooGreen
                )
            }
        }
    }
}

@Composable
private fun textFieldColorsRegister() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = JagooGreen,
    unfocusedBorderColor = JagooGreen.copy(alpha = 0.4f),
    focusedLabelColor    = JagooGreen,
    cursorColor          = JagooGreen
)
