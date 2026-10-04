"""
ui_check.py -- drives the real website in headless Chrome and checks that it
renders, raises no JavaScript errors, and follows the voice pipeline's states.

Uses the Chrome DevTools Protocol directly (no Selenium/Playwright needed):
starts its own server and a headless Chrome, loads the page, replays one
utterance's worth of /ws/ui events through the page's own event handler, and
saves a screenshot of each state to --shots.

It is a rendering check: the events are injected at the browser, so this does
not exercise the edge device or ASR (tests/e2e_pipeline.py does that).

Run:
    python tests/ui_check.py
    python tests/ui_check.py --shots assets/screenshots
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

import websockets

ROOT = Path(__file__).resolve().parent.parent
CHROME_CANDIDATES = [
    os.environ.get("CHROME", ""),
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    shutil.which("google-chrome") or "", shutil.which("chromium") or "", shutil.which("chromium-browser") or "",
]


def get_json(url: str, timeout: float = 60.0):
    deadline = time.monotonic() + timeout
    while True:
        try:
            with urllib.request.urlopen(url, timeout=1) as r:
                return json.load(r)
        except OSError:
            if time.monotonic() > deadline:
                raise
            time.sleep(0.4)


class Page:
    def __init__(self, ws):
        self.ws, self.n, self.errors = ws, 0, []

    async def call(self, method: str, **params):
        self.n += 1
        mid = self.n
        await self.ws.send(json.dumps({"id": mid, "method": method, "params": params}))
        while True:
            msg = json.loads(await self.ws.recv())
            if msg.get("method") == "Runtime.exceptionThrown":
                d = msg["params"]["exceptionDetails"]
                self.errors.append(d.get("exception", {}).get("description") or d.get("text"))
            elif msg.get("method") == "Log.entryAdded" and msg["params"]["entry"]["level"] == "error":
                self.errors.append(msg["params"]["entry"]["text"] + " " + msg["params"]["entry"].get("url", ""))
            elif msg.get("id") == mid:
                return msg.get("result", {})

    async def js(self, expr: str):
        r = await self.call("Runtime.evaluate", expression=expr, returnByValue=True, awaitPromise=True)
        if "exceptionDetails" in r:
            raise RuntimeError(r["exceptionDetails"].get("exception", {}).get("description", "js error"))
        return r["result"].get("value")

    async def shot(self, path: Path, selector: str | None = None):
        if selector:
            await self.js(f"document.querySelector({selector!r}).scrollIntoView({{block:'start'}})")
            await asyncio.sleep(0.5)
        data = await self.call("Page.captureScreenshot", format="png")
        path.write_bytes(base64.b64decode(data["data"]))


async def run(args, port: int, cdp_port: int) -> int:
    shots = Path(args.shots); shots.mkdir(parents=True, exist_ok=True)
    failures: list[str] = []

    def check(ok, label):
        print(f"  {'PASS' if ok else 'FAIL'}  {label}")
        if not ok:
            failures.append(label)

    target = next(t for t in get_json(f"http://127.0.0.1:{cdp_port}/json") if t["type"] == "page")
    async with websockets.connect(target["webSocketDebuggerUrl"], max_size=None) as ws:
        page = Page(ws)
        for domain in ("Page", "Runtime", "Log"):
            await page.call(f"{domain}.enable")
        await page.call("Emulation.setDeviceMetricsOverride", width=args.width, height=args.height,
                        deviceScaleFactor=1, mobile=args.width < 700)
        base = f"http://127.0.0.1:{port}"
        await page.call("Page.navigate", url=base + "/")
        for _ in range(60):
            if await page.js("document.querySelectorAll('.cat').length") and await page.js("Boolean(window.nakshatraUI)"):
                break
            await asyncio.sleep(0.25)
        await asyncio.sleep(1.0)

        state = lambda: page.js("document.documentElement.dataset.voice")  # noqa: E731
        inject = lambda ev: page.js(f"window.nakshatraUI.inject({json.dumps(ev)})")  # noqa: E731
        text = lambda sel: page.js(f"document.querySelector({sel!r}).textContent.trim()")  # noqa: E731

        print("home page")
        kb = get_json(base + "/api/kb")
        check(await page.js("document.querySelectorAll('.cat').length") == len(kb["categories"]), "one card per category")
        check(await page.js("document.querySelectorAll('.feat').length") == sum(1 for t in kb["topics"] if t.get("featured")), "featured cards")
        check(await page.js("document.querySelectorAll('#map-svg .star').length") == sum(1 for t in kb["topics"] if t.get("location")), "one star per mapped topic")
        check(await page.js("document.querySelectorAll('.orb-svg').length") == 2, "voice orbs mounted")
        check(await state() == "idle", "state is idle with no edge device")
        check(await page.js("document.documentElement.scrollWidth <= window.innerWidth"), "no horizontal overflow")
        await page.shot(shots / f"01-hero{args.suffix}.png")

        print("category drawer")
        await page.js("document.querySelector('.cat[data-cat=\"festivals\"]').click()")
        await asyncio.sleep(0.4)
        n_fest = sum(1 for t in kb["topics"] if t["category"] == "festivals")
        check(await page.js("document.querySelectorAll('#cat-drawer .topic-btn').length") == n_fest, "drawer lists the category's topics")
        await page.shot(shots / f"02-explore{args.suffix}.png", "#explore")
        await page.shot(shots / f"03-featured{args.suffix}.png", "#featured")
        await page.shot(shots / f"07-map{args.suffix}.png", "#map")

        print("voice states (events injected at the page's /ws/ui handler)")
        t1 = time.time() * 1000
        await inject({"event": "edge_status", "connected": True})
        check(await state() == "armed", "edge connected -> listening for Nakshatra")
        for i in range(40):
            await inject({"event": "level", "rms": 0.01 + 0.004 * (i % 5)})
        await page.shot(shots / f"04-armed{args.suffix}.png", "#ask")

        await inject({"event": "wake", "t1": t1, "prob": 0.97})
        check(await state() == "wake", "wake event -> Nakshatra detected")
        await asyncio.sleep(1.1)
        check(await state() == "listening", "... then listening")
        for i, partial in enumerate(["tell", "tell me about", "tell me about konark", "tell me about konark temple"]):
            for j in range(8):
                await inject({"event": "level", "rms": 0.03 + 0.02 * ((i + j) % 4), "streaming": True})
            await inject({"event": "partial", "text": partial})
        check("konark temple" in await text("#heard-text"), "live transcript is shown while listening")
        await page.shot(shots / f"05-listening{args.suffix}.png", "#ask")

        res = json.loads(urllib.request.urlopen(urllib.request.Request(
            base + "/api/ask", data=json.dumps({"question": "tell me about konark temple"}).encode(),
            headers={"Content-Type": "application/json"})).read())
        await inject({"event": "transcript", "text": "tell me about konark temple", "t1": t1, "t2": t1 + 4, "t3": t1 + 600, "t4": t1 + 3900, "latency_ms": 4})
        check(await state() == "processing", "transcript -> processing")
        await inject({"event": "content", "transcript": "tell me about konark temple", "match": res["match"], "t5": t1 + 3902, "match_latency_ms": 3902})
        await asyncio.sleep(0.9)
        check(await state() == "answering", "content -> answering")
        check(await text("#answer h3") == "Konark Sun Temple", "answer card shows the matched topic")
        check(kb_answer(kb, "konark-sun-temple")[:60] in (await text("#answer .answer-text")), "answer text comes from the knowledge base")
        await asyncio.sleep(2.2)
        await page.shot(shots / f"06-answering{args.suffix}.png", "#ask")

        print("typed question (real POST /api/ask)")
        await page.js("document.querySelector('#ask-input').value='what is the festival of lights'; document.querySelector('#ask-form').requestSubmit()")
        await asyncio.sleep(1.6)
        check(await text("#answer h3") == "Diwali", "typed descriptor question resolves to Diwali")
        await page.js("document.querySelector('#ask-input').value='what is the capital of france'; document.querySelector('#ask-form').requestSubmit()")
        await asyncio.sleep(1.6)
        check("not in my archive" in await text("#answer h3"), "out-of-domain question shows the no-match card")

        print("archive page")
        await page.call("Page.navigate", url=base + "/library.html")
        for _ in range(40):
            if await page.js("document.querySelectorAll('.lib-item').length"):
                break
            await asyncio.sleep(0.25)
        check(await page.js("document.querySelectorAll('.lib-item').length") == len(kb["topics"]), "archive lists every topic")
        await page.js("const i=document.querySelector('#lib-search'); i.value='silk'; i.dispatchEvent(new Event('input'))")
        n = await page.js("document.querySelectorAll('.lib-item').length")
        check(0 < n < 10, f"search narrows the list ({n} results for 'silk')")
        await page.shot(shots / f"08-archive{args.suffix}.png")

        check(not page.errors, "no JavaScript errors" + (f": {page.errors[:3]}" if page.errors else ""))

    print(f"\n{'ALL CHECKS PASSED' if not failures else f'{len(failures)} CHECK(S) FAILED'}   screenshots in {shots}")
    return 1 if failures else 0


def kb_answer(kb: dict, topic_id: str) -> str:
    return next(t["answer"] for t in kb["topics"] if t["id"] == topic_id)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--shots", default=str(ROOT / "assets" / "screenshots"))
    ap.add_argument("--width", type=int, default=1440)
    ap.add_argument("--height", type=int, default=900)
    ap.add_argument("--suffix", default="")
    ap.add_argument("--port", type=int, default=8766)
    args = ap.parse_args()

    chrome = next((c for c in CHROME_CANDIDATES if c and Path(c).exists()), None)
    if chrome is None:
        print("Chrome/Chromium not found (set CHROME=/path/to/chrome); skipping.")
        return 0

    cdp_port = args.port + 1
    with tempfile.TemporaryDirectory() as tmp:
        server = subprocess.Popen([sys.executable, str(ROOT / "backend" / "cloud_server.py"), "--host", "127.0.0.1",
                                   "--port", str(args.port), "--vosk-model", "none"],  # ASR is not needed to render
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        browser = subprocess.Popen([chrome, "--headless=new", "--disable-gpu", "--no-first-run", "--hide-scrollbars",
                                    f"--remote-debugging-port={cdp_port}", f"--user-data-dir={tmp}", "about:blank"],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            get_json(f"http://127.0.0.1:{args.port}/api/health")
            get_json(f"http://127.0.0.1:{cdp_port}/json/version")
            return asyncio.run(run(args, args.port, cdp_port))
        finally:
            for p in (browser, server):
                p.terminate()
            for p in (browser, server):
                try:
                    p.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    p.kill()


if __name__ == "__main__":
    sys.exit(main())
