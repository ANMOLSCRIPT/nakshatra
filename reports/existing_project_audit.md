# Existing project audit — Nakshatra (SIH26214)

Audited on 2026-10-04, before any Android work. Everything below was read from
the files in this folder or observed from the running server; nothing is assumed.

## 1. What the project is

Nakshatra is a **voice-activated cultural-heritage companion** for SIH Problem
Statement 214 ("Cultural Heritage and Traditions of India", hardware category).
An ESP32 with an INMP441 microphone detects the wake word "Nakshatra" on the
device, streams the question that follows to a server, the server transcribes it
and answers from a curated knowledge base, and a website shows and speaks the answer.

## 2. Folder structure

| Path | Contents |
|---|---|
| `backend/` | FastAPI server (`cloud_server.py`), answer engine (`heritage_matcher.py`), tests, Vosk models (downloaded, git-ignored), run-time log |
| `frontend/` | The website: `index.html`, `library.html`, `css/styles.css`, six ES modules in `js/` — no framework, no build step |
| `knowledge_base/heritage.json` | The single source of content: 15 categories, 101 topics |
| `hardware/` | Keyword-spotting pipeline, dataset, `edge_agent.py` (laptop stand-in for the ESP32), `PROTOCOL.md`, checksummed `firmware_handoff/` bundle. **Frozen** — not touched by this work |
| `scripts/` | KB validator, README question-list generator, ASR model download, `run_tests.sh` |
| `tests/` | End-to-end pipeline test, headless-Chrome UI test, ASR accuracy sweep |
| `docs/architecture.md` | Architecture notes |
| `assets/screenshots/` | Eight website screenshots |
| `submission/` | Presentation / demo placeholders and demo script |

## 3. Backend — what the website is actually connected to

**There is no Supabase, no Firebase, no SQL database, no object storage and no
authentication.** The backend is one Python process:

* **FastAPI + uvicorn**, one port (8000), bound to `0.0.0.0`.
* **Data store:** `knowledge_base/heritage.json`, loaded into memory at start-up.
* **ASR:** Vosk (Kaldi), Indian-English model, run locally.
* **Answer engine:** `heritage_matcher.py` — exact question → fuzzy (rapidfuzz)
  → descriptor → keywords → phonetic (metaphone). Returns one topic or nothing.
  No external AI API is called.

### API surface

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/health` | GET | Server, ASR and knowledge-base status; whether an edge device is connected |
| `/api/kb` | GET | `{meta, categories[], topics[]}` — the whole knowledge base |
| `/api/ask` | POST `{"question": "..."}` | `{question, match, match_ms}`; `match` is a topic plus `confidence` and `matched_by`, or `null` |
| `/ws/ui` | WebSocket | Broadcast of the live voice pipeline: `edge_status`, `level`, `wake`, `partial`, `final_segment`, `transcript`, `content` |
| `/ws/edge` | WebSocket | Ingest from the edge device (wake event, PCM frames, `utterance_end`) |
| `/` | GET | The website, served as static files |

### Topic schema (as served by `/api/kb`)

`id`, `category`, `name`, `motif`, `question`, `alternative_questions[]`,
`aliases[]`, `descriptors[]`, `keywords[]`, `answer`, `related_topics[]`,
`location {place, state, lat, lon} | null`, `era`, optional `featured`,
optional `image`, plus server-added `category_name` and
`related[] {id, name, question}`.

Counts on the audited data: 101 topics, 363 listed phrasings, 15 categories,
78 topics with a location, 8 featured.

## 4. Website — features that actually exist

| Feature | Where | Notes |
|---|---|---|
| Hero with live voice orb and counts | `index.html`, `main.js` | Counts are computed from the KB |
| How it works (5-step pipeline) | `index.html` | Lights up with the live state |
| Explore India: 15 themes → topics | `main.js` | Tapping a topic asks its question |
| Featured heritage: 8 cards | `main.js` | Line art, or a photograph if the topic has `image` |
| Ask Nakshatra console | `voice.js`, `main.js` | Six states (idle → armed → wake → listening → processing → answering), typed questions, suggestion chips, answer card with related topics, telemetry tiles |
| Spoken answers | `voice.js` | Browser speech synthesis, on/off switch and voice picker, preference in `localStorage` |
| Heritage constellation (map) | `map.js` | Topics plotted at their latitude/longitude, joined by a minimum spanning tree, theme filter. No map tiles, no map API key |
| Archive | `library.html`, `library.js` | Every topic with search and theme filter, "Read aloud" |
| Procedural art | `art.js` | 22 line-art motifs and the orb; no image files |

**Not present** (and therefore not built for Android): user accounts, favourites
or bookmarks, user contributions, quizzes, recommendations beyond the per-topic
related list, media galleries, remote images, GPS-based discovery, generative AI.

## 5. Visual identity

* Palette: lamp-black aubergine `#140d12`, temple gold `#e9b44c`, saffron
  `#e2782a`, vermilion `#c8402b`, peacock `#2a9d9a`, on sandstone ivory `#f7efde`.
* Type: Cormorant Garamond (display serif) and DM Sans (UI).
* Per-category two-stop gradients (`PALETTE` in `art.js`), a separate set of
  eight jewel-tone gradients for featured cards.
* The accent colour shifts with the voice state (gold, saffron, peacock).

## 6. Configuration, environment and deployment

* No `.env` files, no environment variables, no secrets anywhere in the tree.
* No Node.js toolchain and no `package.json`.
* No deployment configuration: the project runs locally on the demo laptop, and
  the ESP32 reaches it over the venue Wi-Fi.
* Python 3.11 virtual environment in `.venv/` (git-ignored).

## 7. Tests and scripts

`scripts/run_tests.sh` runs: KB validation, README question-list check,
answer-engine regression (468 cases), KWS config and MFCC self-tests, firmware
checksums and C parity, end-to-end pipeline, the website in headless Chrome,
and an ASR accuracy sweep.

## 8. Git state

The folder was **not a git repository** at the time of the audit (a `.gitignore`
and `.gitattributes` exist). No GitHub repository for this project was found on
the `ANMOLSCRIPT` account.

## 9. Consequences for the Android app

1. The app is a client of the same FastAPI server: `/api/kb`, `/api/ask`,
   `/api/health` and `/ws/ui`. No backend, database or endpoint is duplicated.
2. The server is a LAN service, so the app needs a configurable server address
   (emulator default `http://10.0.2.2:8000`) and must allow plain HTTP.
3. There is nothing secret to embed: the API needs no key.
4. The illustrations must be drawn natively; they are generated from the same
   `art.js` so the two clients cannot drift.
5. The backend needed no change to serve a native client.
