package com.enginesense.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.enginesense.app.ui.theme.GetAutoSureColors

/** A filled-segment progress bar for a known-length flow (Login through Vehicle Details) — [current] is 1-indexed. */
@Composable
fun StepProgressIndicator(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < current) GetAutoSureColors.Accent else GetAutoSureColors.SurfaceRaised)
            ) {}
        }
    }
}
