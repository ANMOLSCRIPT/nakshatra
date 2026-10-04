"""
e2e_pipeline.py -- the whole voice pipeline, with no microphone and no ESP32.

    held-out "Nakshatra" recording + a spoken question
        -> hardware/edge_agent.py  (the REAL KeywordSpotter: MFCC -> int8 TFLite
                                    model -> k-of-window detector, and the REAL
                                    EdgeAgent state machine and wire protocol)
        -> ws://.../ws/edge
        -> backend/cloud_server.py (Vosk ASR -> heritage answer engine)
        -> ws://.../ws/ui          (what a browser receives)

Only the sound source is substituted: instead of PortAudio, audio is fed to
EdgeAgent.handle_chunk() in 100 ms chunks at real-time pace. The wake word is a
recording the model was never trained on; the question is synthesised with the
macOS `say` voice (on other platforms the question is silence and only the
protocol sequence is checked).

Checks, in order:
  1. a hard negative ("confusable word") recording does NOT wake the device
  2. the wake-word recording DOES, and the UI receives `wake`
  3. PCM is streamed, the server emits live `level` and `partial` events
  4. silence ends the utterance; the UI receives `transcript` then `content`
  5. the answer is the expected topic

Run (starts its own server on a spare port):
    python tests/e2e_pipeline.py
    python tests/e2e_pipeline.py --question "What is the festival of lights?" --expect diwali
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

import numpy as np
import soundfile as sf
import websockets

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "hardware"))
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")

import edge_agent  # noqa: E402  (hardware/edge_agent.py -- unmodified reference edge device)

RECORDINGS = ROOT / "hardware" / "kws" / "data" / "recordings"
WAKE_WAV = RECORDINGS / "positive" / "positive_laptop_test_027.wav"   # the firmware bundle's GO vector
HARDNEG_WAV = RECORDINGS / "hardneg" / "hardneg_laptop_test_001.wav"  # ... and its NO-GO vector
HOP = edge_agent.HOP_SAMPLES
SR = edge_agent.SAMPLE_RATE


def load_f32(path: Path) -> np.ndarray:
    audio, sr = sf.read(str(path), dtype="float32", always_2d=False)
    assert sr == SR, f"{path} is {sr} Hz"
    return audio if audio.ndim == 1 else audio[:, 0]


def synthesise(text: str, voice: str, out_dir: Path) -> np.ndarray:
    if shutil.which("say") is None:
        return np.zeros(SR, dtype=np.float32)
    wav = out_dir / "question.wav"
    subprocess.run(["say", "-v", voice, "-o", str(wav), "--data-format=LEI16@16000", text], check=True)
    return load_f32(wav)


def chunks(audio: np.ndarray):
    pad = (-len(audio)) % HOP
    audio = np.concatenate([audio, np.zeros(pad, dtype=np.float32)])
    for i in range(0, len(audio), HOP):
        yield audio[i:i + HOP]


def wait_for_server(port: int, proc: subprocess.Popen, timeout: float = 90.0) -> dict:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if proc.poll() is not None:
            raise RuntimeError(f"server exited early with code {proc.returncode}")
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/api/health", timeout=1) as r:
                return json.load(r)
        except OSError:
            time.sleep(0.5)
    raise TimeoutError("server did not come up")


async def run(args, port: int) -> int:
    failures: list[str] = []

    def check(ok: bool, label: str) -> None:
        print(f"  {'PASS' if ok else 'FAIL'}  {label}")
        if not ok:
            failures.append(label)

    spotter = edge_agent.KeywordSpotter({"threshold": None, "k": None, "window": None, "refractory_hops": None})
    spotter.warmup()
    op = spotter.op
    print(f"edge model: {edge_agent.MODEL_PATH.relative_to(ROOT)}  contract {spotter.contract_hash}  "
          f"theta={op['threshold']} k={op['k']}/{op['window']} ({spotter.op_source})")

    agent = edge_agent.EdgeAgent(spotter, f"ws://127.0.0.1:{port}/ws/edge")
    events: list[dict] = []

    async def ui_listener() -> None:
        async with websockets.connect(f"ws://127.0.0.1:{port}/ws/ui") as ws:
            async for raw in ws:
                events.append(json.loads(raw))

    tasks = [asyncio.create_task(ui_listener()),
             asyncio.create_task(agent.sender()),
             asyncio.create_task(agent.connection_manager())]

    async def feed(audio: np.ndarray) -> None:
        for chunk in chunks(audio):
            agent.handle_chunk(chunk)
            await asyncio.sleep(HOP / SR)  # real-time pace, like a microphone

    silence = lambda s: np.zeros(int(SR * s), dtype=np.float32)  # noqa: E731
    question = synthesise(args.question, args.voice, Path(args.tmp))
    spoken = shutil.which("say") is not None

    try:
        await asyncio.sleep(1.0)  # let both sockets connect
        check(any(e.get("event") == "edge_status" and e.get("connected") for e in events),
              "server tells the UI an edge device connected")

        print("\n1. hard negative (a confusable word) must not wake the device")
        await feed(np.concatenate([silence(1.2), load_f32(HARDNEG_WAV), silence(1.0)]))
        check(agent.detections == 0 and agent.state == "IDLE", "no wake on the hard-negative recording")
        check(any(e.get("event") == "level" and not e.get("streaming") for e in events),
              "idle `level` readings reach the UI")
        check(not any(e.get("event") in ("partial", "transcript") for e in events),
              "no audio left the device while idle")

        print("\n2. held-out \"Nakshatra\" recording must wake it")
        await feed(np.concatenate([load_f32(WAKE_WAV), silence(0.3)]))
        check(agent.detections == 1, "KWS fired exactly once")
        check(agent.state == "STREAMING", "edge switched IDLE -> STREAMING")

        print(f"\n3. question: {args.question!r}" + ("" if spoken else "  (no `say` on this platform: silence)"))
        await feed(question)
        await feed(silence(edge_agent.STREAMING_SILENCE_S + 0.6))
        check(agent.state == "IDLE", "silence ended the utterance (STREAMING -> IDLE)")
        await asyncio.sleep(1.5)  # let the server finish and broadcast

        kinds = [e["event"] for e in events]
        wake = next((e for e in events if e["event"] == "wake"), None)
        transcript = next((e for e in events if e["event"] == "transcript"), None)
        content = next((e for e in events if e["event"] == "content"), None)

        check(wake is not None and wake["prob"] > op["threshold"], f"UI got `wake` (prob {wake and round(wake['prob'], 3)})")
        check(any(e["event"] == "level" and e.get("streaming") for e in events), "live voice level reported while streaming")
        check(transcript is not None and content is not None, "UI got `transcript` and `content`")
        if transcript and content:
            check(kinds.index("wake") < kinds.index("transcript") < kinds.index("content"),
                  "order is wake -> transcript -> content")
            lat = transcript.get("latency_ms")
            check(lat is not None and 0 <= lat < 1500, f"wake -> first audio at server: {lat and round(lat)} ms")
            print(f"        ASR heard: {transcript['text']!r}")
            if spoken:
                check("partial" in kinds, "streaming partial transcripts arrived before the final one")
                got = content["match"]["id"] if content["match"] else None
                check(got == args.expect, f"answer is {got!r} (expected {args.expect!r})")
                if content["match"]:
                    m = content["match"]
                    print(f"        matched by {m['matched_by']} at {m['confidence']:.2f}; "
                          f"wake -> answer {content['match_latency_ms'] / 1000:.1f} s")
                    print(f"        answer: {m['answer'][:110]}...")
    finally:
        agent.stop_requested = True
        for t in tasks:
            t.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)

    print(f"\n{'ALL CHECKS PASSED' if not failures else f'{len(failures)} CHECK(S) FAILED'}")
    return 1 if failures else 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--question", default="Why do we celebrate Diwali in India?")
    ap.add_argument("--expect", default="diwali", help="topic id the answer should resolve to")
    ap.add_argument("--voice", default="Rishi", help="macOS `say` voice for the question")
    ap.add_argument("--port", type=int, default=8765)
    args = ap.parse_args()

    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        args.tmp = tmp
        log = open(Path(tmp) / "server.log", "w")
        server = subprocess.Popen([sys.executable, str(ROOT / "backend" / "cloud_server.py"),
                                   "--host", "127.0.0.1", "--port", str(args.port)],
                                  stdout=log, stderr=subprocess.STDOUT)
        try:
            health = wait_for_server(args.port, server)
            print(f"server up: ASR {health['asr']['model']} (enabled={health['asr']['enabled']}), "
                  f"{health['knowledge_base']['topics']} topics")
            return asyncio.run(run(args, args.port))
        finally:
            server.terminate()
            try:
                server.wait(timeout=10)
            except subprocess.TimeoutExpired:
                server.kill()
            log.close()


if __name__ == "__main__":
    sys.exit(main())
