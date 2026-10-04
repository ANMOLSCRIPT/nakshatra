"""
validate_kb.py -- structural checks for knowledge_base/heritage.json.

Run after every edit to the knowledge base:
    python scripts/validate_kb.py

Fails (exit 1) on anything that would break the matcher or the UI: duplicate
ids, unknown categories, dangling related_topics, a descriptor phrase claimed
by two topics, missing fields, or an answer too long to be spoken comfortably.
"""

from __future__ import annotations

import json
import re
import sys
from collections import Counter
from pathlib import Path

KB_PATH = Path(__file__).resolve().parent.parent / "knowledge_base" / "heritage.json"
REQUIRED = ("id", "category", "name", "question", "alternative_questions", "aliases",
            "descriptors", "keywords", "answer", "related_topics")
MAX_ANSWER_WORDS = 85   # ~35 s of speech; longer answers lose a listener
MIN_ANSWER_WORDS = 35


def main() -> int:
    kb = json.loads(KB_PATH.read_text(encoding="utf-8"))
    topics, categories = kb["topics"], {c["id"] for c in kb["categories"]}
    errors: list[str] = []

    ids = Counter(t.get("id") for t in topics)
    errors += [f"duplicate id {i!r}" for i, n in ids.items() if n > 1]

    descriptors: dict[str, str] = {}
    questions: dict[str, str] = {}
    for t in topics:
        tid = t.get("id", "?")
        errors += [f"{tid}: missing field {f!r}" for f in REQUIRED if f not in t]
        if t.get("category") not in categories:
            errors.append(f"{tid}: unknown category {t.get('category')!r}")
        errors += [f"{tid}: related topic {r!r} does not exist"
                   for r in t.get("related_topics", []) if r not in ids]
        if tid in t.get("related_topics", []):
            errors.append(f"{tid}: lists itself as a related topic")
        words = len(t.get("answer", "").split())
        if not MIN_ANSWER_WORDS <= words <= MAX_ANSWER_WORDS:
            errors.append(f"{tid}: answer is {words} words (want {MIN_ANSWER_WORDS}-{MAX_ANSWER_WORDS})")
        if re.search(r"nakshatra", t.get("answer", ""), re.IGNORECASE):
            errors.append(f"{tid}: answer says the wake word aloud (would re-trigger the device)")
        for d in t.get("descriptors", []):
            key = " ".join(re.findall(r"[a-z0-9']+", d.lower()))
            if key in descriptors and descriptors[key] != tid:
                errors.append(f"descriptor {d!r} claimed by both {descriptors[key]} and {tid}")
            descriptors[key] = tid
        for q in [t.get("question", ""), *t.get("alternative_questions", [])]:
            key = " ".join(re.findall(r"[a-z0-9']+", q.lower()))
            if key in questions and questions[key] != tid:
                errors.append(f"question {q!r} listed under both {questions[key]} and {tid}")
            questions[key] = tid
        loc = t.get("location")
        if loc and not (6 <= loc["lat"] <= 37.5 and 68 <= loc["lon"] <= 98):
            errors.append(f"{tid}: location {loc['lat']},{loc['lon']} is outside India")

    per_cat = Counter(t["category"] for t in topics)
    errors += [f"category {c!r} has no topics" for c in categories if not per_cat[c]]

    print(f"{len(topics)} topics, {len(questions)} questions, {len(categories)} categories, "
          f"{sum(1 for t in topics if t.get('location'))} mapped, "
          f"{sum(1 for t in topics if t.get('featured'))} featured")
    for c in kb["categories"]:
        print(f"  {per_cat[c['id']]:>3}  {c['name']}")
    if errors:
        print(f"\n{len(errors)} problem(s):")
        for e in errors:
            print("  -", e)
        return 1
    print("\nknowledge base OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
