package com.enginesense.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.storage.AppMode
import com.enginesense.app.storage.LanguagePrefs
import com.enginesense.app.ui.components.StepProgressIndicator
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * The three-mode screens from the design canvas "GetAutoSure – Vehicle Intelligence Screens":
 * "Who are you?" (asked once, before sign-in), the buyer's home, and Profile with the mode switch.
 */

private fun AppMode.icon(): ImageVector = when (this) {
    AppMode.OWN_CAR -> Icons.Filled.DirectionsCar
    AppMode.BUYING -> Icons.Filled.Search
    AppMode.DEALER -> Icons.Filled.Storefront
}

/** Step 2 of first launch. The choice decides what comes after sign-in and which home opens. */
@Composable
fun ChooseModeScreen(initial: AppMode?, onContinue: (AppMode) -> Unit) {
    var picked by remember { mutableStateOf(initial ?: AppMode.OWN_CAR) }
    Column(
        Modifier.fillMaxSize().background(GetAutoSureColors.Background).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StepProgressIndicator(current = 1, total = 3)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Who are you?", style = GetAutoSureType.ScreenTitle)
            Text("We'll set up the app for you. You can switch anytime in Profile.", style = GetAutoSureType.Caption)
        }
        AppMode.entries.forEach { mode -> ModeCard(mode, selected = mode == picked) { picked = mode } }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GetAutoSureColors.Surface).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = GetAutoSureColors.TextPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Everyone gets the AI Mechanic and video calls with a real expert.", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextPrimary))
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = { onContinue(picked) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** The picked card is black (the design's hero style); the others are white with a border. */
@Composable
private fun ModeCard(mode: AppMode, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) GetAutoSureColors.Ink else GetAutoSureColors.Surface
    val title = if (selected) GetAutoSureColors.OnInk else GetAutoSureColors.TextPrimary
    val body = if (selected) GetAutoSureColors.OnInkMuted else GetAutoSureColors.TextSecondary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg)
            .border(1.dp, if (selected) GetAutoSureColors.Ink else GetAutoSureColors.Border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                .background(if (selected) GetAutoSureColors.InkRaised else GetAutoSureColors.AccentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(mode.icon(), contentDescription = null, tint = if (selected) GetAutoSureColors.AccentBright else GetAutoSureColors.Accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(mode.title, color = title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(mode.subtitle, color = body, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (selected) GetAutoSureColors.AccentBright else Color.Transparent)
                .border(if (selected) 0.dp else 1.5.dp, GetAutoSureColors.Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = GetAutoSureColors.Ink, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * Home in "buying a used car" mode: the four-step check, an expert before you pay, the last car
 * checked, and a way to make that car "mine" once bought.
 */
@Composable
fun BuyerHomeScreen(
    viewModel: GetAutoSureViewModel,
    onStartCheck: () -> Unit,
    onOpenLastCheck: () -> Unit,
    onExpert: () -> Unit,
    onBoughtIt: () -> Unit
) {
    val last by viewModel.prePurchaseResult.collectAsState()
    Column(
        Modifier.fillMaxSize().background(GetAutoSureColors.Background).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("Buying a used car?", style = GetAutoSureType.Caption)
            Text("Check a car", style = GetAutoSureType.ScreenTitle)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(GetAutoSureColors.Ink).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Know what you're buying before you pay", color = GetAutoSureColors.OnInk, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
            listOf(
                "Car number and the seller's price",
                "RTO record: owners, loan, insurance",
                "Plug in the scanner, engine on",
                "Verdict, repair costs and fair price"
            ).forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(GetAutoSureColors.InkRaised), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = GetAutoSureColors.AccentBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(step, color = Color(0xFFD1D5DB), fontSize = 13.sp)
                }
            }
            Button(
                onClick = onStartCheck,
                colors = ButtonDefaults.buttonColors(containerColor = GetAutoSureColors.AccentBright, contentColor = GetAutoSureColors.Ink),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Start a check", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }

        WhiteRow(
            icon = Icons.Filled.SupportAgent,
            title = "Talk to an expert before you pay",
            subtitle = "15-min video call on your scan",
            action = "Book",
            onClick = onExpert
        )

        Text("Car you checked", style = GetAutoSureType.SectionTitle)
        val result = last
        if (result == null) {
            Text("Cars you check show up here, with their score and repair costs.", style = GetAutoSureType.Caption)
        } else {
            val score = result.health.score
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GetAutoSureColors.Surface).clickable(onClick = onOpenLastCheck).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(result.vehicleId.vin?.let { "VIN …${it.takeLast(6)}" } ?: "Last car checked", style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        "${SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(result.timestampMillis))} · ${result.findings.size} issue${if (result.findings.size == 1) "" else "s"} found",
                        style = GetAutoSureType.Caption
                    )
                }
                Text(
                    score?.toString() ?: "–",
                    color = when {
                        score == null -> GetAutoSureColors.Unavailable
                        score >= 80 -> GetAutoSureColors.Good
                        score >= 60 -> GetAutoSureColors.Watch
                        else -> GetAutoSureColors.Attention
                    },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GetAutoSureColors.AccentSoft).clickable(onClick = onBoughtIt).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Bought this car?", style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
                    Text("Switch to \"I own a car\" for alerts and service reminders", style = GetAutoSureType.Caption)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = GetAutoSureColors.Accent)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WhiteRow(icon: ImageVector, title: String, subtitle: String, action: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(GetAutoSureColors.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = GetAutoSureColors.Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = GetAutoSureType.Caption)
        }
        if (action != null) Text(action, color = GetAutoSureColors.Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        else Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = GetAutoSureColors.TextSecondary)
    }
}

/** A row in Profile: label, optional value on the right, and where it goes. */
private data class ProfileLink(val label: String, val route: String?, val url: String? = null)

/** Profile, in every mode: switch mode (keeps everything), then the account's cars, reports and bookings. */
@Composable
fun ProfileScreen(mode: AppMode, onSwitchMode: (AppMode) -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(LanguagePrefs.get(context)) }
    var choosingLanguage by remember { mutableStateOf(false) }
    if (choosingLanguage) {
        LanguageSheet(language, onDismiss = { choosingLanguage = false }) { picked ->
            LanguagePrefs.set(context, picked)
            language = picked
            choosingLanguage = false
        }
    }
    val links = buildList {
        if (mode == AppMode.OWN_CAR) add(ProfileLink("My car", "vehicleDetails"))
        if (mode == AppMode.DEALER) {
            add(ProfileLink("Showroom profile", "dealerProfile"))
            add(ProfileLink("Credits and packs", "dealerCheckout/5"))
        }
        add(ProfileLink("Reports", if (mode == AppMode.DEALER) "dealerReports" else "reports"))
        add(ProfileLink("Expert bookings", "myConsultations"))
        add(ProfileLink("Guides and tools", "guidesAndTools"))
        if (mode == AppMode.OWN_CAR) add(ProfileLink("Drive lab (test)", "driveLab"))
        add(ProfileLink("Privacy policy", null, "https://getautosure.com/privacy"))
    }
    Column(
        Modifier.fillMaxSize().background(GetAutoSureColors.Background).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Profile", style = GetAutoSureType.ScreenTitle)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GetAutoSureColors.Surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("APP MODE", color = GetAutoSureColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            AppMode.entries.forEach { m ->
                val current = m == mode
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (current) GetAutoSureColors.Ink else GetAutoSureColors.Surface)
                        .border(1.dp, if (current) GetAutoSureColors.Ink else GetAutoSureColors.Border, RoundedCornerShape(12.dp))
                        .clickable(enabled = !current) { onSwitchMode(m) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(m.title, color = if (current) GetAutoSureColors.OnInk else GetAutoSureColors.TextPrimary, fontSize = 14.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(if (current) "Current" else "Switch", color = if (current) GetAutoSureColors.AccentBright else GetAutoSureColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Switching keeps all your cars, reports and bookings.", style = GetAutoSureType.Caption)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GetAutoSureColors.Surface)
                .clickable { choosingLanguage = true }.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Language", style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
            Text(language.nativeName, color = GetAutoSureColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = GetAutoSureColors.TextSecondary)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GetAutoSureColors.Surface).padding(horizontal = 16.dp)) {
            links.forEachIndexed { i, link ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        when {
                            link.route != null -> onNavigate(link.route)
                            link.url != null -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                        }
                    }.padding(vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(link.label, style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = GetAutoSureColors.TextSecondary)
                }
                if (i < links.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(GetAutoSureColors.Border))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
