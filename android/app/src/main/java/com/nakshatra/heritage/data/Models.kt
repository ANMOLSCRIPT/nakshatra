package com.nakshatra.heritage.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lenient on purpose: the server may add fields, and older servers may omit optional ones. */
val NakshatraJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

/** `GET /api/kb` -- the whole knowledge base, exactly as the website receives it. */
@Serializable
data class KnowledgeBase(
    val meta: KbMeta = KbMeta(),
    val categories: List<Category> = emptyList(),
    val topics: List<Topic> = emptyList(),
) {
    val questionCount: Int get() = topics.sumOf { 1 + it.alternativeQuestions.size }
    val placeCount: Int get() = topics.count { it.location != null }
    val featured: List<Topic> get() = topics.filter { it.featured }

    fun category(id: String): Category? = categories.firstOrNull { it.id == id }
    fun topic(id: String): Topic? = topics.firstOrNull { it.id == id }
    fun topicsIn(categoryId: String): List<Topic> = topics.filter { it.category == categoryId }

    /** A topic's own motif, else its category's, else the mandala -- the website's rule. */
    fun motifOf(topic: Topic): String = topic.motif ?: category(topic.category)?.motif ?: "mandala"
}

@Serializable
data class KbMeta(
    val title: String = "",
    val domain: String = "",
    val version: String = "",
    @SerialName("problem_statement") val problemStatement: String = "",
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val short: String = "",
    val motif: String = "mandala",
    val blurb: String = "",
) {
    val label: String get() = short.ifBlank { name }
}

@Serializable
data class GeoLocation(
    val place: String = "",
    val state: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
) {
    val label: String get() = listOf(place, state).filter { it.isNotBlank() }.joinToString(", ")
}

@Serializable
data class RelatedTopic(val id: String, val name: String = "", val question: String = "")

@Serializable
data class Topic(
    val id: String,
    val category: String = "",
    val name: String = "",
    val motif: String? = null,
    val question: String = "",
    @SerialName("alternative_questions") val alternativeQuestions: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val answer: String = "",
    val location: GeoLocation? = null,
    val era: String? = null,
    val featured: Boolean = false,
    /** Optional photograph, served by the same server under /assets/heritage/. */
    val image: String? = null,
    @SerialName("category_name") val categoryName: String = "",
    val related: List<RelatedTopic> = emptyList(),
    // Present only when the topic is the answer to a question.
    val confidence: Double? = null,
    @SerialName("matched_by") val matchedBy: String? = null,
) {
    /** "Konark · Odisha", or null when the topic has no place. */
    val placeLine: String? get() = location?.let { "${it.place} · ${it.state}" }

    /** The first sentence of the answer, used as a card teaser (as the website does). */
    val firstSentence: String get() = answer.split(Regex("(?<=\\.)\\s"), limit = 2).first()

    /** Lower-cased text the archive search runs over -- the same fields as library.js. */
    val haystack: String by lazy {
        (listOf(name, question) + alternativeQuestions + aliases + keywords +
            listOfNotNull(answer, location?.place, location?.state)).joinToString(" ").lowercase()
    }
}

@Serializable
data class AskRequest(val question: String)

/** `POST /api/ask`. `match` is null when the answer engine finds nothing. */
@Serializable
data class AskResponse(
    val question: String = "",
    val match: Topic? = null,
    @SerialName("match_ms") val matchMs: Double? = null,
)

/** `GET /api/health`. */
@Serializable
data class Health(
    val status: String = "",
    @SerialName("wake_word") val wakeWord: String = "",
    @SerialName("edge_connected") val edgeConnected: Boolean = false,
    @SerialName("ui_clients") val uiClients: Int = 0,
    val asr: Asr = Asr(),
    @SerialName("knowledge_base") val knowledgeBase: KbCounts = KbCounts(),
) {
    @Serializable
    data class Asr(val enabled: Boolean = false, val engine: String = "", val model: String = "")

    @Serializable
    data class KbCounts(val topics: Int = 0, val questions: Int = 0, val categories: Int = 0)
}

/**
 * One broadcast on `WS /ws/ui` (hardware/PROTOCOL.md). A single flat shape for
 * every event keeps unknown or future events harmless.
 */
@Serializable
data class LiveEvent(
    val event: String = "",
    val connected: Boolean? = null,
    val rms: Double? = null,
    val prob: Double? = null,
    val text: String? = null,
    val transcript: String? = null,
    val match: Topic? = null,
    val t1: Double? = null,
    val t2: Double? = null,
    val t3: Double? = null,
    val t4: Double? = null,
    val t5: Double? = null,
)

/** Archive search, as on the website: every whitespace-separated term must appear. */
fun List<Topic>.search(query: String): List<Topic> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return if (terms.isEmpty()) this else filter { t -> terms.all { it in t.haystack } }
}
