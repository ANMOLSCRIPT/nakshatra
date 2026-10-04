"""
asr_eval.py -- how well does the ASR + answer engine cope with spoken
heritage questions?

Synthesises each topic's primary question with the macOS `say` voice (Indian
English by default), pushes the audio through the server's own UtteranceASR in
100 ms frames exactly as /ws/edge does, and checks that the transcript
resolves to the right topic. Compares vocabulary bias ON vs OFF.

macOS only (needs `say`). Synthetic speech is cleaner than a real microphone,
so treat the numbers as an upper bound, not a field measurement.

Run:
    python tests/asr_eval.py                 # every topic's primary question
    python tests/asr_eval.py -v              # show every transcript
    python tests/asr_eval.py --voice Samantha
    python tests/asr_eval.py --model vosk-model-en-in-0.5
    python tests/asr_eval.py --all-questions # every listed phrasing (slow)
"""

from __future__ import annotations

import argparse
import hashlib
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "backend"))

import cloud_server  # noqa: E402  (loads the default Vosk model on import)

CACHE = ROOT / "tests" / ".tts_cache"
FRAME_BYTES = 3200  # 1600 samples = 100 ms, the edge protocol's frame size


def synthesise(text: str, voice: str) -> bytes:
    """16 kHz mono int16 PCM for `text`, with 0.3 s lead-in and 1.5 s tail of
    silence (the edge only ends an utterance after 1.5 s of quiet)."""
    CACHE.mkdir(exist_ok=True)
    wav = CACHE / f"{voice}-{hashlib.sha1(text.encode()).hexdigest()[:16]}.wav"
    if not wav.exists():
        subprocess.run(["say", "-v", voice, "-o", str(wav), "--data-format=LEI16@16000", text],
                       check=True)
    pcm = wav.read_bytes()[4096:]  # skip the CoreAudio WAV header + filler chunk
    return b"\x00" * 9600 + pcm + b"\x00" * 48000


def transcribe(pcm: bytes) -> str:
    utt = cloud_server.UtteranceASR(t1=0.0)
    for i in range(0, len(pcm), FRAME_BYTES):
        utt.process_frame(pcm[i:i + FRAME_BYTES])
    return utt.final_result()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--voice", default="Rishi", help="macOS voice (default: Rishi, en_IN)")
    ap.add_argument("--model", default=None,
                    help="Vosk model directory under backend/models/vosk/ (default: the server's default)")
    ap.add_argument("--all-questions", action="store_true")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    if shutil.which("say") is None:
        print("asr_eval needs the macOS `say` command; skipping.")
        return 0
    if args.model:
        cloud_server.load_vosk_model(cloud_server.resolve_vosk_model(args.model))
    if cloud_server.vosk_model is None:
        print("no Vosk model loaded -- run scripts/download_asr_model.sh first")
        return 1

    matcher = cloud_server.heritage_matcher
    cases = [(q, t["id"]) for t in matcher.topics
             for q in (matcher.all_questions(t) if args.all_questions else [t["question"]])]

    summary = {}
    for bias in (True, False):
        cloud_server.VOCAB_BIAS_ENABLED = bias
        ok, rows = 0, []
        for question, want in cases:
            text = transcribe(synthesise(question, args.voice))
            match = matcher.match(text)
            got = match["topic"]["id"] if match else None
            ok += got == want
            rows.append((question, text, want, got, match["layer"] if match else "-"))
        summary[bias] = ok
        label = "vocab bias ON " if bias else "vocab bias OFF"
        print(f"\n{label}: {ok}/{len(cases)} questions answered correctly "
              f"({100.0 * ok / len(cases):.0f}%)")
        for question, text, want, got, layer in rows:
            if args.verbose or got != want:
                flag = "ok  " if got == want else ("WRONG" if got else "none")
                print(f"  [{flag:5}] said {question!r}\n          heard {text!r} -> {got} ({layer})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
