package com.jagoo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.jagoo.app.data.Dish
import com.jagoo.app.data.SelectedOption
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooGreenLight
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooTextSecondary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.PillShape
import com.jagoo.app.ui.theme.PoppinsFontFamily

@Composable
fun DishDetailDialog(
    dish: Dish,
    onDismiss: () -> Unit,
    onAddToCart: (Dish, List<SelectedOption>, String, Int) -> Unit
) {
    var quantity by remember { mutableIntStateOf(1) }
    var specialNotes by remember { mutableStateOf("") }

    val selectedOptionMap = remember { mutableStateMapOf<String, MutableList<SelectedOption>>() }

    val unitPrice = dish.price + selectedOptionMap.values.flatten().sumOf { it.optionPrice }
    val totalPrice = unitPrice * quantity

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = JagooCardWhite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    AsyncImage(
                        model = dish.imageUrl,
                        contentDescription = dish.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(48.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = JagooSpacing.md, vertical = 14.dp)
                ) {
                    Text(
                        text = dish.name,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = JagooTextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = dish.description,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        color = JagooTextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    dish.optionGroups.forEach { group ->
                        Text(
                            text = group.title,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = JagooTextPrimary
                        )
                        Text(
                            text = if (group.isRequired) "Obligatorio" else "Opcional",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.sp,
                            color = if (group.isRequired) JagooGreen else JagooTextMuted
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        group.options.forEach { option ->
                            val currentSelectedList = selectedOptionMap.getOrPut(group.id) { mutableListOf() }
                            val isSelected = currentSelectedList.any { it.optionId == option.id }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = if (group.maxSelection == 1) androidx.compose.ui.semantics.Role.RadioButton else androidx.compose.ui.semantics.Role.Checkbox) {
                                        if (group.maxSelection == 1) {
                                            currentSelectedList.clear()
                                            currentSelectedList.add(
                                                SelectedOption(
                                                    optionGroupId = group.id,
                                                    optionGroupTitle = group.title,
                                                    optionId = option.id,
                                                    optionName = option.name,
                                                    optionPrice = option.price
                                                )
                                            )
                                        } else {
                                            if (isSelected) {
                                                currentSelectedList.removeAll { it.optionId == option.id }
                                            } else {
                                                currentSelectedList.add(
                                                    SelectedOption(
                                                        optionGroupId = group.id,
                                                        optionGroupTitle = group.title,
                                                        optionId = option.id,
                                                        optionName = option.name,
                                                        optionPrice = option.price
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (group.maxSelection == 1) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = null,
                                            colors = RadioButtonDefaults.colors(selectedColor = JagooGreen)
                                        )
                                    } else {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(checkedColor = JagooGreen)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = option.name,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 13.sp,
                                        color = JagooTextPrimary
                                    )
                                }

                                if (option.price > 0) {
                                    Text(
                                        text = "+$${String.format("%.2f", option.price).replace('.', ',')}",
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = JagooGreen
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Text(
                        text = "Instrucciones especiales",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = JagooTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = specialNotes,
                        onValueChange = { specialNotes = it },
                        placeholder = {
                            Text(
                                text = "Ej. Sin cebolla, extra salsa, mayonesa aparte...",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                color = JagooTextMuted
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Surface(
                    shadowElevation = 8.dp,
                    color = JagooCardWhite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = JagooSpacing.md, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        QuantitySelector(
                            quantity = quantity,
                            onDecrease = { if (quantity > 1) quantity-- },
                            onIncrease = { quantity++ }
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                            onClick = {
                                onAddToCart(
                                    dish,
                                    selectedOptionMap.values.flatten(),
                                    specialNotes,
                                    quantity
                                )
                                onDismiss()
                            },
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = "Agregar  $${String.format("%.2f", totalPrice).replace('.', ',')}",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = JagooGreen
                            )
                        }
                    }
                }
            }
        }
    }
}
