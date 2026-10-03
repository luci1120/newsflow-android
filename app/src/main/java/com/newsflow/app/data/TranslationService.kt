package com.newsflow.app.data

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder

/**
 * Translation service — 3-tier fallback
 * Tier 1: Local dictionary (instant)
 * Tier 2: MyMemory API (online, 5000 words/day free)
 */
object TranslationService {

    private const val TAG = "TranslationService"
    private val gson = Gson()

    suspend fun translateWord(word: String, targetLang: String): WordEntry? =
        withContext(Dispatchers.IO) {
            val cleanWord = word.lowercase().replace(Regex("[^a-z]"), "")

            // Tier 1: Local dictionary
            MockData.localDictionary[cleanWord]?.let { return@withContext it }

            // Tier 2: MyMemory API
            try {
                val encoded = URLEncoder.encode(cleanWord, "UTF-8")
                val url = URL("https://api.mymemory.translated.net/get?q=$encoded&langpair=en|$targetLang")
                val json = url.readText()
                val resp = gson.fromJson(json, MyMemoryResponse::class.java)
                val translation = resp?.responseData?.translatedText
                if (!translation.isNullOrEmpty()) {
                    return@withContext WordEntry(
                        word = cleanWord,
                        phonetic = "",
                        translations = mapOf(targetLang to translation),
                        exampleSentences = emptyList(),
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed for '$cleanWord'", e)
            }

            null
        }

    private data class MyMemoryResponse(
        val responseData: ResponseData?,
    )

    private data class ResponseData(
        val translatedText: String?,
    )
}
