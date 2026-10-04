package com.nakshatra.heritage.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nakshatra.heritage.BuildConfig
import com.nakshatra.heritage.ServerCheck
import com.nakshatra.heritage.data.Settings
import com.nakshatra.heritage.data.ThemeMode
import com.nakshatra.heritage.ui.art.Motif
import com.nakshatra.heritage.ui.components.*
import com.nakshatra.heritage.ui.theme.nk

private val TEAM = listOf(
    "Radhika Chopra" to "Embedded",
    "Shireen Sandilya" to "Embedded",
    "Ansh Jayara" to "Embedded",
    "Kavyansh Malhotra" to "ML",
    "Anmol Garg" to "Embedded / Cloud",
    "Harshit Sharma" to "Cloud / ML",
)

private val TECHNOLOGY = listOf(
    "ESP32-WROOM-32 · INMP441 I²S microphone",
    "DS-CNN-S keyword model, int8 TFLite Micro",
    "MFCC front end · WebSocket PCM streaming",
    "Vosk ASR · FastAPI · rapidfuzz answer engine",
    "Android app: Kotlin · Jetpack Compose · Material 3",
)

@Composable
fun SettingsScreen(
    settings: Settings,
    check: ServerCheck,
    ttsAvailable: Boolean,
    onBack: () -> Unit,
    onSaveServer: (String) -> Boolean,
    onCheckServer: () -> Unit,
    onSetTts: (Boolean) -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
) {
    var address by rememberSaveable(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { onCheckServer() }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) {
            Row(Modifier.padding(start = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
            }

            Group("Nakshatra server") {
                Text(
                    "The app reads the archive and asks its questions on the same server as the website and the ESP32. " +
                        "Enter the address of the computer running backend/cloud_server.py. On the Android emulator that is 10.0.2.2:8000.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Server address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { onSaveServer(address); keyboard?.hide() }),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GoldButton("Save and connect", { onSaveServer(address); keyboard?.hide() })
                    GhostButton("Use default", { address = BuildConfig.DEFAULT_SERVER_URL; onSaveServer(BuildConfig.DEFAULT_SERVER_URL) })
                }
                Spacer(Modifier.height(14.dp))
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    when (check) {
                        ServerCheck.Idle -> Unit
                        ServerCheck.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Contacting the server…", style = MaterialTheme.typography.bodyMedium)
                        }
                        is ServerCheck.Failed -> Text(check.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        is ServerCheck.Ok -> {
                            val h = check.health
                            Text("Connected to ${check.url}", style = MaterialTheme.typography.titleSmall, color = nk.live)
                            Spacer(Modifier.height(6.dp))
                            Fact("Knowledge base", "${h.knowledgeBase.topics} topics · ${h.knowledgeBase.questions} questions · ${h.knowledgeBase.categories} themes")
                            Fact("Speech recognition", if (h.asr.enabled) "${h.asr.engine} · ${h.asr.model}" else "Not loaded on the server")
                            Fact("Edge device", if (h.edgeConnected) "Connected" else "Not connected")
                            Fact("Wake word", h.wakeWord.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }

            if (ttsAvailable) {
                Group("Spoken answers") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Read answers aloud", style = MaterialTheme.typography.titleMedium)
                            Text("Uses this device's text-to-speech voice, Indian English when installed.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = settings.ttsOn, onCheckedChange = onSetTts)
                    }
                }
            }

            Group("Appearance") {
                Column(Modifier.selectableGroup()) {
                    listOf(ThemeMode.SYSTEM to "Follow the system", ThemeMode.DARK to "Lamp-black (dark)", ThemeMode.LIGHT to "Sandstone (light)").forEach { (mode, label) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = settings.theme == mode, role = Role.RadioButton, onClick = { onSetTheme(mode) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.theme == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Group("About Nakshatra") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Motif("wheel", Modifier.size(44.dp), color = MaterialTheme.colorScheme.primary, baseStroke = 5f)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Nakshatra", style = MaterialTheme.typography.headlineSmall)
                        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "An intelligent digital gateway to India's cultural heritage, built on a low-power voice-activated edge device. " +
                        "This app is the mobile companion of the Nakshatra website: the same archive, the same answer engine and a live view of the same voice pipeline.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                Eyebrow("Smart India Hackathon")
                Spacer(Modifier.height(6.dp))
                Fact("Problem statement", "214 — Cultural Heritage and Traditions of India")
                Fact("PS ID", "SIH26214 · Hardware edition")
                Spacer(Modifier.height(14.dp))
                Eyebrow("Technology")
                Spacer(Modifier.height(6.dp))
                TECHNOLOGY.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
                Spacer(Modifier.height(14.dp))
                Eyebrow("Team")
                Spacer(Modifier.height(6.dp))
                TEAM.forEach { (name, role) -> Fact(name, role) }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
        NkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Eyebrow(title)
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
    }
}
