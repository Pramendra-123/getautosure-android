package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.drivelab.DriveLabStore
import com.enginesense.app.drivelab.DriveLabSummary
import com.enginesense.app.drivelab.DriveRecorderService
import com.enginesense.app.owner.DetectedFill
import com.enginesense.app.owner.ExpenseCategory
import com.enginesense.app.owner.ExpenseEntry
import com.enginesense.app.owner.FuelAutomation
import com.enginesense.app.owner.FuelEntry
import com.enginesense.app.owner.OwnerCarData
import com.enginesense.app.owner.PaymentKind
import com.enginesense.app.owner.PaymentSmsParser
import com.enginesense.app.ui.components.OwnerText
import com.enginesense.app.ui.components.pickDate
import com.enginesense.app.ui.theme.GetAutoSureColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private const val PROMPT_DAYS = 30L

/** Today's odometer for the picked car: the car's own reading, else the last one given plus km driven since. */
@Composable
fun rememberOdometer(viewModel: GetAutoSureViewModel, owner: OwnerCarData, drives: DriveLabSummary?): Int? {
    val garage by viewModel.garage.collectAsState()
    val profileKm by viewModel.vehicleOdometerKm.collectAsState()
    val known = FuelAutomation.lastKnownOdometer(owner, profileKm, garage.active?.addedMillis ?: 0L)
    return FuelAutomation.estimatedOdometer(drives?.latestOdometerKm, known?.first, known?.second, drives?.trips.orEmpty())
}

/** Fill-ups the fuel gauge shows for the picked car, newest first. Empty for cars that don't report fuel level. */
@Composable
private fun rememberDetectedFills(viewModel: GetAutoSureViewModel): List<DetectedFill> {
    val context = LocalContext.current
    val garage by viewModel.garage.collectAsState()
    val recorder by DriveRecorderService.status.collectAsState()
    val activeId = garage.active?.id
    val fills by produceState(emptyList<DetectedFill>(), activeId, recorder.samplesThisRun / 200) {
        value = withContext(Dispatchers.IO) {
            FuelAutomation.detectFills(DriveLabStore(context).loadSamples().filter { garage.carAt(it.timeMillis) == activeId }).sortedByDescending { it.atMillis }
        }
    }
    return fills
}

/**
 * "Filled about 37 L on 21 Nov — how much did you pay?" Shown on the Garage when the fuel gauge
 * jumped between two drives. The first time it also asks the tank size, which turns % into litres.
 */
@Composable
fun FillUpPrompt(viewModel: GetAutoSureViewModel) {
    val owner by viewModel.ownerData.collectAsState()
    val drives = rememberCarDrives(viewModel)
    val odometer = rememberOdometer(viewModel, owner, drives)
    val cutoff = System.currentTimeMillis() - PROMPT_DAYS * 24 * 60 * 60 * 1000
    val fill = rememberDetectedFills(viewModel).firstOrNull { it.atMillis > cutoff && it.atMillis !in owner.handledFills } ?: return
    var amount by remember(fill.atMillis) { mutableStateOf("") }
    var tank by remember(fill.atMillis) { mutableStateOf(owner.tankLitres?.toString() ?: "") }
    val tankLitres = tank.toIntOrNull()?.takeIf { it in 20..120 }
    val litres = tankLitres?.let { fill.litres(it) }
    val date = Instant.ofEpochMilli(fill.atMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.AccentSoft).padding(16.dp)) {
        Text("Fill-up spotted", style = OwnerText.RowSub.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold))
        Text(
            if (litres != null) "About %.0f L on %s".format(litres, date.format(DAY)) else "Tank went from %.0f%% to %.0f%% on %s".format(fill.fromPercent, fill.toPercent, date.format(DAY)),
            style = OwnerText.Section
        )
        Text("How much did you pay? That's all we need.", style = OwnerText.RowSub)
        Spacer(Modifier.height(8.dp))
        if (owner.tankLitres == null) {
            OutlinedTextField(tank, { tank = it.filter(Char::isDigit).take(3) }, label = { Text("Fuel tank size (litres) — once only") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Amount paid (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { viewModel.updateOwnerData { it.copy(handledFills = it.handledFills + fill.atMillis) } }) {
                Text("Not a fill-up", color = GetAutoSureColors.TextSecondary)
            }
            TextButton(
                enabled = amount.toIntOrNull() != null && (owner.tankLitres != null || tankLitres != null),
                onClick = {
                    val paid = amount.toIntOrNull() ?: return@TextButton
                    val size = owner.tankLitres ?: tankLitres ?: return@TextButton
                    viewModel.updateOwnerData {
                        it.copy(
                            tankLitres = size,
                            handledFills = it.handledFills + fill.atMillis,
                            fuel = it.fuel + FuelEntry(System.currentTimeMillis(), date.toString(), fill.litres(size), paid, odometer, fill.fullTank)
                        )
                    }
                }
            ) { Text("Save", fontWeight = FontWeight.Bold, color = GetAutoSureColors.Accent) }
        }
    }
}

/**
 * A payment SMS shared into the app (long-press → Share → GetAutoSure): what we read from it,
 * for the owner to check and save. Fuel becomes a fill-up; everything else an expense.
 */
@Composable
fun SharedPaymentDialog(viewModel: GetAutoSureViewModel) {
    val context = LocalContext.current
    val text by viewModel.sharedText.collectAsState()
    val message = text ?: return
    val parsed = remember(message) { PaymentSmsParser.parse(message) }
    var amount by remember(message) { mutableStateOf(parsed?.amountInr?.toString() ?: "") }
    var kind by remember(message) { mutableStateOf(parsed?.kind ?: PaymentKind.OTHER) }
    var date by remember(message) { mutableStateOf(parsed?.date ?: LocalDate.now()) }

    AlertDialog(
        onDismissRequest = { viewModel.clearSharedText() },
        title = { Text(if (parsed != null) "Add this payment?" else "Add a payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (parsed == null) Text("We couldn't read an amount from that message. Fill it in below.", style = OwnerText.RowSub)
                parsed?.merchant?.let { Text(it, style = OwnerText.RowTitle) }
                Text("Date: ${date.format(DAY)}", style = OwnerText.RowTitle.copy(color = GetAutoSureColors.Accent), modifier = Modifier.clickable { pickDate(context, date) { date = it } })
                PaymentKind.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { k ->
                            val on = k == kind
                            Text(
                                k.label,
                                color = if (on) GetAutoSureColors.OnInk else GetAutoSureColors.TextPrimary,
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) GetAutoSureColors.Ink else GetAutoSureColors.SurfaceRaised)
                                    .clickable { kind = k }.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Amount (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount.toIntOrNull() != null,
                onClick = {
                    val paid = amount.toIntOrNull() ?: return@TextButton
                    val id = System.currentTimeMillis()
                    viewModel.updateOwnerData {
                        if (kind == PaymentKind.FUEL) it.copy(fuel = it.fuel + FuelEntry(id, date.toString(), null, paid, null, fullTank = false))
                        else it.copy(expenses = it.expenses + ExpenseEntry(id, date.toString(), expenseCategory(kind), paid, parsed?.merchant ?: "From SMS"))
                    }
                    viewModel.clearSharedText()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { viewModel.clearSharedText() }) { Text("Cancel") } }
    )
}

private fun expenseCategory(kind: PaymentKind): ExpenseCategory = when (kind) {
    PaymentKind.TOLL -> ExpenseCategory.TOLL
    PaymentKind.PARKING -> ExpenseCategory.PARKING
    PaymentKind.WASH -> ExpenseCategory.WASH
    PaymentKind.SERVICE -> ExpenseCategory.SERVICE
    PaymentKind.PARTS -> ExpenseCategory.PARTS
    PaymentKind.FUEL, PaymentKind.OTHER -> ExpenseCategory.OTHER
}
