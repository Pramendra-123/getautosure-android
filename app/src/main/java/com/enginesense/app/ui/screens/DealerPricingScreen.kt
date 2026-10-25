package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.ai.RepairCostEntry
import com.enginesense.app.vehicle.ai.RepairCostReference
import com.enginesense.app.vehicle.pricing.DealerVehicleOptions
import com.enginesense.app.vehicle.pricing.OdometerBucket
import com.enginesense.app.vehicle.pricing.SimpleBuyPriceCalculator
import com.enginesense.app.vehicle.pricing.parseNonNegativeAmount

/**
 * Dealer Sathi's "Max Buy Price" calculator — the simplified flow that replaced the
 * comparable-matching screen from Phase 1/1.1/2. The formula is `Buy price = Selling price -
 * Refurbishment - Margin`. Selling price is a manually-typed field for now; it's meant to become
 * backend-computed once the product owner supplies that logic, so it's labeled accordingly
 * rather than claiming to be the final input shape. The old comparable-matching engine that used
 * to back this screen has been removed entirely, not just unwired.
 *
 * Vehicle/location dropdown data in [DealerVehicleOptions] is placeholder pending the real
 * lists/pricing logic the product owner said they'd provide — none of those fields feed into the
 * calculation yet, they're captured for context only.
 */
@Composable
fun DealerPricingScreen() {
    var make by remember { mutableStateOf<String?>(null) }
    var model by remember { mutableStateOf<String?>(null) }
    var year by remember { mutableStateOf<Int?>(null) }
    var odometerBucket by remember { mutableStateOf<OdometerBucket?>(null) }
    var ownership by remember { mutableStateOf<String?>(null) }
    var stateName by remember { mutableStateOf<String?>(null) }
    var city by remember { mutableStateOf<String?>(null) }

    var engineCondition by remember { mutableStateOf<Int?>(null) }
    var exteriorCondition by remember { mutableStateOf<Int?>(null) }
    var interiorCondition by remember { mutableStateOf<Int?>(null) }

    val selectedRepairs = remember { mutableStateListOf<RepairCostEntry>() }
    var refurbCategoryToAdd by remember { mutableStateOf(RepairCostReference.entries.first().category) }
    var manualAdditionalCost by remember { mutableStateOf("") }

    var sellingPrice by remember { mutableStateOf("") }
    var marginPercent by remember { mutableIntStateOf(10) }

    val refurbTotal = SimpleBuyPriceCalculator.totalRefurbishmentInr(selectedRepairs, parseNonNegativeAmount(manualAdditionalCost))
    val result = SimpleBuyPriceCalculator.calculate(parseNonNegativeAmount(sellingPrice), refurbTotal, marginPercent)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Max Buy Price", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text("Dealer Sathi", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(18.dp))

        Text("Vehicle", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))
        TwoDropdowns(
            "Make", make, DealerVehicleOptions.makes, { it }, { make = it },
            "Model", model, DealerVehicleOptions.models, { it }, { model = it }
        )
        Spacer(Modifier.height(10.dp))
        TwoDropdowns(
            "Year", year, DealerVehicleOptions.years, { it.toString() }, { year = it },
            "Odometer", odometerBucket, OdometerBucket.entries, { it.label }, { odometerBucket = it }
        )
        Spacer(Modifier.height(10.dp))
        DropdownField("Ownership", ownership, DealerVehicleOptions.ownershipOptions, { it }, { ownership = it })

        Spacer(Modifier.height(20.dp))
        Text("Location", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))
        TwoDropdowns(
            "State", stateName, DealerVehicleOptions.states, { it }, {
                stateName = it
                city = null // previous city may not belong to the newly-picked state
            },
            "City", city, DealerVehicleOptions.citiesFor(stateName ?: ""), { it }, { city = it }
        )

        Spacer(Modifier.height(20.dp))
        Text("Under Car Condition", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))
        DropdownField("Engine condition", engineCondition, DealerVehicleOptions.conditionRatingOptions, { DealerVehicleOptions.conditionRatingLabel(it) }) { engineCondition = it }
        Spacer(Modifier.height(10.dp))
        TwoDropdowns(
            "Exterior rating", exteriorCondition, DealerVehicleOptions.conditionRatingOptions, { DealerVehicleOptions.conditionRatingLabel(it) }, { exteriorCondition = it },
            "Interior rating", interiorCondition, DealerVehicleOptions.conditionRatingOptions, { DealerVehicleOptions.conditionRatingLabel(it) }, { interiorCondition = it }
        )

        Spacer(Modifier.height(20.dp))
        Text("Refurbishment", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Add a repair item", style = GetAutoSureType.Caption)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownField(label = null, selected = refurbCategoryToAdd, options = RepairCostReference.entries.map { it.category }, displayText = { it }) {
                            refurbCategoryToAdd = it
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val entry = RepairCostReference.entries.firstOrNull { it.category == refurbCategoryToAdd }
                        if (entry != null && selectedRepairs.none { it.category == entry.category }) {
                            selectedRepairs.add(entry)
                        }
                    }) { Text("Add") }
                }

                if (selectedRepairs.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    selectedRepairs.forEach { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().background(GetAutoSureColors.SurfaceRaised, RoundedCornerShape(8.dp)).padding(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.category, style = GetAutoSureType.Body)
                                Text("₹${entry.minInr}–${entry.maxInr} indicative", style = GetAutoSureType.Caption)
                            }
                            Text(
                                "Remove",
                                style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention),
                                modifier = Modifier.clickable { selectedRepairs.remove(entry) }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = manualAdditionalCost,
                    onValueChange = { manualAdditionalCost = it },
                    label = { Text("Additional cost (manual)") },
                    placeholder = { Text("e.g. 3000") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    "Auto-fill from OBD scan — coming soon",
                    style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextSecondary)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Pricing", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                OutlinedTextField(
                    value = sellingPrice,
                    onValueChange = { sellingPrice = it },
                    label = { Text("Selling price") },
                    placeholder = { Text("e.g. 650000") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Auto-calculated from backend pricing logic — coming soon. Enter manually for now.",
                    style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextSecondary)
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Margin", style = GetAutoSureType.Caption, modifier = Modifier.weight(1f))
                    Text("₹${"%,.0f".format(result.marginAmountInr)}", style = GetAutoSureType.Caption)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().background(GetAutoSureColors.SurfaceRaised, RoundedCornerShape(10.dp)).padding(6.dp)
                ) {
                    OutlinedButton(onClick = { marginPercent = (marginPercent - 1).coerceAtLeast(0) }) { Text("−") }
                    Text(
                        "$marginPercent%",
                        style = GetAutoSureType.Body.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    OutlinedButton(onClick = { marginPercent = (marginPercent + 1).coerceAtMost(50) }) { Text("+") }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        ResultCard(result, refurbTotal)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun <T1, T2> TwoDropdowns(
    label1: String, selected1: T1?, options1: List<T1>, displayText1: (T1) -> String, onSelect1: (T1) -> Unit,
    label2: String, selected2: T2?, options2: List<T2>, displayText2: (T2) -> String, onSelect2: (T2) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.weight(1f)) { DropdownField(label1, selected1, options1, displayText1, onSelect1) }
        Box(modifier = Modifier.weight(1f)) { DropdownField(label2, selected2, options2, displayText2, onSelect2) }
    }
}

@Composable
private fun <T> DropdownField(label: String?, selected: T?, options: List<T>, displayText: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        label?.let {
            Text(it, style = GetAutoSureType.Caption)
            Spacer(Modifier.height(4.dp))
        }
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        selected?.let(displayText) ?: "Select",
                        style = GetAutoSureType.Body.copy(color = if (selected != null) GetAutoSureColors.TextPrimary else GetAutoSureColors.TextSecondary),
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = GetAutoSureColors.TextSecondary)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(displayText(option)) }, onClick = { onSelect(option); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: com.enginesense.app.vehicle.pricing.SimpleBuyPriceResult, refurbTotal: Double) {
    val backgroundColor = if (result.isViable) GetAutoSureColors.Surface else GetAutoSureColors.Attention.copy(alpha = 0.15f)
    Card(colors = CardDefaults.cardColors(containerColor = backgroundColor), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (result.isViable) {
                Text("Max buy price", style = GetAutoSureType.Caption)
                Text(
                    "₹${"%,.0f".format(result.buyPriceInr)}",
                    style = GetAutoSureType.ScreenTitle.copy(color = GetAutoSureColors.Good, fontWeight = FontWeight.Bold)
                )
            } else {
                Text("NOT VIABLE", style = GetAutoSureType.ScreenTitle.copy(color = GetAutoSureColors.Attention, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(4.dp))
                Text(
                    "Refurbishment and margin exceed the selling price. Raw result: ₹${"%,.0f".format(result.rawBuyPriceInr)}",
                    style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "BP = SP (₹${"%,.0f".format(result.sellingPriceInr)}) − Refurb (₹${"%,.0f".format(refurbTotal)}) − Margin (₹${"%,.0f".format(result.marginAmountInr)})",
                style = GetAutoSureType.Caption
            )
        }
    }
}
