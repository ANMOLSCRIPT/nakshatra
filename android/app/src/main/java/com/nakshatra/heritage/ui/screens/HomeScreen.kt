package com.nakshatra.heritage.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nakshatra.heritage.data.Category
import com.nakshatra.heritage.data.KbState
import com.nakshatra.heritage.data.KnowledgeBase
import com.nakshatra.heritage.data.Topic
import com.nakshatra.heritage.data.heritageImageUrl
import com.nakshatra.heritage.ui.art.Motif
import com.nakshatra.heritage.ui.art.VoiceOrb
import com.nakshatra.heritage.ui.art.categoryBrush
import com.nakshatra.heritage.ui.art.featuredBrush
import com.nakshatra.heritage.ui.components.*
import com.nakshatra.heritage.ui.theme.Brand
import com.nakshatra.heritage.ui.theme.nk
import com.nakshatra.heritage.voice.VoiceState
import com.nakshatra.heritage.voice.VoiceUi

// Star positions of the website's hero sky (fractions of width, height).
private val SKY = listOf(0.12f to 0.22f, 0.31f to 0.64f, 0.47f to 0.14f, 0.58f to 0.78f, 0.69f to 0.09f, 0.88f to 0.86f, 0.94f to 0.18f, 0.22f to 0.88f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: KbState,
    voice: VoiceUi,
    level: Float,
    serverUrl: String,
    onRefresh: () -> Unit,
    onOpenAsk: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTopic: (Topic) -> Unit,
    onOpenCategory: (Category) -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.loading && state.kb != null, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            item(key = "hero") { Hero(state.kb, voice, level, onOpenAsk, onOpenArchive, onOpenSettings) }

            val kb = state.kb
            if (kb == null) {
                item(key = "state") {
                    if (state.loading) {
                        LoadingView(Modifier.height(260.dp))
                    } else {
                        MessageView(
                            title = "The archive is out of reach",
                            body = (state.error ?: "The Nakshatra server could not be reached.") +
                                " Start it with “python backend/cloud_server.py” and make sure this device is on the same network.",
                        ) {
                            GoldButton("Try again", onRefresh)
                            Spacer(Modifier.height(8.dp))
                            GhostButton("Server settings", onOpenSettings)
                        }
                    }
                }
                return@LazyColumn
            }

            if (state.fromCache && state.error != null) {
                item(key = "offline") { OfflineBanner(state.error, onRefresh, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            }

            item(key = "featured-head") {
                SectionHeader(
                    "Featured heritage", "${kb.featured.size.spelled()} places every Indian story passes through",
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp),
                )
            }
            item(key = "featured") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(kb.featured, key = { _, t -> t.id }) { i, topic ->
                        FeaturedCard(topic, kb, i, serverUrl) { onOpenTopic(topic) }
                    }
                }
            }

            item(key = "explore-head") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 34.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SectionHeader("Explore India", "${kb.categories.size.spelled()} doors into a living heritage", Modifier.weight(1f))
                    TextButton(onOpenExplore) { Text("All themes") }
                }
            }
            item(key = "explore") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(kb.categories, key = { it.id }) { c ->
                        CategoryTile(c, kb.topicsIn(c.id).size, Modifier.width(156.dp)) { onOpenCategory(c) }
                    }
                }
            }

            item(key = "how") { HowItWorks(kb, voice.state, Modifier.padding(horizontal = 20.dp).padding(top = 34.dp)) }

            item(key = "foot") {
                Text(
                    "Wake word: “Nakshatra” · detected on-device\n${kb.topics.size} topics · ${kb.questionCount} questions · ${kb.categories.size} themes",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 26.dp),
                )
            }
        }
    }
}

internal fun Int.spelled(): String = listOf("No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
    "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen", "Twenty")
    .getOrElse(this) { toString() }

@Composable
private fun Hero(
    kb: KnowledgeBase?,
    voice: VoiceUi,
    level: Float,
    onOpenAsk: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = nk
    val accent by animateColorAsState(colors.accent(voice.state), tween(400), label = "accent")
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom)))
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(Brand.Saffron.copy(alpha = if (colors.dark) 0.20f else 0.13f), Color.Transparent),
                    Offset(size.width * 0.5f, size.height * 0.30f), size.width * 0.85f))
                drawRect(Brush.radialGradient(listOf(Brand.Vermilion.copy(alpha = if (colors.dark) 0.16f else 0.08f), Color.Transparent),
                    Offset(0f, 0f), size.width * 0.8f))
                if (colors.dark) SKY.forEach { (x, y) -> drawCircle(colors.star.copy(alpha = 0.8f), 1.6.dp.toPx() / 2 + 0.6f, Offset(size.width * x, size.height * y * 0.42f)) }
            },
    ) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Motif("wheel", Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary, baseStroke = 7f)
                Spacer(Modifier.width(10.dp))
                Text("Nakshatra", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings and about") }
            }

            Box(
                Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 260.dp).fillMaxWidth(0.62f).aspectRatio(1f)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Open Ask Nakshatra", onClick = onOpenAsk)
                    .semantics { contentDescription = "Voice orb. ${if (voice.server) voice.state.line else "Connecting to the server"}" },
            ) { VoiceOrb(voice.state, level, Modifier.fillMaxSize()) }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .border(1.dp, accent.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(10.dp))
                Text(if (voice.server) voice.state.line else "Connecting to the server…", style = MaterialTheme.typography.labelLarge)
            }
            if (voice.heard.isNotBlank() && voice.state in listOf(VoiceState.WAKE, VoiceState.LISTENING, VoiceState.PROCESSING)) {
                Text(
                    "“${voice.heard}”", style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }

            Column(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(top = 22.dp)) {
                Eyebrow("Smart India Hackathon · Problem Statement 214")
                Spacer(Modifier.height(12.dp))
                Text(
                    buildAnnotatedString {
                        append("Discover India.\n")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)) {
                            append("One question at a time.")
                        }
                    },
                    style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Nakshatra is a voice companion for India's cultural heritage. Say its name, ask about a temple, a festival, " +
                        "a dance or a craft, and it answers aloud — with the wake word recognised on a tiny ESP32, not in the cloud.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GoldButton("Ask Nakshatra", onOpenAsk)
                    GhostButton("Explore the archive", onOpenArchive)
                }
                if (kb != null) {
                    HorizontalDivider(Modifier.padding(top = 24.dp, bottom = 16.dp), color = colors.hairline)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat(kb.topics.size, "heritage topics")
                        Stat(kb.questionCount, "ways to ask")
                        Stat(kb.categories.size, "themes")
                        Stat(kb.placeCount, "places on the map")
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: Int, label: String) {
    Column(Modifier.widthIn(max = 84.dp).semantics(mergeDescendants = true) {}) {
        Text(value.toString(), style = MaterialTheme.typography.displaySmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A featured card: jewel-tone gradient, the topic's line art (or its photograph), place, name, teaser. */
@Composable
fun FeaturedCard(topic: Topic, kb: KnowledgeBase, index: Int, serverUrl: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 236.dp, height = 320.dp)
            .clip(MaterialTheme.shapes.large)
            .background(featuredBrush(index))
            .clickable(role = Role.Button, onClickLabel = "Open ${topic.name}", onClick = onClick),
    ) {
        if (topic.image != null) {
            AsyncImage(heritageImageUrl(serverUrl, topic.image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Motif(
                kb.motifOf(topic), Modifier.align(Alignment.TopCenter).padding(top = 22.dp).size(140.dp),
                color = Color(0xFFFFF0D2).copy(alpha = 0.9f), baseStroke = 1.6f,
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.38f to Color.Transparent, 1f to Color(0xE00E0706))))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(
                (topic.placeLine ?: topic.categoryName).uppercase(), style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFF6D99A), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(topic.name, style = MaterialTheme.typography.headlineSmall, color = Color(0xFFFFF8EA), maxLines = 2)
            Spacer(Modifier.height(6.dp))
            Text(
                topic.firstSentence, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFF4DE).copy(alpha = 0.86f),
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A theme: its motif on the category gradient, its name and how many topics it holds. */
@Composable
fun CategoryTile(category: Category, count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(categoryBrush(category.id))
            .clickable(role = Role.Button, onClickLabel = "Open ${category.name}", onClick = onClick)
            .padding(16.dp),
    ) {
        Motif(category.motif, Modifier.size(44.dp), color = Color(0xFFFFE9B8), baseStroke = 4.5f)
        Spacer(Modifier.height(14.dp))
        Text(
            category.label, style = MaterialTheme.typography.titleLarge, color = Color(0xFFFFF8EA),
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text("$count topic${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFF4DE).copy(alpha = 0.8f))
    }
}

private class Step(val title: String, val body: String, val where: String, val onDevice: Boolean, val states: Set<VoiceState>)

@Composable
private fun HowItWorks(kb: KnowledgeBase, state: VoiceState, modifier: Modifier = Modifier) {
    val steps = listOf(
        Step("Wake word", "An ESP32 listens through an INMP441 microphone and runs a 44.8 kB keyword model every 100 ms. It reacts to one word only: Nakshatra.", "On device", true, setOf(VoiceState.WAKE)),
        Step("Voice capture", "The question that follows is streamed over Wi-Fi as raw 16 kHz audio in 100 ms frames, with 300 ms of pre-roll so the first word is never clipped.", "Edge to Wi-Fi", true, setOf(VoiceState.LISTENING)),
        Step("AI understanding", "A streaming speech recogniser turns the audio into text as you speak, so the words appear on screen before you finish the sentence.", "Server ASR", false, setOf(VoiceState.LISTENING)),
        Step("Heritage knowledge", "The answer engine matches the question against ${kb.topics.size} topics by name, by description and by sound, so many wordings reach the same answer.", "Knowledge base", false, setOf(VoiceState.PROCESSING)),
        Step("Spoken answer", "A short, accurate answer is shown and read aloud, and the device goes back to listening for its name.", "App / device", false, setOf(VoiceState.ANSWERING)),
    )
    Column(modifier.widthIn(max = MaxContentWidth)) {
        SectionHeader(
            "How Nakshatra works", "From a spoken word to a spoken answer",
            lede = "A hybrid edge-and-cloud pipeline. The device stays silent on the network until it hears its name; only then does your question travel to the server.",
        )
        Spacer(Modifier.height(18.dp))
        steps.forEachIndexed { i, step ->
            val active = state in step.states
            val scheme = MaterialTheme.colorScheme
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(if (active) scheme.secondary else Color.Transparent)
                        .border(1.5.dp, if (active) scheme.secondary else scheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("0${i + 1}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (active) scheme.onSecondary else scheme.primary)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(step.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(2.dp))
                    Text(step.body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        step.where.uppercase(), style = MaterialTheme.typography.labelSmall,
                        color = if (step.onDevice) nk.onEdgeChip else nk.onCloudChip,
                        modifier = Modifier.clip(CircleShape).background(if (step.onDevice) nk.edgeChip else nk.cloudChip)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        NkCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                Fact("44.8", "kB", "int8 keyword model that fits an ESP32", Modifier.weight(1f))
                Fact("100", "ms", "between wake-word inferences", Modifier.weight(1f))
            }
            HorizontalDivider(color = nk.hairline)
            Row(Modifier.fillMaxWidth()) {
                Fact("16", "kHz", "mono audio, streamed only after the wake word", Modifier.weight(1f))
                Fact(kb.questionCount.toString(), "", "question phrasings understood", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Fact(value: String, unit: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp).semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            if (unit.isNotEmpty()) {
                Text(unit, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
