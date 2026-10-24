package com.enginesense.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enginesense.app.ui.theme.GetAutoSureType

private val GUIDES_AND_TOOLS_ITEMS = listOf(
    DiscoverMoreItem(
        "warningLightGuide", "Dashboard Warning Light Guide",
        "What that icon on your dash actually means", Icons.Filled.WarningAmber
    ),
    // External site, not a native screen — this is reference/video content, not something
    // that needs OBD data, so it's fine to live on the website rather than duplicate it in-app.
    DiscoverMoreItem(
        route = "", title = "Car Care Learning Hub", subtitle = "Guides on common problems, maintenance & more",
        icon = Icons.Filled.OndemandVideo, externalUrl = "https://getautosure.com/learn"
    )
)

/** Decode your dashboard, learn your car — the quick-reference light guide and the deeper learning hub, in one place. */
@Composable
fun GuidesAndToolsScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Guides & tools", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text("Decode your dashboard, learn your car", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(16.dp))
        GUIDES_AND_TOOLS_ITEMS.forEach { item ->
            DiscoverMoreRow(item) {
                val url = item.externalUrl
                if (url != null) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                else onNavigate(item.route)
            }
        }
    }
}
