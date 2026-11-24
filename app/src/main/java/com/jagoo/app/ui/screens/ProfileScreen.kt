package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.ui.components.JagooHeader
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooRed
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooTextSecondary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.PillShape
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.AuthViewModel
import com.jagoo.app.viewmodel.DeleteAccountState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.jagoo.app.viewmodel.ProductViewModel

@Composable
fun ProfileScreen(
    productViewModel: ProductViewModel,
    authViewModel: AuthViewModel,
    onNavigateToAddresses: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onLogout: () -> Unit = {},
    onAccountDeleted: () -> Unit = {}
) {
    val deleteState by authViewModel.deleteState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }

    LaunchedEffect(deleteState) {
        if (deleteState is DeleteAccountState.Done) {
            showDeleteDialog = false
            authViewModel.resetDeleteState()
            onAccountDeleted()
        }
    }

    if (showDeleteDialog) {
        val loading = deleteState is DeleteAccountState.Loading
        AlertDialog(
            onDismissRequest = {
                if (!loading) {
                    showDeleteDialog = false
                    deleteConfirmText = ""
                    authViewModel.resetDeleteState()
                }
            },
            title = {
                Text("Eliminar mi cuenta", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Esta acción es permanente y no se puede deshacer.",
                        fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                    Text(
                        "Se borrará: tu perfil, direcciones, favoritos, comprobantes de pago subidos y tu historial de notificaciones.",
                        fontFamily = PoppinsFontFamily, fontSize = 13.sp
                    )
                    Text(
                        "Se conservará sin tus datos personales: los pedidos pasados (por obligación contable), sin nombre, dirección ni notas.",
                        fontFamily = PoppinsFontFamily, fontSize = 13.sp
                    )
                    Text(
                        "Escribe ELIMINAR para confirmar:",
                        fontFamily = PoppinsFontFamily, fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        singleLine = true,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    (deleteState as? DeleteAccountState.Error)?.let {
                        Text(it.message, color = JagooRed, fontFamily = PoppinsFontFamily, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { authViewModel.deleteAccount() },
                    enabled = !loading && deleteConfirmText.trim().equals("ELIMINAR", ignoreCase = true),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = JagooRed)
                    } else {
                        Text("Eliminar definitivamente", color = JagooRed, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deleteConfirmText = ""
                        authViewModel.resetDeleteState()
                    },
                    enabled = !loading,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) { Text("Cancelar", fontFamily = PoppinsFontFamily) }
            }
        )
    }

    val favIds      by productViewModel.favoriteRestaurantIds.collectAsState()
    val primaryAddr  = productViewModel.getPrimaryAddress()

    var displayName  by remember { mutableStateOf("") }
    var displayPhone by remember { mutableStateOf("") }
    var editName     by remember { mutableStateOf("") }
    var editPhone    by remember { mutableStateOf("") }
    var editing      by remember { mutableStateOf(false) }
    var saving       by remember { mutableStateOf(false) }
    var saveMsg      by remember { mutableStateOf<String?>(null) }

    // Cargar perfil real desde Supabase al entrar
    LaunchedEffect(Unit) {
        authViewModel.loadProfile { name, phone ->
            displayName  = name.ifBlank { "Mi perfil" }
            displayPhone = phone.ifBlank { "Sin teléfono" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        JagooHeader()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Tarjeta de perfil editable ────────────────────────────────
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(JagooYellow)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Perfil",
                                tint = JagooGreen,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        if (editing) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("Nombre") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editPhone,
                                    onValueChange = { editPhone = it },
                                    label = { Text("Teléfono") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = JagooTextPrimary
                                )
                                Text(
                                    text = displayPhone,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 13.sp,
                                    color = JagooTextSecondary
                                )
                            }
                        }

                        if (editing) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = JagooGreen,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(onClick = {
                                    saving = true
                                    authViewModel.updateProfile(editName, editPhone) { ok ->
                                        saving = false
                                        if (ok) {
                                            displayName  = editName
                                            displayPhone = editPhone
                                            saveMsg      = "Guardado"
                                            editing      = false
                                        } else {
                                            saveMsg = "Error al guardar"
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Check, "Guardar", tint = JagooGreen)
                                }
                            }
                        } else {
                            IconButton(onClick = {
                                editName  = displayName
                                editPhone = displayPhone
                                editing   = true
                                saveMsg   = null
                            }) {
                                Icon(Icons.Default.Edit, "Editar", tint = JagooGreen, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    if (saveMsg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = saveMsg!!,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            color = if (saveMsg == "Guardado") JagooGreen else JagooRed
                        )
                    }
                }
            }

            // ── Opciones del menú ─────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ProfileMenuItem(
                        icon     = Icons.Default.LocationOn,
                        title    = "Mis Direcciones",
                        subtitle = primaryAddr,
                        onClick  = onNavigateToAddresses
                    )
                    ProfileMenuItem(
                        icon     = Icons.Default.History,
                        title    = "Historial de Pedidos",
                        subtitle = "Ver pedidos anteriores",
                        onClick  = onNavigateToHistory
                    )
                    ProfileMenuItem(
                        icon     = Icons.Default.Favorite,
                        title    = "Restaurantes Favoritos",
                        subtitle = "${favIds.size} guardados",
                        onClick  = {}
                    )
                    ProfileMenuItem(
                        icon     = Icons.Default.CreditCard,
                        title    = "Métodos de Pago",
                        subtitle = "Efectivo / Transferencia",
                        onClick  = {}
                    )
                    ProfileMenuItem(
                        icon     = Icons.Default.Notifications,
                        title    = "Notificaciones",
                        subtitle = "Promociones y estado del pedido",
                        onClick  = {}
                    )
                    ProfileMenuItem(
                        icon     = Icons.Default.HelpOutline,
                        title    = "Centro de Ayuda",
                        subtitle = "Preguntas frecuentes y soporte",
                        onClick  = {}
                    )
                }
            }

            // ── Cerrar sesión ─────────────────────────────────────────────
            Button(
                onClick  = onLogout,
                shape    = PillShape,
                colors   = ButtonDefaults.buttonColors(containerColor = JagooRed),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text       = "Cerrar sesión",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    color      = Color.White
                )
            }

            com.jagoo.app.ui.components.LegalLinks()

            // ── Eliminar cuenta ───────────────────────────────────────────
            TextButton(
                onClick  = { deleteConfirmText = ""; authViewModel.resetDeleteState(); showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text       = "Eliminar mi cuenta",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    color      = JagooRed
                )
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(JagooGreen.copy(alpha = 0.1f))
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = JagooGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title,    fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = JagooTextPrimary)
                Text(text = subtitle, fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = JagooTextSecondary)
            }
        }
        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
}
