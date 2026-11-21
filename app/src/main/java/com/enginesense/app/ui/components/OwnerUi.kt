package com.enginesense.app.ui.components

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.owner.Level
import com.enginesense.app.report.VehicleReportInfo
import com.enginesense.app.report.VehicleReportPdfGenerator
import com.enginesense.app.report.sharePdfIntent
import com.enginesense.app.ui.theme.GetAutoSureColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Owner-dashboard text sizes: bigger than the old cards, like a settings list. */
object OwnerText {
    val RowTitle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = GetAutoSureColors.TextPrimary)
    val RowSub = TextStyle(fontSize = 14.sp, color = GetAutoSureColors.TextSecondary, lineHeight = 19.sp)
    val Section = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GetAutoSureColors.TextPrimary)
    val Big = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = GetAutoSureColors.OnInk)
    val InkLabel = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GetAutoSureColors.AccentBright)
    val InkSub = TextStyle(fontSize = 14.sp, color = GetAutoSureColors.OnInkMuted, lineHeight = 19.sp)
}

fun levelColor(level: Level): Color = when (level) {
    Level.GOOD -> GetAutoSureColors.Good
    Level.WATCH -> GetAutoSureColors.Watch
    Level.ATTENTION -> GetAutoSureColors.Attention
    Level.UNKNOWN -> GetAutoSureColors.TextSecondary
}

/** A white rounded group of rows with hairlines between them. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface), content = content)
}

@Composable
fun ListDivider() = HorizontalDivider(color = GetAutoSureColors.SurfaceRaised, modifier = Modifier.padding(start = 56.dp))

/** One list row: orange icon, bold title, grey line, and a chevron (or [trailing]). */
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    subtitleColor: Color = GetAutoSureColors.TextSecondary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = GetAutoSureColors.Accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = OwnerText.RowTitle)
            if (subtitle != null) Text(subtitle, style = OwnerText.RowSub.copy(color = subtitleColor))
        }
        if (trailing != null) trailing() else if (onClick != null) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = GetAutoSureColors.Unavailable, modifier = Modifier.size(22.dp))
        }
    }
}

/** The black card used for the one number that matters on a page. */
@Composable
fun InkCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Ink).padding(16.dp), content = content)
}

@Composable
fun FilterChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) GetAutoSureColors.OnInk else GetAutoSureColors.TextPrimary),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) GetAutoSureColors.Ink else GetAutoSureColors.Surface)
                    .border(1.dp, if (on) GetAutoSureColors.Ink else GetAutoSureColors.Border, RoundedCornerShape(20.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/** A short grey note under a group, e.g. where the dates came from. */
@Composable
fun GroupNote(text: String) {
    Text(text, style = OwnerText.RowSub.copy(fontSize = 13.sp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = OwnerText.Section, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
    Spacer(Modifier.height(2.dp))
}

/** Opens Google Maps (or the browser) searching near the user, e.g. "car service centre". */
fun openNearby(context: Context, query: String) {
    val uri = Uri.parse("geo:0,0?q=" + Uri.encode(query))
    val maps = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(maps)
    } catch (e: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/" + Uri.encode(query))))
    }
}

fun openWeb(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
    }
}

/** A system date picker; [onPicked] gets the chosen date. */
fun pickDate(context: Context, initial: LocalDate?, onPicked: (LocalDate) -> Unit) {
    val d = initial ?: LocalDate.now()
    DatePickerDialog(context, { _, y, m, day -> onPicked(LocalDate.of(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}

/** "Share with my mechanic": makes the latest scan's PDF and opens the share sheet (WhatsApp, etc.). */
suspend fun shareLatestReport(context: Context, viewModel: GetAutoSureViewModel) {
    val result = viewModel.latestResult.value
    if (result == null) {
        Toast.makeText(context, "Scan your car first — the report comes from the scan.", Toast.LENGTH_LONG).show()
        return
    }
    val info = VehicleReportInfo(
        brand = viewModel.vehicleBrand.value, nickname = viewModel.vehicleNickname.value, model = viewModel.vehicleModel.value,
        year = viewModel.vehicleYear.value, fuelType = viewModel.vehicleFuelType.value, odometerKm = viewModel.vehicleOdometerKm.value,
        plate = viewModel.vehiclePlate.value, rc = viewModel.vehicleRc.value
    )
    try {
        val file = withContext(Dispatchers.IO) { VehicleReportPdfGenerator.generate(context, result, info) }
        context.startActivity(sharePdfIntent(context, file))
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't make the report. Try again.", Toast.LENGTH_LONG).show()
    }
}
