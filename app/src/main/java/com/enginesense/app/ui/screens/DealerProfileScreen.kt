package com.enginesense.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enginesense.app.storage.DealerProfile
import com.enginesense.app.storage.DealerProfileStore
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import java.io.ByteArrayOutputStream

/** Longest edge a stored logo is downscaled to — keeps the PDF small/fast without needing a visibly higher-res badge than it's ever drawn at. */
private const val MAX_LOGO_DIMENSION_PX = 256

/**
 * Single-dealer local profile — see [DealerProfileStore]'s doc for why this stays on-device
 * rather than a backend. Saving here is what makes [VehicleReportPdfGenerator] switch from the
 * plain GetAutoSure report every user gets to the dealer-branded version.
 */
@Composable
fun DealerProfileScreen(onSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { DealerProfileStore(context) }
    val existing = remember { store.load() }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var tagline by remember { mutableStateOf(existing?.tagline ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var logoBitmap by remember {
        mutableStateOf(if (store.hasLogo()) BitmapFactory.decodeFile(store.logoFile.absolutePath) else null)
    }
    var saved by remember { mutableStateOf(false) }

    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        if (decoded != null) {
            val scaled = downscale(decoded, MAX_LOGO_DIMENSION_PX)
            logoBitmap = scaled
            val bytes = ByteArrayOutputStream().apply { scaled.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
            store.saveLogo(bytes)
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Dealer Profile", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text(
            "Shown on every PDF report you generate — your logo, name and contact details replace the default GetAutoSure-only header.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            val bmp = logoBitmap
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(GetAutoSureColors.Surface)
                    .clickable { pickLogo.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), contentDescription = "Dealer logo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text("Logo", style = GetAutoSureType.Caption)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                OutlinedButton(onClick = { pickLogo.launch("image/*") }) {
                    Text(if (logoBitmap != null) "Change logo" else "Choose logo")
                }
                if (logoBitmap != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Remove",
                        style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention),
                        modifier = Modifier.clickable {
                            logoBitmap = null
                            store.clearLogo()
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Dealer / business name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = tagline, onValueChange = { tagline = it }, label = { Text("Tagline (optional)") }, placeholder = { Text("e.g. Certified Pre-Owned Dealer") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, placeholder = { Text("e.g. Andheri West, Mumbai") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                store.save(DealerProfile(name = name.trim(), tagline = tagline.trim(), phone = phone.trim(), location = location.trim()))
                saved = true
                onSaved()
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save profile") }

        if (saved) {
            Spacer(Modifier.height(10.dp))
            Text("Saved — your next PDF report will use this branding.", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Good))
        }
    }
}

/** Scales so the longer edge is at most [maxDimension]px, preserving aspect ratio; returns the source unchanged if it's already smaller. */
private fun downscale(source: Bitmap, maxDimension: Int): Bitmap {
    val longerEdge = maxOf(source.width, source.height)
    if (longerEdge <= maxDimension) return source
    val scale = maxDimension.toFloat() / longerEdge
    val newW = (source.width * scale).toInt().coerceAtLeast(1)
    val newH = (source.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, newW, newH, true)
}
