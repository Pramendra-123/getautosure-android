package com.enginesense.app.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enginesense.app.report.sharePdfIntent
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shows a generated report PDF inside the app, page by page, using Android's own [PdfRenderer] —
 * no extra library, and no hand-off to whichever PDF app the phone happens to have.
 */
@Composable
fun PdfViewerScreen(path: String) {
    val context = LocalContext.current
    val file = remember(path) { File(path) }
    var pages by remember(path) { mutableStateOf<List<Bitmap>?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        val rendered = withContext(Dispatchers.IO) { runCatching { renderPages(file) }.getOrNull() }
        if (rendered == null) failed = true else pages = rendered
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Report PDF", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(12.dp))
        val loaded = pages
        when {
            failed -> Text("Couldn't open the report. Go back and prepare it again.", style = GetAutoSureType.Body)
            loaded == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GetAutoSureColors.Accent)
            }
            else -> LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(loaded) { page ->
                    Image(
                        bitmap = page.asImageBitmap(),
                        contentDescription = "Report page",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().background(Color.White)
                    )
                }
            }
        }
        if (!failed) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { context.startActivity(com.enginesense.app.report.viewPdfIntent(context, file)) },
                    modifier = Modifier.weight(1f)
                ) { Text("Open in another app") }
                Button(onClick = { context.startActivity(sharePdfIntent(context, file)) }, modifier = Modifier.weight(1f)) { Text("Share") }
            }
        }
    }
}

/** Each page drawn at twice the PDF's size, so small print stays sharp when it's scaled to the screen. */
private fun renderPages(file: File): List<Bitmap> =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer ->
            (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }
