package com.jagoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.jagoo.app.data.Dish
import com.jagoo.app.ui.theme.JagooBackground
import com.jagoo.app.ui.theme.JagooCardWhite
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooRed
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.JagooTextMuted
import com.jagoo.app.ui.theme.JagooTextPrimary
import com.jagoo.app.ui.theme.JagooTextSecondary
import com.jagoo.app.ui.theme.JagooYellow
import com.jagoo.app.ui.theme.PillShape
import com.jagoo.app.ui.theme.PoppinsFontFamily
import com.jagoo.app.viewmodel.ProductViewModel

@Composable
fun AdminMenuScreen(
    restaurantId: String,
    productViewModel: ProductViewModel,
    onBackClick: () -> Unit
) {
    val restaurant = productViewModel.getRestaurantById(restaurantId)
    val dishes = productViewModel.getDishesForRestaurant(restaurantId)

    var showEditDialog by remember { mutableStateOf(false) }
    var dishToEdit by remember { mutableStateOf<Dish?>(null) }
    var dishToDelete by remember { mutableStateOf<Dish?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JagooBackground)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JagooYellow)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    tint = JagooGreen,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                imageVector = Icons.Default.RestaurantMenu,
                contentDescription = "Menú",
                tint = JagooGreen,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Menú: ${restaurant?.name ?: "Restaurante"}",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = JagooGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Botón Agregar Nuevo Plato
            Button(
                onClick = {
                    dishToEdit = null
                    showEditDialog = true
                },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = JagooYellow),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Agregar",
                    tint = JagooGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Agregar Nuevo Plato",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = JagooGreen
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(dishes) { dish ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = JagooCardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = dish.imageUrl,
                                contentDescription = dish.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dish.name,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = JagooTextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${dish.menuSection} · $${String.format("%.2f", dish.price).replace('.', ',')}",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = JagooGreen
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (dish.isAvailable) "Disponible" else "Agotado",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        color = if (dish.isAvailable) Color(0xFF2E7D32) else JagooRed
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = dish.isAvailable,
                                        onCheckedChange = { isAvail ->
                                            productViewModel.toggleDishAvailability(dish.id, restaurantId, isAvail)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = JagooGreen
                                        ),
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            }

                            // Botones Editar y Eliminar
                            Row {
                                IconButton(
                                    onClick = {
                                        dishToEdit = dish
                                        showEditDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Editar",
                                        tint = JagooGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { dishToDelete = dish }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Eliminar",
                                        tint = JagooRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Crear/Editar Plato
    if (showEditDialog) {
        EditDishModal(
            dish = dishToEdit,
            restaurantId = restaurantId,
            restaurantName = restaurant?.name ?: "",
            onDismiss = { showEditDialog = false },
            onSave = { updatedDish ->
                productViewModel.saveDish(updatedDish)
                showEditDialog = false
            }
        )
    }

    // Diálogo Eliminar Plato
    dishToDelete?.let { dish ->
        AlertDialog(
            onDismissRequest = { dishToDelete = null },
            title = {
                Text(
                    text = "¿Eliminar plato?",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "¿Estás seguro de eliminar '${dish.name}' del menú de tu restaurante?",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        productViewModel.deleteDish(dish.id, restaurantId)
                        dishToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JagooRed),
                    shape = PillShape
                ) {
                    Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { dishToDelete = null }) {
                    Text("Cancelar", color = JagooGreen)
                }
            }
        )
    }
}

@Composable
private fun EditDishModal(
    dish: Dish?,
    restaurantId: String,
    restaurantName: String,
    onDismiss: () -> Unit,
    onSave: (Dish) -> Unit
) {
    var name by remember { mutableStateOf(dish?.name ?: "") }
    var description by remember { mutableStateOf(dish?.description ?: "") }
    var priceText by remember { mutableStateOf(dish?.price?.toString() ?: "") }
    var imageUrl by remember { mutableStateOf(dish?.imageUrl ?: "") }
    var section by remember { mutableStateOf(dish?.menuSection ?: "Especiales") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = JagooCardWhite,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (dish == null) "Nuevo Plato" else "Editar Plato",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = JagooGreen
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del plato") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Precio ($)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("URL de la imagen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = section,
                    onValueChange = { section = it },
                    label = { Text("Sección del menú (Ej. Hamburguesas)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val parsedPrice = priceText.toDoubleOrNull() ?: 0.0
                            val newDish = Dish(
                                id = dish?.id ?: "d_${System.currentTimeMillis()}",
                                restaurantId = restaurantId,
                                restaurantName = restaurantName,
                                name = name.ifBlank { "Nuevo Plato" },
                                description = description,
                                price = parsedPrice,
                                imageUrl = imageUrl.ifBlank { "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500" },
                                menuSection = section.ifBlank { "Especiales" },
                                isAvailable = dish?.isAvailable ?: true,
                                isPopular = dish?.isPopular ?: false,
                                isOffer = dish?.isOffer ?: false,
                                optionGroups = dish?.optionGroups ?: emptyList()
                            )
                            onSave(newDish)
                        },
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(containerColor = JagooYellow)
                    ) {
                        Text("Guardar", color = JagooGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
