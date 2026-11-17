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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
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
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val state    by authViewModel.state.collectAsState()
    val email    by authViewModel.email.collectAsState()
    val password by authViewModel.password.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }

    // Cuando el login es exitoso, navegar a Home
    LaunchedEffect(state) {
        if (state is AuthState.Customer) onLoginSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = JagooSpacing.xl, vertical = JagooSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── Logo ─────────────────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(JagooYellow)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = "Jagoo",
                    tint = JagooGreen,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(JagooSpacing.md))

            Text(
                text = "Jagoo",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = JagooGreen
            )

            Text(
                text = "Delivery a tu puerta",
                fontFamily = PoppinsFontFamily,
                fontSize = 14.sp,
                color = JagooTextMuted
            )

            Spacer(modifier = Modifier.height(JagooSpacing.xl))

            // ── Título sección ────────────────────────────────────────────
            Text(
                text = "Iniciar sesión",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = JagooTextPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(JagooSpacing.md))

            // ── Campo Email ───────────────────────────────────────────────
            OutlinedTextField(
                value = email,
                onValueChange = { authViewModel.onEmailChange(it) },
                label = { Text("Email", fontFamily = PoppinsFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = JagooGreen)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                colors = textFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(JagooSpacing.sm))

            // ── Campo Contraseña ──────────────────────────────────────────
            OutlinedTextField(
                value = password,
                onValueChange = { authViewModel.onPasswordChange(it) },
                label = { Text("Contraseña", fontFamily = PoppinsFontFamily) },
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
                colors = textFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(JagooSpacing.md))

            // ── Mensaje de error ──────────────────────────────────────────
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

            // ── Mensaje rol incorrecto ────────────────────────────────────
            if (state is AuthState.WrongRole) {
                val role = (state as AuthState.WrongRole).role
                Text(
                    text = "Tu cuenta tiene el rol \"$role\".\nEste rol solo puede acceder desde la página web.",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = Color(0xFFE65100),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(JagooSpacing.sm))
            }

            // ── Botón Ingresar ────────────────────────────────────────────
            Button(
                onClick = {
                    if (state is AuthState.Error) authViewModel.clearError()
                    authViewModel.login()
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
                        text = "Ingresar",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = JagooGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(JagooSpacing.md))

            // ── Ir a Registro ─────────────────────────────────────────────
            TextButton(onClick = { authViewModel.clearError(); onNavigateToRegister() }) {
                Text(
                    text = "¿No tienes cuenta? Regístrate",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 14.sp,
                    color = JagooGreen
                )
            }
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = JagooGreen,
    unfocusedBorderColor = JagooGreen.copy(alpha = 0.4f),
    focusedLabelColor    = JagooGreen,
    cursorColor          = JagooGreen
)
