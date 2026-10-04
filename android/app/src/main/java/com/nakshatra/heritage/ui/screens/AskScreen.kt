package com.nakshatra.heritage.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nakshatra.heritage.data.KnowledgeBase
import com.nakshatra.heritage.data.Topic
import com.nakshatra.heritage.ui.art.Motif
import com.nakshatra.heritage.ui.art.VoiceOrb
import com.nakshatra.heritage.ui.components.*
import com.nakshatra.heritage.ui.theme.nk
import com.nakshatra.heritage.voice.Answer
import com.nakshatra.heritage.voice.Source
import com.nakshatra.heritage.voice.VoiceState
import com.nakshatra.heritage.voice.VoiceUi
import kotlin.math.roundToInt

// Topics offered as "Try asking" chips, in this order (skipped if absent) -- TRY_IDS in main.js.
private val TRY_IDS = listOf("konark-sun-temple", "diwali", "bharatanatyam", "rani-ki-vav", "hindustani-vs-carnatic", "madhubani", "nalanda", "yoga")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(
    kb: KnowledgeBase?,
    voice: VoiceUi,
    levels: FloatArray,
    serverUrl: String,
    ttsOn: Boolean,
    ttsAvailable: Boolean,
    onAsk: (String) -> Unit,
    onSetTts: (Boolean) -> Unit,
    onOpenTopic: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val accent by animateColorAsState(nk.accent(voice.state), tween(400), label = "accent")
    val offline = voice.state == VoiceState.IDLE && !voice.server
    var input by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        if (input.isNotBlank()) {
            onAsk(input)
            input = ""
            keyboard?.hide()
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(scroll)
            .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                SectionHeader("Ask Nakshatra", "Say the name. Ask the question.", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            StatusPill(voice.server, voice.edge)

            Spacer(Modifier.height(16.dp))
            Stepper(voice.state, accent)

            Spacer(Modifier.height(16.dp))
            NkCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    VoiceOrb(voice.state, levels.lastOrNull() ?: 0f, Modifier.size(132.dp).clearAndSetSemantics {})
                    Waveform(levels, live = voice.state != VoiceState.IDLE, color = accent, modifier = Modifier.fillMaxWidth().height(44.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (offline) "Connecting to the server…" else voice.state.line,
                        style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (offline) "Looking for $serverUrl. You can change the address in Settings." else voice.state.hint,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    if (offline) {
                        TextButton(onOpenSettings) { Text("Server settings") }
                    }

                    val heardText = voice.heard.ifBlank { voice.question }
                    val hearing = voice.state in listOf(VoiceState.WAKE, VoiceState.LISTENING, VoiceState.PROCESSING)
                    if (heardText.isNotBlank() && (hearing || voice.state == VoiceState.ANSWERING)) {
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh).height(IntrinsicSize.Min)) {
                            Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(
                                    when {
                                        voice.state == VoiceState.LISTENING || voice.state == VoiceState.WAKE -> "HEARING"
                                        voice.source == Source.TYPED -> "YOU ASKED"
                                        else -> "HEARD"
                                    },
                                    style = MaterialTheme.typography.labelSmall, color = accent,
                                )
                                Text("“$heardText”", style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input, onValueChange = { if (it.length <= 200) input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type a question", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true, shape = CircleShape,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submit() }),
                        )
                        Spacer(Modifier.width(8.dp))
                        GoldButton("Ask", { submit() }, enabled = input.isNotBlank())
                    }
                    Text(
                        "For example: Why do we celebrate Diwali?", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 16.dp),
                    )

                    if (ttsAvailable) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Read answers aloud", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = ttsOn, onCheckedChange = onSetTts)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            // On a phone the answer card sits below the console: scroll a new answer to the top.
            var answerTop by remember { mutableIntStateOf(0) }
            LaunchedEffect(voice.answer) {
                if (voice.answer != Answer.None) {
                    delay(200)
                    scroll.animateScrollTo(answerTop)
                }
            }
            AnimatedContent(
                targetState = voice.answer,
                transitionSpec = { (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 8 }) togetherWith fadeOut(tween(150)) },
                label = "answer",
                // While a new question is being heard, the previous answer is dimmed, as on the website.
                modifier = Modifier.onGloballyPositioned { answerTop = it.positionInParent().y.toInt() }
                    .alpha(if (voice.answer != Answer.None && voice.state in listOf(VoiceState.WAKE, VoiceState.LISTENING, VoiceState.PROCESSING)) 0.45f else 1f),
            ) { answer ->
                NkCard(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                    when (answer) {
                        Answer.None -> MessageView("Your answer will appear here", "Say “Nakshatra”, then ask — or tap a suggestion.", motif = "wheel")
                        is Answer.Failed -> MessageView("The server did not answer", answer.message) {
                            GhostButton("Server settings", onOpenSettings)
                        }
                        Answer.NoMatch -> NoMatch(voice.question, kb, onAsk)
                        is Answer.Match -> AnswerBody(answer.topic, kb, onAsk, onOpenTopic)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Telemetry(voice)

            val picks = TRY_IDS.mapNotNull { kb?.topic(it) }
            if (picks.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Eyebrow("Try asking")
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    picks.forEach { t -> NkChip(t.question, { onAsk(t.question) }) }
                }
            }
        }
    }
}

/** The six-step state indicator. */
@Composable
private fun Stepper(state: VoiceState, accent: Color) {
    val at = state.ordinal
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { contentDescription = "Voice state: ${state.label}" }) {
        VoiceState.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s ->
                    val active = s.ordinal == at
                    val done = s.ordinal < at && at > 1
                    Column(
                        Modifier.weight(1f).heightIn(min = 62.dp).clip(MaterialTheme.shapes.small)
                            .background(if (active) accent else scheme.surfaceContainerLow)
                            .border(1.dp, if (active) accent else if (done) scheme.primary.copy(alpha = 0.4f) else nk.hairline, MaterialTheme.shapes.small)
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                            .clearAndSetSemantics {},
                    ) {
                        val on = if (active) Color(0xFF140D12) else if (done) scheme.onSurface else scheme.onSurfaceVariant
                        Text("0${s.ordinal + 1}", style = MaterialTheme.typography.labelSmall, color = on.copy(alpha = 0.75f))
                        Text(s.label, style = MaterialTheme.typography.labelMedium, color = on, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The real input level: from the device while idle, from its audio while it streams. */
@Composable
private fun Waveform(levels: FloatArray, live: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val n = levels.size
        val bar = size.width / n
        for (i in 0 until n) {
            val amp = if (live) maxOf(0.03f, levels[i]) else 0.03f
            val h = minOf(size.height, amp * size.height * 0.96f)
            drawRoundRect(
                color.copy(alpha = 0.25f + 0.75f * i / n),
                topLeft = Offset(i * bar + bar * 0.2f, (size.height - h) / 2f), size = Size(bar * 0.6f, h),
                cornerRadius = CornerRadius(bar * 0.3f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerBody(topic: Topic, kb: KnowledgeBase?, onAsk: (String) -> Unit, onOpenTopic: (String) -> Unit) {
    Box {
        Motif(
            kb?.motifOf(topic) ?: topic.motif ?: "mandala",
            Modifier.align(Alignment.TopEnd).offset(x = 36.dp, y = (-26).dp).size(190.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), baseStroke = 1.6f,
        )
        Column(Modifier.padding(20.dp)) {
            Text(
                topic.categoryName.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 5.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(topic.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            MetaRow(listOfNotNull(topic.location?.label, topic.era))
            Spacer(Modifier.height(14.dp))
            Text(topic.answer, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { onOpenTopic(topic.id) }, contentPadding = PaddingValues(0.dp)) { Text("Open ${topic.name}") }
            if (topic.related.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = nk.hairline)
                Text("RELATED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    topic.related.forEach { r -> NkChip(r.name, { onAsk(r.question) }) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoMatch(question: String, kb: KnowledgeBase?, onAsk: (String) -> Unit) {
    Column(Modifier.padding(20.dp)) {
        Text(
            "NO MATCH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 5.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("That is not in my archive yet", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            "I could not connect ${if (question.isNotBlank()) "“$question”" else "that"} to a topic I know. " +
                "Try naming a monument, a festival, a dance form or a craft.",
            style = MaterialTheme.typography.bodyLarge,
        )
        val picks = TRY_IDS.take(4).mapNotNull { kb?.topic(it) }
        if (picks.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 10.dp), color = nk.hairline)
            Text("TRY ONE OF THESE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                picks.forEach { t -> NkChip(t.question, { onAsk(t.question) }) }
            }
        }
    }
}

/** The four telemetry tiles of the website's console. */
@Composable
private fun Telemetry(voice: VoiceUi) {
    val match = (voice.answer as? Answer.Match)?.topic
    val typed = voice.source == Source.TYPED
    val total = if (voice.t1 != null && voice.t5 != null) (voice.t5 - voice.t1).coerceAtLeast(0.0) else null
    val wakeToAudio = if (voice.t1 != null && voice.t2 != null) (voice.t2 - voice.t1).coerceAtLeast(0.0).roundToInt() else null
    val tiles = listOf(
        "Edge device" to if (voice.edge) "Connected" else if (voice.server) "Offline" else "No server",
        "Wake confidence" to (voice.wakeProb?.let { "${(it * 100).roundToInt()}%" } ?: if (typed) "Typed" else "—"),
        if (typed) "Match time" to (voice.matchMs?.let { "$it ms" } ?: "—")
        else "Wake to answer" to (total?.let { "%.1f s".format(it / 1000) + (wakeToAudio?.let { ms -> " · audio in $ms ms" } ?: "") } ?: "—"),
        "Matched by" to (match?.let { "${it.matchedBy ?: "—"}${it.confidence?.let { c -> " ${(c * 100).roundToInt()}%" } ?: ""}" } ?: "—"),
    )
    NkCard(Modifier.fillMaxWidth()) {
        tiles.chunked(2).forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = nk.hairline)
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {}) {
                        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
