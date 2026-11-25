package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.KmPace
import com.enginesense.app.vehicle.KmRange
import com.enginesense.app.vehicle.OdometerMatch
import com.enginesense.app.vehicle.OdometerReading
import com.enginesense.app.vehicle.PrePurchaseAnalyzer

/**
 * The required km on the Odometer and car number step. The buyer types the exact dashboard reading
 * — the only way to check it against the car's computer — or, when the dashboard is dead, picks a range.
 * Shows the odometer checks as they type: computer match, km a year for the car's age, service book.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OdometerEntry(
    input: String, onInput: (String) -> Unit,
    byRange: Boolean, onByRange: (Boolean) -> Unit,
    range: KmRange?, onRange: (KmRange) -> Unit,
    ecuKm: Double?, ageYears: Double?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (byRange) "Roughly how many km?" else "Km on the dashboard", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.width(8.dp))
        Text(
            "Required",
            style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp),
            modifier = Modifier.clip(RoundedCornerShape(50)).background(GetAutoSureColors.AccentSoft).padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }

    if (!byRange) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { if (input.isEmpty()) runCatching { focus.requestFocus() } }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GetAutoSureColors.Ink).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Type exactly what the dashboard shows", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.OnInkMuted))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = input,
                    onValueChange = { onInput(it.filter(Char::isDigit).trimStart('0').take(7)) },
                    singleLine = true,
                    textStyle = TextStyle(color = GetAutoSureColors.OnInk, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, letterSpacing = 1.sp),
                    cursorBrush = SolidColor(GetAutoSureColors.AccentBright),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    visualTransformation = KmGrouping,
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (input.isEmpty()) Text("0", style = TextStyle(color = Color(0xFF555555), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold))
                            inner()
                        }
                    },
                    modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 40.dp).focusRequester(focus)
                )
                Text(" km", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.OnInkMuted, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(bottom = 6.dp))
            }
        }

        val km = OdometerReading.exactKm(input)
        if (km != null) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(14.dp)) {
                if (ecuKm != null) {
                    when (PrePurchaseAnalyzer.compareOdometer(ecuKm, km)) {
                        OdometerMatch.MATCHES -> KmCheck(GetAutoSureColors.Good, "Matches the car's computer", " (${"%,d".format(ecuKm.toLong())} km). No sign of a rolled-back odometer.")
                        OdometerMatch.SMALL_GAP -> KmCheck(
                            GetAutoSureColors.Watch, "Small gap from the car's computer",
                            ": it says ${"%,d".format(ecuKm.toLong())} km, ${"%,d".format(kotlin.math.abs(ecuKm - km).toLong())} km apart. Often a typo or a replaced cluster. Read the dashboard again, and ask the seller if it's real."
                        )
                        OdometerMatch.MISMATCH -> KmCheck(
                            GetAutoSureColors.Attention, "Doesn't match the car's computer",
                            ": it says ${"%,d".format(ecuKm.toLong())} km, ${"%,d".format(kotlin.math.abs(ecuKm - km).toLong())} km different. Check you typed it right, then ask the seller why."
                        )
                    }
                } else {
                    KmCheck(GetAutoSureColors.Unavailable, "This car doesn't share its km over OBD", ", so we can't check the dashboard against it. The service book is your check.")
                }
                ageYears?.let { age ->
                    val p = OdometerReading.perYear(km, age)
                    val years = if (age < 1) "under a year" else "%.1f years".format(age)
                    when (p.pace) {
                        KmPace.HIGH -> KmCheck(GetAutoSureColors.Watch, "About ${"%,d".format(p.kmPerYear)} km a year", " over $years. That's above the usual 12–15,000, so expect a lower price.")
                        KmPace.SUSPICIOUSLY_LOW -> KmCheck(GetAutoSureColors.Watch, "Only about ${"%,d".format(p.kmPerYear)} km a year", " over $years. Very low km is a common sign of a rolled-back odometer. Check the service book.")
                        KmPace.NORMAL -> KmCheck(GetAutoSureColors.Good, "About ${"%,d".format(p.kmPerYear)} km a year", " over $years. Normal use.")
                    }
                }
                KmCheck(GetAutoSureColors.Unavailable, "Ask for the service book.", " The last service km should be lower than ${"%,d".format(km.toLong())}.")
            }
        }
        TextButton(onClick = { onByRange(true) }, modifier = Modifier.fillMaxWidth()) { Text("Dashboard not working? Pick a range") }
    } else {
        Text("Ask the seller, or check the last service bill.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KmRange.values().forEach { r ->
                val on = r == range
                Text(
                    (if (on) "✓ " else "") + r.label,
                    style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextPrimary, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (on) GetAutoSureColors.AccentSoft else GetAutoSureColors.Surface)
                        .border(1.5.dp, if (on) GetAutoSureColors.AccentBright else GetAutoSureColors.Border, RoundedCornerShape(50))
                        .clickable { onRange(r) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        if (range != null) {
            Spacer(Modifier.height(10.dp))
            val fit = ecuKm?.let { e ->
                if (range.contains(e)) " The car's computer says ${"%,d".format(e.toLong())} km, which fits this range."
                else " The car's computer says ${"%,d".format(e.toLong())} km, which doesn't fit this range. Ask the seller."
            } ?: ""
            Text(
                "Odometer not checked. We'll price the car at about ${"%,d".format(range.midKm)} km. A dead dashboard is itself worth asking about: a replaced cluster is one way the km gets reset.$fit",
                style = GetAutoSureType.Caption.copy(color = Color(0xFF6B3D04)),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFDF3E3)).padding(12.dp)
            )
        }
        TextButton(onClick = { onByRange(false) }, modifier = Modifier.fillMaxWidth()) { Text("I can read the dashboard: type exact km") }
    }
}

@Composable
private fun KmCheck(dot: Color, bold: String, rest: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)); append(bold); pop()
                append(rest)
            },
            style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextPrimary)
        )
    }
}

/** 48250 shows as 48,250 while the field keeps plain digits. */
private object KmGrouping : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        if (digits.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val out = "%,d".format(digits.toLong())
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val commasBefore = (digits.length - 1) / 3 - (digits.length - offset).let { if (it <= 0) 0 else (it - 1) / 3 }
                return (offset + commasBefore).coerceIn(0, out.length)
            }
            override fun transformedToOriginal(offset: Int): Int = out.take(offset).count(Char::isDigit).coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
