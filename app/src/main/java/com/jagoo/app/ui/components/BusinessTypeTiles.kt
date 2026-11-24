package com.jagoo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.graphics.Color as AndroidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.data.BusinessType
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.JagooSpacing
import com.jagoo.app.ui.theme.PoppinsFontFamily

/**
 * Cuatro tipos de negocio con íconos grandes y texto oscuro sobre fondo claro
 * (contraste alto). Tocar uno filtra la lista; tocarlo otra vez quita el filtro.
 * Cada tile mide al menos 96dp de alto (más de 48dp de área táctil).
 */
@Composable
fun BusinessTypeTiles(
    selectedType: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = JagooSpacing.md),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BusinessType.entries.chunked(2).forEach { rowTypes ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowTypes.forEach { type ->
                    BusinessTypeTile(
                        type = type,
                        selected = selectedType == type.key,
                        onClick = { onSelect(type.key) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BusinessTypeTile(
    type: BusinessType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = Color(AndroidColor.parseColor(type.colorHex))
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        border = if (selected) BorderStroke(3.dp, JagooGreen) else BorderStroke(1.dp, Color(0x33000000)),
        modifier = modifier
            .heightIn(min = 96.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Button)
            .semantics {
                contentDescription = "${type.label}, filtrar negocios"
                stateDescription = if (selected) "Filtro activo" else "Filtro inactivo"
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .padding(10.dp)
        ) {
            Text(text = type.emoji, fontSize = 38.sp)
            Text(
                text = type.label,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF1B1B1B),
                textAlign = TextAlign.Center
            )
        }
    }
}
