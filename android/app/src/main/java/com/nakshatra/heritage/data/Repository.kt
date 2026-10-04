package com.nakshatra.heritage.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class Settings(
    val serverUrl: String,
    val ttsOn: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
)

/** User preferences. The website keeps the same two (spoken answers, voice) in localStorage. */
class SettingsStore(context: Context, private val defaultServerUrl: String) {
    private val prefs = context.getSharedPreferences("nakshatra", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        Settings(
            serverUrl = prefs.getString(KEY_SERVER, null) ?: defaultServerUrl,
            ttsOn = prefs.getBoolean(KEY_TTS, true),
            theme = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
        ),
    )
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER, url).apply()
        _settings.update { it.copy(serverUrl = url) }
    }

    fun setTts(on: Boolean) {
        prefs.edit().putBoolean(KEY_TTS, on).apply()
        _settings.update { it.copy(ttsOn = on) }
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _settings.update { it.copy(theme = mode) }
    }

    private companion object {
        const val KEY_SERVER = "server_url"
        const val KEY_TTS = "tts_on"
        const val KEY_THEME = "theme"
    }
}

/** What the screens know about the knowledge base. */
data class KbState(
    val kb: KnowledgeBase? = null,
    val loading: Boolean = true,
    /** Why the last refresh failed, in words a visitor can act on. */
    val error: String? = null,
    /** True while [kb] is the copy saved from an earlier session, not a fresh reply. */
    val fromCache: Boolean = false,
)

/** A network failure as a sentence. */
fun describeFailure(t: Throwable, serverUrl: String): String = when (t) {
    is ConnectException, is UnknownHostException -> "Could not reach the Nakshatra server at $serverUrl."
    is SocketTimeoutException -> "The Nakshatra server at $serverUrl did not respond in time."
    is HttpException -> "The server at $serverUrl answered with HTTP ${t.code()}."
    is IOException -> "The connection to $serverUrl was interrupted."
    else -> "The server at $serverUrl sent a reply this app could not read."
}

/**
 * The single source of heritage content for the app: the same FastAPI server
 * the website talks to. The last good reply of `/api/kb` is kept on disk so the
 * archive can still be read when the server is out of reach; it is never a
 * substitute for the server and is replaced by every successful refresh.
 */
class HeritageRepository(
    private val apis: ApiFactory,
    private val settings: SettingsStore,
    private val cacheFile: File,
) {
    private val _state = MutableStateFlow(KbState())
    val state: StateFlow<KbState> = _state.asStateFlow()
    private val refreshLock = Mutex()

    private val baseUrl: String get() = settings.settings.value.serverUrl
    private fun api() = apis.forBase(baseUrl)

    suspend fun refresh() = refreshLock.withLock {
        val url = baseUrl
        _state.update { it.copy(loading = true) }
        try {
            val kb = withContext(Dispatchers.IO) { apis.forBase(url).knowledgeBase() }
            _state.value = KbState(kb = kb, loading = false)
            withContext(Dispatchers.IO) {
                runCatching { cacheFile.writeText(NakshatraJson.encodeToString(KnowledgeBase.serializer(), kb)) }
            }
        } catch (t: Exception) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            val cached = _state.value.kb ?: withContext(Dispatchers.IO) { readCache() }
            _state.value = KbState(kb = cached, loading = false, error = describeFailure(t, url), fromCache = cached != null)
        }
    }

    private fun readCache(): KnowledgeBase? = runCatching {
        if (!cacheFile.exists()) null
        else NakshatraJson.decodeFromString(KnowledgeBase.serializer(), cacheFile.readText()).takeIf { it.topics.isNotEmpty() }
    }.getOrNull()

    suspend fun ask(question: String): AskResponse = withContext(Dispatchers.IO) { api().ask(AskRequest(question)) }

    /** Health of the server at [url] (defaults to the saved address). */
    suspend fun health(url: String = baseUrl): Health = withContext(Dispatchers.IO) { apis.forBase(url).health() }
}
