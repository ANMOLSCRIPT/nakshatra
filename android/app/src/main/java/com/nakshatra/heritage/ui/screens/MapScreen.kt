package com.nakshatra.heritage.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakshatra.heritage.data.Constellation
import com.nakshatra.heritage.data.KbState
import com.nakshatra.heritage.data.KnowledgeBase
import com.nakshatra.heritage.data.Topic
import com.nakshatra.heritage.ui.art.categoryColor
import com.nakshatra.heritage.ui.components.*
import com.nakshatra.heritage.ui.theme.Brand
import com.nakshatra.heritage.ui.theme.Sans
import kotlin.math.hypot

private const val PAD = 1.4f

@Composable
fun MapScreen(
    state: KbState,
    focusTopicId: String?,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onAsk: (String) -> Unit,
    onOpenTopic: (Topic) -> Unit,
) {
    KbContent(state, onRefresh, onOpenSettings, Modifier.statusBarsPadding()) { kb ->
        val sky = remember(kb) { Constellation.of(kb.topics) }
        if (sky.stars.isEmpty()) {
            Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
                MessageView("No places to plot", "None of the topics in the archive has a location yet.", motif = "ghat")
            }
            return@KbContent
        }
        // Start with something on the card rather than an empty box: the first featured place.
        var selectedId by rememberSaveable(focusTopicId) {
            mutableStateOf(focusTopicId?.takeIf { id -> sky.stars.any { it.topic.id == id } }
                ?: (sky.stars.firstOrNull { it.topic.featured } ?: sky.stars.first()).topic.id)
        }
        var filter by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = sky.stars.firstOrNull { it.topic.id == selectedId }?.topic
        val present = remember(kb) { kb.categories.filter { c -> sky.stars.any { it.topic.category == c.id } } }

        val header: @Composable () -> Unit = {
            SectionHeader(
                "Cultural map", "The heritage constellation",
                lede = "Each star is a place where a tradition lives, plotted at its true latitude and longitude. Tap a star; pinch to zoom.",
            )
        }
        val legend: @Composable (Modifier) -> Unit = { m ->
            LazyRow(m, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
                item { NkChip("All", { filter = null }, selected = filter == null) }
                items(present, key = { it.id }) { c ->
                    NkChip(c.label, { filter = if (filter == c.id) null else c.id }, selected = filter == c.id, dot = categoryColor(c.id))
                }
            }
        }
        val card: @Composable (Modifier) -> Unit = { m -> SelectedCard(selected, kb, m, onAsk, onOpenTopic) }
        val map: @Composable (Modifier) -> Unit = { m ->
            StarMap(sky, selectedId, filter, m) { selectedId = it.id }
        }

        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize().padding(top = 12.dp, bottom = 12.dp)) {
                    map(Modifier.weight(1f).fillMaxHeight().padding(start = 20.dp))
                    Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        Box(Modifier.padding(horizontal = 20.dp)) { header() }
                        legend(Modifier.padding(top = 8.dp))
                        card(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
                    Box(Modifier.padding(horizontal = 20.dp)) { header() }
                    legend(Modifier.padding(top = 8.dp))
                    map(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp))
                    card(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun SelectedCard(topic: Topic?, kb: KnowledgeBase, modifier: Modifier, onAsk: (String) -> Unit, onOpenTopic: (Topic) -> Unit) {
    NkCard(modifier.fillMaxWidth()) {
        if (topic == null) {
            Text("Tap a star.", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@NkCard
        }
        Column(Modifier.padding(16.dp)) {
            Eyebrow(kb.category(topic.category)?.label ?: topic.categoryName, color = categoryColor(topic.category))
            Spacer(Modifier.height(6.dp))
            Text(topic.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                listOfNotNull(topic.location?.label, topic.era).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GoldButton("Ask Nakshatra", { onAsk(topic.question) })
                GhostButton("Open", { onOpenTopic(topic) })
            }
        }
    }
}

/** The star chart itself. Always night-coloured, like the website's map section. */
@Composable
private fun StarMap(sky: Constellation, selectedId: String?, filter: String?, modifier: Modifier, onSelect: (Topic) -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()
    val twinkle by rememberInfiniteTransition(label = "twinkle")
        .animateFloat(0.55f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "twinkle")

    fun clampPan(p: Offset, z: Float) = Offset(
        p.x.coerceIn(-canvas.width * (z - 1) / 2f, canvas.width * (z - 1) / 2f),
        p.y.coerceIn(-canvas.height * (z - 1) / 2f, canvas.height * (z - 1) / 2f),
    )

    // Design units -> pixels, shared by drawing and hit-testing.
    fun place(x: Float, y: Float, w: Float, h: Float): Offset {
        val base = minOf(w / (Constellation.WIDTH + 2 * PAD), h / (Constellation.HEIGHT + 2 * PAD))
        val cx = w / 2f
        val cy = h / 2f
        val px = cx + (x - Constellation.WIDTH / 2f) * base
        val py = cy + (y - Constellation.HEIGHT / 2f) * base
        return Offset(cx + (px - cx) * zoom + pan.x, cy + (py - cy) * zoom + pan.y)
    }

    Canvas(
        modifier
            .onSizeChanged { canvas = it }
            .clip(MaterialTheme.shapes.large)
            .background(Brush.radialGradient(listOf(Brand.NightGlow, Brand.Night)))
            .semantics {
                contentDescription = "Star chart of ${sky.stars.size} heritage places across India. " +
                    "The Archive tab lists every place as text."
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val next = (zoom * zoomChange).coerceIn(1f, 6f)
                    val fromCentre = centroid - Offset(size.width / 2f, size.height / 2f)
                    pan = clampPan((pan - fromCentre) * (next / zoom) + fromCentre + panChange, next)
                    zoom = next
                }
            }
            .pointerInput(sky, filter) {
                detectTapGestures(
                    onDoubleTap = { at ->
                        val next = if (zoom > 1.2f) 1f else 2.6f
                        val fromCentre = at - Offset(size.width / 2f, size.height / 2f)
                        pan = if (next == 1f) Offset.Zero else clampPan((pan - fromCentre) * (next / zoom) + fromCentre, next)
                        zoom = next
                    },
                    onTap = { at ->
                        val reach = 30.dp.toPx()
                        sky.stars
                            .filter { filter == null || it.topic.category == filter }
                            .map { it to place(it.x, it.y, size.width.toFloat(), size.height.toFloat()) }
                            .minByOrNull { (_, p) -> hypot(p.x - at.x, p.y - at.y) }
                            ?.takeIf { (_, p) -> hypot(p.x - at.x, p.y - at.y) <= reach }
                            ?.let { (star, _) -> onSelect(star.topic) }
                    },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        val grid = Color(0xFFA0AAFF).copy(alpha = 0.10f)
        val label = TextStyle(color = Color(0xFFBEC4FF).copy(alpha = 0.42f), fontSize = 9.sp, fontFamily = Sans)

        for (lon in 70..95 step 5) {
            val x = ((lon - Constellation.LON0) * Constellation.K).toFloat()
            val top = place(x, 0f, w, h)
            val bottom = place(x, Constellation.HEIGHT, w, h)
            drawLine(grid, top, bottom, 1f)
            drawText(measurer, "$lon°E", Offset(bottom.x + 4f, (bottom.y + 4f).coerceAtMost(h - 14.sp.toPx())), label)
        }
        for (lat in 10..35 step 5) {
            val y = (Constellation.LAT1 - lat).toFloat()
            val left = place(0f, y, w, h)
            val right = place(Constellation.WIDTH, y, w, h)
            drawLine(grid, left, right, 1f)
            drawText(measurer, "$lat°N", Offset((left.x - 30.dp.toPx()).coerceAtLeast(4f), left.y - 12.sp.toPx()), label)
        }

        val points = sky.stars.map { place(it.x, it.y, w, h) }
        sky.links.forEach { (a, b) -> drawLine(Brand.Gold.copy(alpha = 0.24f), points[a], points[b], 1.dp.toPx()) }

        sky.stars.forEachIndexed { i, star ->
            val p = points[i]
            val colour = categoryColor(star.topic.category)
            val dim = filter != null && star.topic.category != filter
            val active = star.topic.id == selectedId
            val a = if (dim) 0.14f else if (i % 3 == 0 && !active) twinkle else 1f
            drawCircle(colour.copy(alpha = (if (active) 0.34f else 0.18f) * a), (if (active) 13 else 7).dp.toPx(), p)
            drawCircle(colour.copy(alpha = a), (if (active) 4.4f else 2.6f).dp.toPx(), p)
            if (active) drawCircle(Color.White.copy(alpha = 0.85f), 13.dp.toPx(), p, style = Stroke(1.dp.toPx()))
        }
    }
}
