package com.enginesense.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ui.screens.ChatBubble
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType

/**
 * What the assistant knows about the screen it was opened from: a short line to show, questions to
 * suggest, and how to frame a question. [aboutUsedCar] makes it answer from the car being checked
 * on the Used Car Check, not the user's own.
 */
data class AiScreenContext(
    val screen: String,
    val suggestions: List<String>,
    val questionPrefix: String = "",
    val aboutUsedCar: Boolean = false
)

/**
 * Screens where the floating button would get in the way: sign-up, a scan in progress, full-screen
 * tools, or the chat itself.
 */
private val NO_ASSISTANT_ROUTES = listOf(
    "splash", "login", "phoneOtp", "enterRegistration", "selectBrand", "selectModel", "addVehicle",
    "scanning", "aiMechanic", "liveData", "performance", "tripMileage", "pdfViewer", "booking"
)

fun showsAssistant(route: String?): Boolean = route != null && NO_ASSISTANT_ROUTES.none { route.startsWith(it) }

/**
 * The context for the screen the user is on. [findingTitle] is the problem the screen is about,
 * when it's one problem's screen.
 */
fun aiContextFor(route: String?, findingTitle: String?): AiScreenContext = when {
    route == null -> AiScreenContext("This screen", listOf("Is my car healthy?"))
    route == "home" -> AiScreenContext("Home", listOf("Is my car healthy?", "What should I fix first?", "Is it safe for a long drive?"))
    route == "connect" -> AiScreenContext("Scan", listOf("How do I connect the scanner?", "Where is the OBD port in my car?", "Can I scan with the engine off?"))
    route.startsWith("issueDetail") || route.startsWith("repairGuidance") -> {
        val t = findingTitle ?: "this problem"
        AiScreenContext(t, listOf("Can I fix \"$t\" myself?", "How much will \"$t\" cost to fix?", "Is it safe to drive with this?"))
    }
    route == "scanResults" || route == "aiReport" || route == "healthBreakdown" || route.startsWith("categoryDetail") ->
        AiScreenContext("Your scan report", listOf("Explain my score", "What should I fix first?", "Is it safe to drive?"))
    route.startsWith("prePurchase") -> AiScreenContext(
        "Used car you're checking",
        listOf("Should I buy this car?", "What should I ask the seller?", "What's a fair price to offer?"),
        questionPrefix = "About the used car I'm checking to buy (not my own car): ",
        aboutUsedCar = true
    )
    route == "milStatus" || route == "warningLightGuide" ->
        AiScreenContext("Warning lights", listOf("What does my warning light mean?", "Is it safe to drive with it on?"))
    route == "understandRepair" -> AiScreenContext("Repair guidance", listOf("What should I fix first?", "Which of these can I do myself?"))
    route == "allSensors" -> AiScreenContext("Sensor readings", listOf("Are my readings normal?", "Which reading should I worry about?"))
    route == "reports" -> AiScreenContext("Reports", listOf("Summarise my last scan", "How has my car changed over time?"))
    else -> AiScreenContext("This screen", listOf("Is my car healthy?", "What should I fix first?"))
}

/** The round AI button that sits in the bottom corner of every screen. */
@Composable
fun AiAssistantButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = GetAutoSureColors.Accent,
        contentColor = GetAutoSureColors.Background
    ) { Icon(Icons.Filled.AutoAwesome, contentDescription = "Ask AI Mechanic") }
}

/**
 * The chat that slides up from the button: what it knows about this screen at the top, the
 * shared AI Mechanic conversation, questions for this screen, and a box to ask anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(
    viewModel: GetAutoSureViewModel,
    screenContext: AiScreenContext,
    onDismiss: () -> Unit,
    onOpenFullChat: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsState()
    val loading by viewModel.aiMechanicLoading.collectAsState()
    val ownResult by viewModel.latestResult.collectAsState()
    val usedCarResult by viewModel.prePurchaseResult.collectAsState()
    val brand by viewModel.vehicleBrand.collectAsState()
    val model by viewModel.vehicleModel.collectAsState()
    val context = LocalContext.current
    var question by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    fun ask(q: String) {
        if (q.isBlank() || loading) return
        viewModel.askAiMechanic(screenContext.questionPrefix + q, context, aboutUsedCar = screenContext.aboutUsedCar)
        question = ""
    }
    LaunchedEffect(messages.size, loading) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GetAutoSureColors.Background
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(horizontal = 16.dp).imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = GetAutoSureColors.Accent)
                Spacer(Modifier.width(8.dp))
                Text("AI Mechanic", style = GetAutoSureType.SectionTitle, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenFullChat) { Text("Full chat") }
            }
            // What it already knows, so the user doesn't have to explain.
            // On the Used Car Check it's the seller's car, so the user's own car isn't named.
            val car = if (screenContext.aboutUsedCar) null else listOfNotNull(brand, model.takeIf { it.isNotBlank() }).joinToString(" ").ifBlank { null }
            val scan = (if (screenContext.aboutUsedCar) usedCarResult else ownResult)?.health?.score?.let { "scan $it/100" }
            Text(
                "Knows: " + listOfNotNull(screenContext.screen, car, scan).joinToString(" · "),
                style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent)
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (messages.isEmpty()) {
                    item { Text("Ask me anything about what's on this screen.", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.TextSecondary)) }
                }
                items(messages) { ChatBubble(it) }
                if (loading) item { Text("Thinking…", style = GetAutoSureType.Caption) }
            }

            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(screenContext.suggestions) { s -> SuggestionChip(onClick = { ask(s) }, label = { Text(s) }) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = { Text("Ask about this…") },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { ask(question) }, enabled = !loading && question.isNotBlank()) { Text("Send") }
            }
        }
    }
}
