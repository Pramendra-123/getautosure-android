package com.enginesense.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.CategoryHealth
import com.enginesense.app.vehicle.CategoryState

/** The one primary indicator on the home screen: a circular ring sized by [score], colored by [ringColor]. */
@Composable
fun HealthScoreRing(score: Int, ringColor: Color, modifier: Modifier = Modifier, diameter: Dp = 150.dp) {
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = (diameter.value / 16).dp.toPx()
            drawArc(
                color = GetAutoSureColors.SurfaceRaised,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(size.width - stroke, size.height - stroke),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * (score / 100f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(size.width - stroke, size.height - stroke),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = GetAutoSureType.ScoreNumber.copy(fontSize = (diameter.value / 2.9f).sp))
            Text("/ 100", style = GetAutoSureType.Caption)
        }
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/** One row in the category breakdown: "Engine · good", colored and labeled, no raw numbers. */
@Composable
fun CategoryRow(health: CategoryHealth, onClick: () -> Unit) {
    val color = GetAutoSureColors.forCategoryState(health.state)
    val label = when (health.state) {
        CategoryState.GOOD -> "Good"
        CategoryState.WATCH -> "Watch"
        CategoryState.ATTENTION -> "Needs attention"
        CategoryState.UNAVAILABLE -> "No data"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GetAutoSureColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color)
        Spacer(Modifier.width(12.dp))
        Text(health.category.displayName, style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
        Text(label, style = GetAutoSureType.Caption.copy(color = color))
    }
}

@Composable
fun SeverityBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, style = GetAutoSureType.Caption.copy(color = color))
    }
}

/** A WHOOP-dashboard-style stat card: label + a real value (or an honest "—" when unavailable). */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, note: String? = null) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GetAutoSureColors.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label.uppercase(), style = GetAutoSureType.Caption)
        Spacer(Modifier.height(4.dp))
        Text(value, style = GetAutoSureType.SectionTitle)
        if (note != null) {
            Text(note, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent))
        }
    }
}

@Composable
fun DemoDataBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GetAutoSureColors.SurfaceRaised)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text("Demo data — for illustration only, not a real vehicle scan.", style = GetAutoSureType.Caption)
    }
}
