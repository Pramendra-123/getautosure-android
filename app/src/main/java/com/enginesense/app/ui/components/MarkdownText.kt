package com.enginesense.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders the small, predictable Markdown subset the AI Mechanic's system prompt asks Gemini to
 * use — `#`/`##`/`###` headers, `**bold**`, `*italic*`, `* `/`- ` bullets, `1. ` numbered lists,
 * `> ` blockquotes, and `---` dividers (see the system prompt in
 * supabase/functions/ai-mechanic/index.ts) — not a general-purpose Markdown engine. A real chat
 * bubble was showing these symbols literally (###, **, >, ---) when the raw reply text was just
 * passed to a plain [Text]; this is deliberately scoped to exactly what we tell Gemini to
 * produce, which we control, rather than pulling in an external Markdown dependency for a
 * vocabulary this small.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier, baseStyle: TextStyle) {
    Column(modifier = modifier) {
        markdown.trim('\n').split("\n").forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                line == "---" -> HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                line.startsWith("### ") -> Text(inlineStyled(line.removePrefix("### ")), style = headerStyle(baseStyle, 1.05f))
                line.startsWith("## ") -> Text(inlineStyled(line.removePrefix("## ")), style = headerStyle(baseStyle, 1.12f))
                line.startsWith("# ") -> Text(inlineStyled(line.removePrefix("# ")), style = headerStyle(baseStyle, 1.2f))
                line.startsWith("> ") -> Text(
                    inlineStyled(line.removePrefix("> ")),
                    style = baseStyle.copy(fontStyle = FontStyle.Italic),
                    modifier = Modifier.padding(start = 8.dp)
                )
                line.startsWith("* ") || line.startsWith("- ") -> ListLine("•", line.drop(2), baseStyle)
                NUMBERED_LIST_PREFIX.matches(line) -> {
                    val number = line.substringBefore(". ")
                    ListLine("$number.", line.substringAfter(". "), baseStyle, markerBold = true)
                }
                else -> Text(inlineStyled(line), style = baseStyle)
            }
        }
    }
}

private val NUMBERED_LIST_PREFIX = Regex("""^\d+\. .+""")

private fun headerStyle(baseStyle: TextStyle, scale: Float): TextStyle =
    baseStyle.copy(fontWeight = FontWeight.Bold, fontSize = (baseStyle.fontSize.value * scale).sp)

@Composable
private fun ListLine(marker: String, text: String, baseStyle: TextStyle, markerBold: Boolean = false) {
    Row(modifier = Modifier.padding(start = 4.dp)) {
        Text("$marker  ", style = if (markerBold) baseStyle.copy(fontWeight = FontWeight.Bold) else baseStyle)
        Text(inlineStyled(text), style = baseStyle)
    }
}

/** Converts `**bold**` and `*italic*` spans within a single line into styled text; everything else passes through unchanged. */
private fun inlineStyled(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end == -1) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
            }
            text.startsWith("*", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end == -1) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
            }
            else -> {
                // Only reached when text[i] isn't '*', so this always finds something after i
                // (or nothing at all) — never loops in place.
                val next = listOf(text.indexOf("**", i), text.indexOf("*", i)).filter { it >= 0 }.minOrNull() ?: text.length
                append(text.substring(i, next))
                i = next
            }
        }
    }
}
