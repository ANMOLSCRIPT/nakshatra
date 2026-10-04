package com.nakshatra.heritage

import com.nakshatra.heritage.data.ApiFactory
import com.nakshatra.heritage.data.AskRequest
import com.nakshatra.heritage.data.LiveEvent
import com.nakshatra.heritage.data.NakshatraJson
import com.nakshatra.heritage.data.describeFailure
import com.nakshatra.heritage.data.heritageImageUrl
import com.nakshatra.heritage.data.liveSocketUrl
import com.nakshatra.heritage.data.normalizeServerUrl
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ConnectException

class NetworkTest {
    private lateinit var server: MockWebServer
    private val base get() = server.url("/").toString().trimEnd('/')

    @Before fun start() { server = MockWebServer().apply { start() } }
    @After fun stop() = server.shutdown()

    @Test
    fun `server addresses are normalised`() {
        assertEquals("http://192.168.0.102:8000", normalizeServerUrl("192.168.0.102:8000"))
        assertEquals("http://10.0.2.2:8000", normalizeServerUrl(" http://10.0.2.2:8000/ "))
        assertEquals("https://example.org", normalizeServerUrl("https://example.org/api/kb?x=1"))
        assertEquals("http://laptop.local:8000", normalizeServerUrl("laptop.local:8000"))
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("not a url"))
        assertNull(normalizeServerUrl("ftp://host"))
    }

    @Test
    fun `socket and image urls follow the base url`() {
        assertEquals("ws://10.0.2.2:8000/ws/ui", liveSocketUrl("http://10.0.2.2:8000"))
        assertEquals("wss://example.org/ws/ui", liveSocketUrl("https://example.org/"))
        assertEquals("http://h:8000/assets/heritage/a.jpg", heritageImageUrl("http://h:8000", "a.jpg"))
    }

    @Test
    fun `ask posts the question and reads a match`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"question":"the festival of lights","match":{"id":"diwali","category":"festivals","name":"Diwali","motif":"lamp",
               "question":"Why do we celebrate Diwali in India?","alternative_questions":["What is Diwali?"],"aliases":["Deepavali"],
               "answer":"Diwali, the festival of lights.","location":null,"era":"Autumn","category_name":"Festivals",
               "related":[{"id":"rangoli","name":"Rangoli","question":"What is Rangoli?"}],"confidence":0.97,"matched_by":"descriptor",
               "a_field_added_later":123},"match_ms":1.2}""",
        ))
        val res = ApiFactory().forBase(base).ask(AskRequest("the festival of lights"))
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/ask", sent.path)
        assertEquals("""{"question":"the festival of lights"}""", sent.body.readUtf8())
        assertEquals("diwali", res.match?.id)
        assertEquals("descriptor", res.match?.matchedBy)
        assertNull(res.match?.location)
        assertEquals("Rangoli", res.match?.related?.single()?.name)
    }

    @Test
    fun `no match is a null match, not an error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"question":"how do I fix my bike","match":null,"match_ms":4.65}"""))
        assertNull(ApiFactory().forBase(base).ask(AskRequest("how do I fix my bike")).match)
    }

    @Test
    fun `health is read`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"status":"ok","wake_word":"nakshatra","edge_connected":true,"ui_clients":1,
               "asr":{"enabled":true,"engine":"vosk","model":"vosk-model-en-in-0.5","sample_rate":16000},
               "knowledge_base":{"topics":101,"questions":363,"categories":15}}""",
        ))
        val h = ApiFactory().forBase(base).health()
        assertTrue(h.edgeConnected)
        assertEquals(101, h.knowledgeBase.topics)
        assertEquals("vosk-model-en-in-0.5", h.asr.model)
    }

    @Test
    fun `live events decode, including ones this app does not know`() {
        val wake = NakshatraJson.decodeFromString<LiveEvent>("""{"event":"wake","t1":1.7e12,"prob":0.93}""")
        assertEquals("wake", wake.event)
        assertEquals(0.93, wake.prob!!, 1e-9)
        val level = NakshatraJson.decodeFromString<LiveEvent>("""{"event":"level","rms":0.0412,"streaming":true}""")
        assertEquals(0.0412, level.rms!!, 1e-9)
        val content = NakshatraJson.decodeFromString<LiveEvent>("""{"event":"content","transcript":"x","match":null,"t5":5.0,"match_latency_ms":3.0}""")
        assertNull(content.match)
        assertFalse(NakshatraJson.decodeFromString<LiveEvent>("""{"event":"edge_status","connected":false}""").connected!!)
    }

    @Test
    fun `failures are described in words`() {
        assertTrue(describeFailure(ConnectException(), "http://h:8000").contains("http://h:8000"))
    }
}
