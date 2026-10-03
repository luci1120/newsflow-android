package com.newsflow.app.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Fetches lesson JSON from a remote host so content can update daily without
 * shipping a new APK.
 *
 * Expected layout on the host:
 *
 *   {BASE_URL}/index.json
 *   {
 *     "updatedAt": "2026-10-03T12:00:00Z",
 *     "lessons": ["tX6Om_7V9OM.json", "q4PkX8_xUYk.json", ...]
 *   }
 *
 *   {BASE_URL}/<videoId>.json      ← same shape transcribe.py emits
 *
 * Fetched lessons are cached under filesDir/lessons_cache so the app still
 * works offline after the first successful sync.
 *
 * Set [BASE_URL] to your host to enable. Leave blank to stay offline-only.
 */
object RemoteLessonService {

    private const val TAG = "RemoteLessonService"
    private const val CACHE_DIR = "lessons_cache"

    /**
     * Static host root, built by tools/publish.py.
     *
     *   {BASE_URL}/index.json      ← lesson list + metadata
     *   {BASE_URL}/<videoId>.json  ← one lesson
     *
     * Change this if you host somewhere other than GitHub Pages.
     */
    const val BASE_URL = "https://luci1120.github.io/newsflow-lessons/lessons"

    private val gson = Gson()

    fun isConfigured(): Boolean = BASE_URL.isNotBlank()

    private data class IndexDto(
        val updatedAt: String? = null,
        val lessons: List<String> = emptyList(),
    )

    private data class LessonDto(
        val videoId: String,
        val title: String,
        val source: String,
        val durationSecs: Int,
        val publishedAt: String? = null,
        val segments: List<SegmentDto>,
    )

    private data class SegmentDto(
        val index: Int,
        @SerializedName("startMs") val startMs: Int,
        @SerializedName("endMs") val endMs: Int,
        val transcript: String,
        val solution: List<List<String>>? = null,
    )

    /** Read whatever is already cached on disk (no network). Newest first. */
    suspend fun loadCached(context: Context): List<NewsItem> =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, CACHE_DIR)
            if (!dir.isDirectory) return@withContext emptyList()
            dir.listFiles { f -> f.name.endsWith(".json") && f.name != "index.json" }
                ?.mapNotNull { parse(it.readText(), it.name) }
                ?.sortedByDescending { it.publishedAt }
                ?: emptyList()
        }

    /**
     * Download the index and every listed lesson, refreshing the cache.
     * Returns the fresh lessons, or an empty list on any failure.
     */
    suspend fun sync(context: Context): List<NewsItem> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()

        try {
            val indexJson = httpGet("$BASE_URL/index.json")
            val index = gson.fromJson(indexJson, IndexDto::class.java)
            val files = index?.lessons.orEmpty()
            if (files.isEmpty()) {
                Log.w(TAG, "remote index is empty")
                return@withContext emptyList()
            }

            val dir = File(context.filesDir, CACHE_DIR).apply { mkdirs() }
            val lessons = mutableListOf<NewsItem>()

            for (name in files) {
                try {
                    val body = httpGet("$BASE_URL/$name")
                    val item = parse(body, name)
                    if (item != null) {
                        File(dir, name).writeText(body)
                        lessons += item
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "failed to fetch $name, using cache if present", e)
                    val cached = File(dir, name)
                    if (cached.isFile) {
                        parse(cached.readText(), name)?.let { lessons += it }
                    }
                }
            }
            Log.i(TAG, "synced ${lessons.size} lessons")
            lessons
        } catch (e: Exception) {
            Log.e(TAG, "sync failed, falling back to cache", e)
            loadCached(context)
        }
    }

    private fun parse(json: String, name: String): NewsItem? = try {
        val dto = gson.fromJson(json, LessonDto::class.java)
        if (dto == null || dto.videoId.isBlank()) {
            null
        } else {
            NewsItem(
                id = dto.videoId,
                title = dto.title,
                source = dto.source,
                youtubeVideoId = dto.videoId,
                thumbnailUrl = "https://img.youtube.com/vi/${dto.videoId}/mqdefault.jpg",
                durationSecs = dto.durationSecs,
                publishedAt = dto.publishedAt.orEmpty(),
                description = null,
                segments = dto.segments.map { s ->
                    NewsSegment(
                        index = s.index,
                        startMs = s.startMs,
                        endMs = s.endMs,
                        transcript = s.transcript,
                        solution = s.solution ?: emptyList(),
                    )
                },
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "cannot parse $name", e)
        null
    }

    private fun httpGet(url: String): String =
        URL(url).openConnection().run {
            connectTimeout = 10_000
            readTimeout = 15_000
            (getInputStream().bufferedReader()).use { it.readText() }
        }
}
