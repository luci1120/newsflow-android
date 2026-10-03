package com.newsflow.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * YouTube Data API v3 service — fetches the latest videos from news channels.
 *
 * Free quota: 10,000 units/day.
 *  - channels.list      = 1 unit  (resolve the "uploads" playlist)
 *  - playlistItems.list = 1 unit  (list that playlist's videos)
 *
 * To enable: paste your key into [API_KEY]. Create one at
 * https://console.cloud.google.com/apis/library/youtube.googleapis.com
 */
object YouTubeService {

    private const val TAG = "YouTubeService"

    /** TODO: replace with your own API key. */
    const val API_KEY = "YOUR_YOUTUBE_API_KEY"

    /** Verified official channel IDs (resolved from the channels' @handles). */
    private val channelIds = mapOf(
        "CNN10" to "UCTOoRgpHTjAQPk6Ak70u-pA",
        "CBS" to "UC8p1vwvWtl6T73JiExfWs1g",
        "PBS" to "UC6ZFN9Tx6xh-skXCuRHCDpQ",
        "ABC" to "UCBi2mrWuNuyYy4gbM6fU18Q",
    )

    /** Display order for grouped lists. */
    val sourceOrder = listOf("CNN10", "CBS", "PBS", "ABC")

    fun isConfigured(): Boolean =
        API_KEY.isNotBlank() && API_KEY != "YOUR_YOUTUBE_API_KEY"

    /**
     * Fetch the latest uploads for one source.
     * Returns an empty list if the key is missing or the request fails.
     */
    suspend fun fetchLatest(source: String, maxResults: Int = 5): List<NewsItem> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext emptyList()
            val channelId = channelIds[source] ?: return@withContext emptyList()

            try {
                val uploads = getUploadsPlaylistId(channelId)
                    ?: return@withContext emptyList()
                val json = httpGet(
                    "https://www.googleapis.com/youtube/v3/playlistItems" +
                        "?part=snippet,contentDetails" +
                        "&playlistId=$uploads" +
                        "&maxResults=$maxResults" +
                        "&key=$API_KEY"
                )
                val root = JSONObject(json)
                val items = root.optJSONArray("items") ?: return@withContext emptyList()

                // Collect (videoId, snippet) pairs first
                data class Raw(val id: String, val snippet: JSONObject)

                val raws = (0 until items.length()).mapNotNull { i ->
                    val item = items.optJSONObject(i) ?: return@mapNotNull null
                    val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
                    val details = item.optJSONObject("contentDetails") ?: return@mapNotNull null
                    val videoId = details.optString("videoId")
                    if (videoId.isBlank()) return@mapNotNull null
                    Raw(videoId, snippet)
                }

                // One extra call resolves durations for every video at once
                val durations = getDurations(raws.map { it.id })

                raws.map { raw ->
                    val secs = durations[raw.id] ?: 0
                    NewsItem(
                        id = raw.id,
                        title = raw.snippet.optString("title", "Untitled"),
                        source = source,
                        youtubeVideoId = raw.id,
                        thumbnailUrl = "https://img.youtube.com/vi/${raw.id}/mqdefault.jpg",
                        durationSecs = secs,
                        publishedAt = raw.snippet.optString("publishedAt", ""),
                        description = raw.snippet.optString("description", ""),
                        segments = generateSegments(secs),
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchLatest($source) failed", e)
                emptyList()
            }
        }

    /** Resolve durations (seconds) for up to 50 video IDs in a single API call. */
    private fun getDurations(videoIds: List<String>): Map<String, Int> {
        if (videoIds.isEmpty()) return emptyMap()
        val ids = videoIds.take(50).joinToString(",")
        val json = httpGet(
            "https://www.googleapis.com/youtube/v3/videos" +
                "?part=contentDetails&id=$ids&key=$API_KEY"
        )
        val items = JSONObject(json).optJSONArray("items") ?: return emptyMap()
        val result = mutableMapOf<String, Int>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val iso = item.optJSONObject("contentDetails")?.optString("duration") ?: ""
            result[id] = parseIsoDuration(iso)
        }
        return result
    }

    /** Parse an ISO-8601 duration such as "PT1M30S" into seconds. */
    private fun parseIsoDuration(iso: String): Int {
        val m = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").find(iso)
            ?: return 0
        val h = m.groupValues[1].toIntOrNull() ?: 0
        val min = m.groupValues[2].toIntOrNull() ?: 0
        val s = m.groupValues[3].toIntOrNull() ?: 0
        return h * 3600 + min * 60 + s
    }

    /**
     * Split a video into ~5 equal learning segments.
     *
     * Transcripts are NOT available here — YouTube's caption download endpoint
     * requires OAuth. Wire up an ASR/caption source to fill [NewsSegment.transcript]
     * with real text; until then segments carry a placeholder line.
     */
    /**
     * Crude equal-length split, used only when a video has no real transcript.
     *
     * Prefer the sentence-level segmentation produced by tools/transcribe.py —
     * an even split cuts sentences in half and makes dictation impossible.
     */
    fun generateSegments(durationSecs: Int): List<NewsSegment> {
        if (durationSecs <= 0) return emptyList()
        val segCount = 5
        val len = (durationSecs / segCount).coerceAtLeast(1)
        return (0 until segCount).mapNotNull { i ->
            val start = i * len
            val end = if (i == segCount - 1) durationSecs else (i + 1) * len
            if (start >= durationSecs) return@mapNotNull null
            NewsSegment(
                index = i,
                startMs = start * 1000,
                endMs = end * 1000,
                transcript = "Segment ${i + 1} — transcript not available yet.",
            )
        }
    }

    private fun getUploadsPlaylistId(channelId: String): String? {
        val json = httpGet(
            "https://www.googleapis.com/youtube/v3/channels" +
                "?part=contentDetails&id=$channelId&key=$API_KEY"
        )
        val items = JSONObject(json).optJSONArray("items") ?: return null
        if (items.length() == 0) return null
        return items.optJSONObject(0)
            ?.optJSONObject("contentDetails")
            ?.optJSONObject("relatedPlaylists")
            ?.optString("uploads")
    }

    private fun httpGet(url: String): String =
        URL(url).openConnection().run {
            connectTimeout = 10_000
            readTimeout = 10_000
            (getInputStream().bufferedReader()).use { it.readText() }
        }
}
