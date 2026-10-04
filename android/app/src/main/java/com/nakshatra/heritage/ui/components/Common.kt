package com.nakshatra.heritage.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nakshatra.heritage.data.KbState
import com.nakshatra.heritage.data.KnowledgeBase
import com.nakshatra.heritage.ui.art.Motif
import com.nakshatra.heritage.ui.theme.nk

/** Readable line length on tablets and in landscape. */
val MaxContentWidth = 840.dp

/** The small uppercase label with a diamond that opens every section of the website. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).rotate(45f).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun SectionHeader(eyebrow: String, title: String, modifier: Modifier = Modifier, lede: String? = null) {
    Column(modifier) {
        Eyebrow(eyebrow)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        if (lede != null) {
            Spacer(Modifier.height(8.dp))
            Text(lede, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The app's card: a quiet surface with the gold-tinted hairline of the website. */
@Composable
fun NkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(1.dp, nk.hairline)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border) { Column(content = content) }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border) { Column(content = content) }
    }
}

@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = CircleShape) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick, modifier.heightIn(min = 48.dp), shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}

/** A pill: suggestion, related topic or filter. At least 48dp tall to the touch. */
@Composable
fun NkChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dot: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(if (selected) scheme.primary else Color.Transparent)
                .border(1.dp, if (selected) scheme.primary else scheme.outline.copy(alpha = 0.7f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text, style = MaterialTheme.typography.labelMedium,
                color = if (selected) scheme.onPrimary else scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Konark, Odisha  ◆ 13th century CE" -- the meta line of an answer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetaRow(items: List<String>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    if (items.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).rotate(45f).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
                Spacer(Modifier.width(8.dp))
                Text(item, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}

/** Server / edge status, as in the website's navigation bar. */
@Composable
fun StatusPill(server: Boolean, edge: Boolean, modifier: Modifier = Modifier) {
    val on = server && edge
    val text = if (!server) "Server offline" else if (edge) "Edge device connected" else "Edge device offline"
    Row(
        modifier
            .clip(CircleShape)
            .border(1.dp, nk.hairline, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) nk.live else MaterialTheme.colorScheme.outline))
        Spacer(Modifier.width(8.dp))
        Text(
            text, style = MaterialTheme.typography.labelMedium,
            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        )
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier, message: String = "Opening the heritage archive…") {
    Column(modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        CircularProgressIndicator(strokeWidth = 3.dp)
        Spacer(Modifier.height(18.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Full-screen message with a motif: errors and empty results. */
@Composable
fun MessageView(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    motif: String = "mandala",
    motifSize: Dp = 112.dp,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Motif(motif, Modifier.size(motifSize), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), baseStroke = 2f)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(20.dp))
        actions()
    }
}

/** Shown above content that is the saved copy of the archive rather than a fresh reply. */
@Composable
fun OfflineBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "$message Showing the archive saved on this device.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f),
            )
            TextButton(onRetry) { Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

/**
 * Loading / error / content for every screen that needs the knowledge base.
 * With no archive at all it explains how to reach the server; with a saved
 * archive it shows the content and lets the caller place an [OfflineBanner].
 */
@Composable
fun KbContent(
    state: KbState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (KnowledgeBase) -> Unit,
) {
    val kb = state.kb
    when {
        kb != null -> content(kb)
        state.loading -> LoadingView(modifier)
        else -> Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), Arrangement.Center) {
            MessageView(
                title = "The archive is out of reach",
                body = (state.error ?: "The Nakshatra server could not be reached.") +
                    " Start it with “python backend/cloud_server.py” and make sure this device is on the same network.",
            ) {
                GoldButton("Try again", onRetry)
                Spacer(Modifier.height(8.dp))
                GhostButton("Server settings", onOpenSettings)
            }
        }
    }
}

/**
 * Status-bar backing for pages whose coloured header runs to the top edge: invisible
 * while the header is showing, solid once text has scrolled up underneath the clock.
 */
@Composable
fun BoxScope.StatusBarScrim(visible: Boolean) {
    val alpha by animateFloatAsState(if (visible) 0.96f else 0f, label = "scrim")
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.background.copy(alpha = alpha)),
    )
}
