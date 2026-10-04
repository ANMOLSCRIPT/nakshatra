"""
cloud_server.py -- receives audio from the edge device, transcribes it with
Vosk, answers the question from the cultural-heritage knowledge base, and
broadcasts everything to connected browser UIs in real time.

Endpoints:
    /ws/edge      the edge device (ESP32 firmware, or hardware/edge_agent.py as
                  its laptop stand-in) connects here -- see hardware/PROTOCOL.md
                  for the exact wire format this endpoint implements.
    /ws/ui        browsers connect here; receive a broadcast of every event the
                  server produces (forwarded edge events, ASR partials/finals,
                  heritage answers, latency breakdowns, connection state).
    /api/health   server, ASR and knowledge-base status (JSON).
    /api/kb       the full knowledge base (categories + topics) for the UI.
    /api/ask      POST {"question": "..."} -> the same answer engine the voice
                  path uses, for typed questions and suggestion chips (website
                  and Android app).
    /             the website (frontend/), served as static files.

Run:
    python backend/cloud_server.py
    (or, from backend/: uvicorn cloud_server:app --host 0.0.0.0 --port 8000)
"""

from __future__ import annotations

import argparse
import array
import asyncio
import json
import logging
import math
import sys
import time
from pathlib import Path

import vosk
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

sys.path.insert(0, str(Path(__file__).resolve().parent))  # run from any directory
from heritage_matcher import get_matcher  # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(name)s] %(message)s")
logger = logging.getLogger("cloud_server")
vosk.SetLogLevel(-1)  # silence Vosk's own C++ logging

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
WEB_DIR = PROJECT_ROOT / "frontend"
VOSK_MODELS_ROOT = ROOT / "models" / "vosk"
# Preferred first. The large Indian-English model knows most heritage proper
# nouns (Diwali, Konark, Bharatanatyam...); the small one does not and is only
# a fallback for machines that cannot spare the disk or RAM. Measured with
# tests/asr_eval.py -- see the README's ASR section.
VOSK_MODEL_CANDIDATES = ("vosk-model-en-in-0.5", "vosk-model-small-en-in-0.4")
VOSK_MODEL_DIR = next((VOSK_MODELS_ROOT / name for name in VOSK_MODEL_CANDIDATES
                       if (VOSK_MODELS_ROOT / name).exists()),
                      VOSK_MODELS_ROOT / VOSK_MODEL_CANDIDATES[-1])
UNMATCHED_LOG = ROOT / "logs" / "unmatched.jsonl"
SAMPLE_RATE = 16000

app = FastAPI(title="Nakshatra cloud server")


# =============================================================================
# Global state
# =============================================================================

class UIHub:
    """Broadcast hub for every /ws/ui client."""

    def __init__(self):
        self.clients: set[WebSocket] = set()

    async def register(self, ws: WebSocket) -> None:
        self.clients.add(ws)

    def unregister(self, ws: WebSocket) -> None:
        self.clients.discard(ws)

    async def broadcast(self, message: dict) -> None:
        if not self.clients:
            return
        payload = json.dumps(message)
        dead = []
        for client in list(self.clients):
            try:
                await client.send_text(payload)
            except Exception:
                dead.append(client)
        for client in dead:
            self.clients.discard(client)


ui_hub = UIHub()
heritage_matcher = get_matcher()

vosk_model = None


def resolve_vosk_model(name_or_path: str) -> Path:
    """A bare name is looked up under models/vosk/; anything else is a path."""
    p = Path(name_or_path)
    return p if p.exists() else VOSK_MODELS_ROOT / name_or_path


def load_vosk_model(path: Path) -> None:
    global vosk_model, VOSK_MODEL_DIR
    try:
        if not path.exists():
            raise FileNotFoundError(f"missing Vosk model at {path}")
        t0 = time.perf_counter()
        vosk_model = vosk.Model(str(path))
        VOSK_MODEL_DIR = path
        logger.info("Vosk model loaded from %s in %.2fs", path, time.perf_counter() - t0)
    except Exception as exc:  # noqa: BLE001 -- ASR must not prevent server startup
        vosk_model = None
        logger.warning("ASR disabled: could not load Vosk model (%s)", exc)


# When run as a script, main() loads the model after parsing --vosk-model;
# when imported (tests), load the default now.
if __name__ != "__main__":
    load_vosk_model(VOSK_MODEL_DIR)


def now_ms() -> float:
    return time.time() * 1000.0


# =============================================================================
# ASR domain vocabulary -- biases Vosk toward heritage words
# =============================================================================
# vosk-model-small-en-in-0.4 is speed-optimized and Indian proper nouns are
# thinly represented in its language model. KaldiRecognizer's optional third
# argument (a JSON word list) constrains the decoder to exactly those words, so
# the list has to cover everything a visitor might say about the knowledge
# base: every word of every topic name, alias, question, descriptor and
# keyword, plus ordinary question-framing words. Words the acoustic model has
# no pronunciation for are ignored by Vosk; anything else it hears becomes
# "[unk]", which the matcher discards.

_QUERY_FRAMING_WORDS = """
tell me about what is are was were the a an of in on at for to from by with and or
who whom whose when where why how which do does did we you i it they this that
show give explain describe please know want can could would
famous special history significance important meaning mean story information
celebrate celebrated built made called known come comes origin originate
india indian temple festival dance music art painting city fort palace
classical folk traditional culture heritage tradition ancient old
""".split()


def build_asr_vocabulary() -> list[str]:
    return sorted(heritage_matcher.vocabulary() | set(_QUERY_FRAMING_WORDS))


ASR_VOCAB_WORDS = build_asr_vocabulary()
ASR_VOCAB_GRAMMAR = json.dumps(ASR_VOCAB_WORDS + ["[unk]"])

# Set from main()'s --vocab-bias before uvicorn starts serving; read per-
# utterance in UtteranceASR.__init__, which only runs once requests are being
# handled (i.e. always after main() has had a chance to flip this).
VOCAB_BIAS_ENABLED = False


# =============================================================================
# REST API -- knowledge base + typed questions
# =============================================================================
# NOTE: the catch-all mount that serves the website ("/", "/css/...",
# "/js/...") is registered at the BOTTOM of this file, after every API and
# websocket route -- Starlette matches routes in registration order, and a
# Mount at "/" matches every path prefix, so mounting it here would shadow the
# routes below before they ever get a chance to match.

class AskRequest(BaseModel):
    # Bounded so an oversized body cannot tie up the matcher; the website and
    # the Android app both cap a typed question at 200 characters.
    question: str = Field(max_length=500)


def match_payload(match: dict | None) -> dict | None:
    """The answer as sent to a UI: the topic (with its spoken answer), how
    sure the matcher is, and which layer decided."""
    if match is None:
        return None
    return {
        **heritage_matcher.public_topic(match["topic"]),
        "confidence": round(match["confidence"], 3),
        "matched_by": match["layer"],
    }


@app.get("/api/health")
async def api_health() -> dict:
    return {
        "status": "ok",
        "wake_word": "nakshatra",
        "edge_connected": edge_state.connected,
        "ui_clients": len(ui_hub.clients),
        "asr": {
            "enabled": vosk_model is not None,
            "engine": "vosk",
            "model": VOSK_MODEL_DIR.name,
            "sample_rate": SAMPLE_RATE,
            "vocab_bias": VOCAB_BIAS_ENABLED,
            "vocab_words": len(ASR_VOCAB_WORDS),
        },
        "knowledge_base": {
            "topics": len(heritage_matcher.topics),
            "questions": sum(len(heritage_matcher.all_questions(t)) for t in heritage_matcher.topics),
            "categories": len(heritage_matcher.categories),
        },
    }


@app.get("/api/kb")
async def api_kb() -> JSONResponse:
    return JSONResponse(
        {
            "meta": heritage_matcher.meta,
            "categories": heritage_matcher.categories,
            "topics": [heritage_matcher.public_topic(t) for t in heritage_matcher.topics],
        },
        headers={"Cache-Control": "no-cache"},
    )


@app.post("/api/ask")
async def api_ask(req: AskRequest) -> dict:
    """Typed question -> the same matcher the voice path uses."""
    t0 = time.perf_counter()
    match = heritage_matcher.match(req.question)
    return {
        "question": req.question,
        "match": match_payload(match),
        "match_ms": round((time.perf_counter() - t0) * 1000.0, 2),
    }


# =============================================================================
# /ws/ui -- browser clients
# =============================================================================

@app.websocket("/ws/ui")
async def ws_ui(websocket: WebSocket) -> None:
    await websocket.accept()
    await ui_hub.register(websocket)
    await websocket.send_text(json.dumps({
        "event": "edge_status",
        "connected": edge_state.connected,
    }))
    try:
        while True:
            # UI clients don't send us anything meaningful; just block until
            # they disconnect so we notice and clean up.
            await websocket.receive_text()
    except WebSocketDisconnect:
        pass
    finally:
        ui_hub.unregister(websocket)


# =============================================================================
# /ws/edge -- the edge device (edge_agent.py today, ESP32 firmware later)
# =============================================================================

class EdgeState:
    """Tracks whether an edge device is currently connected, so a fresh
    /ws/ui client can be told the current status instead of guessing."""

    def __init__(self):
        self.connected = False


edge_state = EdgeState()


class UtteranceASR:
    """One Vosk recognizer + latency timestamps for a single wake -> streaming
    -> utterance_end cycle. Recreated fresh on every `wake` event.
    """

    def __init__(self, t1: float):
        self.t1 = t1        # wake detected (from edge)
        self.t2: float | None = None   # first PCM frame received at server
        self.t3: float | None = None   # first ASR partial produced
        self.last_partial = ""
        # Vosk's endpointer finalises a segment whenever it hears trailing silence
        # (which every real utterance has: the edge only sends utterance_end after
        # 1.5 s of it), so the words arrive as "final" results DURING streaming.
        # FinalResult() at utterance_end then returns only what is left after the
        # last endpoint -- usually "". Keep every finalised segment so
        # final_result() can return the whole utterance.
        self.segments: list[str] = []

        if vosk_model is None:
            self.recognizer = None
        elif VOCAB_BIAS_ENABLED:
            self.recognizer = vosk.KaldiRecognizer(vosk_model, SAMPLE_RATE, ASR_VOCAB_GRAMMAR)
        else:
            self.recognizer = vosk.KaldiRecognizer(vosk_model, SAMPLE_RATE)

    def process_frame(self, pcm: bytes) -> tuple[str, str]:
        """Runs on a worker thread (via run_in_executor) so the receive loop
        never blocks on native Vosk calls. Returns (kind, text) where kind is
        "final", "partial", or "none"."""
        if self.recognizer is None:
            return ("none", "")
        if self.recognizer.AcceptWaveform(pcm):
            text = json.loads(self.recognizer.Result()).get("text", "")
            if text:
                self.segments.append(text)
            return ("final", text)
        partial = json.loads(self.recognizer.PartialResult()).get("partial", "")
        return ("partial", partial)

    def final_result(self) -> str:
        """The whole utterance: every segment Vosk finalised while streaming,
        plus whatever FinalResult() still holds. Called once, after the last
        frame, so no worker thread is appending any more."""
        if self.recognizer is None:
            return ""
        tail = json.loads(self.recognizer.FinalResult()).get("text", "")
        return " ".join([*self.segments, *([tail] if tail else [])])


@app.websocket("/ws/edge")
async def ws_edge(websocket: WebSocket) -> None:
    await websocket.accept()
    edge_state.connected = True
    logger.info("edge connected")
    await ui_hub.broadcast({"event": "edge_status", "connected": True})

    loop = asyncio.get_running_loop()
    utterance: UtteranceASR | None = None

    try:
        while True:
            message = await websocket.receive()

            if message.get("type") == "websocket.disconnect":
                break

            text = message.get("text")
            data = message.get("bytes")

            if text is not None:
                try:
                    payload = json.loads(text)
                except json.JSONDecodeError:
                    logger.warning("dropping malformed JSON frame from edge: %r", text[:200])
                    continue
                await _handle_edge_event(payload, loop)
                event = payload.get("event")
                if event == "wake":
                    utterance = UtteranceASR(t1=payload.get("t1", now_ms()))
                elif event == "utterance_end":
                    if utterance is not None:
                        await _finish_utterance(utterance, loop)
                    utterance = None

            elif data is not None:
                if utterance is None:
                    # Defensive: PCM with no active utterance (e.g. we missed
                    # the wake event). Ignore -- never crash the connection.
                    continue
                if utterance.t2 is None:
                    utterance.t2 = now_ms()
                await _handle_pcm_frame(utterance, data, loop)

    except WebSocketDisconnect:
        pass
    finally:
        edge_state.connected = False
        logger.info("edge disconnected")
        await ui_hub.broadcast({"event": "edge_status", "connected": False})


async def _handle_edge_event(payload: dict, loop: asyncio.AbstractEventLoop) -> None:
    event = payload.get("event")
    if event == "level":
        await ui_hub.broadcast({"event": "level", "rms": payload.get("rms")})
    elif event == "wake":
        await ui_hub.broadcast({
            "event": "wake",
            "t1": payload.get("t1"),
            "prob": payload.get("prob"),
        })
    elif event == "utterance_end":
        pass  # handled by _finish_utterance, called by the caller
    else:
        logger.warning("unknown edge event type: %r", event)


def pcm_rms(pcm: bytes) -> float:
    """RMS of an int16 little-endian PCM frame, scaled to [0, 1) like the
    edge's own `level.rms`."""
    samples = array.array("h")
    samples.frombytes(pcm[: len(pcm) - (len(pcm) % 2)])
    if sys.byteorder == "big":
        samples.byteswap()
    if not samples:
        return 0.0
    return math.sqrt(sum(x * x for x in samples) / len(samples)) / 32768.0


async def _handle_pcm_frame(utterance: UtteranceASR, pcm: bytes, loop: asyncio.AbstractEventLoop) -> None:
    # The edge only sends `level` while IDLE; while it streams, derive the same
    # reading from the audio itself so the UI's waveform shows the real voice.
    await ui_hub.broadcast({"event": "level", "rms": round(pcm_rms(pcm), 4), "streaming": True})
    kind, value = await loop.run_in_executor(None, utterance.process_frame, pcm)
    if kind == "final" and value:
        await ui_hub.broadcast({"event": "final_segment", "text": value})
    elif kind == "partial" and value and value != utterance.last_partial:
        if utterance.t3 is None:
            utterance.t3 = now_ms()
        utterance.last_partial = value
        await ui_hub.broadcast({"event": "partial", "text": value})


def log_unmatched(transcript: str) -> None:
    """Keep every spoken question the answer engine could not place, so the
    knowledge base can be taught what the recogniser actually hears (add the
    phrase to that topic's `aliases` in heritage.json)."""
    try:
        UNMATCHED_LOG.parent.mkdir(exist_ok=True)
        with open(UNMATCHED_LOG, "a", encoding="utf-8") as f:
            f.write(json.dumps({"t": time.strftime("%Y-%m-%d %H:%M:%S"), "heard": transcript}) + "\n")
    except OSError as exc:  # never let logging break the voice path
        logger.warning("could not write %s: %s", UNMATCHED_LOG, exc)


async def _finish_utterance(utterance: UtteranceASR, loop: asyncio.AbstractEventLoop) -> None:
    final_text = await loop.run_in_executor(None, utterance.final_result)
    t4 = now_ms()
    logger.info("utterance transcript %r (%d segment(s) finalised while streaming)",
                final_text, len(utterance.segments))

    # NOTE: T1 (edge) and T2 (server) currently share the same laptop clock,
    # so T2 - T1 is a clean wall-clock latency measurement today. Once the
    # edge is a real ESP32 on the network, T1 comes from a different clock
    # than T2-T5 and this subtraction will need explicit clock-offset
    # correction (e.g. an NTP-style handshake at connect time) or the
    # latency numbers will silently include clock skew.
    latency_ms = (utterance.t2 - utterance.t1) if (utterance.t2 is not None) else None

    await ui_hub.broadcast({
        "event": "transcript",
        "text": final_text,
        "t1": utterance.t1,
        "t2": utterance.t2,
        "t3": utterance.t3,
        "t4": t4,
        "latency_ms": latency_ms,
    })

    match = heritage_matcher.match(final_text)
    logger.info("ANSWER: %s", f"{match['topic']['id']} via {match['layer']}" if match else "no match")
    t5 = now_ms()
    if match is None and final_text.strip():
        log_unmatched(final_text)

    await ui_hub.broadcast({
        "event": "content",
        "transcript": final_text,
        "match": match_payload(match),
        "t5": t5,
        "match_latency_ms": t5 - utterance.t1,
    })


# Catch-all static mount for the website in frontend/ (index.html at "/",
# css/, js/, assets/). Registered last -- see the NOTE above the REST API for
# why this must come after every other route.
app.mount("/", StaticFiles(directory=str(WEB_DIR), html=True), name="web")


# =============================================================================
# main
# =============================================================================

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Nakshatra cloud server.")
    parser.add_argument("--host", default="0.0.0.0",
                         help="interface to bind (default: %(default)s, so an ESP32 on the "
                              "same Wi-Fi can reach it)")
    parser.add_argument("--port", type=int, default=8000, help="port (default: %(default)s)")
    parser.add_argument("--vocab-bias", dest="vocab_bias", action="store_true", default=VOCAB_BIAS_ENABLED,
                         help="constrain Vosk to the heritage word list")
    parser.add_argument("--no-vocab-bias", dest="vocab_bias", action="store_false",
                         help="let Vosk transcribe with its full vocabulary")
    parser.add_argument("--vosk-model", default=VOSK_MODEL_DIR.name,
                         help="Vosk model directory name under backend/models/vosk/ (or a full "
                              "path), e.g. vosk-model-en-in-0.5 for the large Indian-English "
                              "model (default: %(default)s)")
    return parser.parse_args()


def main() -> None:
    global VOCAB_BIAS_ENABLED
    args = parse_args()
    VOCAB_BIAS_ENABLED = args.vocab_bias
    load_vosk_model(resolve_vosk_model(args.vosk_model))
    logger.info(
        "ASR vocabulary bias: %s (%d words + [unk])",
        "ON" if VOCAB_BIAS_ENABLED else "OFF", len(ASR_VOCAB_WORDS),
    )
    logger.info("knowledge base: %d topics in %d categories",
                len(heritage_matcher.topics), len(heritage_matcher.categories))
    logger.info("website: http://localhost:%d/   edge endpoint: ws://<this-machine>:%d/ws/edge",
                args.port, args.port)

    import uvicorn
    # Pass the app object directly, not the "module:attr" string form -- the
    # string form makes uvicorn re-import this file under the module name
    # "cloud_server" even though it's already loaded as "__main__", which
    # silently double-runs every module-level startup step (Vosk model
    # loaded twice, ~2s wasted, double the memory) and serves the second
    # import's app instance while the first import's globals go unused.
    uvicorn.run(app, host=args.host, port=args.port, log_level="info")


if __name__ == "__main__":
    main()
