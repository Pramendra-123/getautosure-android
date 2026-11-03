package com.enginesense.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enginesense.app.consultation.ConsultationPricing
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType

/** False when no mechanic has a free time to book, so the card doesn't send people to a dead end. Provided at the app root. */
val LocalMechanicAvailable = compositionLocalOf { true }

/**
 * The hand-off to a real mechanic, shown where a customer most needs one: a serious fault in a
 * scan, a used car worth walking away from, or a question the AI can't settle. Shows nothing
 * while no mechanic can be booked.
 */
@Composable
fun TalkToMechanicCard(headline: String, onClick: () -> Unit) {
    if (!LocalMechanicAvailable.current) return
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Accent.copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(headline, style = GetAutoSureType.Body)
            Spacer(Modifier.height(4.dp))
            Text(
                "${ConsultationPricing.DURATION_MINUTES}-minute video call with a real mechanic · ₹${ConsultationPricing.PRICE_INR}",
                style = GetAutoSureType.Caption
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Talk to a mechanic") }
        }
    }
}
