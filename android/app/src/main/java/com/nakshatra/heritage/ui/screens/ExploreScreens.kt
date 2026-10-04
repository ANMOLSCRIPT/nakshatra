package com.nakshatra.heritage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nakshatra.heritage.data.Category
import com.nakshatra.heritage.data.KbState
import com.nakshatra.heritage.data.KnowledgeBase
import com.nakshatra.heritage.data.Topic
import com.nakshatra.heritage.data.heritageImageUrl
import com.nakshatra.heritage.ui.art.Motif
import com.nakshatra.heritage.ui.art.categoryBrush
import com.nakshatra.heritage.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    state: KbState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCategory: (Category) -> Unit,
) {
    KbContent(state, onRefresh, onOpenSettings, Modifier.statusBarsPadding()) { kb ->
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "head") {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        if (state.fromCache && state.error != null) {
                            OfflineBanner(state.error, onRefresh, Modifier.padding(bottom = 16.dp))
                        }
                        SectionHeader(
                            "Explore India", "${kb.categories.size.spelled()} doors into a living heritage",
                            lede = "Choose a theme to see what Nakshatra can tell you, then open any topic to read or hear the answer.",
                        )
                    }
                }
                items(kb.categories, key = { it.id }) { c ->
                    CategoryTile(c, kb.topicsIn(c.id).size, Modifier.fillMaxWidth()) { onOpenCategory(c) }
                }
            }
        }
    }
}

@Composable
fun CategoryScreen(
    state: KbState,
    categoryId: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTopic: (Topic) -> Unit,
) {
    KbContent(state, onRefresh, onOpenSettings, Modifier.statusBarsPadding()) { kb ->
        val category = kb.category(categoryId)
        if (category == null) {
            MissingView("This theme is no longer in the archive.", onBack)
            return@KbContent
        }
        val topics = kb.topicsIn(categoryId)
        val list = rememberLazyListState()
        val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 300 } }
        Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 28.dp)) {
            item(key = "head") {
                Box(Modifier.fillMaxWidth().background(categoryBrush(category.id))) {
                    Motif(
                        category.motif, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(170.dp),
                        color = Color.White.copy(alpha = 0.16f), baseStroke = 1.6f,
                    )
                    Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 20.dp, bottom = 24.dp)) {
                        IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White) }
                        Column(Modifier.padding(start = 12.dp, top = 28.dp)) {
                            Eyebrow("${topics.size} topic${if (topics.size == 1) "" else "s"}", color = Color(0xFFFFE9B8))
                            Spacer(Modifier.height(8.dp))
                            Text(category.name, style = MaterialTheme.typography.displaySmall, color = Color(0xFFFFF8EA),
                                modifier = Modifier.semantics { heading() })
                            Spacer(Modifier.height(8.dp))
                            Text(category.blurb, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFF4DE).copy(alpha = 0.88f))
                        }
                    }
                }
            }
            if (topics.isEmpty()) {
                item { MessageView("Nothing here yet", "No topics have been added to this theme.", motif = category.motif) }
            }
            items(topics, key = { it.id }) { t ->
                TopicRow(t, kb, Modifier.padding(horizontal = 16.dp).padding(top = 10.dp)) { onOpenTopic(t) }
            }
        }
        StatusBarScrim(scrolled)
        }
    }
}

/** One topic in a list: its motif, its name and the question that reaches it. */
@Composable
fun TopicRow(topic: Topic, kb: KnowledgeBase, modifier: Modifier = Modifier, onClick: () -> Unit) {
    NkCard(modifier.fillMaxWidth(), onClick = onClick, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Motif(kb.motifOf(topic), Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary, baseStroke = 5f)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(topic.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(topic.question, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MissingView(message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
        MessageView("Not found", message) { GoldButton("Go back", onBack) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicScreen(
    state: KbState,
    topicId: String,
    serverUrl: String,
    speaking: String?,
    ttsAvailable: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onReadAloud: (String) -> Unit,
    onAsk: (String) -> Unit,
    onShowOnMap: (Topic) -> Unit,
    onOpenTopic: (String) -> Unit,
) {
    KbContent(state, onRefresh, onOpenSettings, Modifier.statusBarsPadding()) { kb ->
        val topic = kb.topic(topicId)
        if (topic == null) {
            MissingView("This topic is no longer in the archive.", onBack)
            return@KbContent
        }
        val scroll = rememberScrollState()
        val density = LocalDensity.current
        val scrolled by remember { derivedStateOf { scroll.value > with(density) { 250.dp.toPx() } } }
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 300.dp).background(categoryBrush(topic.category))) {
                if (topic.image != null) {
                    AsyncImage(heritageImageUrl(serverUrl, topic.image), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                } else {
                    Motif(
                        kb.motifOf(topic), Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp).size(170.dp),
                        color = Color(0xFFFFF0D2).copy(alpha = 0.9f), baseStroke = 1.5f,
                    )
                }
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xD90E0706))))
                IconButton(onBack, Modifier.statusBarsPadding().padding(start = 8.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp).padding(top = 210.dp, bottom = 20.dp)) {
                    Eyebrow(kb.category(topic.category)?.name ?: topic.categoryName, color = Color(0xFFF6D99A))
                    Spacer(Modifier.height(8.dp))
                    Text(topic.name, style = MaterialTheme.typography.displaySmall, color = Color(0xFFFFF8EA),
                        modifier = Modifier.semantics { heading() })
                }
            }

            Column(Modifier.widthIn(max = MaxContentWidth).padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 32.dp)) {
                MetaRow(listOfNotNull(topic.location?.label, topic.era))
                Spacer(Modifier.height(16.dp))
                Text(topic.answer, style = MaterialTheme.typography.bodyLarge)

                Spacer(Modifier.height(20.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ttsAvailable) {
                        val on = speaking == topic.answer
                        Button(onClick = { onReadAloud(topic.answer) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(if (on) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (on) "Stop" else "Read aloud")
                        }
                    }
                    OutlinedButton(onClick = { onAsk(topic.question) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.QuestionAnswer, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ask Nakshatra")
                    }
                    if (topic.location != null) {
                        OutlinedButton(onClick = { onShowOnMap(topic) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.Place, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Show on map")
                        }
                    }
                }

                val questions = listOf(topic.question) + topic.alternativeQuestions
                Spacer(Modifier.height(28.dp))
                Eyebrow("Ways to ask")
                Spacer(Modifier.height(4.dp))
                questions.forEach { q ->
                    TextButton(onClick = { onAsk(q) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp)) {
                        Text("“$q”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (topic.aliases.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Eyebrow("Also known as")
                    Spacer(Modifier.height(8.dp))
                    Text(topic.aliases.joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (topic.related.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Eyebrow("Related")
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        topic.related.forEach { r -> NkChip(r.name, { onOpenTopic(r.id) }) }
                    }
                }
            }
        }
        StatusBarScrim(scrolled)
        }
    }
}
