package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ui.theme.GetAutoSureType
import kotlinx.coroutines.launch

@Composable
fun WorkshopScreen(viewModel: GetAutoSureViewModel) {
    val result by viewModel.latestResult.collectAsState()
    var suggestions by remember { mutableStateOf<List<String>?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Find a workshop", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "Not connected to a real workshop network yet — this is a placeholder for that integration.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(20.dp))

        Button(onClick = {
            scope.launch {
                val findings = result?.findings.orEmpty()
                suggestions = viewModel.workshopService.findNearbyWorkshops(findings, locationHint = null).map { it.name }
            }
        }) { Text("Search nearby") }

        Spacer(Modifier.height(16.dp))
        suggestions?.forEach { name -> Text("• $name", style = GetAutoSureType.Body) }
    }
}

@Composable
fun VehicleInfoScreen(viewModel: GetAutoSureViewModel) {
    var regNumber by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Vehicle info & challan", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "Not connected to a real RC/challan/insurance provider yet — this is a placeholder for that integration.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = regNumber,
            onValueChange = { regNumber = it },
            label = { Text("Registration number") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            scope.launch {
                val info = viewModel.vehicleInfoService.lookup(regNumber)
                resultText = info.sourceNote
            }
        }) { Text("Look up") }

        resultText?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = GetAutoSureType.Body)
        }
    }
}

@Composable
fun MarketplaceScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Marketplace", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "Buy, sell or value your car — not connected to a real listings/valuation " +
                "service yet. This is a placeholder for that integration.",
            style = GetAutoSureType.Caption
        )
    }
}

@Composable
fun WarrantyScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Warranty & RSA", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your manufacturer warranty coverage and roadside assistance (RSA) status and " +
                "contact details will show here — not connected to a real OEM/RSA provider yet. " +
                "This is a placeholder for that integration.",
            style = GetAutoSureType.Caption
        )
    }
}
