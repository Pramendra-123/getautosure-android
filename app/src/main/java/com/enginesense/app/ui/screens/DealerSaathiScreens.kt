package com.enginesense.app.ui.screens

import android.net.Uri
import android.widget.VideoView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.enginesense.app.R
import com.enginesense.app.dealerreport.DEALER_PACKS
import com.enginesense.app.dealerreport.DealerPack
import com.enginesense.app.dealerreport.indianGrouping
import com.enginesense.app.storage.DealerProfileStore
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType

/*
 * Dealer Saathi's premium landing page and pack checkout (sample: brand/dealer-saathi/premium-page-sample.png).
 * Payments aren't live: the checkout says so and reports stay free until they are. Pack prices are
 * introductory prices (DEALER_PACKS).
 */

private val CardBg = GetAutoSureColors.Surface
private val CardBorder = GetAutoSureColors.Border
private val OnAccent = Color(0xFF1A0A00)

@Composable
fun DealerScreen(
    onSetUpDealerProfile: () -> Unit = {},
    onStartReport: () -> Unit = {},
    onUsedCarCheck: () -> Unit = {},
    onBuyPack: (cars: Int) -> Unit = {},
    onMyReports: () -> Unit = {}
) {
    val context = LocalContext.current
    // Re-read on every visit, so coming back from the profile screen shows what was just saved.
    val dealer = remember { DealerProfileStore(context).load() }
    var selectedPack by remember { mutableStateOf(DEALER_PACKS.first { it.popular }) }
    var sample by remember { mutableStateOf<Int?>(null) }
    val start = if (dealer == null) onSetUpDealerProfile else onStartReport

    Column(modifier = Modifier.fillMaxSize().background(GetAutoSureColors.Background).verticalScroll(rememberScrollState())) {
        // A dealer who's set up comes back for their reports and credits first.
        if (dealer != null) MyReportsCard(onOpen = onMyReports, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
        Hero(onStart = start, startLabel = if (dealer == null) "Set up your profile →" else "Create a report →")

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(22.dp))
            SectionHeading("One car. Three ready outputs.", "Tap a sample to see it full size.")

            OutputCard(
                image = R.drawable.dealer_sample_report, imageBg = Color.White,
                title = "Vehicle report",
                body = "Branded PDF with health score, RTO record, faults with repair costs and odometer check.",
                link = "Open sample report →",
                onClick = { sample = R.drawable.dealer_sample_report }
            )
            PhotoStudioCard()
            OutputCard(
                image = R.drawable.dealer_sample_status, imageBg = Color.Black, imageBorder = GetAutoSureColors.Accent,
                title = "WhatsApp Status",
                body = "A ready poster with your logo, phone, badges and price. Post it to Status and groups.",
                link = "See sample Status →",
                onClick = { sample = R.drawable.dealer_sample_status }
            )

            Spacer(Modifier.height(14.dp))
            SectionHeading("How it works", null)
            VideoSlot()
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("Scan the car", "Car number", "Rate it", "Photos").forEachIndexed { i, label ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(28.dp).border(1.5.dp, GetAutoSureColors.Accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("${i + 1}", color = GetAutoSureColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = GetAutoSureType.Caption, textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            PricingCard(selected = selectedPack, onSelect = { selectedPack = it })
            WhatYouGetCard()

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onBuyPack(selectedPack.cars) },
                colors = ButtonDefaults.buttonColors(containerColor = GetAutoSureColors.AccentBright, contentColor = OnAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("Buy ${packName(selectedPack)} · ₹${indianGrouping(selectedPack.priceInr.toLong())} →", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = start,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(if (dealer == null) "Set up your dealer profile" else "Start a report · free for now", color = GetAutoSureColors.TextPrimary, fontSize = 15.sp) }
            Spacer(Modifier.height(12.dp))
            Text(
                if (dealer == null) "Your logo, name, numbers and showroom go on every report — set them up once."
                else "Profile: ${dealer.name} · Edit",
                style = GetAutoSureType.Caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSetUpDealerProfile).padding(vertical = 6.dp)
            )
            Text(
                "Buying a car, not selling? Use Used Car Check →",
                style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onUsedCarCheck).padding(vertical = 6.dp)
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    sample?.let { res -> SampleViewer(res) { sample = null } }
}

@Composable
private fun Hero(onStart: () -> Unit, startLabel: String) {
    Box(
        Modifier.fillMaxWidth().height(470.dp)
            .background(Brush.radialGradient(listOf(Color(0xFF3A2A1C), Color(0xFF16171B), GetAutoSureColors.Ink), radius = 1100f), RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
    ) {
        Box(Modifier.fillMaxWidth().height(150.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color(0xFF1B1C20), Color(0xFF0D0E10)))))
        // A dealer's yard: three popular cars, the white Grand Vitara in front (design: brand/dealer-saathi/hero-and-pricing-mockup.png, D)
        Box(Modifier.width(310.dp).height(200.dp).align(Alignment.TopEnd).offset(x = 6.dp, y = 120.dp)) {
            Image(
                painterResource(R.drawable.model_toyota_fortuner), contentDescription = null,
                modifier = Modifier.width(185.dp).align(Alignment.TopStart).graphicsLayer { scaleX = -1f }
            )
            Image(
                painterResource(R.drawable.model_hyundai_creta), contentDescription = null,
                modifier = Modifier.width(185.dp).align(Alignment.TopEnd)
            )
            Image(
                painterResource(R.drawable.model_maruti_grand_vitara), contentDescription = null,
                modifier = Modifier.width(220.dp).align(Alignment.BottomCenter)
            )
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dealer ", style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.OnInk))
                Text("Saathi", style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.AccentBright))
                Spacer(Modifier.width(8.dp))
                Text(
                    "PREMIUM", color = OnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(GetAutoSureColors.AccentBright, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("FOR USED CAR DEALERS", color = Color(0xFFB9BBC0), fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
            Text("List like a pro.", color = GetAutoSureColors.OnInk, fontSize = 32.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold)
            Text("Sell faster.", color = GetAutoSureColors.AccentBright, fontSize = 32.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text("A verified health report, studio photos and a WhatsApp Status poster, made in one flow.", color = Color(0xFFD3D4D8), fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroPoint(Icons.Filled.VerifiedUser, "Build trust")
                HeroPoint(Icons.Filled.Bolt, "Save time")
                HeroPoint(Icons.Filled.BarChart, "More leads")
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = GetAutoSureColors.AccentBright, contentColor = OnAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(startLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun HeroPoint(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = GetAutoSureColors.AccentBright, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color(0xFFE6E6E8), fontSize = 13.sp)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String?) {
    Text(title, color = GetAutoSureColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (subtitle != null) Text(subtitle, style = GetAutoSureType.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun PremiumCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(bottom = 12.dp)
            .background(CardBg, RoundedCornerShape(16.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun OutputCard(
    @DrawableRes image: Int, imageBg: Color, imageBorder: Color? = null,
    title: String, body: String, link: String, onClick: () -> Unit
) {
    PremiumCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(image), contentDescription = "Sample $title",
                contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                modifier = Modifier.size(width = 112.dp, height = 140.dp).clip(RoundedCornerShape(10.dp)).background(imageBg)
                    .then(if (imageBorder != null) Modifier.border(1.5.dp, imageBorder, RoundedCornerShape(10.dp)) else Modifier)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = GetAutoSureColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(body, style = GetAutoSureType.Caption)
                Spacer(Modifier.height(6.dp))
                Text(link, color = GetAutoSureColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PhotoStudioCard() {
    PremiumCard {
        Text("Photo studio", color = GetAutoSureColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text("Gemini puts the car on a grey studio floor. The car itself is never edited.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // A real dealer's photo of a Ciaz and what the Photo studio made of it (plate hidden).
            BeforeAfterTile(Modifier.weight(1f), "Before", R.drawable.studio_before_ciaz, GetAutoSureColors.Ink, GetAutoSureColors.OnInk)
            BeforeAfterTile(Modifier.weight(1f), "After", R.drawable.studio_after_ciaz, GetAutoSureColors.AccentBright, OnAccent)
        }
    }
}

@Composable
private fun BeforeAfterTile(modifier: Modifier, tag: String, image: Int, tagBg: Color, tagFg: Color) {
    Box(modifier.aspectRatio(560f / 532f).clip(RoundedCornerShape(8.dp))) {
        Image(
            painterResource(image), contentDescription = "$tag photo",
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        Text(
            tag, color = tagFg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(6.dp).background(tagBg, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * The ~50s walkthrough (res/raw/dealer_how_it_works.mp4: real screen recordings of the flow, made
 * from brand/dealer-saathi/how-it-works.mp4), playing muted on a loop like a GIF — it has no sound.
 */
@Composable
private fun VideoSlot() {
    val context = LocalContext.current
    AndroidView(
        factory = {
            VideoView(it).apply {
                setVideoURI(Uri.parse("android.resource://${context.packageName}/${R.raw.dealer_how_it_works}"))
                setOnPreparedListener { player ->
                    player.isLooping = true
                    player.setVolume(0f, 0f)
                    start()
                }
            }
        },
        onRelease = { it.stopPlayback() },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B0C0E))
    )
}

@Composable
private fun PricingCard(selected: DealerPack, onSelect: (DealerPack) -> Unit) {
    PremiumCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = GetAutoSureColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Simple pricing", color = GetAutoSureColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text("Buy a pack. One credit makes one car's report.", style = GetAutoSureType.Caption, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DEALER_PACKS.forEach { pack -> PackTile(Modifier.weight(1f), pack, pack == selected) { onSelect(pack) } }
        }
        Text(
            "Introductory prices · Credits never expire · Scan and RTO check are free",
            style = GetAutoSureType.Caption.copy(fontSize = 11.sp), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
    }
}

@Composable
private fun PackTile(modifier: Modifier, pack: DealerPack, selected: Boolean, onClick: () -> Unit) {
    Box(modifier.padding(top = 10.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .background(if (selected) GetAutoSureColors.AccentSoft else GetAutoSureColors.Surface, RoundedCornerShape(12.dp))
                .border(if (selected) 2.dp else 1.dp, if (selected) GetAutoSureColors.Accent else GetAutoSureColors.Border, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(top = 16.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(packName(pack), color = GetAutoSureColors.TextSecondary, fontSize = 12.sp)
            Text("₹${indianGrouping(pack.priceInr.toLong())}", color = GetAutoSureColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (pack.cars == 1) "per car" else "₹${pack.perCarInr} / car", style = GetAutoSureType.Caption.copy(fontSize = 11.sp))
        }
        if (pack.popular) {
            Text(
                "Popular", color = OnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-9).dp)
                    .background(GetAutoSureColors.AccentBright, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun WhatYouGetCard() {
    PremiumCard {
        Text("What you get", color = GetAutoSureColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        listOf(
            "PDF report with your logo, two phones and map link",
            "RTO record: owner, insurance, PUC, loan, blacklist",
            "Engine scan, fault codes and tentative repair costs",
            "Odometer check and self-tests",
            "Up to 6 studio photos on a grey studio floor",
            "WhatsApp Status poster with your price"
        ).forEach { ListLine("✓", GetAutoSureColors.Good, it, GetAutoSureColors.TextPrimary) }
        Spacer(Modifier.height(10.dp))
        Text("Not included", color = GetAutoSureColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        listOf(
            "Body, tyre or brake check by us (you rate these)",
            "Challans and service history",
            "Warranty on the car"
        ).forEach { ListLine("✕", GetAutoSureColors.Unavailable, it, GetAutoSureColors.TextSecondary) }
    }
}

@Composable
private fun ListLine(mark: String, markColor: Color, text: String, textColor: Color) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(mark, color = markColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(22.dp))
        Text(text, color = textColor, fontSize = 14.sp)
    }
}

/** Full-screen look at a sample (report page or Status poster). Tap anywhere to close. */
@Composable
private fun SampleViewer(@DrawableRes res: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0xEE000000)).clickable(onClick = onClose).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(res), contentDescription = "Sample", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
            Text("Sample · made-up data · tap to close", style = GetAutoSureType.Caption, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

private fun packName(pack: DealerPack) = if (pack.cars == 1) "1 car" else "${pack.cars} cars"

/**
 * Checkout for a pack. Payments aren't live, so "Pay" explains that instead of charging, and reports
 * stay free until they are.
 */
@Composable
fun DealerCheckoutScreen(cars: Int, onDone: () -> Unit, onStartReport: () -> Unit) {
    var pack by remember { mutableStateOf(DEALER_PACKS.firstOrNull { it.cars == cars } ?: DEALER_PACKS.first { it.popular }) }
    var showNotLive by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(GetAutoSureColors.Background).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Checkout", style = GetAutoSureType.ScreenTitle)
        Text("Dealer Saathi report pack", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(16.dp))

        PremiumCard {
            Text("Choose a pack", color = GetAutoSureColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            DEALER_PACKS.forEach { p ->
                val on = p == pack
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .border(if (on) 2.dp else 1.dp, if (on) GetAutoSureColors.Accent else CardBorder, RoundedCornerShape(10.dp))
                        .clickable { pack = p }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(18.dp).border(2.dp, if (on) GetAutoSureColors.Accent else GetAutoSureColors.Unavailable, CircleShape).padding(4.dp)
                            .background(if (on) GetAutoSureColors.Accent else Color.Transparent, CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(packName(p) + if (p.popular) " · Popular" else "", color = GetAutoSureColors.TextPrimary, fontSize = 15.sp)
                        Text(if (p.cars == 1) "One report" else "₹${p.perCarInr} per car", style = GetAutoSureType.Caption)
                    }
                    Text("₹${indianGrouping(p.priceInr.toLong())}", color = GetAutoSureColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        PremiumCard {
            BillLine("Pack (${packName(pack)})", "₹${indianGrouping(pack.beforeGstInr.toLong())}")
            BillLine("GST 18%", "₹${indianGrouping(pack.gstInr.toLong())}")
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(CardBorder))
            BillLine("Total", "₹${indianGrouping(pack.priceInr.toLong())}", bold = true)
        }

        PremiumCard {
            Text("UPI, card or net banking", color = GetAutoSureColors.TextPrimary, fontSize = 14.sp)
            Text("GST invoice to your email. Credits never expire.", style = GetAutoSureType.Caption)
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { showNotLive = true },
            colors = ButtonDefaults.buttonColors(containerColor = GetAutoSureColors.AccentBright, contentColor = OnAccent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text("Pay ₹${indianGrouping(pack.priceInr.toLong())}", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Text(
            "If a report fails, the credit comes back.",
            style = GetAutoSureType.Caption, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
    }

    if (showNotLive) {
        AlertDialog(
            onDismissRequest = { showNotLive = false },
            title = { Text("Payments open soon") },
            text = { Text("Nothing was charged. Until payments open, Dealer Saathi reports are free — make as many as you like.") },
            confirmButton = { TextButton(onClick = { showNotLive = false; onStartReport() }) { Text("Start a report") } },
            dismissButton = { TextButton(onClick = { showNotLive = false; onDone() }) { Text("Back") } }
        )
    }
}

@Composable
private fun BillLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = if (bold) GetAutoSureColors.TextPrimary else GetAutoSureColors.TextSecondary, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(value, color = GetAutoSureColors.TextPrimary, fontSize = if (bold) 16.sp else 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
