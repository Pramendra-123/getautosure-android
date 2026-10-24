package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType

/** One row in "My Dashboard" — a real free feature, a PRO-badged one, or an external link. */
data class DiscoverMoreItem(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    /** Non-null for a row that opens an outside site/app instead of an in-app screen — [route] is unused for these. */
    val externalUrl: String? = null,
    /** True for a GetAutoSure Pro feature — shown with a lock + PRO badge instead of the usual chevron, per the Free/Premium split. */
    val isPremium: Boolean = false
)

@Composable
fun DiscoverMoreRow(item: DiscoverMoreItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, contentDescription = null, tint = GetAutoSureColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = GetAutoSureType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = GetAutoSureType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (item.isPremium) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(GetAutoSureColors.AccentGold.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("PRO", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.AccentGold, fontSize = 10.sp), maxLines = 1, softWrap = false)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            when {
                item.isPremium -> Icons.Filled.Lock
                item.externalUrl != null -> Icons.Filled.OpenInNew
                else -> Icons.Filled.ChevronRight
            },
            contentDescription = null,
            tint = if (item.isPremium) GetAutoSureColors.Unavailable else GetAutoSureColors.TextSecondary,
            modifier = if (item.externalUrl != null || item.isPremium) Modifier.size(18.dp) else Modifier
        )
    }
}
