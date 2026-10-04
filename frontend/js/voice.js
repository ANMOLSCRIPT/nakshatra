/**
 * voice.js -- the voice-interaction state machine.
 *
 * A pure renderer of what the pipeline is really doing: every transition is
 * driven by a message from /ws/ui (see hardware/PROTOCOL.md) or by the reply
 * to a typed question. Nothing here simulates a detection or an answer.
 *
 *   idle        no edge device connected (or the server is unreachable)
 *   armed       edge connected, listening for the wake word "Nakshatra"
 *   wake        the edge's keyword model fired            <- `wake`
 *   listening   the edge is streaming the question        <- PCM / `partial`
 *   processing  utterance ended, transcript being matched <- `transcript`
 *   answering   an answer (or "no match") is on screen    <- `content`
 *
 * The current state is mirrored to <html data-voice="..."> so CSS animates
 * every orb, stepper and pill on the page from one attribute.
 */

import { askQuestion, connectUI } from "./api.js";

const root = document.documentElement;
const WAKE_FLASH_MS = 900;     // how long "Nakshatra detected" stays up before "Listening"
const MIN_PROCESSING_MS = 450; // matching takes ~1 ms; keep the step readable
const LEVEL_GAIN = 9;          // speech RMS is ~0.02-0.15; map it onto 0..1
const HISTORY = 96;

export const store = {
  state: "idle",
  server: false,       // /ws/ui socket open
  edge: false,         // an edge device is connected to the server
  source: null,        // "voice" | "typed"
  wakeProb: null,
  heard: "",           // live transcript while listening
  question: "",        // final transcript / typed question
  answer: undefined,   // undefined = nothing asked yet, null = no match, object = topic
  latency: {},         // t1..t5 from the server, ms since epoch
  matchMs: null,
  detections: 0,
  levels: new Float32Array(HISTORY),
  ttsOn: readPref("nakshatra.tts", "1") === "1",
  voiceName: readPref("nakshatra.voice", ""),   // "" = automatic (prefer Indian English)
};

const listeners = new Set();
export function subscribe(fn) { listeners.add(fn); fn(store); return () => listeners.delete(fn); }
function emit() { listeners.forEach((fn) => fn(store)); }

function readPref(key, fallback) {
  try { return localStorage.getItem(key) ?? fallback; } catch { return fallback; }
}
function writePref(key, value) {
  try { localStorage.setItem(key, value); } catch { /* private mode: preference just won't persist */ }
}

const restState = () => (store.server && store.edge ? "armed" : "idle");

let timers = [];
function later(fn, ms) { const id = setTimeout(fn, ms); timers.push(id); return id; }
function clearTimers() { timers.forEach(clearTimeout); timers = []; }

function setState(next) {
  store.state = next;
  root.dataset.voice = next;
  emit();
}

function pushLevel(rms) {
  const v = Math.min(1, Math.max(0, (rms || 0) * LEVEL_GAIN));
  store.levels.copyWithin(0, 1);
  store.levels[HISTORY - 1] = v;
  root.style.setProperty("--level", v.toFixed(3));
}

// ---------------------------------------------------------------------------
// Spoken answers (browser speech synthesis)
// ---------------------------------------------------------------------------

const synth = "speechSynthesis" in window ? window.speechSynthesis : null;
let voices = [];
function refreshVoices() { voices = synth ? synth.getVoices() : []; emit(); }
if (synth) { refreshVoices(); synth.addEventListener?.("voiceschanged", refreshVoices); }

function pickVoice() {
  const by = (test) => voices.find(test);
  const chosen = store.voiceName && by((v) => v.name === store.voiceName);
  if (chosen) return chosen;
  return by((v) => v.lang === "en-IN") || by((v) => v.lang.startsWith("en-IN")) ||
         by((v) => v.lang.startsWith("en-GB")) || by((v) => v.lang.startsWith("en")) || null;
}

function speak(text, onDone) {
  if (!synth || !store.ttsOn) return false;
  synth.cancel();
  const u = new SpeechSynthesisUtterance(text);
  const v = pickVoice();
  if (v) { u.voice = v; u.lang = v.lang; } else { u.lang = "en-IN"; }
  u.rate = 0.96;
  u.onend = onDone;
  // No onerror handler on purpose: when the browser refuses to speak (autoplay
  // policy before the first click, no voices installed) the answer must stay
  // on screen for its reading time instead of vanishing at once.
  synth.speak(u);
  return true;
}

export const ttsAvailable = () => Boolean(synth);
export function setTTS(on) {
  store.ttsOn = on;
  writePref("nakshatra.tts", on ? "1" : "0");
  if (!on && synth) synth.cancel();
  emit();
}
export function stopSpeaking() { if (synth) synth.cancel(); }

/** Voices the browser offers, Indian English and Hindi first, then other English, then the rest. */
export function listVoices() {
  const rank = (v) => (v.lang.startsWith("en-IN") ? 0 : v.lang.startsWith("hi") ? 1 : v.lang.startsWith("en") ? 2 : 3);
  return [...voices].sort((a, b) => rank(a) - rank(b) || a.name.localeCompare(b.name));
}

/** Choose the answer voice by name ("" = automatic) and play a short sample. */
export function setVoice(name) {
  store.voiceName = name;
  writePref("nakshatra.voice", name);
  emit();
  if (store.ttsOn) speak("Namaste. This is how I will read your answers.", () => {});
}

// ---------------------------------------------------------------------------
// Answer presentation
// ---------------------------------------------------------------------------

const NO_MATCH_SPEECH = "I could not find that in my heritage archive yet. Try asking about a monument, a festival, a dance or a craft.";

function present(match) {
  store.answer = match;
  setState("answering");

  let finished = false;
  const done = () => {
    if (finished) return;
    finished = true;
    if (store.state === "answering") setState(restState());
  };
  const text = match ? match.answer : NO_MATCH_SPEECH;
  const words = text.split(/\s+/).length;
  // Reading-time fallback for when speech is off, blocked by the browser's
  // autoplay policy, or simply never reports `onend`.
  later(done, Math.max(6000, words * 430) + 2500);
  speak(text, () => later(done, 600));
}

function presentAfterProcessing(match, startedAt) {
  const wait = Math.max(0, MIN_PROCESSING_MS - (performance.now() - startedAt));
  later(() => present(match), wait);
}

// ---------------------------------------------------------------------------
// Voice path: events from /ws/ui
// ---------------------------------------------------------------------------

let segments = [];
let processingSince = 0;

function onEvent(msg) {
  switch (msg.event) {
    case "edge_status":
      store.edge = Boolean(msg.connected);
      if (!store.edge && (store.state === "wake" || store.state === "listening")) {
        clearTimers(); // the device dropped mid-question: nothing more will arrive
        setState(restState());
      } else if (store.state === "idle" || store.state === "armed") {
        setState(restState());
      } else emit();
      break;

    case "level":
      pushLevel(msg.rms);
      break;

    case "wake":
      clearTimers();
      stopSpeaking();
      segments = [];
      Object.assign(store, { source: "voice", wakeProb: msg.prob, heard: "", question: "", latency: { t1: msg.t1 }, matchMs: null });
      store.detections += 1;
      setState("wake");
      later(() => { if (store.state === "wake") setState("listening"); }, WAKE_FLASH_MS);
      break;

    case "partial":
      store.heard = [...segments, msg.text || ""].join(" ").trim();
      emit();
      break;

    case "final_segment":
      if (msg.text) segments.push(msg.text);
      store.heard = segments.join(" ");
      emit();
      break;

    case "transcript":
      clearTimers();
      store.question = msg.text || "";
      store.heard = store.question;
      store.latency = { t1: msg.t1, t2: msg.t2, t3: msg.t3, t4: msg.t4 };
      processingSince = performance.now();
      setState("processing");
      break;

    case "content":
      store.latency.t5 = msg.t5;
      if (!store.question && msg.transcript) store.question = msg.transcript;
      presentAfterProcessing(msg.match || null, processingSince || performance.now());
      break;

    default:
      break; // unknown events are ignored, not fatal -- forward compatible
  }
}

function onLink(open) {
  store.server = open;
  if (!open) store.edge = false;
  if (store.state === "idle" || store.state === "armed" || !open) {
    if (!open) clearTimers();
    setState(restState());
  } else emit();
}

// ---------------------------------------------------------------------------
// Typed path: suggestion chips, cards and the text box
// ---------------------------------------------------------------------------

export async function ask(question) {
  const q = (question || "").trim();
  if (!q) return;
  clearTimers();
  stopSpeaking();
  Object.assign(store, { source: "typed", question: q, heard: q, wakeProb: null, latency: {}, matchMs: null });
  const startedAt = performance.now();
  setState("processing");
  try {
    const res = await askQuestion(q);
    store.matchMs = res.match_ms;
    presentAfterProcessing(res.match || null, startedAt);
  } catch (err) {
    console.error(err);
    store.answer = { error: true };
    setState(restState());
  }
}

export function start() {
  root.dataset.voice = store.state;
  connectUI(onEvent, onLink);
  // Rehearsal hook: drive the UI from the devtools console without an edge
  // device, e.g. nakshatraUI.inject({event: "wake", prob: 0.93, t1: Date.now()})
  window.nakshatraUI = { inject: onEvent, store };
}

export const STATE_COPY = {
  idle: { label: "Idle", line: "Edge device offline", hint: "Start the edge device to use your voice, or type a question below." },
  armed: { label: "Listening for Nakshatra", line: "Say “Nakshatra” to begin", hint: "The wake word is detected on the device. Nothing is sent until you say it." },
  wake: { label: "Nakshatra detected", line: "Nakshatra detected", hint: "Wake word recognised on the edge device." },
  listening: { label: "Listening", line: "Listening — ask your question", hint: "Your voice is streaming to the speech recogniser." },
  processing: { label: "Processing", line: "Understanding your question", hint: "Matching the transcript against the heritage archive." },
  answering: { label: "Answering", line: "Here is what I found", hint: "Say “Nakshatra” again to ask something else." },
};
export const STATE_ORDER = ["idle", "armed", "wake", "listening", "processing", "answering"];
