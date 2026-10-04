# System architecture

## End-to-end flow

```text
                    USER
                     │  "Nakshatra … why do we celebrate Diwali?"
                     ▼
        ┌──────────────────────────┐
        │  ESP32-WROOM-32          │   hardware/  (edge_agent.py is the laptop stand-in)
        │  INMP441 I²S microphone  │
        │                          │   IDLE: every 100 ms
        │  MFCC (49×10) ─► DS-CNN-S│     1 s window ─► MFCC ─► int8 model ─► softmax
        │  int8, 44.8 kB           │     fire when 3 of last 5 P(keyword) > 0.75
        └────────────┬─────────────┘   Only a level reading (one float) leaves the device.
                     │
                     │  wake word detected ─► STREAMING
                     ▼
        WebSocket  ws://server:8000/ws/edge          hardware/PROTOCOL.md
          {"event":"wake","t1":…,"prob":…}
          <binary> 3 pre-roll frames + live frames   int16 LE, mono, 16 kHz, 100 ms each
          {"event":"utterance_end"}                  after 1.5 s of silence, or 8 s
                     │
                     ▼
        ┌──────────────────────────┐
        │  backend/cloud_server.py │   FastAPI + uvicorn
        │                          │
        │  Vosk streaming ASR      │   partial transcripts while the user is speaking
        │        │                 │
        │        ▼                 │
        │  heritage_matcher.py     │   question ─► fuzzy ─► descriptor ─► keywords ─► phonetic
        │        │                 │
        │        ▼                 │
        │  knowledge_base/         │   101 topics, 15 categories, one spoken answer each
        │    heritage.json         │
        └────────────┬─────────────┘
                     │  WebSocket /ws/ui  (broadcast to every open browser)
                     │    edge_status · level · wake · partial · final_segment
                     │    transcript {t1..t4} · content {match, t5}
                     ▼
        ┌──────────────────────────┐   ┌──────────────────────────┐
        │  frontend/  (website)    │   │  android/  (Android app) │
        │                          │   │                          │
        │  answer card + speech    │   │  answer card + speech    │
        └──────────────────────────┘   └──────────────────────────┘
          both: a state machine driven only by those events —
          idle ─► listening for Nakshatra ─► Nakshatra detected
               ─► listening ─► processing ─► answering
```

The website and the Android app are two clients of the same server. Both load
the archive from `GET /api/kb`, send typed questions to `POST /api/ask`, read
`GET /api/health` and follow `/ws/ui`. Neither holds a copy of the content.

## Why the split is where it is

* **Privacy and bandwidth.** While idle the device transmits one number (the
  input level) every 200 ms and no audio at all. Speech leaves the device only
  after the on-device model has heard the wake word.
* **Cost.** A 44.8 kB int8 model and a fixed-point MFCC fit a microcontroller;
  large-vocabulary speech recognition does not, so it runs on the server.
* **Latency.** The device sends 300 ms of pre-roll with the wake event, so the
  first word of the question is never clipped by detection delay, and the
  recogniser starts decoding while the user is still speaking.

## Components

### Edge (`hardware/`)

| File | Role |
|---|---|
| `kws/config.py` | Single source of truth. `FROZEN` values (sample rate, framing, mel/MFCC sizes) are hashed into `FEATURE_CONTRACT_HASH`, stamped on every checkpoint and exported header, so a model can never silently run against a front end it was not trained on. |
| `kws/features.py` | MFCC as explicit array operations (pre-emphasis, framing, symmetric Hann, FFT, mel filterbank, log, DCT-II), so the firmware port can be checked number-for-number. |
| `kws/model.py`, `train.py`, `quantize.py` | DS-CNN-S, float training, int8 post-training quantisation. |
| `kws/eval_streaming.py`, `eval_heldout.py` | Detection policy evaluated on continuous audio and on a held-out session. |
| `kws/export_c.py`, `make_firmware_bundle.py` | Model and feature tables as C arrays; builds `firmware_handoff/`. |
| `edge_agent.py` | The reference edge device and the specification of the firmware's behaviour. |
| `firmware_handoff/` | Checksummed bundle the ESP32 firmware is built from, with parity and go/no-go tests. |
| `PROTOCOL.md` | The wire contract. |

### Server (`backend/`)

| File | Role |
|---|---|
| `cloud_server.py` | `/ws/edge` ingest, one Vosk recogniser per utterance (run on a worker thread so the socket never blocks), latency timestamps T1–T5, `/ws/ui` broadcast, REST API, static website. |
| `heritage_matcher.py` | The answer engine: resolves a transcript to one topic, or to nothing. |
| `test_matcher.py` | Regression set for the answer engine. |
| `test_edge_sim.py` | Replays a WAV file as an edge device (no keyword model). |

### Knowledge (`knowledge_base/heritage.json`)

One file, read by the server at start-up and served to the website through
`GET /api/kb`. Nothing about the content is hard-coded in Python or JavaScript.

### Website (`frontend/`)

Static HTML, CSS and ES modules, no build step, served by the same FastAPI
process. `voice.js` owns the interaction state; `main.js` renders every section
from the knowledge base; `art.js` draws all illustrations procedurally;
`map.js` plots the heritage constellation.

## Latency timestamps

| Stamp | Taken where | Meaning |
|---|---|---|
| T1 | edge | wake word detected |
| T2 | server | first audio frame received |
| T3 | server | first partial transcript produced |
| T4 | server | final transcript assembled (after `utterance_end`) |
| T5 | server | answer chosen |

`T2 − T1` is the wake-to-server latency; `T5 − T4` is the answer engine (about
a millisecond). `T4 − T1` is dominated by how long the user speaks plus the
1.5 s of silence that ends an utterance. T1 comes from the edge clock, so with
a real ESP32 `T2 − T1` includes clock skew unless the firmware syncs time.

### Android app (`android/`)

Kotlin, Jetpack Compose, Material 3; one activity. See [android/README.md](../android/README.md).

| File | Role |
|---|---|
| `data/Models.kt` | The JSON of `/api/kb`, `/api/ask`, `/api/health` and `/ws/ui`, decoded leniently so new server fields are harmless. |
| `data/Network.kt` | Retrofit API; a `/ws/ui` WebSocket with the website's reconnect backoff (1 s growing to 8 s). |
| `data/Repository.kt` | The knowledge base as a `StateFlow`, plus the last good reply on disk so the archive stays readable when the server is out of reach. |
| `voice/VoiceEngine.kt` | A port of `voice.js`: the same six states, timings and transitions, driven only by real events. |
| `data/Constellation.kt` | A port of `map.js`: projection, nudging apart places that share a town, minimum spanning tree. |
| `ui/art/MotifData.kt` | The 22 line-art motifs, generated from `frontend/js/art.js` by `scripts/export_android_motifs.mjs`. |

The app connects when it comes on screen and closes its socket when it leaves,
so it holds no connection in the background. The server address is a setting:
`http://10.0.2.2:8000` on the emulator, the laptop's LAN address on a phone.
