package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.enginesense.app.consultation.ConsultationPricing
import com.enginesense.app.consultation.ConsultationViewModel
import com.enginesense.app.consultation.Expert
import com.enginesense.app.consultation.LoadState
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType

/** Header, pricing, and the expert card list — everything here currently comes from [com.enginesense.app.consultation.MockExpertRepository], see that class's doc for why the visible preview banner matters. */
@Composable
fun ExpertListScreen(viewModel: ConsultationViewModel, onSelectExpert: (String) -> Unit, onMyConsultations: () -> Unit) {
    val expertsState by viewModel.experts.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Get Real Expert", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text(
            "Get expert advice for your car, directly from a real automotive professional.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.background(GetAutoSureColors.AccentGold.copy(alpha = 0.15f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    "₹${ConsultationPricing.PRICE_INR} for ${ConsultationPricing.DURATION_MINUTES} minutes",
                    style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.AccentGold)
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = onMyConsultations, modifier = Modifier.fillMaxWidth()) {
            Text("My Consultations")
        }
        Spacer(Modifier.height(20.dp))

        when (val state = expertsState) {
            is LoadState.Loading -> LoadingRow()
            is LoadState.Error -> ErrorRow(state.message, onRetry = { viewModel.loadExperts() })
            is LoadState.Success -> {
                if (state.data.isEmpty()) {
                    Text("No experts available right now — check back soon.", style = GetAutoSureType.Caption)
                } else {
                    Text(
                        "Preview data — these are illustrative profiles for testing, not real experts yet.",
                        style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention)
                    )
                    Spacer(Modifier.height(12.dp))
                    state.data.forEach { expert -> ExpertCard(expert, onClick = { onSelectExpert(expert.id) }) }
                }
            }
        }
    }
}

@Composable
private fun ExpertCard(expert: Expert, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface),
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(GetAutoSureColors.SurfaceRaised),
                    contentAlignment = Alignment.Center
                ) {
                    if (expert.photoUrl != null) {
                        AsyncImage(model = expert.photoUrl, contentDescription = "${expert.name}'s photo", modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = GetAutoSureColors.TextSecondary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(expert.name, style = GetAutoSureType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                    Text(expert.title, style = GetAutoSureType.Caption)
                    if (expert.yearsExperience > 0) {
                        Text("${expert.yearsExperience} years experience", style = GetAutoSureType.Caption)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(expert.bio, style = GetAutoSureType.Caption, maxLines = 3)
            if (expert.specializations.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    expert.specializations.take(3).forEach { spec ->
                        Box(modifier = Modifier.background(GetAutoSureColors.SurfaceRaised, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(spec, style = GetAutoSureType.Caption)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("View Availability") }
        }
    }
}

@Composable
internal fun LoadingRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = GetAutoSureColors.Accent)
    }
}

@Composable
internal fun ErrorRow(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}
