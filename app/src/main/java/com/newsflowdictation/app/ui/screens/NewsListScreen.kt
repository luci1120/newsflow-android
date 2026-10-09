package com.newsflowdictation.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.newsflowdictation.app.ads.AdIds
import com.newsflowdictation.app.ads.BannerAd
import com.newsflowdictation.app.data.NewsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsListScreen(
    news: List<NewsItem>,
    onOpenNews: (NewsItem) -> Unit,
    isLoading: Boolean = false,
    showAds: Boolean = true,
) {
    // Fixed source order: CNN 10 → CBS → PBS → ABC
    val sourceOrder = listOf("CNN10", "CBS", "PBS", "ABC")

    // Only offer sources that actually have lessons — avoids empty filters
    val sources = remember(news) {
        listOf("ALL") + sourceOrder.filter { s -> news.any { it.source == s } }
    }
    var selectedSource by remember { mutableStateOf("ALL") }

    val filtered = if (selectedSource == "ALL") news
        else news.filter { it.source == selectedSource }

    // Group by source in the fixed order; newest first inside each group
    val grouped = remember(filtered) {
        sourceOrder
            .mapNotNull { src ->
                val items = filtered
                    .filter { it.source == src }
                    .sortedByDescending { it.publishedAt }
                if (items.isEmpty()) null else src to items
            }
            .toMap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Newsflow Dictation", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
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
        ) {
            // Source filter chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(sources) { source ->
                    FilterChip(
                        label = { Text(if (source == "ALL") "All Sources" else source) },
                        selected = selectedSource == source,
                        onClick = { selectedSource = source },
                    )
                }
            }

            Divider()

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // News list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (selectedSource == "ALL") {
                    grouped.forEach { (source, items) ->
                        item {
                            SourceHeader(source = source, count = items.size)
                        }
                        items(items) { newsItem ->
                            NewsCard(news = newsItem, onClick = { onOpenNews(newsItem) })
                        }
                        item { Divider() }
                    }
                } else {
                    items(filtered) { newsItem ->
                        NewsCard(news = newsItem, onClick = { onOpenNews(newsItem) })
                    }
                }
            }

            // AdMob 横幅（加载失败时高度为 0，不留白）。会员不显示。
            if (showAds) {
                BannerAd(adUnitId = AdIds.bannerList)
            }
        }
    }
}

@Composable
private fun SourceHeader(source: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(getSourceIcon(source), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            getSourceLabel(source),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$count videos",
            fontSize = 13.sp,
            color = Color.Gray,
        )
    }
}

@Composable
private fun NewsCard(news: NewsItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 80.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                AsyncImage(
                    model = news.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Play",
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    tint = Color.White.copy(alpha = 0.8f),
                )
            }

            // Title + meta
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(10.dp),
            ) {
                Text(
                    news.title,
                    maxLines = 2,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Publish date, so the learner can tell new from old at a glance
                    if (news.publishedAt.isNotEmpty()) {
                        Text(
                            news.publishedAt,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Icon(Icons.Default.Schedule, null, Modifier.size(12.dp), tint = Color.Gray)
                    Spacer(Modifier.width(4.dp))
                    Text(news.durationFormatted, fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.Segment, null, Modifier.size(12.dp), tint = Color.Gray)
                    Spacer(Modifier.width(4.dp))
                    Text("${news.segments.size} segments", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

private fun getSourceIcon(source: String) = when (source) {
    "CNN10" -> Icons.Default.School
    "CBS" -> Icons.Default.Tv
    "PBS" -> Icons.Default.LiveTv
    "ABC" -> Icons.Default.Videocam
    else -> Icons.Default.Newspaper
}

private fun getSourceLabel(source: String) = when (source) {
    "CNN10" -> "CNN 10 (student news)"
    "CBS" -> "CBS News"
    "PBS" -> "PBS NewsHour"
    "ABC" -> "ABC News"
    else -> source
}
