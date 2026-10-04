package com.nakshatra.heritage.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

private class ParsedShape(val path: Path, val src: S)

private val parsed = HashMap<String, List<ParsedShape>>()

private fun shapesOf(name: String): List<ParsedShape> = synchronized(parsed) {
    val key = if (name in MOTIF_DATA) name else "mandala"
    parsed.getOrPut(key) {
        MOTIF_DATA.getValue(key).map { ParsedShape(PathParser().parsePathString(it.d).toPath(), it) }
    }
}

/** True if [name] is one of the motifs the website knows; unknown names fall back to the mandala. */
fun isKnownMotif(name: String) = name in MOTIF_DATA

/**
 * Draws a motif into the current draw area (scaled from the 200 x 200 design box).
 * [baseStroke] is the stroke width, in design units, of the main lines; the
 * website sets it per context (thick for small glyphs, hairline for large art).
 */
fun DrawScope.drawMotif(name: String, color: Color, baseStroke: Float = 2f) {
    val side = minOf(size.width, size.height)
    val s = side / 200f
    val dx = (size.width - side) / 2f
    val dy = (size.height - side) / 2f
    // Detail lines are 1 unit on the website; keep them visible on small glyphs.
    val thin = maxOf(1f, baseStroke * 0.42f)
    translate(dx, dy) {
        scale(s, s, pivot = Offset.Zero) {
            for (shape in shapesOf(name)) {
                val src = shape.src
                val width = when {
                    src.w == 0f -> baseStroke
                    src.w <= 1f -> thin
                    else -> src.w * (baseStroke / 2f)
                }
                val draw: DrawScope.() -> Unit = {
                    if (src.style != ShapeStyle.STROKE) drawPath(shape.path, color)
                    if (src.style != ShapeStyle.FILL) {
                        drawPath(
                            shape.path, color,
                            style = Stroke(
                                width = width, cap = StrokeCap.Round, join = StrokeJoin.Round,
                                pathEffect = src.dash?.let { PathEffect.dashPathEffect(it) },
                            ),
                        )
                    }
                }
                if (src.tx != 0f || src.ty != 0f || src.rot != 0f) {
                    translate(src.tx, src.ty) { rotate(src.rot, pivot = Offset.Zero) { draw() } }
                } else {
                    draw()
                }
            }
        }
    }
}

/** A motif as a composable; decorative, so it carries no content description. */
@Composable
fun Motif(
    name: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    baseStroke: Float = 2f,
) {
    Canvas(modifier) { drawMotif(name, color, baseStroke) }
}

/** The category gradient of the website (155 degrees, dark stop first). */
fun categoryBrush(categoryId: String): Brush {
    val (a, b) = CATEGORY_PALETTE[categoryId] ?: CATEGORY_PALETTE.getValue("monuments")
    return Brush.linearGradient(listOf(Color(a), Color(b)), start = Offset.Zero, end = Offset.Infinite)
}

/** The bright stop of a category's gradient -- its star colour on the map. */
fun categoryColor(categoryId: String): Color =
    Color((CATEGORY_PALETTE[categoryId] ?: CATEGORY_PALETTE.getValue("monuments")).second)

fun categoryDeepColor(categoryId: String): Color =
    Color((CATEGORY_PALETTE[categoryId] ?: CATEGORY_PALETTE.getValue("monuments")).first)

// Distinct jewel tones for the featured cards (FEATURED in art.js).
private val FEATURED = listOf(
    0xFF7A2D12 to 0xFFD9792B, 0xFF17425A to 0xFF3D93A8, 0xFF5A1A3C to 0xFFBF4F7A, 0xFF3D2A6B to 0xFF8A6FD0,
    0xFF70440A to 0xFFD9A02A, 0xFF12504A to 0xFF35A08E, 0xFF232A66 to 0xFF5661C4, 0xFF6B1F1F to 0xFFCF5A3A,
)

fun featuredBrush(index: Int): Brush {
    val (a, b) = FEATURED[index.mod(FEATURED.size)]
    return Brush.linearGradient(0f to Color(b), 0.78f to Color(a), start = Offset.Zero, end = Offset.Infinite)
}
