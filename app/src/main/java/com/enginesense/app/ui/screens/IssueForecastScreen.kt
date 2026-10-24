package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.obd.Severity
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.ForecastInsight
import com.enginesense.app.vehicle.IssueForecastAnalyzer

/**
 * Premium's "Issue Forecast" — trend-based prediction from scan history, not a single
 * reading. See [IssueForecastAnalyzer] for the actual (simple, explainable) math; this
 * screen stays honest about needing real history depth rather than faking a trend from one
 * scan — most people testing this on a fresh install will see the "needs more scans" state,
 * which is correct, not a bug.
 */
@Composable
fun IssueForecastScreen(viewModel: GetAutoSureViewModel) {
    val history by viewModel.history.collectAsState()
    val insights = IssueForecastAnalyzer.analyze(history.map { it.snapshot })

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Issue forecast", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text(
            "Trends across your scan history — not just this scan. Needs at least 3 scans with the same reading before it can say anything.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(16.dp))

        if (history.size < 3) {
            Text(
                "You have ${history.size} scan${if (history.size == 1) "" else "s"} so far. Run a few more and check back — there's not enough history yet to spot a trend.",
                style = GetAutoSureType.Body
            )
            return@Column
        }

        if (insights.isEmpty()) {
            Text("Nothing trending in the wrong direction across your scan history. That's good.", style = GetAutoSureType.Body)
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(insights) { insight -> ForecastCard(insight) }
        }
    }
}

@Composable
private fun ForecastCard(insight: ForecastInsight) {
    val color = if (insight.severity == Severity.MODERATE) GetAutoSureColors.Watch else GetAutoSureColors.TextSecondary
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(insight.pid.displayName, style = GetAutoSureType.Body.copy(color = color))
            Spacer(Modifier.height(4.dp))
            Text(insight.message, style = GetAutoSureType.Caption)
        }
    }
}
