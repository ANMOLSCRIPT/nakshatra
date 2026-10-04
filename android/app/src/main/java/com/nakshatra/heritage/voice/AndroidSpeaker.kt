package com.nakshatra.heritage.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Spoken answers through the device's text-to-speech engine -- the native
 * counterpart of the website's use of browser speech synthesis. Indian English
 * is preferred, as on the website.
 */
class AndroidSpeaker(context: Context) : Speaker {
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val counter = AtomicInteger()
    private var ready = false
    private var pending: Pair<String, () -> Unit>? = null

    /** The text being spoken right now, or null -- lets a "Read aloud" button become "Stop". */
    private val _speaking = MutableStateFlow<String?>(null)
    val speaking: StateFlow<String?> = _speaking.asStateFlow()

    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) onReady() else _available.value = false
    }

    private fun onReady() {
        val preferred = listOf(Locale.forLanguageTag("en-IN"), Locale.UK, Locale.US, Locale.getDefault())
        val locale = preferred.firstOrNull {
            runCatching { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)
        }
        if (locale == null) {
            _available.value = false
            return
        }
        tts.language = locale
        tts.setSpeechRate(0.96f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) = finished(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finished(utteranceId)
            override fun onStop(utteranceId: String, interrupted: Boolean) = finished(utteranceId, notify = false)
        })
        ready = true
        pending?.let { (text, done) -> speak(text, done) }
        pending = null
    }

    private fun finished(id: String, notify: Boolean = true) {
        val callback = callbacks.remove(id)
        if (callbacks.isEmpty()) _speaking.value = null
        if (notify) callback?.invoke()
    }

    override fun speak(text: String, onDone: () -> Unit): Boolean {
        if (!_available.value) return false
        if (!ready) {
            // The engine is still starting; say it as soon as it is up.
            pending = text to onDone
            return true
        }
        callbacks.clear()
        val id = "nk-${counter.incrementAndGet()}"
        callbacks[id] = onDone
        _speaking.value = text
        val ok = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
        if (!ok) {
            callbacks.remove(id)
            _speaking.value = null
        }
        return ok
    }

    override fun stop() {
        pending = null
        callbacks.clear()
        _speaking.value = null
        if (ready) tts.stop()
    }
}
