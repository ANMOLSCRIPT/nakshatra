package com.nakshatra.heritage.voice

import com.nakshatra.heritage.data.AskResponse
import com.nakshatra.heritage.data.LiveEvent
import com.nakshatra.heritage.data.Topic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The six interaction states of the website's console, in the same order. */
enum class VoiceState(val label: String, val line: String, val hint: String) {
    IDLE("Idle", "Edge device offline", "Start the edge device to use your voice, or type a question below."),
    ARMED("Listening for Nakshatra", "Say “Nakshatra” to begin", "The wake word is detected on the device. Nothing is sent until you say it."),
    WAKE("Nakshatra detected", "Nakshatra detected", "Wake word recognised on the edge device."),
    LISTENING("Listening", "Listening — ask your question", "Your voice is streaming to the speech recogniser."),
    PROCESSING("Processing", "Understanding your question", "Matching the transcript against the heritage archive."),
    ANSWERING("Answering", "Here is what I found", "Say “Nakshatra” again to ask something else."),
}

enum class Source { VOICE, TYPED }

sealed interface Answer {
    /** Nothing has been asked yet. */
    data object None : Answer

    /** The answer engine found no topic for the question. */
    data object NoMatch : Answer

    data class Match(val topic: Topic) : Answer

    /** The server could not be asked. */
    data class Failed(val message: String) : Answer
}

data class VoiceUi(
    val state: VoiceState = VoiceState.IDLE,
    /** The /ws/ui socket is open. */
    val server: Boolean = false,
    /** An edge device (ESP32 or edge_agent.py) is connected to the server. */
    val edge: Boolean = false,
    val source: Source? = null,
    val wakeProb: Double? = null,
    /** Live transcript while the visitor is speaking. */
    val heard: String = "",
    /** Final transcript, or the typed question. */
    val question: String = "",
    val answer: Answer = Answer.None,
    val t1: Double? = null,
    val t2: Double? = null,
    val t5: Double? = null,
    val matchMs: Double? = null,
)

/** Something that can read an answer aloud. */
interface Speaker {
    /** Starts speaking; false if speech is unavailable. [onDone] may arrive on any thread. */
    fun speak(text: String, onDone: () -> Unit = {}): Boolean
    fun stop()
}

/**
 * The voice-interaction state machine, a port of frontend/js/voice.js.
 *
 * Every transition is driven by a real message from `/ws/ui` or by the reply to
 * a typed question; nothing here simulates a detection or an answer. All
 * methods must be called on [scope]'s (single-threaded) dispatcher.
 */
class VoiceEngine(
    private val scope: CoroutineScope,
    private val ask: suspend (String) -> AskResponse,
    private val speaker: Speaker,
    private val ttsOn: () -> Boolean,
    private val describeFailure: (Throwable) -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _ui = MutableStateFlow(VoiceUi())
    val ui: StateFlow<VoiceUi> = _ui.asStateFlow()

    /** Recent input levels, oldest first, each 0..1 -- the waveform. */
    private val _levels = MutableStateFlow(FloatArray(HISTORY))
    val levels: StateFlow<FloatArray> = _levels.asStateFlow()

    private val timers = mutableListOf<Job>()
    private var segments = mutableListOf<String>()
    private var processingSince = 0L
    private var askJob: Job? = null

    private fun later(ms: Long, block: () -> Unit) {
        timers.removeAll { it.isCompleted }
        timers += scope.launch {
            delay(ms)
            block()
        }
    }

    private fun clearTimers() {
        timers.forEach { it.cancel() }
        timers.clear()
    }

    private fun rest(ui: VoiceUi = _ui.value) = if (ui.server && ui.edge) VoiceState.ARMED else VoiceState.IDLE
    private fun setState(next: VoiceState) = _ui.update { it.copy(state = next) }
    private val state get() = _ui.value.state

    // ---------------------------------------------------------------- voice path

    fun onLink(open: Boolean) {
        _ui.update { it.copy(server = open, edge = open && it.edge) }
        val voice = _ui.value.source == Source.VOICE
        timers.removeAll { it.isCompleted }
        when {
            state == VoiceState.IDLE || state == VoiceState.ARMED -> setState(rest())
            // The socket dropped mid-question: nothing more will arrive for it.
            !open && voice && (state == VoiceState.WAKE || state == VoiceState.LISTENING) -> {
                clearTimers()
                setState(rest())
            }
            // Transcript received but the answer was not: do not wait for it forever.
            !open && voice && state == VoiceState.PROCESSING && timers.isEmpty() -> setState(rest())
            // A typed question is answered over REST and an answer on screen stays
            // for its reading time, so neither depends on the socket.
        }
    }

    fun onEvent(msg: LiveEvent) {
        when (msg.event) {
            "edge_status" -> {
                val edge = msg.connected == true
                _ui.update { it.copy(edge = edge) }
                if (!edge && (state == VoiceState.WAKE || state == VoiceState.LISTENING)) {
                    clearTimers() // the device dropped mid-question: nothing more will arrive
                    setState(rest())
                } else if (state == VoiceState.IDLE || state == VoiceState.ARMED) {
                    setState(rest())
                }
            }

            "level" -> pushLevel(msg.rms ?: 0.0)

            "wake" -> {
                clearTimers()
                askJob?.cancel()
                speaker.stop()
                segments = mutableListOf()
                _ui.update {
                    it.copy(source = Source.VOICE, wakeProb = msg.prob, heard = "", question = "",
                        t1 = msg.t1, t2 = null, t5 = null, matchMs = null, state = VoiceState.WAKE)
                }
                later(WAKE_FLASH_MS) { if (state == VoiceState.WAKE) setState(VoiceState.LISTENING) }
            }

            "partial" -> _ui.update { it.copy(heard = (segments + (msg.text ?: "")).joinToString(" ").trim()) }

            "final_segment" -> {
                msg.text?.takeIf { it.isNotEmpty() }?.let { segments += it }
                _ui.update { it.copy(heard = segments.joinToString(" ")) }
            }

            "transcript" -> {
                clearTimers()
                val text = msg.text.orEmpty()
                processingSince = now()
                _ui.update {
                    it.copy(question = text, heard = text, t1 = msg.t1 ?: it.t1, t2 = msg.t2, state = VoiceState.PROCESSING)
                }
            }

            "content" -> {
                _ui.update { it.copy(t5 = msg.t5, question = it.question.ifEmpty { msg.transcript.orEmpty() }) }
                presentAfterProcessing(msg.match, if (processingSince > 0) processingSince else now())
            }
            // Unknown events are ignored, not fatal -- forward compatible.
        }
    }

    private fun pushLevel(rms: Double) {
        val v = (rms * LEVEL_GAIN).toFloat().coerceIn(0f, 1f)
        val old = _levels.value
        val next = FloatArray(HISTORY)
        System.arraycopy(old, 1, next, 0, HISTORY - 1)
        next[HISTORY - 1] = v
        _levels.value = next
    }

    // ---------------------------------------------------------------- typed path

    /** A typed question, a suggestion chip or a topic's own question. */
    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        clearTimers()
        askJob?.cancel()
        speaker.stop()
        val startedAt = now()
        _ui.update {
            it.copy(source = Source.TYPED, question = q, heard = q, wakeProb = null,
                t1 = null, t2 = null, t5 = null, matchMs = null, state = VoiceState.PROCESSING)
        }
        askJob = scope.launch {
            try {
                val res = ask.invoke(q)
                _ui.update { it.copy(matchMs = res.matchMs) }
                presentAfterProcessing(res.match, startedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(answer = Answer.Failed(describeFailure(e)), state = rest(it)) }
            }
        }
    }

    // ------------------------------------------------------------- presentation

    private fun presentAfterProcessing(match: Topic?, startedAt: Long) {
        // Matching takes about a millisecond; keep the "Processing" step readable.
        val wait = (MIN_PROCESSING_MS - (now() - startedAt)).coerceAtLeast(0)
        later(wait) { present(match) }
    }

    private fun present(match: Topic?) {
        _ui.update { it.copy(answer = match?.let(Answer::Match) ?: Answer.NoMatch, state = VoiceState.ANSWERING) }
        var finished = false
        val done = {
            if (!finished) {
                finished = true
                if (state == VoiceState.ANSWERING) setState(rest())
            }
        }
        val text = match?.answer ?: NO_MATCH_SPEECH
        val words = text.trim().split(Regex("\\s+")).size
        // Reading-time fallback for when speech is off or never reports that it ended.
        later(maxOf(6000L, words * 430L) + 2500L, done)
        if (ttsOn()) speaker.speak(text) { scope.launch { later(600, done) } }
    }

    fun stopSpeaking() = speaker.stop()

    companion object {
        const val WAKE_FLASH_MS = 900L
        const val MIN_PROCESSING_MS = 450L
        const val LEVEL_GAIN = 9.0 // speech RMS is ~0.02-0.15; map it onto 0..1
        const val HISTORY = 48
        const val NO_MATCH_SPEECH = "I could not find that in my heritage archive yet. " +
            "Try asking about a monument, a festival, a dance or a craft."
    }
}
