/**
 * api.js -- everything the site knows about the server.
 *
 *   GET  /api/kb      the knowledge base (categories + topics)
 *   GET  /api/health  server / ASR / knowledge-base status
 *   POST /api/ask     typed question -> answer (same matcher as the voice path)
 *   WS   /ws/ui       live broadcast of the voice pipeline (see hardware/PROTOCOL.md)
 */

async function getJSON(url) {
  const res = await fetch(url, { headers: { Accept: "application/json" } });
  if (!res.ok) throw new Error(`${url} -> HTTP ${res.status}`);
  return res.json();
}

export const loadKB = () => getJSON("/api/kb");
export const loadHealth = () => getJSON("/api/health");

export async function askQuestion(question) {
  const res = await fetch("/api/ask", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question }),
  });
  if (!res.ok) throw new Error(`/api/ask -> HTTP ${res.status}`);
  return res.json();
}

/**
 * Connects to /ws/ui and keeps the connection alive with backoff.
 * onEvent(msg) receives every broadcast; onLink(bool) reports the socket state.
 */
export function connectUI(onEvent, onLink) {
  let delay = 1000;
  const MAX_DELAY = 8000;

  function open() {
    const proto = location.protocol === "https:" ? "wss:" : "ws:";
    const ws = new WebSocket(`${proto}//${location.host}/ws/ui`);
    ws.onopen = () => { delay = 1000; onLink(true); };
    ws.onclose = () => {
      onLink(false);
      setTimeout(open, delay);
      delay = Math.min(delay * 1.7, MAX_DELAY);
    };
    ws.onerror = () => { try { ws.close(); } catch { /* already closing */ } };
    ws.onmessage = (evt) => {
      let msg;
      try { msg = JSON.parse(evt.data); } catch { return; }
      onEvent(msg);
    };
  }
  open();
}
