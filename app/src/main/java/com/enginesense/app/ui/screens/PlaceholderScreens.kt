package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enginesense.app.ui.theme.GetAutoSureType

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
