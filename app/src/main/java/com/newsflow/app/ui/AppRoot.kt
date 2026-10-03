package com.newsflow.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.newsflow.app.ui.screens.NewsListScreen
import com.newsflow.app.ui.screens.PlayerScreen
import com.newsflow.app.ui.screens.SettingsScreen
import com.newsflow.app.data.MockData
import com.newsflow.app.data.NewsItem
import com.newsflow.app.data.RemoteLessonService
import com.newsflow.app.data.SegmentRepository
import com.newsflow.app.data.SettingsService
import com.newsflow.app.data.YouTubeService
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val settings = remember { SettingsService(context) }
    var targetLang by remember { mutableStateOf(settings.getTargetLanguage()) }
    var selectedIndex by remember { mutableStateOf(0) }

    // News feed: remote sync → bundled assets → live YouTube → mock
    var news by remember { mutableStateOf(MockData.getMockNews()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // 1) Remote sync — keeps content fresh without shipping a new APK
        if (RemoteLessonService.isConfigured()) {
            val remote = RemoteLessonService.sync(context)
            if (remote.isNotEmpty()) {
                news = remote
                isLoading = false
                return@LaunchedEffect
            }
            // Network failed — fall back to whatever was cached last time
            val cached = RemoteLessonService.loadCached(context)
            if (cached.isNotEmpty()) {
                news = cached
                isLoading = false
                return@LaunchedEffect
            }
        }

        // 2) Lessons bundled in assets by tools/transcribe.py
        val bundled = SegmentRepository.loadAll(context)
        if (bundled.isNotEmpty()) {
            news = bundled
            isLoading = false
            return@LaunchedEffect
        }

        // 3) Live YouTube feed, only when an API key is configured
        if (YouTubeService.isConfigured()) {
            val fetched = mutableListOf<NewsItem>()
            for (source in YouTubeService.sourceOrder) {
                fetched += YouTubeService.fetchLatest(source)
            }
            if (fetched.isNotEmpty()) {
                news = fetched
            }
        }
        isLoading = false
    }

    // Navigation state
    var selectedNews by remember { mutableStateOf<NewsItem?>(null) }

    if (selectedNews != null) {
        PlayerScreen(
            newsItem = selectedNews!!,
            targetLang = targetLang,
            onBack = { selectedNews = null },
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(if (selectedIndex == 0) Icons.Filled.Newspaper else Icons.Outlined.Newspaper, contentDescription = "News") },
                        label = { Text("News") },
                        selected = selectedIndex == 0,
                        onClick = { selectedIndex = 0 },
                    )
                    NavigationBarItem(
                        icon = { Icon(if (selectedIndex == 1) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = selectedIndex == 1,
                        onClick = { selectedIndex = 1 },
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedIndex) {
                    0 -> NewsListScreen(
                        news = news,
                        isLoading = isLoading,
                        onOpenNews = { selectedNews = it },
                    )
                    1 -> SettingsScreen(
                        currentLang = targetLang,
                        onLanguageChanged = { lang ->
                            targetLang = lang
                            settings.setTargetLanguage(lang)
                        },
                        settings = settings,
                    )
                }
            }
        }
    }
}
