package com.enginesense.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.storage.AppMode
import com.enginesense.app.ui.theme.GetAutoSureColors

data class ModeTab(val route: String, val label: String, val icon: ImageVector)

/**
 * The bottom tabs for a mode. No Scan tab and no AI Mechanic tab: Scan is the big pulsing button
 * on the home page, and AI Mechanic is the floating button on every screen.
 */
data class ModeTabs(val tabs: List<ModeTab>) {
    val tabRoutes: Set<String> get() = tabs.map { it.route }.toSet()

    companion object {
        private val PROFILE = ModeTab("profile", "Profile", Icons.Filled.Person)

        fun forMode(mode: AppMode): ModeTabs = when (mode) {
            AppMode.OWN_CAR -> ModeTabs(listOf(
                ModeTab("home", "Garage", Icons.Filled.Home), ModeTab("trips", "Trips", Icons.Filled.Route),
                ModeTab("wallet", "Wallet", Icons.Filled.AccountBalanceWallet), ModeTab("reports", "Reports", Icons.Filled.Description), PROFILE
            ))
            AppMode.BUYING -> ModeTabs(listOf(ModeTab("home", "Home", Icons.Filled.Home), ModeTab("reports", "Reports", Icons.Filled.Description), PROFILE))
            AppMode.DEALER -> ModeTabs(listOf(ModeTab("home", "Home", Icons.Filled.Home), ModeTab("dealerReports", "Reports", Icons.Filled.Description), PROFILE))
        }
    }
}

@Composable
fun ModeBottomBar(tabs: ModeTabs, currentRoute: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(GetAutoSureColors.Surface).navigationBarsPadding()) {
        HorizontalDivider(color = GetAutoSureColors.Border)
        Row(
            Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.tabs.forEach { TabItem(it, it.route == currentRoute, onSelect, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TabItem(tab: ModeTab, selected: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    val color = if (selected) GetAutoSureColors.Accent else GetAutoSureColors.TextSecondary
    Column(
        modifier.height(56.dp).clip(RoundedCornerShape(28.dp)).clickable(role = Role.Tab) { onSelect(tab.route) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(tab.label, color = color, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}
