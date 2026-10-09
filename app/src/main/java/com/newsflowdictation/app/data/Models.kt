package com.newsflowdictation.app.data

/**
 * Data models for Newsflow Dictation
 */

data class NewsItem(
    val id: String,
    val title: String,
    val source: String, // CNN10, CBS, PBS, ABC
    val youtubeVideoId: String,
    val thumbnailUrl: String,
    val durationSecs: Int,
    val publishedAt: String,
    val segments: List<NewsSegment>,
    val description: String? = null,
) {
    val sourceLabel: String
        get() = when (source.uppercase()) {
            "CNN10" -> "CNN 10"
            "CBS" -> "CBS News"
            "PBS" -> "PBS NewsHour"
            "ABC" -> "ABC News"
            else -> source
        }

    val durationFormatted: String
        get() {
            val m = durationSecs / 60
            val s = durationSecs % 60
            return "$m:${s.toString().padStart(2, '0')}"
        }
}

/**
 * One sentence-level learning segment.
 *
 * [startMs] / [endMs] are milliseconds so we keep sub-second accuracy — a
 * sentence boundary in the source audio is rarely on a whole second.
 *
 * [solution] is a list of word positions, each holding the accepted spellings
 * for that position. e.g. [["2000", "two thousand"]] means either form is
 * correct. This mirrors how DailyDictation grades dictation.
 */
data class NewsSegment(
    val index: Int,
    val startMs: Int,
    val endMs: Int,
    val transcript: String,
    val solution: List<List<String>> = emptyList(),
) {
    val startSec: Double get() = startMs / 1000.0
    val endSec: Double get() = endMs / 1000.0
    val durationMs: Int get() = endMs - startMs

    /** Word positions to grade against; falls back to the plain transcript. */
    val answerKey: List<List<String>>
        get() = solution.ifEmpty { transcript.split(" ").map { listOf(it) } }
}

data class WordEntry(
    val word: String,
    val phonetic: String,
    val translations: Map<String, String>, // langCode -> translation
    val exampleSentences: List<String>,
)

enum class PracticeMode { TYPE, SPEAK }

/** Per-word grading result, used to render the inline diff. */
enum class WordResult { CORRECT, WRONG, MISSING, EXTRA }

data class GradedWord(
    val expected: String,
    val typed: String?,
    val result: WordResult,
)

data class SpeechComparison(
    val score: Double,
    val matchedWords: Int,
    val totalWords: Int,
    val missingWords: List<String>,
    val graded: List<GradedWord> = emptyList(),
) {
    val scorePercent: String get() = "${(score * 100).toInt()}%"
    val passed: Boolean get() = score >= 0.7

    companion object {
        /** Loose comparison used for the Speak mode (order-insensitive). */
        fun compare(expected: String, recognized: String): SpeechComparison {
            val expectedWords = normalizeWords(expected)
            val recognizedWords = normalizeWords(recognized)
            val expectedSet = expectedWords.toSet()
            val recognizedSet = recognizedWords.toSet()
            val matched = expectedSet.intersect(recognizedSet).size
            val missing = expectedSet.subtract(recognizedSet).toList()
            val score = if (expectedSet.isNotEmpty()) matched.toDouble() / expectedSet.size else 0.0
            return SpeechComparison(score, matched, expectedSet.size, missing)
        }

        /**
         * Positional grading used for the Type mode.
         *
         * Walks the answer key word by word. A typed word counts as correct if
         * it matches ANY accepted variant at that position, so "2000" and
         * "two thousand" both pass.
         */
        fun gradePositional(answerKey: List<List<String>>, typed: String): SpeechComparison {
            val typedWords = typed.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val graded = mutableListOf<GradedWord>()
            var matched = 0

            for (i in answerKey.indices) {
                val accepted = answerKey[i]
                val got = typedWords.getOrNull(i)
                val ok = got != null && accepted.any { matches(it, got) }
                if (ok) matched++
                graded += GradedWord(
                    expected = accepted.first(),
                    typed = got,
                    result = if (ok) WordResult.CORRECT else WordResult.WRONG,
                )
            }

            // Anything the learner typed beyond the key is extra
            if (typedWords.size > answerKey.size) {
                for (i in answerKey.size until typedWords.size) {
                    graded += GradedWord(
                        expected = "",
                        typed = typedWords[i],
                        result = WordResult.EXTRA,
                    )
                }
            }

            val missing = graded
                .filter { it.result == WordResult.WRONG }
                .map { it.expected }

            val score = if (answerKey.isNotEmpty()) matched.toDouble() / answerKey.size else 0.0
            return SpeechComparison(score, matched, answerKey.size, missing, graded)
        }

        private fun matches(expected: String, typed: String): Boolean {
            val a = expected.lowercase().trim()
            val b = typed.lowercase().trim()
            return a == b
        }

        private fun normalizeWords(text: String): List<String> =
            text.lowercase()
                .replace(Regex("[^a-z\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() && it.length > 1 }
    }
}

data class SupportedLanguage(val code: String, val name: String, val nativeName: String)

object AppLanguages {
    val languages = listOf(
        SupportedLanguage("zh", "Chinese", "中文"),
        SupportedLanguage("es", "Spanish", "Español"),
        SupportedLanguage("vi", "Vietnamese", "Tiếng Việt"),
        SupportedLanguage("ja", "Japanese", "日本語"),
        SupportedLanguage("ko", "Korean", "한국어"),
        SupportedLanguage("pt", "Portuguese", "Português"),
    )
}
