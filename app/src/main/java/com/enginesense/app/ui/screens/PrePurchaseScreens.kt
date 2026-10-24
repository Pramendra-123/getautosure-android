package com.enginesense.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.FairPriceEstimator
import com.enginesense.app.vehicle.PrePurchaseAnalyzer
import com.enginesense.app.vehicle.PurchaseVerdict
import java.util.Calendar

/** Entry point — explains the feature, then starts a scan same as any other, just flagged as a pre-purchase check (see GetAutoSureViewModel.startPrePurchaseCheck). */
@Composable
fun PrePurchaseCheckScreen(viewModel: GetAutoSureViewModel, onStartScan: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Considering buying a car?", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan it before you commit — the same health check this app runs on your own car, read as a buy/caution/avoid call instead of an ownership status.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(24.dp))
        listOf(
            "Plug in at the seller's location, same as any scan",
            "Free verdict: good buy, buy with caution, or avoid",
            "Dealer Pro: itemized talking points for the negotiation"
        ).forEach { line ->
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Text("•  ", style = GetAutoSureType.Body)
                Text(line, style = GetAutoSureType.Body)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { viewModel.startPrePurchaseCheck(); onStartScan() },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Scan this car") }
    }
}

/**
 * Depreciation-based fair price estimate — see [FairPriceEstimator]. Built on IRDAI's
 * published standard motor depreciation schedule, not any company's internal pricing
 * model. The user supplies the ex-showroom price; this app has no price catalog and
 * never guesses one.
 */
@Composable
private fun FairPriceEstimateCard(viewModel: GetAutoSureViewModel) {
    val year by viewModel.vehicleYear.collectAsState()
    var priceInput by remember { mutableStateOf("") }
    var kmInput by remember { mutableStateOf("") }

    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Fair Price Estimate", style = GetAutoSureType.Body)
            Spacer(Modifier.height(2.dp))
            Text(
                "Based on the IRDAI standard depreciation schedule — a rough estimate, not a verified valuation.",
                style = GetAutoSureType.Caption
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = priceInput,
                onValueChange = { priceInput = it.filter(Char::isDigit) },
                label = { Text("Ex-showroom price (₹)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = kmInput,
                onValueChange = { kmInput = it.filter(Char::isDigit) },
                label = { Text("Odometer reading (km) — optional") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            val price = priceInput.toDoubleOrNull()
            val km = kmInput.toIntOrNull()
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val ageYears = year?.let { (currentYear - it).toDouble().coerceAtLeast(0.5) }

            Spacer(Modifier.height(12.dp))
            when {
                price == null || price <= 0 -> Text(
                    "Enter the ex-showroom price to see an estimate — we don't have a pricing catalog, so this can't be guessed.",
                    style = GetAutoSureType.Caption
                )
                ageYears == null -> Text(
                    "Add the vehicle's year (edit vehicle on Home) to calculate an estimate.",
                    style = GetAutoSureType.Caption
                )
                else -> {
                    val est = FairPriceEstimator.estimate(price, ageYears, km)
                    Text(
                        "₹${formatRupees(est.lowEstimate)} – ₹${formatRupees(est.highEstimate)}",
                        style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.Accent)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${est.depreciationPercent}% depreciation over ${est.ageYears} year${if (est.ageYears == 1) "" else "s"}" +
                            if (km != null) ", adjusted for odometer reading" else "",
                        style = GetAutoSureType.Caption
                    )
                    if (est.beyondPublishedSchedule) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "IRDAI only publishes rates up to 5 years — beyond that this extrapolates, it isn't an official figure.",
                            style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Watch)
                        )
                    }
                }
            }
        }
    }
}

private fun formatRupees(value: Double): String =
    if (value >= 100000) "%.2f L".format(value / 100000) else "%,.0f".format(value)

@Composable
fun PrePurchaseResultScreen(viewModel: GetAutoSureViewModel, onOpenDealer: () -> Unit, onDone: () -> Unit) {
    val result by viewModel.latestResult.collectAsState()
    val context = LocalContext.current
    val r = result

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        if (r == null) {
            Text("No scan result to show.", style = GetAutoSureType.Body)
            Spacer(Modifier.weight(1f))
            Button(onClick = { viewModel.clearPrePurchaseCheck(); onDone() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            return@Column
        }

        val insight = PrePurchaseAnalyzer.analyze(r.findings, r.health.score)
        val (color, emoji) = when (insight.verdict) {
            PurchaseVerdict.GOOD_BUY -> GetAutoSureColors.Good to "🟢"
            PurchaseVerdict.BUY_WITH_CAUTION -> GetAutoSureColors.Watch to "🟡"
            PurchaseVerdict.AVOID -> GetAutoSureColors.Attention to "🔴"
        }
        val verdictLabel = when (insight.verdict) {
            PurchaseVerdict.GOOD_BUY -> "Good buy"
            PurchaseVerdict.BUY_WITH_CAUTION -> "Buy with caution"
            PurchaseVerdict.AVOID -> "Avoid"
        }

        Box(
            modifier = Modifier.size(64.dp).background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(emoji, style = GetAutoSureType.SectionTitle) }
        Spacer(Modifier.height(16.dp))
        Text(verdictLabel, style = GetAutoSureType.ScreenTitle.copy(color = color))
        Spacer(Modifier.height(6.dp))
        Text(insight.headline, style = GetAutoSureType.Body)
        Spacer(Modifier.height(20.dp))

        // Dealer Pro: the itemized list is the paid part — teased by count, not revealed free.
        Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDealer)) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (insight.negotiationPoints.isEmpty()) "No specific talking points from this scan" else "${insight.negotiationPoints.size} thing${if (insight.negotiationPoints.size == 1) "" else "s"} worth raising with the seller",
                        style = GetAutoSureType.Body
                    )
                    Text("Itemized negotiation points — Dealer Pro", style = GetAutoSureType.Caption)
                }
                if (insight.negotiationPoints.isNotEmpty()) {
                    Box(
                        modifier = Modifier.background(GetAutoSureColors.Good.copy(alpha = 0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text("PRO", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Good, fontSize = 10.sp)) }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = GetAutoSureColors.Unavailable, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        FairPriceEstimateCard(viewModel)

        Spacer(Modifier.height(16.dp))
        Text(
            "Want a second opinion? These tools factor in local market listings, which this estimate doesn't:",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.cardekho.com/used-car-valuation.htm"))) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("CarDekho SmartPrice →") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.cars24.com/used-car-valuation/"))) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Cars24 Valuation →") }

        Spacer(Modifier.weight(1f))
        Button(onClick = { viewModel.clearPrePurchaseCheck(); onDone() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Done")
        }
    }
}
