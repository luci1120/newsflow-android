package com.newsflowdictation.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Settings service — persists user preferences
 */
class SettingsService(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("newsflow_settings", Context.MODE_PRIVATE)

    fun getTargetLanguage(): String = prefs.getString(KEY_LANG, "zh") ?: "zh"
    fun setTargetLanguage(lang: String) = prefs.edit().putString(KEY_LANG, lang).apply()

    fun getPlaybackSpeed(): Float = prefs.getFloat(KEY_SPEED, 1.0f)
    fun setPlaybackSpeed(speed: Float) = prefs.edit().putFloat(KEY_SPEED, speed).apply()

    /** Show the transcript text under the player. Off by default — listening first. */
    fun getShowTranscript(): Boolean = prefs.getBoolean(KEY_SHOW_TRANSCRIPT, false)
    fun setShowTranscript(show: Boolean) =
        prefs.edit().putBoolean(KEY_SHOW_TRANSCRIPT, show).apply()

    /**
     * Tap a word in the transcript to see its translation inline.
     * On by default — only matters once the learner turns subtitles on.
     */
    fun getWordPopupEnabled(): Boolean = prefs.getBoolean(KEY_WORD_POPUP, true)
    fun setWordPopupEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_WORD_POPUP, enabled).apply()

    /** Show the player's speed chips. On by default. */
    fun getShowSpeedBar(): Boolean = prefs.getBoolean(KEY_SPEED_BAR, true)
    fun setShowSpeedBar(show: Boolean) =
        prefs.edit().putBoolean(KEY_SPEED_BAR, show).apply()

    fun getCompletedNews(): Set<String> = prefs.getStringSet(KEY_COMPLETED, emptySet()) ?: emptySet()
    fun markNewsCompleted(newsId: String) {
        val current = getCompletedNews().toMutableSet()
        current.add(newsId)
        prefs.edit().putStringSet(KEY_COMPLETED, current).apply()
    }

    companion object {
        private const val KEY_LANG = "target_language"
        private const val KEY_SPEED = "playback_speed"
        private const val KEY_COMPLETED = "completed_news"
        private const val KEY_SHOW_TRANSCRIPT = "show_transcript"
        private const val KEY_WORD_POPUP = "word_popup_enabled"
        private const val KEY_SPEED_BAR = "show_speed_bar"
    }
}
