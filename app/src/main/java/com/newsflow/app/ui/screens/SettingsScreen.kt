package com.newsflow.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newsflow.app.BuildConfig
import com.newsflow.app.data.AppLanguages
import com.newsflow.app.data.SettingsService
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentLang: String,
    onLanguageChanged: (String) -> Unit,
    settings: SettingsService,
    isPremium: Boolean = false,
    onOpenUpgrade: () -> Unit = {},
    onDebugSetPremium: (Boolean) -> Unit = {},
) {
    var selectedLang by remember { mutableStateOf(currentLang) }
    var speed by remember { mutableStateOf(settings.getPlaybackSpeed()) }
    var showTranscript by remember { mutableStateOf(settings.getShowTranscript()) }
    var wordPopup by remember { mutableStateOf(settings.getWordPopupEnabled()) }
    var showSpeedBar by remember { mutableStateOf(settings.getShowSpeedBar()) }
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ---------- Premium ----------
            SectionHeader("Premium")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = onOpenUpgrade,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isPremium) Icons.Default.CheckCircle else Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isPremium) "Premium — ads removed" else "Remove ads",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Text(
                            if (isPremium) "Thank you for supporting NewsFlow"
                            else "From \$0.99/month · cancel anytime",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            // 调试开关：仅 debug 构建可见，用来验证「会员不显示广告」
            if (BuildConfig.DEBUG) {
                SwitchRow(
                    title = "Debug: simulate Premium",
                    subtitle = "Debug builds only — hides all ads",
                    checked = isPremium,
                    onCheckedChange = onDebugSetPremium,
                    icon = Icons.Default.BugReport,
                )
            }

            Divider()

            // ---------- Playback ----------
            SectionHeader("Playback")

            ListItem(
                headlineContent = { Text("Default Speed") },
                supportingContent = { Text("Applied whenever a lesson opens") },
                leadingContent = { Icon(Icons.Default.Speed, contentDescription = null) },
            )
            FlowRow(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                speeds.forEach { s ->
                    FilterChip(
                        label = { Text("${s}x") },
                        selected = kotlin.math.abs(speed - s) < 0.01f,
                        onClick = {
                            speed = s
                            settings.setPlaybackSpeed(s)
                        },
                    )
                }
            }

            SwitchRow(
                title = "Show speed chips in player",
                subtitle = "Off keeps the player clean; change speed here instead",
                checked = showSpeedBar,
                onCheckedChange = {
                    showSpeedBar = it
                    settings.setShowSpeedBar(it)
                },
                icon = Icons.Default.Tune,
            )

            Divider()

            // ---------- Display ----------
            SectionHeader("Display")

            SwitchRow(
                title = "Show transcript",
                subtitle = "Hidden by default so you listen first",
                checked = showTranscript,
                onCheckedChange = {
                    showTranscript = it
                    settings.setShowTranscript(it)
                },
                icon = Icons.Default.Subtitles,
            )

            SwitchRow(
                title = "Tap a word to translate",
                subtitle = "Shows a small card under the transcript — no popups",
                checked = wordPopup,
                onCheckedChange = {
                    wordPopup = it
                    settings.setWordPopupEnabled(it)
                },
                icon = Icons.Default.Translate,
            )

            Divider()

            // ---------- Translation ----------
            SectionHeader("Translation Language")
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppLanguages.languages.forEach { lang ->
                    FilterChip(
                        label = { Text("${lang.name} (${lang.nativeName})") },
                        selected = selectedLang == lang.code,
                        onClick = {
                            selectedLang = lang.code
                            onLanguageChanged(lang.code)
                        },
                    )
                }
            }

            Divider()

            // ---------- About ----------
            SectionHeader("About")
            ListItem(
                headlineContent = { Text("App Version") },
                supportingContent = { Text("1.0.0 (Build 1)") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
            )
            ListItem(
                headlineContent = { Text("Content Source") },
                supportingContent = { Text("CNN 10 — CNN's daily student news program") },
                leadingContent = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
            )

            Divider()

            // ---------- Legal ----------
            SectionHeader("Legal")
            ListItem(
                headlineContent = { Text("Privacy Policy") },
                leadingContent = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            ListItem(
                headlineContent = { Text("Terms of Service") },
                leadingContent = { Icon(Icons.Default.Description, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            ListItem(
                headlineContent = { Text("Content Attribution") },
                supportingContent = { Text("Videos embedded from YouTube; all rights belong to CNN") },
                leadingContent = { Icon(Icons.Default.Copyright, contentDescription = null) },
            )

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, fontSize = 12.sp) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
