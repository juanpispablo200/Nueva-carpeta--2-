package com.jagoo.app.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.jagoo.app.viewmodel.KushkiViewModel

/**
 * KushkiPaymentScreen — formulario de pago con tarjeta via Kushki.
 *
 * Flujo:
 * 1. Usuario ingresa datos de la tarjeta
 * 2. KushkiViewModel tokeniza la tarjeta con la clave pública (nunca sale del dispositivo)
 * 3. El token se envía a la Edge Function process-kushki-payment
 * 4. La Edge Function cobra en Kushki y registra en BD
 * 5. Si aprobado → navegar a Orders
 */
@Composable
fun KushkiPaymentScreen(
    cartViewModel:   CartViewModel,
    kushkiViewModel: KushkiViewModel,
    orderId:         String,
    totalAmount:     Double,
    onPaymentSuccess: () -> Unit,
    onBackClick:     () -> Unit
) {
    val loading by kushkiViewModel.loading.collectAsState()
    val error   by kushkiViewModel.error.collectAsState()
    val success by kushkiViewModel.success.collectAsState()

    var cardNumber  by remember { mutableStateOf("") }
    var cardName    by remember { mutableStateOf("") }
    var cardExpiry  by remember { mutableStateOf("") }  // MM/YY
    var cardCvv     by remember { mutableStateOf("") }

    // Navegar al éxito
    if (success) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            onPaymentSuccess()
        }
    }

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
            Icon(Icons.Default.CreditCard, "Pago", tint = JagooGreen, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Pago con tarjeta",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = JagooGreen,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.Lock, "Seguro", tint = JagooGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Pago seguro", fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = JagooGreen)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Total
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JagooGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Total a cobrar", fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                    Text(
                        "$${String.format("%.2f", totalAmount)}",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        color = JagooYellow
                    )
                    Text("Pedido #$orderId", fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }

            // Formulario de tarjeta
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Datos de la tarjeta",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = JagooTextPrimary
                    )

                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { if (it.length <= 16) cardNumber = it.filter { c -> c.isDigit() } },
                        label = { Text("Número de tarjeta") },
                        placeholder = { Text("1234 5678 9012 3456") },
                        leadingIcon = { Icon(Icons.Default.CreditCard, null, tint = JagooGreen) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = cardName,
                        onValueChange = { cardName = it.uppercase() },
                        label = { Text("Nombre en la tarjeta") },
                        placeholder = { Text("JUAN PABLO GARCIA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = cardExpiry,
                            onValueChange = {
                                val digits = it.filter { c -> c.isDigit() }.take(4)
                                cardExpiry = if (digits.length > 2) "${digits.take(2)}/${digits.drop(2)}" else digits
                            },
                            label = { Text("Vencimiento") },
                            placeholder = { Text("MM/YY") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cardCvv,
                            onValueChange = { if (it.length <= 4) cardCvv = it.filter { c -> c.isDigit() } },
                            label = { Text("CVV") },
                            placeholder = { Text("123") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        "🔒 Tus datos son cifrados con TLS. Kushki no almacena datos de tarjeta.",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.sp,
                        color = JagooTextMuted,
                        lineHeight = 15.sp
                    )
                }
            }

            if (error != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = JagooRed.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        error!!,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        color = JagooRed,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Botón pagar
            Button(
                onClick = {
                    kushkiViewModel.processPayment(
                        orderId     = orderId,
                        amount      = totalAmount,
                        cardNumber  = cardNumber,
                        cardName    = cardName,
                        cardExpiry  = cardExpiry,
                        cardCvv     = cardCvv
                    )
                },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = JagooGreen),
                enabled = !loading && cardNumber.length == 16 && cardName.isNotBlank() &&
                          cardExpiry.length == 5 && cardCvv.length >= 3,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Procesando...", color = Color.White, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        "Pagar $${String.format("%.2f", totalAmount)}",
                        color = Color.White,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
