package com.enginesense.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.storage.AppLanguage
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.vehicle.cards.CardUrgency
import com.enginesense.app.vehicle.cards.FaultCard
import com.enginesense.app.vehicle.cards.FaultCardLabels
import java.text.NumberFormat
import java.util.Locale

private fun urgencyColor(u: CardUrgency) = when (u) {
    CardUrgency.STOP -> GetAutoSureColors.Attention
    CardUrgency.NOW -> GetAutoSureColors.AccentBright
    CardUrgency.SOON -> Color(0xFFE6B422)
    CardUrgency.WAIT -> Color(0xFF3FB27F)
}

private fun inr(n: Int) = "₹" + NumberFormat.getIntegerInstance(Locale("en", "IN")).format(n)

/**
 * A fault card on a black card, in the user's language. The EN | local switch lets a family member
 * flip one card to English without changing the app's setting. A language not translated yet shows
 * English with a note.
 */
@Composable
fun FaultCardView(card: FaultCard, language: AppLanguage, modifier: Modifier = Modifier) {
    val local = language.code
    var showLocal by remember(card, local) { mutableStateOf(true) }
    val lang = if (showLocal && card.has(local)) local else "en"
    val t = card.textIn(lang)
    val l = FaultCardLabels.of(lang)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GetAutoSureColors.Ink).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.code, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(GetAutoSureColors.AccentBright).padding(horizontal = 8.dp, vertical = 2.dp))
            Spacer(Modifier.width(6.dp))
            Text(l.urgency.getValue(card.urgency), color = Color(0xFF111111), fontWeight = FontWeight.Bold, fontSize = 11.sp,
                modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(999.dp)).background(urgencyColor(card.urgency)).padding(horizontal = 8.dp, vertical = 2.dp))
            Spacer(Modifier.weight(1f))
            if (language != AppLanguage.ENGLISH && card.has(local)) {
                Row(Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, Color(0xFF444444), RoundedCornerShape(999.dp))) {
                    Segment("EN", selected = lang == "en") { showLocal = false }
                    Segment(language.nativeName, selected = lang != "en") { showLocal = true }
                }
            }
        }
        Text(t.title, color = GetAutoSureColors.OnInk, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
        Text(t.meaning, color = GetAutoSureColors.OnInk, fontSize = 14.sp, lineHeight = 20.sp)
        if (language != AppLanguage.ENGLISH && !card.has(local)) {
            Text("${language.englishName} version coming soon.", color = GetAutoSureColors.OnInkMuted, fontSize = 12.sp)
        }
        Section(l.notice) { t.notice.forEach { Bullet(it) } }
        Section(l.causes) {
            t.causes.forEachIndexed { i, cause ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("${i + 1}. $cause", color = GetAutoSureColors.OnInk, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                    if (i == 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(l.mostCommon, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(GetAutoSureColors.AccentBright).padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
            }
        }
        Callout(l.drive, t.drive, accent = true)
        Callout(l.firstCheck, t.firstCheck, accent = false)
        Section(l.cost) {
            t.costParts.zip(card.costs).forEach { (part, range) ->
                Row {
                    Text(part, color = GetAutoSureColors.OnInk, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                    Text("${inr(range.first)}–${inr(range.last)}", color = GetAutoSureColors.OnInk, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (t.indiaNote.isNotBlank()) {
            Text("${l.indiaNote}: ${t.indiaNote}", color = Color(0xFFFFD9C2), fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (selected) Color.White else GetAutoSureColors.OnInkMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(if (selected) GetAutoSureColors.AccentBright else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 3.dp))
}

@Composable
private fun Section(heading: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(heading, color = GetAutoSureColors.AccentBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Bullet(text: String) {
    Text("• $text", color = GetAutoSureColors.OnInk, fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
private fun Callout(heading: String, body: String, accent: Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GetAutoSureColors.InkRaised)) {
        if (accent) Box(Modifier.width(3.dp).background(GetAutoSureColors.AccentBright).padding(vertical = 30.dp))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(heading, color = GetAutoSureColors.AccentBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(body, color = GetAutoSureColors.OnInk, fontSize = 13.5.sp, lineHeight = 19.sp)
        }
    }
}
