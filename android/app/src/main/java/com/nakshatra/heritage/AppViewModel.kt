package com.nakshatra.heritage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nakshatra.heritage.data.Health
import com.nakshatra.heritage.data.ThemeMode
import com.nakshatra.heritage.data.describeFailure
import com.nakshatra.heritage.data.normalizeServerUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The result of asking a server for its health. */
sealed interface ServerCheck {
    data object Idle : ServerCheck
    data object Checking : ServerCheck
    data class Ok(val url: String, val health: Health) : ServerCheck
    data class Failed(val message: String) : ServerCheck
}

/** Screen-facing view of the process-wide objects in [NakshatraApp]. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NakshatraApp

    val kb = app.repository.state
    val settings = app.settings.settings
    val voice = app.voice.ui
    val levels = app.voice.levels
    val speaking = app.speaker.speaking
    val ttsAvailable = app.speaker.available

    private val _check = MutableStateFlow<ServerCheck>(ServerCheck.Idle)
    val check: StateFlow<ServerCheck> = _check.asStateFlow()
    private var checkJob: Job? = null

    fun refresh() {
        viewModelScope.launch { app.repository.refresh() }
    }

    fun ask(question: String) = app.voice.ask(question)

    /** Reads [text] aloud, or stops if it is the text being read. */
    fun toggleReadAloud(text: String) {
        if (speaking.value == text) app.speaker.stop() else app.speaker.speak(text)
    }

    fun setTts(on: Boolean) {
        app.settings.setTts(on)
        if (!on) app.speaker.stop()
    }

    fun setTheme(mode: ThemeMode) = app.settings.setTheme(mode)

    /** Asks the saved server for its status (shown on the Settings screen). */
    fun checkServer() = runCheck(settings.value.serverUrl)

    /** Saves a new server address, reconnects to it and reports whether it answers. */
    fun saveServer(input: String): Boolean {
        val url = normalizeServerUrl(input)
        if (url == null) {
            _check.value = ServerCheck.Failed("That is not a server address. Use the form 192.168.0.10:8000.")
            return false
        }
        if (url != settings.value.serverUrl) app.useServer(url)
        runCheck(url)
        return true
    }

    private fun runCheck(url: String) {
        checkJob?.cancel()
        _check.value = ServerCheck.Checking
        checkJob = viewModelScope.launch {
            _check.value = try {
                ServerCheck.Ok(url, app.repository.health(url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ServerCheck.Failed(describeFailure(e, url))
            }
        }
    }
}
