package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.storage.AppLanguage
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType

/** First launch, before "Who are you?": each language in its own script so people find theirs at a glance. */
@Composable
fun ChooseLanguageScreen(initial: AppLanguage, onContinue: (AppLanguage) -> Unit) {
    var picked by remember { mutableStateOf(initial) }
    Column(
        Modifier.fillMaxSize().background(GetAutoSureColors.Background).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Choose your language", style = GetAutoSureType.ScreenTitle)
            Text("Fault explanations, AI Mechanic replies and reports come in this language. You can change it anytime in Profile.", style = GetAutoSureType.Caption)
        }
        Spacer(Modifier.height(4.dp))
        AppLanguage.entries.forEach { lang -> LanguageRow(lang, selected = lang == picked) { picked = lang } }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onContinue(picked) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** Profile → Language. Says plainly what changes, so nobody expects the menus to switch too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(current: AppLanguage, onDismiss: () -> Unit, onSave: (AppLanguage) -> Unit) {
    var picked by remember { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = GetAutoSureColors.Background) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("Language", style = GetAutoSureType.SectionTitle)
            AppLanguage.entries.forEach { lang -> LanguageRow(lang, selected = lang == picked) { picked = lang } }
            if (picked != AppLanguage.ENGLISH) {
                Text(
                    "In ${picked.englishName}: fault explanations, AI Mechanic replies and your reports. Menus and buttons stay in English for now.",
                    color = GetAutoSureColors.Accent, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GetAutoSureColors.AccentSoft).padding(10.dp)
                )
            }
            Button(onClick = { onSave(picked) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Save", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun LanguageRow(lang: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) GetAutoSureColors.AccentSoft else GetAutoSureColors.Surface)
            .border(1.5.dp, if (selected) GetAutoSureColors.AccentBright else GetAutoSureColors.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(lang.nativeName, color = GetAutoSureColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (lang != AppLanguage.ENGLISH) Text(lang.englishName, style = GetAutoSureType.Caption.copy(fontSize = 12.sp))
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(Color.Transparent)
                .border(if (selected) 6.dp else 2.dp, if (selected) GetAutoSureColors.AccentBright else GetAutoSureColors.Border, CircleShape)
        )
    }
}
