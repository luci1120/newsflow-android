package com.newsflowdictation.app.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads lesson JSON produced by tools/transcribe.py from app assets.
 *
 * Files live in assets/lessons/<videoId>.json and look like:
 *
 * {
 *   "videoId": "tX6Om_7V9OM",
 *   "title": "...",
 *   "source": "CNN10",
 *   "durationSecs": 607,
 *   "segments": [
 *     { "index": 0, "startMs": 11180, "endMs": 17700,
 *       "transcript": "Wake up, ...",
 *       "solution": [["Wake"], ["up,"], ...] }
 *   ]
 * }
 */
object SegmentRepository {

    private const val TAG = "SegmentRepository"
    private const val LESSON_DIR = "lessons"
    private const val INDEX_FILE = "index.json"

    private val gson = Gson()

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

    /** List the lesson JSON files bundled in assets/lessons (index.json excluded). */
    suspend fun listLessonFiles(context: Context): List<String> =
        withContext(Dispatchers.IO) {
            try {
                context.assets.list(LESSON_DIR)
                    ?.filter { it.endsWith(".json") && it != INDEX_FILE }
                    ?.sorted()
                    ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "cannot list assets/$LESSON_DIR", e)
                emptyList()
            }
        }

    /** Load every bundled lesson, newest first. */
    suspend fun loadAll(context: Context): List<NewsItem> =
        withContext(Dispatchers.IO) {
            listLessonFiles(context)
                .mapNotNull { loadFile(context, it) }
                .sortedByDescending { it.publishedAt }
        }

    /** Load a single lesson file by name (e.g. "tX6Om_7V9OM.json"). */
    suspend fun loadFile(context: Context, fileName: String): NewsItem? =
        withContext(Dispatchers.IO) {
            try {
                val json = context.assets
                    .open("$LESSON_DIR/$fileName")
                    .bufferedReader()
                    .use { it.readText() }

                val dto = gson.fromJson(json, LessonDto::class.java) ?: return@withContext null

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
            } catch (e: Exception) {
                Log.e(TAG, "cannot load lesson $fileName", e)
                null
            }
        }
}
