package com.nakshatra.heritage.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/** The REST API of backend/cloud_server.py -- the same three calls frontend/js/api.js makes. */
interface NakshatraApi {
    @GET("api/kb")
    suspend fun knowledgeBase(): KnowledgeBase

    @GET("api/health")
    suspend fun health(): Health

    @POST("api/ask")
    suspend fun ask(@Body body: AskRequest): AskResponse
}

/**
 * Turns what a person types into a server address ("192.168.0.102:8000",
 * "http://10.0.2.2:8000/") into a canonical base URL without a trailing slash,
 * or null if it is not a usable http(s) address.
 */
fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)) trimmed else "http://$trimmed"
    val url = withScheme.toHttpUrlOrNull() ?: return null
    if (url.host.isBlank()) return null
    return url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/')
}

/** http://host:8000 -> ws://host:8000/ws/ui */
fun liveSocketUrl(baseUrl: String): String =
    baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://").trimEnd('/') + "/ws/ui"

/** Where an optional topic photograph lives on the server. */
fun heritageImageUrl(baseUrl: String, image: String): String = baseUrl.trimEnd('/') + "/assets/heritage/" + image

class ApiFactory(val http: OkHttpClient = defaultClient()) {
    private val cache = HashMap<String, NakshatraApi>()

    @Synchronized
    fun forBase(baseUrl: String): NakshatraApi = cache.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(http)
            .addConverterFactory(NakshatraJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NakshatraApi::class.java)
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Keeps a connection to `/ws/ui` alive with the website's backoff (1 s growing
 * by 1.7x to 8 s). [onLink] reports the socket state; [onEvent] receives every
 * broadcast. Both are called on OkHttp's reader thread.
 */
class LiveSocket(
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val onEvent: (LiveEvent) -> Unit,
    private val onLink: (Boolean) -> Unit,
) {
    private var socket: WebSocket? = null
    private var retry: Job? = null
    private var url: String? = null
    private var delayMs = MIN_DELAY

    /** Connects to [baseUrl], replacing any current connection. */
    @Synchronized
    fun connect(baseUrl: String) {
        closeCurrent()
        url = liveSocketUrl(baseUrl)
        delayMs = MIN_DELAY
        open()
    }

    @Synchronized
    fun disconnect() {
        url = null
        closeCurrent()
        onLink(false)
    }

    private fun closeCurrent() {
        retry?.cancel()
        retry = null
        socket?.cancel()
        socket = null
    }

    @Synchronized
    private fun open() {
        val target = url ?: return
        val request = try {
            Request.Builder().url(target).build()
        } catch (_: IllegalArgumentException) {
            return
        }
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrent(webSocket)) return
                delayMs = MIN_DELAY
                onLink(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent(webSocket)) return
                val event = runCatching { NakshatraJson.decodeFromString<LiveEvent>(text) }.getOrNull() ?: return
                onEvent(event)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = dropped(webSocket)
        })
    }

    @Synchronized
    private fun isCurrent(ws: WebSocket) = ws === socket

    @Synchronized
    private fun dropped(ws: WebSocket) {
        if (ws !== socket) return
        socket = null
        onLink(false)
        val wait = delayMs
        delayMs = (delayMs * 1.7).toLong().coerceAtMost(MAX_DELAY)
        retry = scope.launch {
            delay(wait)
            open()
        }
    }

    private companion object {
        const val MIN_DELAY = 1000L
        const val MAX_DELAY = 8000L
    }
}
