package app.chalkroom.feature.lesson

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Speech to text for voice mode. Text only: no audio is recorded or kept, nothing heard is logged. */
interface SpeechInput {
    fun start()
    fun cancel()
    fun destroy()

    interface Listener {
        fun onListening()
        fun onQuiet()
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(error: VoiceError)
    }
}

/**
 * Android's [SpeechRecognizer], kept listening across pauses: it ends a session on every silence, so
 * each result or quiet spell starts the next one. Everything runs on the main thread.
 */
class AndroidSpeechInput(private val context: Context, private val listener: SpeechInput.Listener) : SpeechInput {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var quietRestarts = 0

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        // Without this the recognizer ignores a per-app language.
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, context.resources.configuration.locales[0].toLanguageTag())
    }

    override fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError(VoiceError.UNAVAILABLE)
            return
        }
        active = true
        quietRestarts = 0
        listen()
    }

    override fun cancel() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
    }

    override fun destroy() {
        cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        if (!active) return
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(callbacks)
            recognizer = it
        }
        r.startListening(intent)
    }

    /** Some recognizers report no-match right after a start, so never restart back to back. */
    private fun listenSoon() {
        handler.postDelayed(::listen, RESTART_DELAY_MS)
    }

    private fun stopWith(error: VoiceError) {
        cancel()
        listener.onError(error)
    }

    private fun Bundle?.bestMatch(): String? = this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (active) listener.onListening()
        }

        override fun onBeginningOfSpeech() {
            if (active) quietRestarts = 0
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!active) return
            partialResults.bestMatch()?.let(listener::onPartial)
        }

        override fun onResults(results: Bundle?) {
            if (!active) return
            results.bestMatch()?.takeIf { it.isNotBlank() }?.let(listener::onFinal)
            // A silence ended the session; listen again so a pause never closes voice mode.
            listen()
        }

        // The API 31 codes are plain ints; older recognizers just never send them.
        @SuppressLint("InlinedApi")
        override fun onError(error: Int) {
            if (!active) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    quietRestarts++
                    // Don't leave the mic open and beeping forever.
                    if (quietRestarts > MAX_QUIET_RESTARTS) {
                        stopWith(VoiceError.FAILED)
                    } else {
                        listener.onQuiet()
                        listenSoon()
                    }
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> listenSoon()
                // The app holds the mic by now; it's the recognition service that can't serve us.
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
                -> stopWith(VoiceError.UNAVAILABLE)
                else -> stopWith(VoiceError.FAILED)
            }
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val RESTART_DELAY_MS = 300L
        const val MAX_QUIET_RESTARTS = 5
    }
}
