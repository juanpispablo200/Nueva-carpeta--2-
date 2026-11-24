package com.jagoo.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooRed
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.PillShape
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.CartViewModel
import com.jagoo.app.viewmodel.SettingsViewModel

/**
 * PaymentInstructionsScreen — Muestra datos bancarios y permite subir comprobante.
 *
 * Flujo:
 * 1. Usuario ve el total y los datos bancarios (cargados desde settings en BD)
 * 2. Sale de la app, abre su banco, transfiere
 * 3. Regresa, sube foto del comprobante → va a Storage
 * 4. El pedido pasa a estado "verifying"
 * 5. Admin aprueba desde el panel web → pedido avanza a "accepted"
 */
@Composable
fun PaymentInstructionsScreen(
    cartViewModel:    CartViewModel,
    settingsViewModel: SettingsViewModel,
    orderId:          String,
    totalAmount:      Double,
    onPaymentUploaded: () -> Unit
) {
    val context   = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val bankInfo    by settingsViewModel.bankInfo.collectAsState()
    val bankLoading by settingsViewModel.loading.collectAsState()

    var referenceNumber  by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var uploading        by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }

    // Launcher para seleccionar imagen de galería
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        uploadError = null
    }

    // Datos bancarios desde BD (via SettingsViewModel)
    val bankName      = bankInfo.bankName
    val accountType   = bankInfo.accountType
    val accountNumber = bankInfo.accountNumber
    val accountHolder = bankInfo.holder
    val ruc           = bankInfo.ruc

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JagooYellow)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = "Pago",
                tint = JagooGreen,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Completa tu Pago",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = JagooGreen
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Total a pagar
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JagooGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Total a transferir",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$${String.format("%.2f", totalAmount)}",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pedido ${com.jagoo.app.data.orderNumber(orderId)}",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // Plazo para subir el comprobante (si no llega, el servidor cancela el pedido)
            val deadlineMillis = cartViewModel.activeOrder.collectAsState().value
                ?.takeIf { it.id == orderId }?.transferDeadlineMillis
            val minutesLeft = rememberMinutesLeft(deadlineMillis)
            val expired = deadlineMillis != null && minutesLeft <= 0L
            if (expired) {
                com.jagoo.app.ui.components.ErrorCard(
                    message = "El plazo para subir el comprobante venció y el pedido se cancela. Si ya transferiste, escríbenos; si no, haz un pedido nuevo."
                )
            } else {
                com.jagoo.app.ui.components.NoticeCard(
                    label = "Plazo",
                    message = if (deadlineMillis != null)
                        "Te quedan $minutesLeft min para subir el comprobante. Si no lo subes a tiempo, el pedido se cancela."
                    else
                        "Sube el comprobante dentro de 30 minutos desde que hiciste el pedido. Si no, se cancela."
                )
            }

            // Instrucciones
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📱 Cómo pagar",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = JagooTextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    listOf(
                        "1. Abre tu app bancaria o banca web",
                        "2. Realiza una transferencia al siguiente número de cuenta",
                        "3. Usa el monto exacto: $$${String.format("%.2f", totalAmount)}",
                        "4. Toma una captura o guarda el comprobante",
                        "5. Regresa aquí y súbelo abajo"
                    ).forEach { step ->
                        Text(
                            text = step,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            color = JagooTextMuted,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }

            // Datos bancarios
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = "Banco",
                            tint = JagooGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Datos de la cuenta",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = JagooTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    DataRow("Banco", bankName, clipboard)
                    DataRow("Tipo", accountType, clipboard)
                    DataRow("Número de cuenta", accountNumber, clipboard)
                    DataRow("Titular", accountHolder, clipboard)
                    DataRow("RUC", ruc, clipboard)
                }
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

            // Subir comprobante
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📸 Sube tu comprobante",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = JagooTextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = referenceNumber,
                        onValueChange = { referenceNumber = it },
                        label = { Text("Número de referencia (opcional)") },
                        placeholder = { Text("Ej. 123456789") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (selectedImageUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(JagooGreen.copy(alpha = 0.1f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Seleccionado",
                                tint = JagooGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Comprobante listo para subir",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                color = JagooGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedImageUri == null) JagooYellow else Color.Gray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "Seleccionar",
                            tint = JagooGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedImageUri == null) "Seleccionar Comprobante" else "Cambiar Comprobante",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = JagooGreen
                        )
                    }

                    if (uploadError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uploadError!!,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            color = JagooRed
                        )
                    }
                }
            }

            // Botón confirmar
            Button(
                onClick = {
                    if (selectedImageUri == null) {
                        uploadError = "Selecciona un comprobante primero"
                        return@Button
                    }
                    uploading = true
                    uploadError = null

                    cartViewModel.uploadPaymentProof(
                        orderId         = orderId,
                        imageUri        = selectedImageUri!!,
                        referenceNumber = referenceNumber.ifBlank { null },
                        amount          = totalAmount,
                        context         = context,
                        onSuccess = {
                            uploading = false
                            onPaymentUploaded()
                        },
                        onError = { error ->
                            uploading = false
                            uploadError = error
                        }
                    )
                },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = JagooGreen),
                enabled = selectedImageUri != null && !uploading && !expired,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (uploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enviando...", color = Color.White)
                } else {
                    Text(
                        text = "Confirmar Pago",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/** Minutos que faltan hasta el plazo; se refresca cada 20 s. */
@Composable
private fun rememberMinutesLeft(deadlineMillis: Long?): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(deadlineMillis) {
        while (deadlineMillis != null) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(20_000L)
        }
    }
    return com.jagoo.app.data.TransferRules.minutesLeft(deadlineMillis, now)
}

@Composable
private fun DataRow(label: String, value: String, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontFamily = PoppinsFontFamily,
                fontSize = 11.sp,
                color = JagooTextMuted
            )
            Text(
                text = value,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = JagooTextPrimary
            )
        }
        IconButton(
            onClick = {
                clipboard.setText(AnnotatedString(value))
            },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copiar",
                tint = JagooGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
