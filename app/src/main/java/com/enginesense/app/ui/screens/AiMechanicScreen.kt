package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ui.components.MarkdownText
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.ai.AiBackendConfig
import com.enginesense.app.vehicle.ai.ChatMessage

private val SUGGESTED_QUESTIONS = listOf(
    "Is my car safe to drive?",
    "What does this issue mean?",
    "How urgent is this?",
    "What should I ask the mechanic?",
    "How much could this cost?"
)

@Composable
fun AiMechanicScreen(viewModel: GetAutoSureViewModel, advancedMode: Boolean = false, onNavigate: (String) -> Unit = {}) {
    val messages by viewModel.chatMessages.collectAsState()
    val result by viewModel.latestResult.collectAsState()
    val loading by viewModel.aiMechanicLoading.collectAsState()
    var question by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI Mechanic", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text(
            if (AiBackendConfig.isConfigured) {
                "Answers from a real AI model, grounded in your scan data when available."
            } else {
                "Rule-based for now, not a live AI model — answers only come from your scan data."
            },
            style = GetAutoSureType.Caption
        )
        if (advancedMode) {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.AccentGold.copy(alpha = 0.12f))) {
                Text(
                    "Preview — once Premium billing is live, Advanced AI Mechanic will reason over your full scan history and trends, not just this scan. For now it answers the same way as Free.",
                    style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.AccentGold),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        if (result == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "No scan yet — I can still answer general car questions. Run a scan for answers tailored to your car.",
                style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextSecondary)
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message -> ChatBubble(message) }
            if (loading) {
                item { ThinkingBubble() }
            }
        }

        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SUGGESTED_QUESTIONS) { suggestion ->
                SuggestionChip(
                    onClick = { if (!loading) viewModel.askAiMechanic(suggestion, context) },
                    label = { Text(suggestion) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.weight(1f),
                enabled = !loading,
                placeholder = { Text("Ask about your car…") }
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = !loading && question.isNotBlank(),
                onClick = {
                    viewModel.askAiMechanic(question, context)
                    question = ""
                }
            ) { Text("Send") }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface)) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = GetAutoSureColors.Accent)
                Spacer(Modifier.width(8.dp))
                Text("Thinking…", style = GetAutoSureType.Body)
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.fromUser) GetAutoSureColors.Accent.copy(alpha = 0.2f) else GetAutoSureColors.Surface
            )
        ) {
            if (message.fromUser) {
                // The user's own typed text is never Markdown — parsing it as such could
                // misrender something they typed literally, e.g. "what about *this*".
                Text(message.text, modifier = Modifier.padding(12.dp), style = GetAutoSureType.Body)
            } else {
                MarkdownText(message.text, modifier = Modifier.padding(12.dp), baseStyle = GetAutoSureType.Body)
            }
        }
    }
}
