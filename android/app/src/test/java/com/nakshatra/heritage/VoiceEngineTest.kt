package com.nakshatra.heritage

import com.nakshatra.heritage.data.AskResponse
import com.nakshatra.heritage.data.LiveEvent
import com.nakshatra.heritage.data.Topic
import com.nakshatra.heritage.voice.Answer
import com.nakshatra.heritage.voice.Source
import com.nakshatra.heritage.voice.Speaker
import com.nakshatra.heritage.voice.VoiceEngine
import com.nakshatra.heritage.voice.VoiceState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceEngineTest {
    private class FakeSpeaker : Speaker {
        val spoken = mutableListOf<String>()
        var stops = 0
        var onDone: (() -> Unit)? = null
        override fun speak(text: String, onDone: () -> Unit): Boolean { spoken += text; this.onDone = onDone; return true }
        override fun stop() { stops++ }
    }

    private val diwali = Topic(id = "diwali", name = "Diwali", answer = "Diwali is the festival of lights.", matchedBy = "question", confidence = 1.0)

    private fun TestScope.engine(
        speaker: FakeSpeaker = FakeSpeaker(),
        tts: Boolean = true,
        reply: suspend (String) -> AskResponse = { AskResponse(it, diwali, 1.2) },
    ) = VoiceEngine(backgroundScope + StandardTestDispatcher(testScheduler), reply, speaker, { tts }, { "failed: ${it.message}" }) { testScheduler.currentTime }

    private operator fun kotlinx.coroutines.CoroutineScope.plus(d: kotlinx.coroutines.CoroutineDispatcher) =
        kotlinx.coroutines.CoroutineScope(coroutineContext + d)

    @Test
    fun `starts idle and arms only when server and edge are both connected`() = runTest {
        val e = engine()
        assertEquals(VoiceState.IDLE, e.ui.value.state)
        e.onLink(true)
        assertEquals(VoiceState.IDLE, e.ui.value.state)
        e.onEvent(LiveEvent("edge_status", connected = true))
        assertEquals(VoiceState.ARMED, e.ui.value.state)
        e.onLink(false)
        assertEquals(VoiceState.IDLE, e.ui.value.state)
        assertEquals(false, e.ui.value.edge)
    }

    @Test
    fun `typed question goes processing, answering, then rests`() = runTest {
        val speaker = FakeSpeaker()
        val e = engine(speaker, tts = false)
        e.ask("  why do we celebrate diwali ")
        assertEquals(VoiceState.PROCESSING, e.ui.value.state)
        assertEquals("why do we celebrate diwali", e.ui.value.question)
        assertEquals(Source.TYPED, e.ui.value.source)
        runCurrent()
        assertEquals("the Processing step stays readable", VoiceState.PROCESSING, e.ui.value.state)
        advanceTimeBy(VoiceEngine.MIN_PROCESSING_MS + 1)
        assertEquals(VoiceState.ANSWERING, e.ui.value.state)
        assertEquals(Answer.Match(diwali), e.ui.value.answer)
        assertEquals(1.2, e.ui.value.matchMs!!, 1e-9)
        assertTrue("speech is off", speaker.spoken.isEmpty())
        advanceTimeBy(9000)
        assertEquals(VoiceState.IDLE, e.ui.value.state)
        assertEquals("the answer stays on screen", Answer.Match(diwali), e.ui.value.answer)
    }

    @Test
    fun `answer is spoken and the state rests after speech ends`() = runTest {
        val speaker = FakeSpeaker()
        val e = engine(speaker)
        e.ask("diwali")
        advanceTimeBy(600)
        assertEquals(listOf(diwali.answer), speaker.spoken)
        speaker.onDone!!.invoke()
        advanceTimeBy(700)
        assertEquals(VoiceState.IDLE, e.ui.value.state)
    }

    @Test
    fun `no match is presented and spoken as such`() = runTest {
        val speaker = FakeSpeaker()
        val e = engine(speaker) { AskResponse(it, null, 0.5) }
        e.ask("capital of France")
        advanceTimeBy(600)
        assertEquals(Answer.NoMatch, e.ui.value.answer)
        assertEquals(VoiceState.ANSWERING, e.ui.value.state)
        assertEquals(listOf(VoiceEngine.NO_MATCH_SPEECH), speaker.spoken)
    }

    @Test
    fun `an unreachable server is an error, not an answer`() = runTest {
        val e = engine { throw IOException("down") }
        e.ask("diwali")
        advanceTimeBy(600)
        assertEquals(Answer.Failed("failed: down"), e.ui.value.answer)
        assertEquals(VoiceState.IDLE, e.ui.value.state)
    }

    @Test
    fun `voice pipeline events drive every state`() = runTest {
        val speaker = FakeSpeaker()
        val e = engine(speaker)
        e.onLink(true)
        e.onEvent(LiveEvent("edge_status", connected = true))

        e.onEvent(LiveEvent("wake", prob = 0.93, t1 = 1000.0))
        assertEquals(VoiceState.WAKE, e.ui.value.state)
        assertEquals(0.93, e.ui.value.wakeProb!!, 1e-9)
        advanceTimeBy(VoiceEngine.WAKE_FLASH_MS + 1)
        assertEquals(VoiceState.LISTENING, e.ui.value.state)

        e.onEvent(LiveEvent("partial", text = "why do we"))
        assertEquals("why do we", e.ui.value.heard)
        e.onEvent(LiveEvent("final_segment", text = "why do we celebrate"))
        e.onEvent(LiveEvent("partial", text = "diwali"))
        assertEquals("why do we celebrate diwali", e.ui.value.heard)

        e.onEvent(LiveEvent("transcript", text = "why do we celebrate diwali", t1 = 1000.0, t2 = 1040.0))
        assertEquals(VoiceState.PROCESSING, e.ui.value.state)
        e.onEvent(LiveEvent("content", transcript = "why do we celebrate diwali", match = diwali, t5 = 5200.0))
        advanceTimeBy(VoiceEngine.MIN_PROCESSING_MS + 1)
        assertEquals(VoiceState.ANSWERING, e.ui.value.state)
        assertEquals(Source.VOICE, e.ui.value.source)
        assertEquals(5200.0, e.ui.value.t5!!, 1e-9)
        assertEquals(1040.0, e.ui.value.t2!!, 1e-9)

        advanceTimeBy(20_000)
        assertEquals("back to listening for the wake word", VoiceState.ARMED, e.ui.value.state)
    }

    @Test
    fun `edge dropping mid-question returns to rest`() = runTest {
        val e = engine()
        e.onLink(true)
        e.onEvent(LiveEvent("edge_status", connected = true))
        e.onEvent(LiveEvent("wake", prob = 0.9, t1 = 1.0))
        e.onEvent(LiveEvent("edge_status", connected = false))
        assertEquals(VoiceState.IDLE, e.ui.value.state)
        advanceTimeBy(5000)
        assertEquals(VoiceState.IDLE, e.ui.value.state)
    }

    @Test
    fun `levels are scaled, clamped and kept as a rolling history`() = runTest {
        val e = engine()
        e.onEvent(LiveEvent("level", rms = 0.05))
        e.onEvent(LiveEvent("level", rms = 5.0))
        val levels = e.levels.value
        assertEquals(VoiceEngine.HISTORY, levels.size)
        assertEquals(1f, levels.last(), 1e-6f)
        assertEquals(0.45f, levels[levels.size - 2], 1e-4f)
        assertEquals(0f, levels.first(), 0f)
    }

    @Test
    fun `unknown events are ignored`() = runTest {
        val e = engine()
        val before = e.ui.value
        e.onEvent(LiveEvent("something_new"))
        assertEquals(before, e.ui.value)
    }
}
