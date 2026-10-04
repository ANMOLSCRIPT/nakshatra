"""
test_matcher.py -- regression check for heritage_matcher.py.

Not a pytest suite -- a standalone script that prints what each query resolves
to and which layer produced it, so a threshold or knowledge-base change can be
eyeballed against a fixed set before committing to it. Exits 1 on any miss.

Run:
    python backend/test_matcher.py          # summary per group
    python backend/test_matcher.py -v       # every query
"""

from __future__ import annotations

import sys

from heritage_matcher import HeritageMatcher

# (query, expected topic id or None for "must not match")

# The same topic asked four different ways must land on the same answer.
VARIATION_CASES = [
    ("Tell me about Konark Temple", "konark-sun-temple"),
    ("What is Konark Temple?", "konark-sun-temple"),
    ("Why is Konark Sun Temple famous?", "konark-sun-temple"),
    ("Give me information about Konark.", "konark-sun-temple"),
    ("what is the history of konark sun temple", "konark-sun-temple"),
    ("why do we celebrate diwali in india", "diwali"),
    ("why do we celebrate diwali", "diwali"),
    ("what is the significance of holi", "holi"),
    ("what is navratri", "navratri"),
    ("what is durga puja", "durga-puja"),
    ("tell me about the taj mahal", "taj-mahal"),
    ("what is special about hampi", "hampi"),
    ("tell me about the ajanta caves", "ajanta-caves"),
    ("what are the ellora caves", "ellora-caves"),
    ("what is the significance of sanchi stupa", "sanchi-stupa"),
    ("what is bharatanatyam", "bharatanatyam"),
    ("what is kathak", "kathak"),
    ("what is kathakali", "kathakali"),
    ("what is kuchipudi", "kuchipudi"),
    ("what is odissi", "odissi"),
    ("what is bihu", "bihu"),
    ("what is pongal", "pongal"),
    ("what is onam", "onam"),
    ("what is raksha bandhan", "raksha-bandhan"),
    ("what is madhubani painting", "madhubani"),
    ("what is warli art", "warli"),
    ("what is pattachitra", "pattachitra"),
    ("what is banarasi silk", "banarasi-silk"),
    ("what is the difference between hindustani and carnatic music", "hindustani-vs-carnatic"),
    ("what are india's unesco world heritage sites", "unesco-sites-india"),
    ("what is rani ki vav", "rani-ki-vav"),
    ("what is fatehpur sikri", "fatehpur-sikri"),
    ("what is the red fort", "red-fort"),
    ("what is nalanda", "nalanda"),
    ("what is the mahabodhi temple", "mahabodhi-temple"),
]

# Described, not named -- what Vosk hands us when it cannot spell the noun.
DESCRIPTOR_CASES = [
    ("what is the festival of lights", "diwali"),
    ("tell me about the festival of colours", "holi"),
    ("which temple is shaped like a chariot", "konark-sun-temple"),
    ("tell me about the sun temple", "konark-sun-temple"),
    ("where did buddha attain enlightenment", "mahabodhi-temple"),
    ("what is the classical dance of tamil nadu", "bharatanatyam"),
    ("tell me about the classical dance of kerala with painted faces", "kathakali"),
    ("what is the harvest festival of kerala", "onam"),
    ("what is the harvest festival of punjab", "baisakhi"),
    ("why is it called the pink city", "jaipur"),
    ("which is the city of lakes", "udaipur"),
    ("tell me about the queen's stepwell", "rani-ki-vav"),
    ("what is the monument of love", "taj-mahal"),
    ("tell me about the largest gathering of pilgrims", "kumbh-mela"),
    ("what is the folk dance of punjab", "bhangra"),
    ("what is the folk dance of gujarat", "garba"),
    ("tell me about the ancient university in bihar", "nalanda"),
    ("what is the traditional medicine of india", "ayurveda"),
    ("what is the sun salutation", "surya-namaskar"),
    ("what does the guest is god mean", "atithi-devo-bhava"),
    ("who built the white marble tomb in agra", "taj-mahal"),
    ("tell me about indian food", "indian-cuisine"),
    ("tell me about south indian food", "dosa-idli"),
]

# Close neighbours: the more specific topic must win.
DISAMBIGUATION_CASES = [
    ("what is carnatic music", "carnatic-music"),
    ("what is hindustani classical music", "hindustani-music"),
    ("how are hindustani and carnatic music different", "hindustani-vs-carnatic"),
    ("tell me about the meenakshi temple", "meenakshi-temple"),
    ("tell me about madurai", "madurai"),
    ("tell me about varanasi", "varanasi"),
    ("what is the ganga aarti", "ganga-aarti"),
    ("tell me about banarasi sarees", "banarasi-silk"),
    ("what is yoga", "yoga"),
    ("when is international yoga day celebrated", "international-yoga-day"),
    ("what is masala chai", "masala-chai"),
    ("tell me about indian spices", "spices"),
    ("what are the classical dances of india", "classical-dances-of-india"),
    ("what is manipuri dance", "manipuri"),
    ("what is the jantar mantar", "jantar-mantar"),
    ("tell me about jaipur", "jaipur"),
    ("what is jaipur blue pottery", "blue-pottery"),
    ("what is tanjore painting", "tanjore-painting"),
    ("tell me about the brihadeeswara temple", "brihadeeswara-temple"),
    ("what is navratri and garba", "navratri"),
]

# Realistic ASR damage: split, misspelt or half-heard proper nouns.
ASR_NOISE_CASES = [
    ("tell me about bharat natyam", "bharatanatyam"),
    ("what is katha kali", "kathakali"),
    ("tell me about the taj", "taj-mahal"),
    ("what is kuchi pudi", "kuchipudi"),
    ("tell me about konarak", "konark-sun-temple"),
    ("what is deepavali", "diwali"),
    ("tell me about qutb minar", "qutub-minar"),
    ("tell me about kutub minar", "qutub-minar"),
    ("what is madhu bani painting", "madhubani"),
    ("tell me about nalanda university", "nalanda"),
    ("what is mohini attam", "mohiniyattam"),
    ("tell me about ajanta", "ajanta-caves"),
    ("what is patachitra", "pattachitra"),
    ("tell me about kanjeevaram sarees", "kanchipuram-silk"),
    ("tell me about the ajanta kaves", "ajanta-caves"),
    ("what is bharatnatyam dance", "bharatanatyam"),
]

# Out of domain, or nothing but filler: must NOT produce an answer.
NEGATIVE_CASES = [
    ("", None),
    ("tell me about", None),
    ("what is the", None),
    ("what is the capital of france", None),
    ("how is the weather today", None),
    ("tell me about the moon mission", None),
    ("what is a black hole", None),
    ("play some music", None),
    ("tell me about a temple", None),
    ("which festival", None),
    ("who won the cricket match", None),
]

GROUPS = [
    ("same topic, different wording", VARIATION_CASES),
    ("described, not named", DESCRIPTOR_CASES),
    ("close neighbours", DISAMBIGUATION_CASES),
    ("ASR noise", ASR_NOISE_CASES),
    ("out of domain", NEGATIVE_CASES),
]


def run(matcher: HeritageMatcher, cases):
    rows = []
    for query, expected in cases:
        result = matcher.match(query)
        got = result["topic"]["id"] if result else None
        rows.append((query, expected, got, result["layer"] if result else "-",
                     f"{result['confidence']:.2f}" if result else "-"))
    return rows


def main() -> int:
    verbose = "-v" in sys.argv
    matcher = HeritageMatcher()
    misses = 0

    # Every question the knowledge base lists must resolve to its own topic.
    listed = [(q, t["id"]) for t in matcher.topics for q in matcher.all_questions(t)]
    groups = [("every question in the knowledge base", listed), *GROUPS]

    for title, cases in groups:
        rows = run(matcher, cases)
        bad = [r for r in rows if r[2] != r[1]]
        misses += len(bad)
        print(f"{'PASS' if not bad else 'FAIL'}  {len(rows) - len(bad):>3}/{len(rows):<3}  {title}")
        for query, expected, got, layer, conf in (rows if verbose else bad):
            tag = "" if got == expected else "   <-- MISS"
            print(f"        {query!r:<62} want {expected or '(none)':<26} "
                  f"got {got or '(none)':<26} {layer:<10} {conf}{tag}")

    print(f"\n{'ALL PASSED' if not misses else f'{misses} MISS(ES)'}")
    return 1 if misses else 0


if __name__ == "__main__":
    sys.exit(main())
