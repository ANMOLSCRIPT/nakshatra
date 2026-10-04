package com.nakshatra.heritage.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nakshatra.heritage.data.KbState
import com.nakshatra.heritage.data.KnowledgeBase
import com.nakshatra.heritage.data.Topic
import com.nakshatra.heritage.data.search
import com.nakshatra.heritage.ui.art.Motif
import com.nakshatra.heritage.ui.components.*

/** The archive: every topic, its answer and the ways to ask, with search and a theme filter. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArchiveScreen(
    state: KbState,
    speaking: String?,
    ttsAvailable: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onReadAloud: (String) -> Unit,
    onAsk: (String) -> Unit,
    onOpenTopic: (Topic) -> Unit,
) {
    KbContent(state, onRefresh, onOpenSettings, Modifier.statusBarsPadding()) { kb ->
        var query by rememberSaveable { mutableStateOf("") }
        var filter by rememberSaveable { mutableStateOf<String?>(null) }
        var open by rememberSaveable { mutableStateOf<String?>(null) }

        val groups = remember(kb, query, filter) {
            kb.categories
                .filter { filter == null || it.id == filter }
                .map { c -> c to kb.topicsIn(c.id).search(query) }
                .filter { it.second.isNotEmpty() }
        }
        val shown = groups.sumOf { it.second.size }

        PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().statusBarsPadding().imePadding(), contentPadding = PaddingValues(top = 20.dp, bottom = 28.dp)) {
                item(key = "head") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        if (state.fromCache && state.error != null) OfflineBanner(state.error, onRefresh, Modifier.padding(bottom = 16.dp))
                        SectionHeader(
                            "The archive", "Everything Nakshatra can answer",
                            lede = "${kb.topics.size} topics across ${kb.categories.size} themes, reachable through ${kb.questionCount} listed phrasings — " +
                                "and many more, because the answer engine matches by name, description and sound.",
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = CircleShape,
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Outlined.Close, "Clear search") }
                            },
                            placeholder = { Text("Search the archive", maxLines = 1) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        )
                        Text(
                            "Try silk, Odisha or harvest.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                        )
                    }
                }
                item(key = "filters") {
                    LazyRow(
                        Modifier.padding(top = 6.dp), contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { NkChip("All", { filter = null }, selected = filter == null) }
                        items(kb.categories, key = { it.id }) { c ->
                            NkChip(c.label, { filter = if (filter == c.id) null else c.id }, selected = filter == c.id)
                        }
                    }
                }

                if (groups.isEmpty()) {
                    item(key = "empty") {
                        MessageView(
                            "Nothing matches",
                            if (query.isBlank()) "This theme has no topics yet." else "Nothing in the archive matches “${query.trim()}”.",
                            motif = "pot",
                        ) {
                            GhostButton("Clear search", { query = ""; filter = null })
                        }
                    }
                }

                groups.forEach { (category, topics) ->
                    item(key = "g-${category.id}") {
                        Text(
                            category.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp).semantics { heading() },
                        )
                    }
                    items(topics, key = { it.id }) { t ->
                        // As on the website, a search that narrows to a few results shows them opened.
                        val expanded = open == t.id || (query.isNotBlank() && shown <= 3)
                        ArchiveItem(
                            t, kb, expanded, speaking == t.answer, ttsAvailable,
                            onToggle = { open = if (open == t.id) null else t.id },
                            onReadAloud = { onReadAloud(t.answer) }, onAsk = { onAsk(t.question) }, onOpen = { onOpenTopic(t) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchiveItem(
    topic: Topic,
    kb: KnowledgeBase,
    expanded: Boolean,
    speakingThis: Boolean,
    ttsAvailable: Boolean,
    onToggle: () -> Unit,
    onReadAloud: () -> Unit,
    onAsk: () -> Unit,
    onOpen: () -> Unit,
) {
    val turn by animateFloatAsState(if (expanded) 45f else 0f, label = "turn")
    NkCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Motif(kb.motifOf(topic), Modifier.size(30.dp), color = MaterialTheme.colorScheme.primary, baseStroke = 5f)
            Spacer(Modifier.width(14.dp))
            Text(topic.question, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.Add, null, Modifier.rotate(turn), tint = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                Text(topic.answer, style = MaterialTheme.typography.bodyMedium)
                val where = listOfNotNull(topic.location?.label, topic.era)
                if (where.isNotEmpty()) MetaRow(where, Modifier.padding(top = 10.dp))
                if (topic.alternativeQuestions.isNotEmpty()) {
                    Text(
                        topic.alternativeQuestions.joinToString("\n") { "“$it”" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (ttsAvailable) TextButton(onReadAloud) { Text(if (speakingThis) "Stop" else "Read aloud") }
                    TextButton(onAsk) { Text("Ask Nakshatra") }
                    TextButton(onOpen) { Text("Open") }
                }
            }
        }
    }
}
