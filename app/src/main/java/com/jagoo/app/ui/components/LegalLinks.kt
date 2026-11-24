package com.jagoo.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagoo.app.BuildConfig
import com.jagoo.app.ui.theme.JagooGreen
import com.jagoo.app.ui.theme.PoppinsFontFamily

/**
 * Enlaces a Términos y Política de privacidad (páginas públicas del panel web).
 * La base se define con `web.base.url` en local.properties. Si no está definida
 * no se muestra nada, para no enlazar a una URL inventada.
 */
@Composable
fun LegalLinks(modifier: Modifier = Modifier) {
    val base = BuildConfig.WEB_BASE_URL
    if (base.isBlank()) return
    val context = LocalContext.current

    fun open(path: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$base$path")))
        } catch (_: Exception) { /* sin navegador disponible */ }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegalLink("Términos y condiciones") { open("/terms") }
        Text(text = "·", fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = JagooGreen)
        LegalLink("Política de privacidad") { open("/privacy") }
    }
}

@Composable
private fun LegalLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
        Text(
            text = label,
            fontFamily = PoppinsFontFamily,
            fontSize = 12.sp,
            color = JagooGreen,
            textDecoration = TextDecoration.Underline
        )
    }
}
