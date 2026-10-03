package com.newsflow.app.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/** Result of one speech-recognition attempt. */
data class SpeechOutcome(
    val text: String = "",
    val error: String? = null,
) {
    val ok: Boolean get() = error == null && text.isNotBlank()
}

/**
 * Speech service — wraps Android SpeechRecognizer + TextToSpeech.
 * Free, native, no external API.
 */
class SpeechService(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.5f) // slow, for learners
                ttsReady = true
            }
        }
        recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            Log.e(TAG, "cannot create SpeechRecognizer", e)
            null
        }
    }

    /** Whether the device has any speech recognition service installed. */
    fun isSpeechAvailable(): Boolean =
        try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            false
        }

    /**
     * Start listening for speech. Never throws — failures come back as
     * [SpeechOutcome.error] so the UI can tell the learner what went wrong.
     */
    suspend fun startListening(
        onPartial: (String) -> Unit = {},
    ): SpeechOutcome = suspendCancellableCoroutine { cont ->

        if (!isSpeechAvailable()) {
            if (cont.isActive) {
                cont.resume(
                    SpeechOutcome(
                        error = "No speech recognition service on this device. " +
                            "Install the Google app, or test on a real phone.",
                    )
                )
            }
            return@suspendCancellableCoroutine
        }

        val rec = recognizer
        if (rec == null) {
            if (cont.isActive) {
                cont.resume(SpeechOutcome(error = "Speech recognizer unavailable."))
            }
            return@suspendCancellableCoroutine
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                Log.e(TAG, "recognition error $error (${errorMessage(error)})")
                if (cont.isActive) cont.resume(SpeechOutcome(error = errorMessage(error)))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (cont.isActive) {
                    cont.resume(
                        if (text.isBlank()) {
                            SpeechOutcome(error = "Didn't catch that — try again.")
                        } else {
                            SpeechOutcome(text = text)
                        }
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (partial.isNotBlank()) onPartial(partial)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        rec.setRecognitionListener(listener)
        rec.startListening(intent)

        cont.invokeOnCancellation {
            runCatching { rec.stopListening() }
        }
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    fun speak(text: String, rate: Float = 0.5f) {
        if (ttsReady) {
            tts?.setSpeechRate(rate)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun shutdown() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val TAG = "SpeechService"

        fun errorMessage(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone error — check the emulator's mic input."
            SpeechRecognizer.ERROR_CLIENT -> "Recognizer client error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission denied. Enable it in system settings."
            SpeechRecognizer.ERROR_NETWORK -> "Network error — speech needs a connection."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out."
            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy — try again."
            SpeechRecognizer.ERROR_SERVER -> "Speech server error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
            else -> "Speech error (code $code)."
        }
    }
}
