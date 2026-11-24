package com.jagoo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooRed
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.PillShape
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.CartViewModel

/**
 * Diálogos del carrito que pueden aparecer desde cualquier pantalla:
 *  - Un pedido es de UN solo negocio: si agregas de otro, se avisa y se pide
 *    confirmación para vaciar el carrito.
 *  - Productos +18: se pide la fecha de nacimiento (una sola vez; queda en el perfil).
 */
@Composable
fun CartDialogs(cartViewModel: CartViewModel) {
    val conflict by cartViewModel.cartConflict.collectAsState()
    val ageGate by cartViewModel.ageGate.collectAsState()
    val ageSaving by cartViewModel.ageSaving.collectAsState()
    val ageError by cartViewModel.ageError.collectAsState()

    conflict?.let { c ->
        AlertDialog(
            onDismissRequest = { cartViewModel.dismissConflictDialog() },
            title = {
                Text("Un pedido por negocio", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = JagooTextPrimary)
            },
            text = {
                Text(
                    text = "Tu carrito tiene productos de '${c.currentRestaurantName}'. Cada pedido es de un solo negocio. " +
                        "¿Quieres vaciar el carrito y empezar uno nuevo con '${c.newRestaurantName}'?",
                    fontFamily = PoppinsFontFamily, fontSize = 14.sp, color = JagooTextPrimary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            },
            confirmButton = {
                Button(
                    onClick = { cartViewModel.confirmClearCartAndAddPending() },
                    colors = ButtonDefaults.buttonColors(containerColor = JagooRed),
                    shape = PillShape,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) { Text("Vaciar y empezar", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, color = Color.White) }
            },
            dismissButton = {
                TextButton(
                    onClick = { cartViewModel.dismissConflictDialog() },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) { Text("Conservar mi carrito", fontFamily = PoppinsFontFamily, color = JagooGreen) }
            }
        )
    }

    ageGate?.let { gate ->
        if (gate.minor) {
            AlertDialog(
                onDismissRequest = { cartViewModel.dismissAgeGate() },
                title = { Text("Solo para mayores de 18", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "No puedes pedir este producto porque la fecha registrada indica que eres menor de 18 años.",
                        fontFamily = PoppinsFontFamily, fontSize = 14.sp,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { cartViewModel.dismissAgeGate() },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) { Text("Entendido", fontFamily = PoppinsFontFamily, color = JagooGreen) }
                }
            )
        } else {
            var day by rememberSaveable { mutableStateOf("") }
            var month by rememberSaveable { mutableStateOf("") }
            var year by rememberSaveable { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { if (!ageSaving) cartViewModel.dismissAgeGate() },
                title = { Text("Confirma tu edad", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "'${gate.dish.name}' es solo para mayores de 18 años. Escribe tu fecha de nacimiento. " +
                                "Se guarda una sola vez en tu perfil y el repartidor verificará tu cédula al entregar.",
                            fontFamily = PoppinsFontFamily, fontSize = 13.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = day, onValueChange = { day = it.filter(Char::isDigit).take(2) },
                                label = { Text("Día") }, singleLine = true, isError = ageError != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = month, onValueChange = { month = it.filter(Char::isDigit).take(2) },
                                label = { Text("Mes") }, singleLine = true, isError = ageError != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = year, onValueChange = { year = it.filter(Char::isDigit).take(4) },
                                label = { Text("Año") }, singleLine = true, isError = ageError != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1.4f)
                            )
                        }
                        ageError?.let { ErrorCard(message = it) }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { cartViewModel.submitBirthDate(day, month, year) },
                        enabled = !ageSaving && day.isNotBlank() && month.isNotBlank() && year.length == 4,
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(containerColor = JagooGreen),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        if (ageSaving) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp))
                        else Text("Confirmar", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { cartViewModel.dismissAgeGate() },
                        enabled = !ageSaving,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) { Text("Cancelar", fontFamily = PoppinsFontFamily, color = JagooGreen) }
                }
            )
        }
    }
}
