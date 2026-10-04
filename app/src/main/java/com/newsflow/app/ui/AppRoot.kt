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
import com.newsflow.app.ui.screens.UpgradeScreen
import com.newsflow.app.billing.BillingService
import com.newsflow.app.billing.PremiumStore
import com.newsflow.app.data.MockData
import com.newsflow.app.data.NewsItem
import com.newsflow.app.data.RemoteLessonService
import com.newsflow.app.data.SegmentRepository
import com.newsflow.app.data.SettingsService
import com.newsflow.app.data.YouTubeService
import androidx.compose.ui.platform.LocalContext
import com.android.billingclient.api.ProductDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val settings = remember { SettingsService(context) }
    val premiumStore = remember { PremiumStore(context) }
    val billing = remember { BillingService(context) }

    var targetLang by remember { mutableStateOf(settings.getTargetLanguage()) }
    var selectedIndex by remember { mutableStateOf(0) }

    // ---------- 会员 / 计费状态 ----------
    // 先用本地缓存决定要不要显示广告（离线也立刻正确），随后被 Play 的真实查询覆盖
    var isPremium by remember { mutableStateOf(premiumStore.isPremium()) }
    var showUpgrade by remember { mutableStateOf(false) }
    var billingConnected by remember { mutableStateOf(false) }
    var billingProducts by remember { mutableStateOf<List<ProductDetails>>(emptyList()) }
    var upgradeMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        billing.onEntitlementChanged = { isPremium = it }
        billing.onProductsLoaded = { billingProducts = it }
        billing.onPurchaseFinished = { success, msg ->
            if (success) {
                upgradeMessage = null
                showUpgrade = false        // 购买成功，回到原来的界面
            } else if (msg != null) {
                upgradeMessage = msg
            }
        }
        billing.start { ok ->
            billingConnected = ok
            isPremium = premiumStore.isPremium()
        }
    }

    DisposableEffect(Unit) {
        onDispose { billing.end() }
    }

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

    when {
        // 付费页（全屏）
        showUpgrade -> UpgradeScreen(
            isPremium = isPremium,
            products = billingProducts,
            billingConnected = billingConnected,
            message = upgradeMessage,
            onBack = {
                upgradeMessage = null
                showUpgrade = false
            },
            onSubscribe = { productId ->
                upgradeMessage = null
                val activity = context as? android.app.Activity
                if (activity != null) {
                    billing.launchPurchase(activity, productId)
                } else {
                    upgradeMessage = "Cannot start purchase from this context."
                }
            },
            onRestore = {
                upgradeMessage = null
                billing.refreshEntitlement { restored ->
                    if (!restored) upgradeMessage = "No active subscription found."
                }
            },
        )

        // 播放页（全屏）
        selectedNews != null -> PlayerScreen(
            newsItem = selectedNews!!,
            targetLang = targetLang,
            onBack = { selectedNews = null },
            showAds = !isPremium,
        )

        else -> Scaffold(
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
                        showAds = !isPremium,
                    )
                    1 -> SettingsScreen(
                        currentLang = targetLang,
                        onLanguageChanged = { lang ->
                            targetLang = lang
                            settings.setTargetLanguage(lang)
                        },
                        settings = settings,
                        isPremium = isPremium,
                        onOpenUpgrade = { showUpgrade = true },
                        onDebugSetPremium = { premium ->
                            premiumStore.debugOverride(premium)
                            isPremium = premium
                        },
                    )
                }
            }
        }
    }
}
