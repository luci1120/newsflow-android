package com.newsflowdictation.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.newsflowdictation.app.data.*
import com.newsflowdictation.app.ads.AdIds
import com.newsflowdictation.app.ads.BannerAd
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    newsItem: NewsItem,
    targetLang: String,
    onBack: () -> Unit,
    showAds: Boolean = true,
) {
    val context = LocalContext.current
    val speechService = remember { SpeechService(context) }
    val settings = remember { SettingsService(context) }

    // 系统返回键回到列表，而不是退出 App
    BackHandler { onBack() }

    var currentSegIdx by remember { mutableStateOf(0) }
    var playbackSpeed by remember { mutableStateOf(settings.getPlaybackSpeed()) }
    var practiceMode by remember { mutableStateOf(PracticeMode.TYPE) }
    var typedText by remember { mutableStateOf("") }
    var recognizedText by remember { mutableStateOf("") }
    var comparison by remember { mutableStateOf<SpeechComparison?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var popupWord by remember { mutableStateOf<WordEntry?>(null) }
    var popupLoading by remember { mutableStateOf(false) }
    val completedSegments = remember { mutableStateOf(setOf<Int>()) }
    val scope = rememberCoroutineScope()
    var playRequest by remember { mutableStateOf<SegmentPlayRequest?>(null) }
    var playToken by remember { mutableStateOf(0) }

    // Display prefs — transcript is hidden by default so the learner listens first
    var showTranscript by remember { mutableStateOf(settings.getShowTranscript()) }
    val showSpeedBar = remember { settings.getShowSpeedBar() }
    val wordPopupEnabled = remember { settings.getWordPopupEnabled() }

    // Speech-recognition state
    var speechError by remember { mutableStateOf<String?>(null) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        speechError = if (granted) null
        else "Microphone permission denied. Enable it in system settings."
    }

    val segments = newsItem.segments
    val currentSegment = if (segments.isNotEmpty()) segments[currentSegIdx] else null
    val currentPassed = completedSegments.value.contains(currentSegIdx)

    DisposableEffect(Unit) {
        onDispose {
            speechService.shutdown()
        }
    }

    fun playSegment(idx: Int) {
        if (idx < 0 || idx >= segments.size) return
        currentSegIdx = idx
        comparison = null
        recognizedText = ""
        typedText = ""
        playToken += 1
        playRequest = SegmentPlayRequest(
            start = segments[idx].startSec,
            end = segments[idx].endSec,
            token = playToken,
        )
    }

    fun replaySegment() {
        val seg = segments.getOrNull(currentSegIdx) ?: return
        playToken += 1
        playRequest = SegmentPlayRequest(
            start = seg.startSec,
            end = seg.endSec,
            token = playToken,
        )
    }

    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        settings.setPlaybackSpeed(speed)
    }

    fun checkAnswer() {
        val seg = currentSegment
        comparison = if (practiceMode == PracticeMode.TYPE) {
            // Positional grading: each word position accepts multiple spellings
            SpeechComparison.gradePositional(
                answerKey = seg?.answerKey ?: emptyList(),
                typed = typedText,
            )
        } else {
            SpeechComparison.compare(seg?.transcript ?: "", recognizedText)
        }
        if ((comparison?.score ?: 0.0) >= 0.7) {
            completedSegments.value = completedSegments.value + currentSegIdx
            // Mark whole news item complete when every segment is passed
            if (completedSegments.value.size >= segments.size) {
                settings.markNewsCompleted(newsItem.id)
            }
        }
    }

    fun onWordTap(word: String) {
        popupLoading = true
        popupWord = null
        scope.launch {
            val entry = TranslationService.translateWord(word, targetLang)
            popupWord = entry ?: WordEntry(
                word = word.lowercase().replace(Regex("[^a-z]"), ""),
                phonetic = "",
                translations = mapOf(targetLang to "(no translation found)"),
                exampleSentences = emptyList(),
            )
            popupLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(newsItem.sourceLabel, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Quick transcript toggle; the default lives in Settings
                    IconButton(onClick = { showTranscript = !showTranscript }) {
                        Icon(
                            if (showTranscript) Icons.Default.Subtitles
                            else Icons.Default.SubtitlesOff,
                            contentDescription = if (showTranscript) "Hide transcript"
                            else "Show transcript",
                            tint = if (showTranscript) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            // AdMob 横幅固定在屏幕底部（加载失败时高度为 0，不留白）。会员不显示。
            if (showAds) {
                BannerAd(adUnitId = AdIds.bannerPlayer)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // YouTube player via WebView
            YouTubePlayer(
                videoId = newsItem.youtubeVideoId,
                speed = playbackSpeed,
                playRequest = playRequest,
                initialStart = segments.firstOrNull()?.startSec ?: 0.0,
                initialEnd = segments.firstOrNull()?.endSec ?: 0.0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )

            // Speed control — hidden by default, change it in Settings instead
            if (showSpeedBar) {
                SpeedBar(
                    currentSpeed = playbackSpeed,
                    onSpeedChange = { setSpeed(it) },
                )
            }

            // Segment timeline
            if (segments.isNotEmpty()) {
                SegmentTimeline(
                    segments = segments,
                    currentIdx = currentSegIdx,
                    completed = completedSegments.value,
                    onSegmentClick = { playSegment(it) },
                )
            }

            // Transcript — off by default, toggle with the icon in the top bar
            if (showTranscript && currentSegment != null && currentSegment.transcript.isNotEmpty()) {
                TranscriptArea(
                    transcript = currentSegment.transcript,
                    tappable = wordPopupEnabled,
                    onWordTap = { if (wordPopupEnabled) onWordTap(it) },
                )
            }

            // Inline word card — replaces the old modal dialog, no popup
            if (wordPopupEnabled && (popupWord != null || popupLoading)) {
                InlineWordCard(
                    entry = popupWord,
                    loading = popupLoading,
                    targetLang = targetLang,
                    onPronounce = { popupWord?.let { speechService.speak(it.word) } },
                    onDismiss = {
                        popupWord = null
                        popupLoading = false
                    },
                )
            }

            // Practice area
            PracticeArea(
                mode = practiceMode,
                typedText = typedText,
                recognizedText = recognizedText,
                isListening = isListening,
                comparison = comparison,
                onModeChange = { practiceMode = it },
                onTypedChange = {
                    typedText = it
                    if (comparison != null) comparison = null
                },
                onStartSpeaking = {
                    speechError = null
                    if (!hasMicPermission) {
                        // First tap asks for the microphone; the learner taps again after granting
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    } else if (!speechService.isSpeechAvailable()) {
                        speechError = "No speech recognition service on this device. " +
                            "Install the Google app, or test on a real phone."
                    } else {
                        isListening = true
                        scope.launch {
                            val outcome = speechService.startListening { partial ->
                                recognizedText = partial
                            }
                            if (outcome.text.isNotBlank()) {
                                recognizedText = outcome.text
                            }
                            if (outcome.error != null) {
                                speechError = outcome.error
                            }
                            isListening = false
                        }
                    }
                },
                onCheck = { checkAnswer() },
                onReplay = { replaySegment() },
                onPrev = { playSegment(currentSegIdx - 1) },
                onNext = { playSegment(currentSegIdx + 1) },
                showPrev = currentSegIdx > 0,
                showNext = currentSegIdx < segments.size - 1,
                canAdvance = currentPassed,
                allDone = completedSegments.value.size >= segments.size,
                speechError = speechError,
            )
        }
    }
}

/** A request to play one specific segment [startSec, endSec]. */
private data class SegmentPlayRequest(
    val start: Double,
    val end: Double,
    val token: Int,
)

@Composable
private fun YouTubePlayer(
    videoId: String,
    speed: Float,
    playRequest: SegmentPlayRequest?,
    initialStart: Double,
    initialEnd: Double,
    modifier: Modifier = Modifier,
) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // Apply speed change
    LaunchedEffect(speed) {
        webViewRef.value?.evaluateJavascript("setSpeed($speed);", null)
    }

    // Play the requested segment (re-triggers on every token change)
    LaunchedEffect(playRequest) {
        playRequest?.let { req ->
            webViewRef.value?.evaluateJavascript(
                "playSegment(${req.start}, ${req.end});",
                null,
            )
        }
    }

    AndroidView(
        factory = { ctx ->
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler(
                    "/assets/",
                    WebViewAssetLoader.AssetsPathHandler(ctx),
                )
                .build()

            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                    }
                }
                webChromeClient = WebChromeClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false

                // Mobile-only player: suppress every desktop-style affordance that
                // would otherwise surface a floating menu over the video.
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.saveFormData = false
                isLongClickable = false
                isHapticFeedbackEnabled = false
                setOnLongClickListener { true }   // swallow long-press entirely

                // Served from https://appassets.androidplatform.net → valid https origin.
                // Initial segment bounds let the page auto-play the first segment on ready.
                loadUrl(
                    "https://appassets.androidplatform.net/assets/youtube_player.html" +
                        "?videoId=$videoId&start=$initialStart&end=$initialEnd",
                )
                webViewRef.value = this
            }
        },
        update = { webViewRef.value = it },
        modifier = modifier,
    )
}

@Composable
private fun SpeedBar(currentSpeed: Float, onSpeedChange: (Float) -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Speed, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Speed", fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(speeds) { _, speed ->
                FilterChip(
                    label = { Text("${speed}x") },
                    selected = kotlin.math.abs(currentSpeed - speed) < 0.01f,
                    onClick = { onSpeedChange(speed) },
                )
            }
        }
    }
}

@Composable
private fun SegmentTimeline(
    segments: List<NewsSegment>,
    currentIdx: Int,
    completed: Set<Int>,
    onSegmentClick: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(segments) { idx, _ ->
            val isCurrent = idx == currentIdx
            val isDone = idx in completed
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    isDone -> Color(0xFFE8F5E9)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                border = if (isCurrent) null
                    else androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
                modifier = Modifier.clickable { onSegmentClick(idx) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isDone && !isCurrent) {
                        Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color(0xFF4CAF50))
                    } else {
                        Icon(
                            if (isCurrent) Icons.Default.PlayArrow else Icons.Default.Circle,
                            null, Modifier.size(14.dp),
                            tint = if (isCurrent) Color.White else Color.Gray,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Seg ${idx + 1}",
                        fontSize = 12.sp,
                        color = if (isCurrent) Color.White else Color.DarkGray,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptArea(
    transcript: String,
    tappable: Boolean,
    onWordTap: (String) -> Unit,
) {
    val words = transcript.split(" ")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFE3F2FD),
    ) {
        FlowRow(
            modifier = Modifier.padding(12.dp),
        ) {
            words.forEach { word ->
                Text(
                    text = "$word ",
                    style = TextStyle(
                        fontSize = 16.sp,
                        // Underline only when tapping does something
                        textDecoration = if (tappable) TextDecoration.Underline
                        else TextDecoration.None,
                        color = if (tappable) MaterialTheme.colorScheme.primary
                        else Color(0xFF1A1A1A),
                    ),
                    modifier = if (tappable) {
                        Modifier.clickable { onWordTap(word) }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
private fun PracticeArea(
    mode: PracticeMode,
    typedText: String,
    recognizedText: String,
    isListening: Boolean,
    comparison: SpeechComparison?,
    onModeChange: (PracticeMode) -> Unit,
    onTypedChange: (String) -> Unit,
    onStartSpeaking: () -> Unit,
    onCheck: () -> Unit,
    onReplay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    showPrev: Boolean,
    showNext: Boolean,
    canAdvance: Boolean,
    allDone: Boolean,
    speechError: String?,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        // Mode toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Practice:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            FilterChip(
                label = { Text("Type") },
                selected = mode == PracticeMode.TYPE,
                onClick = { onModeChange(PracticeMode.TYPE) },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                label = { Text("Speak") },
                selected = mode == PracticeMode.SPEAK,
                onClick = { onModeChange(PracticeMode.SPEAK) },
            )
        }

        Spacer(Modifier.height(8.dp))

        if (mode == PracticeMode.TYPE) {
            Text("Type what you heard:", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = typedText,
                onValueChange = onTypedChange,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                placeholder = { Text("Type the transcript here...") },
                // Dictation grades exact words, so switch off everything that
                // silently rewrites what the learner typed.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
                trailingIcon = {
                    IconButton(onClick = onCheck) {
                        Icon(Icons.Default.Check, contentDescription = "Check")
                    }
                },
            )
        } else {
            Text("Repeat what you heard:", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onStartSpeaking,
                    enabled = !isListening,
                ) {
                    Icon(if (isListening) Icons.Default.Mic else Icons.Default.MicNone, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (isListening) "Listening..." else "Start Speaking")
                }
                Spacer(Modifier.width(8.dp))
                if (recognizedText.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8F5E9),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            recognizedText,
                            modifier = Modifier.padding(8.dp),
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            if (recognizedText.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onCheck) { Text("Check Answer") }
            }

            // Speech failure — surface it instead of failing silently
            if (speechError != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFCEBEB),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFA32D2D),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            speechError,
                            fontSize = 12.sp,
                            color = Color(0xFF791F1F),
                        )
                    }
                }
            }
        }

        // Comparison result
        if (comparison != null) {
            Spacer(Modifier.height(8.dp))
            val isPass = comparison.score >= 0.7
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isPass) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, if (isPass) Color(0xFF4CAF50) else Color(0xFFFF9800)
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isPass) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isPass) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Match: ${comparison.scorePercent} (${comparison.matchedWords}/${comparison.totalWords} words)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Word-level diff: green = correct, red underline = wrong
                    if (comparison.graded.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            comparison.graded.forEach { g ->
                                val (fg, bg) = when (g.result) {
                                    WordResult.CORRECT ->
                                        Color(0xFF27500A) to Color(0xFFEAF3DE)
                                    WordResult.WRONG ->
                                        Color(0xFF791F1F) to Color(0xFFFCEBEB)
                                    WordResult.EXTRA ->
                                        Color(0xFF854F0B) to Color(0xFFFAEEDA)
                                    WordResult.MISSING ->
                                        Color(0xFF5F5E5A) to Color(0xFFF1EFE8)
                                }
                                val label = when (g.result) {
                                    WordResult.CORRECT -> g.expected
                                    WordResult.WRONG -> g.typed ?: "—"
                                    WordResult.EXTRA -> "+${g.typed}"
                                    WordResult.MISSING -> g.expected
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = bg,
                                ) {
                                    Text(
                                        label,
                                        modifier = Modifier.padding(
                                            horizontal = 5.dp, vertical = 2.dp,
                                        ),
                                        fontSize = 13.sp,
                                        color = fg,
                                    )
                                }
                            }
                        }
                    }

                    if (comparison.missingWords.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Missed: ${comparison.missingWords.joinToString(", ")}",
                            fontSize = 12.sp,
                            color = Color(0xFF791F1F),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))

        // Hint when the learner still needs to pass this segment
        if (!canAdvance && showNext) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFF8E1),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFF9A825),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Answer correctly (≥70%) to unlock the next segment. " +
                            "Tap Replay to listen again.",
                        fontSize = 12.sp,
                        color = Color(0xFF795548),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Nav buttons: Replay · Previous · Next
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onReplay,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Default.Replay,
                    contentDescription = "Replay",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Replay", fontSize = 13.sp)
            }

            if (showPrev) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onPrev,
                    modifier = Modifier.weight(1f),
                ) { Text("Previous") }
            }

            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onNext,
                enabled = showNext && canAdvance,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    when {
                        showNext -> "Next Segment"
                        allDone -> "Completed ✓"
                        else -> "Finish"
                    }
                )
            }
        }
    }
}

@Composable
private fun InlineWordCard(
    entry: WordEntry?,
    loading: Boolean,
    targetLang: String,
    onPronounce: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFE3F2FD),
    ) {
        if (loading) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Translating…", fontSize = 13.sp, color = Color.Gray)
            }
        } else if (entry != null) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.word,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (entry.phonetic.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(entry.phonetic, fontSize = 12.sp, color = Color.Gray)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    entry.translations[targetLang] ?: "—",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onPronounce) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Pronounce",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(18.dp),
                        tint = Color.Gray,
                    )
                }
            }
        }
    }
}

