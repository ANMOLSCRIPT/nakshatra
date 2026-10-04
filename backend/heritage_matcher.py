"""
heritage_matcher.py -- matches a spoken transcript to a topic in
knowledge_base/heritage.json.

Same layered design the project has always used (cheap, deterministic, no
external API), retargeted from proper-noun lookup to question answering:

  Layer 0 (question):   the transcript, minus filler words, is exactly one of
                        a topic's listed questions. Confidence 1.0.

  Layer 1 (fuzzy):      rapidfuzz token-set matching of the transcript against
                        every topic's name, aliases and questions. Always
                        available, no model download. A generic-word guard
                        stops "temple" or "festival" alone from carrying a hit,
                        and ties are broken toward the candidate that explains
                        more of what was said ("hindustani and carnatic" beats
                        "carnatic" when both words were spoken).

  Layer 2 (descriptor): each topic's "descriptors" are plain-English phrases
                        ("festival of lights", "sun temple") for people who
                        describe a thing instead of naming it -- Vosk transcribes
                        common English reliably but struggles with Indian proper
                        nouns. An exact multi-word phrase on token boundaries.

  Layer 3 (keywords):   two or more of a topic's keywords in the transcript
                        ("white marble tomb in agra").

  Layer 4 (phonetic):   last resort for garbled proper nouns -- metaphone
                        equality or near-identical spelling against the words
                        topics are actually called ("bharat natyam", "kathakkali").

Precedence: question > strong fuzzy (the user named the thing) > descriptor >
weak fuzzy > keywords > phonetic. A name the user actually said always beats a
description that happens to fit something else.

An optional sentence-transformers layer is available for paraphrases that
share no words with the knowledge base; it is off unless NAKSHATRA_SEMANTIC=1
because it adds a large dependency and a slow first load.
"""

from __future__ import annotations

import json
import logging
import os
import re
from pathlib import Path

from rapidfuzz import fuzz, process

logger = logging.getLogger("heritage_matcher")

KB_PATH = Path(__file__).resolve().parent.parent / "knowledge_base" / "heritage.json"

# ASR output is noisy (small Vosk model, unfamiliar proper nouns), so a garbled
# but close transcript should still clear the bar rather than fall through to
# "no match". See test_matcher.py for the regression set these are tuned on.
FUZZY_THRESHOLD = 75.0        # rapidfuzz token_set_ratio, 0-100
STRONG_FUZZY = 88.0           # at or above this the user named the topic: beats a descriptor
EMBED_THRESHOLD = 0.45        # cosine similarity, 0-1 (optional semantic layer)

DESCRIPTOR_CONFIDENCE = 0.7   # reported for an exact 2+ word descriptor phrase
DESCRIPTOR_MIN_WORDS = 2      # content words; a one-word descriptor is too weak to lead

KEYWORD_MIN_SCORE = 1.5       # summed 1/df weight: at least two keyword hits
KEYWORD_CONFIDENCE = 0.55

# Below STRONG_FUZZY neither side is a subset of the other, so the overlap is
# partial. Require the candidate to explain at least this share of the query's
# discriminative words, or "black hole" matches "Black Pagoda" on one word.
WEAK_FUZZY_MIN_COVERAGE = 0.6

# A query token counts as matching a candidate token when they are this
# similar (rapidfuzz ratio, 0-100) or one contains the other (len >= 4).
FUZZY_CARRIER_MIN_SIMILARITY = 70.0

# Phonetic layer: metaphone alone maps unrelated words together, so the words
# must also look alike; or look near-identical regardless of metaphone.
PHONETIC_MIN_SIMILARITY = 70.0
NEAR_SPELLING_SIMILARITY = 82.0
PHONETIC_MIN_LETTERS = 5      # at 4, metaphone collides everyday words with names ("hole"/"Holi")

# Fields that are spoken or shown. An internal annotation ("TODO: verify")
# in any of them would end up on screen, so it is flagged at startup.
_VISIBLE_FIELDS = ("name", "question", "answer", "era")
_ANNOTATION_RE = re.compile(r"\b(todo|fixme|tbd|verify|placeholder)\b", re.IGNORECASE)

_WORD_RE = re.compile(r"[a-z]+")

# Function words and question-framing words. They carry no topic information
# but do real damage on garbled ASR output: token_set_ratio scores 100 for any
# transcript whose tokens are a subset of a candidate's. Matching runs on the
# transcript with these removed; a transcript that is only these words matches
# nothing.
_STOPWORDS = frozenset("""
a an the and or but of to in on at for with from by as into up down out over
is are was were be been am do does did doesn't doesnt don't dont didn't didnt
can could would will shall should may might have has had
i me my you your we us our he she it its they them their this that these those
there here what which who whom whose when where why how
tell show give about above please let know want like some any mean means meaning
just so than then now not no yes though also very much many
say said explain describe information info details something more
called known famous special important importance significance history story
celebrate celebrated celebrates celebration
hey hello hi okay ok
""".split())

# Words shared by so many topics that they may not carry a match on their own
# ("Konark Temple" vs "Meenakshi Temple"). A fuzzy hit must be carried by at
# least one token that is NOT in this set.
_GENERIC_WORDS = frozenset("""
temple temples fort festival festivals dance dances dancing music art arts
painting paintings india indian indias city cities palace tomb cave caves
classical folk traditional tradition traditions heritage culture cultural
world site sites ancient university great day craft crafts form forms
saree sarees sari state built
""".split())

# Ordinary English words that appear in topic names/aliases ("Black Pagoda",
# "Golden Temple", "Snake Boat Race"). They identify a topic only as part of
# the full phrase, so they are not allowed to anchor the phonetic layer, which
# matches on a single word.
_COMMON_WORDS = frozenset("""
golden blue pottery sweets snake boat race horse international camel fair
lights colours colors pink lakes lake black white south north breakfast
greeting folded hands evening guest string puppet puppets show wheel spinning
soft gold toys wooden spiced queen stepwell hall thousand pillars iron pillar
victory shore five nine nights eight harvest winds palace island observatory
valley civilisation harappan trinity types versus tree spice trade pepper
drums instrument shawl wool embroidery work cloth fabric fabric hand spun
terracotta metal casting wax lost sweets desserts food regional thali medicine
three breathing exercises practice sutras yogic salutation salutations
library ruins living oldest scroll tribal miniature monuments group designs
ceremony hospitality penance descent ganges garden first second boat
""".split())

try:  # phonetic layer dependency; its absence only disables that layer
    import jellyfish as _jellyfish
except ImportError as exc:  # noqa: BLE001
    logger.warning("jellyfish unavailable (%s) -- phonetic fallback layer disabled", exc)
    _jellyfish = None


def _tokens(text: str) -> list[str]:
    """Lowercase tokens; possessives dropped (India's -> india, Queen's -> queen)."""
    return [re.sub(r"'s?$", "", w) for w in re.findall(r"[a-z0-9']+", text.lower())
            if re.sub(r"'s?$", "", w)]


def _content_tokens(text: str) -> list[str]:
    return [w for w in _tokens(text) if w not in _STOPWORDS]


def _content_text(text: str) -> str:
    """Transcript with stopwords removed ("" if nothing meaningful is left)."""
    return " ".join(_content_tokens(text))


def _carriers(text: str) -> list[str]:
    """Tokens allowed to carry a match: not generic, not filler, 3+ letters."""
    return [w for w in _content_tokens(text) if len(w) >= 3 and w not in _GENERIC_WORDS]


def _similar(q: str, c: str) -> bool:
    if len(q) < 4 or len(c) < 4:
        return q == c  # "air" must not pass for "Sadir"; short words match exactly or not at all
    if fuzz.ratio(q, c) >= FUZZY_CARRIER_MIN_SIMILARITY:
        return True
    return len(q) >= 4 and len(c) >= 4 and (q in c or c in q)


def _carriers_matched(query: str, candidate: str) -> int:
    """How many discriminative query tokens the candidate accounts for."""
    cand = _carriers(candidate)
    return sum(1 for q in _carriers(query) if any(_similar(q, c) for c in cand))


def _singular(word: str) -> str:
    return word[:-1] if len(word) > 3 and word.endswith("s") and not word.endswith("ss") else word


# ASR frequently spells out letters it doesn't recognise as a word; collapse
# any run of 2+ single-letter words into one token before matching.
_SPELLED_RE = re.compile(r"\b(?:[a-z]\s+){1,}[a-z]\b")


def _collapse_spelled(text: str) -> str:
    return _SPELLED_RE.sub(lambda m: re.sub(r"\s+", "", m.group(0)), text)


class HeritageMatcher:
    def __init__(self, kb_path: Path = KB_PATH):
        with open(kb_path, "r", encoding="utf-8") as f:
            kb = json.load(f)
        self.meta: dict = kb.get("meta", {})
        self.categories: list[dict] = kb["categories"]
        self.topics: list[dict] = kb["topics"]
        self._category_names = {c["id"]: c["name"] for c in self.categories}
        self._warn_on_annotations()

        # Layer 0: every listed question, reduced to its content words.
        self._question_index: dict[str, int] = {}
        # Layer 1: flattened (topic_index, candidate_string) over every name,
        # alias and question individually, so one strong alias is not diluted
        # by averaging against the topic's other strings.
        self._fuzzy_candidates: list[tuple[int, str]] = []
        for i, topic in enumerate(self.topics):
            seen: set[str] = set()
            for q in self.all_questions(topic):
                key = _content_text(q)
                if key:
                    self._question_index.setdefault(key, i)
            for text in [topic["name"], *topic.get("aliases", []), *self.all_questions(topic)]:
                cand = _content_text(text)
                if cand and cand not in seen:
                    seen.add(cand)
                    self._fuzzy_candidates.append((i, cand))
        self._fuzzy_strings = [c for _, c in self._fuzzy_candidates]

        # Layer 2: descriptor phrase (token tuple) -> topic index.
        self._descriptor_index: dict[tuple[str, ...], int] = {}
        for i, topic in enumerate(self.topics):
            for phrase in topic.get("descriptors", []):
                toks = tuple(_content_tokens(phrase))
                if len(toks) < DESCRIPTOR_MIN_WORDS:
                    logger.info("descriptor %r on %s ignored: fewer than %d words",
                                phrase, topic["id"], DESCRIPTOR_MIN_WORDS)
                    continue
                owner = self._descriptor_index.setdefault(toks, i)
                if owner != i:
                    logger.warning("descriptor %r is claimed by %s and %s; %s keeps it",
                                   phrase, self.topics[owner]["id"], topic["id"],
                                   self.topics[owner]["id"])

        # Layer 3: keyword -> owning topics. A keyword shared by many topics
        # is worth little (weight 1/df).
        self._keyword_owners: dict[tuple[str, ...], set[int]] = {}
        for i, topic in enumerate(self.topics):
            for kw in topic.get("keywords", []):
                toks = tuple(_singular(t) for t in _tokens(kw))
                if toks:
                    self._keyword_owners.setdefault(toks, set()).add(i)

        # Layer 4: what topics are actually CALLED -- name/alias words minus
        # generic words, filler and anything under 4 letters.
        # State names are excluded: "Gujarat" appears in a dozen aliases and says
        # which region, not which topic.
        state_words = {w for t in self.topics if t.get("location")
                       for w in _WORD_RE.findall(t["location"]["state"].lower())}
        self._proper_tokens: dict[str, set[int]] = {}
        for i, topic in enumerate(self.topics):
            for text in [topic["name"], *topic.get("aliases", [])]:
                for tok in _WORD_RE.findall(text.lower()):
                    if (len(tok) >= 4 and tok not in _STOPWORDS and tok not in _GENERIC_WORDS
                            and tok not in _COMMON_WORDS and tok not in state_words):
                        self._proper_tokens.setdefault(tok, set()).add(i)
        self._phonetic_index: dict[str, list[str]] = {}
        if _jellyfish is not None:
            for tok in self._proper_tokens:
                if len(tok) < PHONETIC_MIN_LETTERS:
                    continue
                self._phonetic_index.setdefault(_jellyfish.metaphone(tok), []).append(tok)

        self._embedder = None
        self._topic_embeddings = None
        if os.environ.get("NAKSHATRA_SEMANTIC") == "1":
            self._load_embedder()

        logger.info("knowledge base loaded: %d topics, %d questions, %d fuzzy candidates, "
                    "%d descriptors, %d keywords", len(self.topics), len(self._question_index),
                    len(self._fuzzy_candidates), len(self._descriptor_index),
                    len(self._keyword_owners))

    # -- knowledge-base helpers -------------------------------------------

    @staticmethod
    def all_questions(topic: dict) -> list[str]:
        return [topic["question"], *topic.get("alternative_questions", [])]

    def category_name(self, category_id: str) -> str:
        return self._category_names.get(category_id, category_id)

    def public_topic(self, topic: dict) -> dict:
        """The topic as sent to the UI: everything in the KB plus the readable
        category name and the names of related topics (so a client can render
        the card without a second lookup)."""
        by_id = {t["id"]: t for t in self.topics}
        return {
            **topic,
            "category_name": self.category_name(topic["category"]),
            "related": [{"id": r, "name": by_id[r]["name"], "question": by_id[r]["question"]}
                        for r in topic.get("related_topics", []) if r in by_id],
        }

    def vocabulary(self) -> set[str]:
        """Every word a user might plausibly say about the knowledge base
        (names, aliases, questions, descriptors, keywords) -- used to bias the
        ASR decoder toward the domain."""
        words: set[str] = set()
        for topic in self.topics:
            for text in [topic["name"], *topic.get("aliases", []), *self.all_questions(topic),
                         *topic.get("descriptors", []), *topic.get("keywords", [])]:
                words.update(_WORD_RE.findall(text.lower()))
        return words

    def _warn_on_annotations(self) -> None:
        for topic in self.topics:
            for field in _VISIBLE_FIELDS:
                text = topic.get(field)
                if isinstance(text, str) and _ANNOTATION_RE.search(text):
                    logger.warning("heritage.json: internal annotation in user-visible %s.%s: %r",
                                   topic.get("id"), field, text)

    # -- optional semantic layer -------------------------------------------

    def _load_embedder(self) -> None:
        os.environ.setdefault("USE_TF", "0")
        os.environ.setdefault("TRANSFORMERS_NO_TF", "1")
        try:
            from sentence_transformers import SentenceTransformer, util as st_util
            try:
                model = SentenceTransformer("all-MiniLM-L6-v2", local_files_only=True)
            except Exception:  # noqa: BLE001 -- not cached yet
                model = SentenceTransformer("all-MiniLM-L6-v2")
            texts = [". ".join([t["name"], *self.all_questions(t), t["answer"]]) for t in self.topics]
            self._topic_embeddings = model.encode(texts, convert_to_tensor=True,
                                                  show_progress_bar=False)
            model.encode("warm up", convert_to_tensor=True, show_progress_bar=False)
        except Exception as exc:  # noqa: BLE001 -- any failure is non-fatal
            logger.warning("semantic layer unavailable (%s); continuing without it", exc)
            return
        self._embedder, self._st_util = model, st_util
        logger.info("semantic layer enabled: %d topics embedded", len(self.topics))

    # -- matching -------------------------------------------------------------

    def match(self, transcript: str) -> dict | None:
        """Returns {topic, confidence, layer} or None."""
        transcript = re.sub(r"\[unk\]", " ", transcript or "").strip()
        if not transcript:
            return None
        collapsed = _collapse_spelled(transcript.lower())
        content = _content_text(collapsed)
        if not content:
            logger.info("match %r -> no match (no content words)", transcript)
            return None

        result = (self._match_question(content)
                  or self._match_fuzzy(content, minimum=STRONG_FUZZY)
                  or self._match_descriptor(collapsed)
                  or self._match_fuzzy(content, minimum=FUZZY_THRESHOLD)
                  or self._match_keywords(collapsed)
                  or self._match_embed(collapsed)
                  or self._match_phonetic(content))
        if result is None:
            logger.info("match %r -> no match", transcript)
            return None
        logger.info("match %r -> %s (%s, confidence=%.3f)", transcript,
                    result["topic"]["id"], result["layer"], result["confidence"])
        return result

    def _hit(self, idx: int, confidence: float, layer: str) -> dict:
        return {"topic": self.topics[idx], "confidence": confidence, "layer": layer}

    def _match_question(self, content: str) -> dict | None:
        idx = self._question_index.get(content)
        return None if idx is None else self._hit(idx, 1.0, "question")

    def _match_fuzzy(self, content: str, minimum: float) -> dict | None:
        scored = process.extract(content, self._fuzzy_strings, scorer=fuzz.token_set_ratio,
                                 score_cutoff=minimum, limit=None)
        best_key, best = None, None
        n_query_carriers = len(_carriers(content))
        for cand, score, pos in scored:
            carried = _carriers_matched(content, cand)
            if carried == 0:
                continue  # the only overlap is generic words
            if score < STRONG_FUZZY and carried < WEAK_FUZZY_MIN_COVERAGE * n_query_carriers:
                continue  # partial overlap that leaves most of the query unexplained
            # token_set_ratio is 100 for any subset, so within a tier rank first
            # by how much of the query the candidate explains ("difference ...
            # hindustani ... carnatic" beats plain "hindustani"), then by score,
            # then by overall closeness.
            key = (carried, score, fuzz.token_sort_ratio(content, cand))
            if best_key is None or key > best_key:
                best_key, best = key, (pos, score)
        if best is None:
            return None
        pos, score = best
        return self._hit(self._fuzzy_candidates[pos][0], score / 100.0, "fuzzy")

    def _match_descriptor(self, transcript: str) -> dict | None:
        """Whole-phrase containment on token boundaries (so "sun temple" does
        not match inside "sunday temple"), compared on content words so filler
        in between ("temple that is shaped like a chariot") does not break the
        phrase. The longest matching phrase wins -- it is the most specific."""
        toks = _content_tokens(transcript)
        best: tuple[str, ...] | None = None
        for phrase in self._descriptor_index:
            n = len(phrase)
            if best is not None and n <= len(best):
                continue
            if any(tuple(toks[j:j + n]) == phrase for j in range(len(toks) - n + 1)):
                best = phrase
        if best is None:
            return None
        return self._hit(self._descriptor_index[best], DESCRIPTOR_CONFIDENCE, "descriptor")

    def _match_keywords(self, transcript: str) -> dict | None:
        toks = [_singular(t) for t in _tokens(transcript)]
        scores: dict[int, float] = {}
        for kw, owners in self._keyword_owners.items():
            n = len(kw)
            if any(tuple(toks[j:j + n]) == kw for j in range(len(toks) - n + 1)):
                for i in owners:
                    scores[i] = scores.get(i, 0.0) + 1.0 / len(owners)
        if not scores:
            return None
        ranked = sorted(scores.items(), key=lambda kv: -kv[1])
        best_idx, best_score = ranked[0]
        if best_score < KEYWORD_MIN_SCORE:
            return None
        if len(ranked) > 1 and ranked[1][1] >= best_score:
            return None  # two topics fit equally well: do not guess
        return self._hit(best_idx, KEYWORD_CONFIDENCE, "keywords")

    def _match_embed(self, transcript: str) -> dict | None:
        if self._embedder is None:
            return None
        query = self._embedder.encode(transcript, convert_to_tensor=True, show_progress_bar=False)
        sims = self._st_util.cos_sim(query, self._topic_embeddings)[0]
        idx = int(sims.argmax())
        score = float(sims[idx])
        return self._hit(idx, score, "semantic") if score >= EMBED_THRESHOLD else None

    def _match_phonetic(self, content: str) -> dict | None:
        """Sound-alike / near-spelling overlap between transcript words and the
        words topics are called. Adjacent transcript words are also tried
        joined, because ASR often splits an unfamiliar name in two ("bharat
        natyam"). Confidence is capped below every other layer."""
        words = [w for w in _WORD_RE.findall(content)]
        singles = [w for w in words if len(w) >= PHONETIC_MIN_LETTERS and w not in _GENERIC_WORDS]
        joined = [a + b for a, b in zip(words, words[1:]) if len(a + b) >= 6]

        hits: dict[int, int] = {}
        for probe in singles + joined:
            matched: set[str] = set()
            # Sound-alike is only trusted for a word the recogniser produced
            # whole; a glued pair must also look like the name.
            if _jellyfish is not None and probe in singles:
                for kb_tok in self._phonetic_index.get(_jellyfish.metaphone(probe), []):
                    if fuzz.ratio(probe, kb_tok) >= PHONETIC_MIN_SIMILARITY:
                        matched.add(kb_tok)
            for kb_tok in self._proper_tokens:
                if len(kb_tok) >= PHONETIC_MIN_LETTERS and fuzz.ratio(probe, kb_tok) >= NEAR_SPELLING_SIMILARITY:
                    matched.add(kb_tok)
            for kb_tok in matched:
                for i in self._proper_tokens[kb_tok]:
                    hits[i] = hits.get(i, 0) + 1
        if not hits:
            return None
        top = max(hits.values())
        winners = [i for i, n in hits.items() if n == top]
        if len(winners) > 1:
            return None  # the word belongs to several topics ("kerala"): do not guess
        # 1 matched word -> 0.5, 2 -> 0.6, capped at 0.64: always below a fuzzy hit.
        return self._hit(winners[0], min(0.5 + 0.1 * (top - 1), 0.64), "phonetic")


_matcher: HeritageMatcher | None = None


def get_matcher() -> HeritageMatcher:
    """Lazy process-wide singleton (cloud_server.py calls this at startup so
    the knowledge base is loaded before any request arrives)."""
    global _matcher
    if _matcher is None:
        _matcher = HeritageMatcher()
    return _matcher


if __name__ == "__main__":
    import sys

    logging.basicConfig(level=logging.WARNING)
    query = " ".join(sys.argv[1:]) or "tell me about konark temple"
    result = get_matcher().match(query)
    print(f"query: {query!r}")
    if result is None:
        print("no match")
    else:
        print(f"topic     : {result['topic']['id']}  ({result['layer']}, "
              f"confidence {result['confidence']:.2f})")
        print(f"answer    : {result['topic']['answer']}")
