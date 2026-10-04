package com.nakshatra.heritage

import androidx.compose.ui.graphics.vector.PathParser
import com.nakshatra.heritage.data.Constellation
import com.nakshatra.heritage.data.KnowledgeBase
import com.nakshatra.heritage.data.NakshatraJson
import com.nakshatra.heritage.data.search
import com.nakshatra.heritage.ui.art.CATEGORY_PALETTE
import com.nakshatra.heritage.ui.art.MOTIF_DATA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/** Runs against the project's real knowledge base, the file the server serves through /api/kb. */
class KnowledgeBaseTest {
    private val kb: KnowledgeBase = NakshatraJson.decodeFromString(
        KnowledgeBase.serializer(),
        File("../../knowledge_base/heritage.json").readText(),
    )

    @Test
    fun `parses every category and topic`() {
        assertTrue(kb.categories.isNotEmpty())
        assertTrue(kb.topics.isNotEmpty())
        assertTrue(kb.topics.all { it.id.isNotBlank() && it.name.isNotBlank() && it.answer.isNotBlank() && it.question.isNotBlank() })
        assertTrue("every topic belongs to a known category", kb.topics.all { kb.category(it.category) != null })
        assertEquals(kb.topics.size, kb.categories.sumOf { kb.topicsIn(it.id).size })
        assertTrue(kb.featured.isNotEmpty())
        assertTrue(kb.questionCount > kb.topics.size)
    }

    @Test
    fun `every motif and category the server names can be drawn`() {
        val used = kb.categories.map { it.motif } + kb.topics.map { kb.motifOf(it) }
        assertTrue("unknown motifs: ${used.toSet() - MOTIF_DATA.keys}", MOTIF_DATA.keys.containsAll(used))
        assertTrue(CATEGORY_PALETTE.keys.containsAll(kb.categories.map { it.id }))
    }

    @Test
    fun `motif path data is valid`() {
        MOTIF_DATA.forEach { (name, shapes) ->
            shapes.forEach { s -> assertTrue("$name: ${s.d}", PathParser().parsePathString(s.d).toNodes().isNotEmpty()) }
        }
    }

    @Test
    fun `search requires every term and covers place names`() {
        val odisha = kb.topics.search("odisha")
        assertTrue(odisha.any { it.id == "konark-sun-temple" })
        assertTrue(kb.topics.search("odisha temple").size <= odisha.size)
        assertTrue(kb.topics.search("  ").size == kb.topics.size)
        assertTrue(kb.topics.search("zzzz-not-a-word").isEmpty())
        assertEquals(kb.topics.search("KONARK").map { it.id }, kb.topics.search("konark").map { it.id })
    }

    @Test
    fun `first sentence is a teaser, not the whole answer`() {
        val konark = kb.topic("konark-sun-temple")!!
        assertTrue(konark.firstSentence.endsWith("."))
        assertTrue(konark.firstSentence.length < konark.answer.length)
        assertEquals("Konark · Odisha", konark.placeLine)
    }

    @Test
    fun `constellation plots every located topic and joins them in a tree`() {
        val sky = Constellation.of(kb.topics)
        assertEquals(kb.placeCount, sky.stars.size)
        assertEquals(sky.stars.size - 1, sky.links.size)
        // A spanning tree reaches every star.
        val reached = mutableSetOf(0)
        var grew = true
        while (grew) {
            grew = false
            for ((a, b) in sky.links) {
                if (a in reached != b in reached) { reached += a; reached += b; grew = true }
            }
        }
        assertEquals(sky.stars.size, reached.size)
        // Stars sit inside the chart, and no two are on top of each other.
        sky.stars.forEach { s ->
            assertTrue(s.topic.name, s.x in -1f..Constellation.WIDTH + 1f && s.y in -1f..Constellation.HEIGHT + 1f)
        }
        for (i in sky.stars.indices) for (j in 0 until i) {
            val d = hypot(sky.stars[i].x - sky.stars[j].x, sky.stars[i].y - sky.stars[j].y)
            assertTrue("${sky.stars[i].topic.name} overlaps ${sky.stars[j].topic.name}", d > 0.3f)
        }
    }

    @Test
    fun `projection puts north up and east right`() {
        val (delhiX, delhiY) = Constellation.project(28.6, 77.2)
        val (chennaiX, chennaiY) = Constellation.project(13.1, 80.3)
        assertTrue(delhiY < chennaiY)
        assertTrue(delhiX < chennaiX)
    }
}
