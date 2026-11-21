package com.jagoo.driver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.jagoo.driver.ui.theme.*
import com.jagoo.driver.viewmodel.DriverAuthState
import com.jagoo.driver.viewmodel.DriverAuthViewModel

@Composable
fun DriverLoginScreen(
    authViewModel: DriverAuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val state    by authViewModel.state.collectAsState()
    val email    by authViewModel.email.collectAsState()
    val password by authViewModel.password.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is DriverAuthState.Repartidor) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(JagooYellow)
            ) {
                Icon(
                    imageVector = Icons.Default.TwoWheeler,
                    contentDescription = "Jagoo Driver",
                    tint = JagooGreen,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Jagoo Driver",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = JagooGreen
            )

            Text(
                text = "Acceso exclusivo para repartidores",
                fontFamily = PoppinsFontFamily,
                fontSize = 14.sp,
                color = JagooTextMuted
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Iniciar sesión / Registro",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = JagooTextPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { authViewModel.onEmailChange(it) },
                label = { Text("Email de Repartidor", fontFamily = PoppinsFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = JagooGreen)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Mostrar contraseña",
                            tint = JagooTextMuted
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state is DriverAuthState.Error) {
                Text(
                    text = (state as DriverAuthState.Error).message,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = JagooRed,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Botón Ingresar
            Button(
                onClick = {
                    if (state is DriverAuthState.Error) authViewModel.clearError()
                    authViewModel.login()
                },
                enabled = state !is DriverAuthState.Loading,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (state is DriverAuthState.Loading) {
                    CircularProgressIndicator(color = JagooGreen, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "Ingresar como Repartidor",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = JagooGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón Registrar (en caso de que el usuario aún no exista en Supabase Auth)
            TextButton(
                onClick = {
                    if (state is DriverAuthState.Error) authViewModel.clearError()
                    authViewModel.registerDriver()
                },
                enabled = state !is DriverAuthState.Loading
            ) {
                Text(
                    text = "¿No existe tu cuenta? Registrar como Repartidor",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = JagooGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
