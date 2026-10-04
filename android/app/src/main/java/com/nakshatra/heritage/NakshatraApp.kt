package com.nakshatra.heritage

import android.app.Application
import com.nakshatra.heritage.data.ApiFactory
import com.nakshatra.heritage.data.HeritageRepository
import com.nakshatra.heritage.data.LiveSocket
import com.nakshatra.heritage.data.SettingsStore
import com.nakshatra.heritage.data.describeFailure
import com.nakshatra.heritage.voice.AndroidSpeaker
import com.nakshatra.heritage.voice.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Owns the objects that live as long as the process: one server connection, one voice engine. */
class NakshatraApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settings: SettingsStore
        private set
    lateinit var repository: HeritageRepository
        private set
    lateinit var speaker: AndroidSpeaker
        private set
    lateinit var voice: VoiceEngine
        private set
    private lateinit var socket: LiveSocket

    override fun onCreate() {
        super.onCreate()
        val apis = ApiFactory()
        settings = SettingsStore(this, BuildConfig.DEFAULT_SERVER_URL)
        repository = HeritageRepository(apis, settings, File(filesDir, "kb-cache.json"))
        speaker = AndroidSpeaker(this)
        voice = VoiceEngine(
            scope = scope,
            ask = repository::ask,
            speaker = speaker,
            ttsOn = { settings.settings.value.ttsOn },
            describeFailure = { describeFailure(it, settings.settings.value.serverUrl) },
        )
        socket = LiveSocket(
            http = apis.http,
            scope = scope,
            onEvent = { event -> scope.launch { voice.onEvent(event) } },
            onLink = { open -> scope.launch { voice.onLink(open) } },
        )
    }

    /** Called while the app is on screen: (re)load the archive and follow the live pipeline. */
    fun connect() {
        socket.connect(settings.settings.value.serverUrl)
        scope.launch { repository.refresh() }
    }

    /** Called when the app leaves the screen, so no socket is held in the background. */
    fun disconnect() {
        socket.disconnect()
        speaker.stop()
    }

    /** Switches to another server address and reconnects everything to it. */
    fun useServer(url: String) {
        settings.setServerUrl(url)
        connect()
    }
}
