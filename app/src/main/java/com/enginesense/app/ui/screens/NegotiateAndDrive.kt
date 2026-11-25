package com.enginesense.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.GetAutoSureViewModel.CoachState
import com.enginesense.app.GetAutoSureViewModel.TestDriveState
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.CoachLanguage
import com.enginesense.app.vehicle.CoachTone
import com.enginesense.app.vehicle.DriveCheck
import com.enginesense.app.vehicle.DriveLeg
import com.enginesense.app.vehicle.DriveResult
import com.enginesense.app.vehicle.HeardNoise
import com.enginesense.app.vehicle.NegotiationCoach
import com.enginesense.app.vehicle.NegotiationPlan
import com.enginesense.app.vehicle.PrePurchaseReport
import java.util.Locale
import kotlinx.coroutines.launch

private val AiPurple = GetAutoSureColors.AccentPurple
private val AiSoft = Color(0xFFF1ECFF)

private fun lakh(v: Double): String = if (v >= 100_000) "₹%.2f L".format(v / 100_000) else "₹" + NegotiationCoach.indian(v.toLong())
private fun thousand(v: Double): String = "₹" + "%,d".format((v / 1000).toLong()) + "k"

// ---------------------------------------------------------------- negotiation

/**
 * The negotiation card on Price and report: what to ask off, the three prices, the reason for each
 * rupee, then AI-written words for WhatsApp and for the car. Numbers from [NegotiationPlan]; AI only writes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NegotiationCard(
    viewModel: GetAutoSureViewModel, report: PrePurchaseReport, plan: NegotiationPlan?, carName: String?,
    askingInput: String, onAsking: (String) -> Unit, onSharePdf: suspend () -> java.io.File?
) {
    val context = LocalContext.current
    SectionHeader("Negotiate", "What to ask for, and why — every rupee is something this check found")
    if (plan == null) {
        Text("Add the make, model and year above to see what to ask for.", style = GetAutoSureType.Caption)
        return
    }

    OutlinedTextField(
        value = askingInput,
        onValueChange = { onAsking(it.filter(Char::isDigit).take(9)) },
        label = { Text("Seller's asking price (₹)") },
        supportingText = { askingInput.toDoubleOrNull()?.let { Text(lakh(it)) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )

    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(18.dp)).background(GetAutoSureColors.Ink).padding(16.dp)) {
        val off = plan.askOffInr
        when {
            plan.askingInr == null -> {
                Text("Add the asking price to see how much to ask off.", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.OnInkMuted))
            }
            off != null && off > 0 -> {
                Text("Seller is asking ${lakh(plan.askingInr)}. Ask for", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.OnInkMuted))
                Text("₹${NegotiationCoach.indian(off.toLong())} off", color = GetAutoSureColors.AccentBright, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("Every rupee below is something this check found.", style = GetAutoSureType.Caption.copy(color = Color(0xFFD6D6D6)))
            }
            else -> {
                Text("The asking price is already fair", color = GetAutoSureColors.AccentBright, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("You can still ask for a little off for the repairs below, but don't push hard.", style = GetAutoSureType.Caption.copy(color = Color(0xFFD6D6D6)))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ladder("Open with", lakh(plan.openWith), GetAutoSureColors.OnInk, Modifier.weight(1f))
            Ladder("Fair deal", lakh(plan.fairDeal), GetAutoSureColors.AccentBright, Modifier.weight(1f))
            Ladder("Walk away over", lakh(plan.walkAwayOver), Color(0xFFFF7A6B), Modifier.weight(1f))
        }
    }

    if (plan.reasons.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(14.dp)) {
            plan.reasons.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
                        Text(r.source, style = GetAutoSureType.Caption)
                    }
                    Text(
                        if (r.maxInr > r.minInr) "${thousand(r.minInr)}–${thousand(r.maxInr).drop(1)}" else thousand(r.minInr),
                        style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    // The words: AI writes them in the buyer's language from the plan's numbers.
    var language by rememberSaveable { mutableStateOf(CoachLanguage.EN) }
    var tone by rememberSaveable { mutableStateOf(CoachTone.FIRM) }
    var sellerName by rememberSaveable { mutableStateOf("") }
    val coachState by viewModel.coaching.collectAsState()
    val notes = report.redFlags.map { it.title } + report.worthAsking.map { it.title }

    Spacer(Modifier.height(16.dp))
    Text("Message to the seller", style = GetAutoSureType.SectionTitle)
    Text("AI writes it from the findings above. Edit anything before you send it.", style = GetAutoSureType.Caption)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CoachLanguage.values().forEach { l -> Chip(l.label, l == language) { language = l } }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CoachTone.values().forEach { t -> Chip(t.label, t == tone, Modifier.weight(1f)) { tone = t } }
    }
    OutlinedTextField(
        value = sellerName, onValueChange = { sellerName = it.take(40) },
        label = { Text("Seller's name (optional)") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { viewModel.requestCoaching(plan, carName, sellerName, language, tone, notes, context) },
        enabled = coachState != CoachState.Writing,
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) { Text(if (coachState == CoachState.Writing) "Writing…" else if (coachState is CoachState.Ready) "Write it again" else "✦ Write the message") }

    (coachState as? CoachState.Ready)?.coaching?.let { c ->
        var text by remember(c) { mutableStateOf(c.message) }
        var sharing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 6)
        if (!c.fromAi) Text("Written offline from the same numbers — the AI couldn't be reached.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copy(context, text) }, modifier = Modifier.weight(1f)) { Text("Copy") }
            Button(
                onClick = {
                    sharing = true
                    scope.launch {
                        val pdf = runCatching { onSharePdf() }.getOrNull()
                        shareToWhatsApp(context, text, pdf)
                        sharing = false
                    }
                },
                enabled = !sharing,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FA855)),
                modifier = Modifier.weight(1f)
            ) { Text(if (sharing) "Preparing…" else "Send on WhatsApp") }
        }

        if (c.sayThis.isNotEmpty() || c.replies.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("At the car", style = GetAutoSureType.SectionTitle)
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(14.dp)) {
                if (c.sayThis.isNotEmpty()) {
                    Text("Say this", style = GetAutoSureType.Body.copy(fontWeight = FontWeight.Bold))
                    c.sayThis.forEach { Said(it) }
                }
                if (c.replies.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("If the seller says…", style = GetAutoSureType.Body.copy(fontWeight = FontWeight.Bold))
                    c.replies.forEach { Said(it.you, it.seller) }
                }
            }
            if (c.tip.isNotBlank()) {
                Text(
                    "✦ ${c.tip}", style = GetAutoSureType.Caption.copy(color = Color(0xFF2E2163)),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(AiSoft).padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun Ladder(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(GetAutoSureColors.InkRaised).padding(horizontal = 8.dp, vertical = 9.dp)) {
        Text(label, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.OnInkMuted, fontSize = 10.5.sp))
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun Chip(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        textAlign = TextAlign.Center,
        style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextPrimary, fontWeight = FontWeight.SemiBold),
        modifier = modifier.clip(RoundedCornerShape(50))
            .background(if (on) GetAutoSureColors.AccentSoft else GetAutoSureColors.Surface)
            .border(1.5.dp, if (on) GetAutoSureColors.AccentBright else GetAutoSureColors.Border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun Said(text: String, small: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(AiPurple))
        Spacer(Modifier.width(10.dp))
        Column {
            small?.let { Text(it, style = GetAutoSureType.Caption) }
            Text(text, style = GetAutoSureType.Body)
        }
    }
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Message", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

/** WhatsApp when it's installed (with the PDF attached), else the normal share sheet. */
private fun shareToWhatsApp(context: Context, text: String, pdf: java.io.File?) {
    val send = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        if (pdf != null) {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, com.enginesense.app.report.reportContentUri(context, pdf))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else type = "text/plain"
    }
    val wa = Intent(send).setPackage("com.whatsapp")
    try {
        context.startActivity(wa)
    } catch (e: android.content.ActivityNotFoundException) {
        context.startActivity(Intent.createChooser(send, "Send to the seller"))
    }
}

// ---------------------------------------------------------------- make this check stronger

/** Two optional add-ons under the price — they never block the report, and both update it. */
@Composable
internal fun StrongerCheckCards(drive: DriveResult?, onStartDrive: () -> Unit, onViewDrive: () -> Unit, onExpert: () -> Unit) {
    SectionHeader("Make this check stronger", if (drive == null) "Gearbox, brakes and suspension aren't checked yet" else null)
    if (drive == null) {
        AddOnCard("🚗", "How it drives", "Gearbox, engine under load, overheating. 15 min with an AI co-driver. Free.", "Start ›", GetAutoSureColors.Accent, GetAutoSureColors.AccentSoft, onStartDrive)
    } else {
        AddOnCard(
            "✓", "How it drives: ${drive.score}/5", drive.headline, "View", GetAutoSureColors.Good, Color(0xFFE6F4F0), onViewDrive,
            border = GetAutoSureColors.Good
        )
    }
    Spacer(Modifier.height(8.dp))
    val gearbox = drive?.findings?.firstOrNull { it.serious }
    AddOnCard(
        "🎥", if (gearbox != null) "Not sure about this?" else "A second opinion",
        (if (gearbox != null) "${gearbox.title}. An expert can go through it with you. " else "Video call with a real mechanic, with your report in hand. ") +
            "₹${com.enginesense.app.consultation.ConsultationPricing.PRICE_INR}.",
        "Book ›", AiPurple, AiSoft, onExpert
    )
}

@Composable
private fun AddOnCard(icon: String, title: String, body: String, action: String, accent: Color, iconBg: Color, onClick: () -> Unit, border: Color = GetAutoSureColors.Border) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GetAutoSureColors.Surface)
            .border(1.5.dp, border, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconBg), contentAlignment = Alignment.Center) { Text(icon, fontSize = 18.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.Bold))
            Text(body, style = GetAutoSureType.Caption)
        }
        Spacer(Modifier.width(8.dp))
        Text(action, style = GetAutoSureType.Caption.copy(color = accent, fontWeight = FontWeight.Bold))
    }
}

// ---------------------------------------------------------------- test drive

/** Before the drive: what it checks and the route. Starting it reads the car through the same dongle. */
@Composable
internal fun TestDriveIntro(viewModel: GetAutoSureViewModel, rcModel: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Test drive", style = GetAutoSureType.ScreenTitle)
        Text("About 15 minutes. Keep the dongle plugged in and the phone in a holder. The co-driver tells you what to do and watches the engine and gearbox.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(14.dp)) {
            DriveLeg.values().forEachIndexed { i, leg ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(GetAutoSureColors.SurfaceRaised), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = GetAutoSureType.Caption.copy(fontWeight = FontWeight.Bold, color = GetAutoSureColors.TextPrimary))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(leg.title, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
                        Text(leg.tests, style = GetAutoSureType.Caption)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Drive safely and follow traffic rules. Only do the hard acceleration and firm stop on an empty road. Never look at the phone while driving: turn the voice on.", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Watch))
        Spacer(Modifier.height(16.dp))
        Button(onClick = { viewModel.startTestDrive(rcModel) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Start the drive") }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Not now, back to the price") }
    }
}

/** While driving: big readable numbers, the co-driver's line, what it saw, and the "heard something" buttons. */
@Composable
internal fun TestDriveLive(viewModel: GetAutoSureViewModel, state: TestDriveState) {
    val context = LocalContext.current
    var voice by rememberSaveable { mutableStateOf("hi") } // "off", "hi", "en"
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status -> if (status == TextToSpeech.SUCCESS) tts.value = engine }
        onDispose { engine.stop(); engine.shutdown() }
    }
    val running = state as? TestDriveState.Running
    val spoken = when (voice) { "hi" -> running?.lineHi; "en" -> running?.line; else -> null }
    LaunchedEffect(spoken, tts.value) {
        val t = tts.value ?: return@LaunchedEffect
        if (spoken.isNullOrBlank()) return@LaunchedEffect
        t.language = if (voice == "hi") Locale("hi", "IN") else Locale("en", "IN")
        t.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "codriver")
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0B)).verticalScroll(rememberScrollState()).padding(16.dp)) {
        when (state) {
            TestDriveState.Connecting -> {
                Spacer(Modifier.height(40.dp))
                Text("Connecting to the car…", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Keep the dongle plugged in.", color = Color(0xFF9A9A9A))
            }
            is TestDriveState.Error -> {
                Spacer(Modifier.height(40.dp))
                Text("The drive stopped", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(state.message, color = Color(0xFF9A9A9A))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.endTestDrive() }, modifier = Modifier.fillMaxWidth()) { Text("Keep what was measured") }
                TextButton(onClick = { viewModel.cancelTestDrive() }, modifier = Modifier.fillMaxWidth()) { Text("Discard the drive", color = Color(0xFFBDBDBD)) }
            }
            is TestDriveState.Running -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Test drive", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("● %d:%02d".format(state.elapsedS / 60, state.elapsedS % 60), color = Color(0xFFFF6B5B), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Gauge(state.speedKmh?.toInt()?.toString() ?: "–", "KM/H", Color.White, Modifier.weight(1f))
                    Gauge(state.rpm?.toInt()?.let { "%,d".format(it) } ?: "–", "RPM", Color.White, Modifier.weight(1f))
                    Gauge(state.coolantC?.toInt()?.let { "$it°" } ?: "–", "ENGINE", if ((state.coolantC ?: 0.0) >= 105) Color(0xFFFF6B5B) else Color(0xFFFFB86B), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Leg ${state.legIndex + 1} of ${DriveLeg.values().size}", color = Color(0xFF9A9A9A), fontSize = 12.sp)
                        Text(state.leg.title, color = GetAutoSureColors.AccentBright, fontWeight = FontWeight.Bold)
                    }
                    if (state.legIndex < DriveLeg.values().size - 1) {
                        TextButton(onClick = { viewModel.nextDriveLeg() }) { Text("Next ›", color = Color.White) }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF22183F))
                        .border(1.dp, Color(0xFF3A2B6B), RoundedCornerShape(16.dp)).padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("✦ CO-DRIVER", color = Color(0xFFB9A6FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when (voice) { "hi" -> "🔊 हिन्दी"; "en" -> "🔊 English"; else -> "🔇 Voice off" },
                            color = Color(0xFFB9A6FF), fontSize = 11.sp,
                            modifier = Modifier.clickable { voice = when (voice) { "hi" -> "en"; "en" -> "off"; else -> "hi" } }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(if (voice == "hi") state.lineHi else state.line, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp)
                }
                state.events.take(6).forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("%d:%02d".format(e.tMs / 60_000, e.tMs / 1000 % 60), color = Color(0xFF777777), fontSize = 11.sp, modifier = Modifier.width(38.dp))
                        Box(Modifier.padding(top = 4.dp).size(9.dp).clip(CircleShape).background(if (e.check == DriveCheck.WATCH) Color(0xFFF0A43A) else Color(0xFF22B38F)))
                        Spacer(Modifier.width(8.dp))
                        Text(e.text, color = Color(0xFFDADADA), fontSize = 12.5.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Heard something?", color = Color(0xFF9A9A9A), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeardNoise.values().forEach { n ->
                        val on = n in state.heard
                        Text(
                            (if (on) "✓ " else "") + n.label, color = if (on) Color.Black else Color(0xFFDADADA), fontSize = 12.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) GetAutoSureColors.AccentBright else Color(0xFF1A1A1A))
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(50)).clickable { viewModel.markHeard(n) }.padding(vertical = 10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.endTestDrive() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B2B)),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("End the drive") }
            }
            TestDriveState.Idle -> Unit
        }
    }
}

@Composable
private fun Gauge(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color(0xFF9A9A9A), fontSize = 10.sp)
    }
}

/** After the drive: the score and each check. Its findings are already in the report, price and negotiation. */
@Composable
internal fun DriveSummary(drive: DriveResult, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("How it drove", style = GetAutoSureType.ScreenTitle)
        Text("${drive.durationS / 60} min · ${"%.1f".format(drive.distanceKm)} km · ${drive.legsDone} of 5 legs", style = GetAutoSureType.Caption)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Ink).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (drive.score > 0) "${drive.score}/5" else "–", color = GetAutoSureColors.AccentBright, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(12.dp))
            Text(drive.headline, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(14.dp)) {
            if (drive.findings.isEmpty()) Text("The car didn't send enough data. Try a longer drive with the engine warm.", style = GetAutoSureType.Body)
            drive.findings.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(if (f.check == DriveCheck.WATCH) GetAutoSureColors.Watch else GetAutoSureColors.Good))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(f.title, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.Bold))
                        Text(
                            f.detail + (f.cost?.let { " Tentative repair: ₹${"%,d".format(it.minInr)}–${"%,d".format(it.maxInr)}." } ?: ""),
                            style = GetAutoSureType.Caption
                        )
                    }
                }
            }
        }
        if (drive.findings.any { it.cost != null && it.check == DriveCheck.WATCH }) {
            Text(
                "✦ Added to your report, the price after repairs and the negotiation sheet.",
                style = GetAutoSureType.Caption.copy(color = Color(0xFF2E2163)),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(AiSoft).padding(12.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Back to the price and negotiation") }
    }
}
