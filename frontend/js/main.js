/**
 * main.js -- builds the home page from the knowledge base and keeps every
 * voice element in step with the live pipeline state (voice.js).
 *
 * Nothing on this page is hard-coded content: categories, featured cards,
 * suggestion chips, the map and all counts come from GET /api/kb.
 */

import { loadKB } from "./api.js";
import { featuredGradient, gradientFor, motifSVG, orbSVG } from "./art.js";
import { renderMap } from "./map.js";
import { ask, listVoices, setTTS, setVoice, start, STATE_COPY, STATE_ORDER, store, subscribe, ttsAvailable } from "./voice.js";

const $ = (sel, el = document) => el.querySelector(sel);
const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

// Topics offered as "Try asking" chips, in this order (skipped if absent).
const TRY_IDS = ["konark-sun-temple", "diwali", "bharatanatyam", "rani-ki-vav", "hindustani-vs-carnatic", "madhubani", "nalanda", "yoga"];

let KB = { categories: [], topics: [] };
const categoryById = (id) => KB.categories.find((c) => c.id === id) || {};
const motifOf = (topic) => topic.motif || categoryById(topic.category).motif || "mandala";

// --------------------------------------------------------------------------
// Static mounts
// --------------------------------------------------------------------------

$$("[data-orb]").forEach((el) => { el.innerHTML = orbSVG(); });
$$("[data-motif]").forEach((el) => { el.innerHTML = motifSVG(el.dataset.motif); });

function askFromPage(question) {
  $("#ask").scrollIntoView({ behavior: "smooth", block: "start" });
  ask(question);
}

// --------------------------------------------------------------------------
// Knowledge-base driven sections
// --------------------------------------------------------------------------

function renderCounts() {
  const questions = KB.topics.reduce((n, t) => n + 1 + (t.alternative_questions || []).length, 0);
  const places = KB.topics.filter((t) => t.location).length;
  const counts = { topics: KB.topics.length, questions, categories: KB.categories.length, places };
  $$("[data-count]").forEach((el) => { el.textContent = counts[el.dataset.count] ?? el.textContent; });
  $("#hero-stats").innerHTML = [
    [counts.topics, "heritage topics"],
    [counts.questions, "ways to ask"],
    [counts.categories, "themes"],
    [places, "places on the map"],
  ].map(([n, label]) => `<div><dt>${n}</dt><dd>${label}</dd></div>`).join("");
  $("#footer-kb").textContent = `${counts.topics} topics · ${counts.questions} questions · ${counts.categories} themes`;
}

function renderCategories() {
  const grid = $("#cat-grid");
  const drawer = $("#cat-drawer");
  grid.innerHTML = KB.categories.map((c) => {
    const n = KB.topics.filter((t) => t.category === c.id).length;
    return `<button class="cat" type="button" role="listitem" data-cat="${esc(c.id)}" aria-expanded="false" aria-controls="cat-drawer" style="--cat-grad:${gradientFor(c.id)}">
      ${motifSVG(c.motif)}
      <span class="cat-name">${esc(c.short || c.name)}</span>
      <span class="cat-count">${n} topic${n === 1 ? "" : "s"}</span>
    </button>`;
  }).join("");

  function open(catId) {
    const c = categoryById(catId);
    const topics = KB.topics.filter((t) => t.category === catId);
    drawer.hidden = false;
    drawer.innerHTML = `<h3>${esc(c.name)}</h3><p>${esc(c.blurb || "")}</p>
      <div class="topic-list">${topics.map((t) => `
        <button class="topic-btn" type="button" data-ask="${esc(t.question)}">
          <b>${esc(t.name)}</b><span>${esc(t.question)}</span>
        </button>`).join("")}</div>`;
    // Restart the entrance animation when switching categories.
    drawer.style.animation = "none"; void drawer.offsetWidth; drawer.style.animation = "";
  }

  grid.addEventListener("click", (e) => {
    const btn = e.target.closest(".cat");
    if (!btn) return;
    const wasOpen = btn.getAttribute("aria-expanded") === "true";
    $$(".cat", grid).forEach((b) => b.setAttribute("aria-expanded", "false"));
    if (wasOpen) { drawer.hidden = true; return; }
    btn.setAttribute("aria-expanded", "true");
    open(btn.dataset.cat);
  });
  drawer.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-ask]");
    if (btn) askFromPage(btn.dataset.ask);
  });
}

function renderFeatured() {
  const featured = KB.topics.filter((t) => t.featured);
  $("#feat-grid").innerHTML = featured.map((t, i) => {
    const firstSentence = t.answer.split(/(?<=\.)\s/)[0];
    const place = t.location ? `${t.location.place} · ${t.location.state}` : categoryById(t.category).name;
    const photo = t.image ? `<img class="feat-photo" src="assets/heritage/${esc(t.image)}" alt="" loading="lazy" onerror="this.remove()">` : "";
    return `<button class="feat" type="button" data-ask="${esc(t.question)}" style="--feat-grad:${featuredGradient(i)}">
      ${photo || motifSVG(motifOf(t))}
      <div class="feat-body">
        <span class="feat-place">${esc(place)}</span>
        <h3>${esc(t.name)}</h3>
        <p class="feat-line">${esc(firstSentence)}</p>
        <span class="feat-ask">Ask Nakshatra</span>
      </div>
    </button>`;
  }).join("");
  $("#feat-grid").addEventListener("click", (e) => {
    const card = e.target.closest("[data-ask]");
    if (card) askFromPage(card.dataset.ask);
  });
}

function renderTryChips() {
  const picks = TRY_IDS.map((id) => KB.topics.find((t) => t.id === id)).filter(Boolean);
  $("#try-chips").innerHTML = picks.map((t) => `<button class="chip" type="button" data-ask="${esc(t.question)}">${esc(t.question)}</button>`).join("");
  $("#try-chips").addEventListener("click", (e) => {
    const chip = e.target.closest("[data-ask]");
    if (chip) ask(chip.dataset.ask);
  });
}

// --------------------------------------------------------------------------
// Ask console
// --------------------------------------------------------------------------

$("#stepper").innerHTML = STATE_ORDER.map((s, i) => `<li class="step" data-state="${s}">
  <span class="step-n">0${i + 1}</span><span class="step-label">${STATE_COPY[s].label}</span></li>`).join("");

$("#ask-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const input = $("#ask-input");
  if (!input.value.trim()) return;
  ask(input.value);
  input.value = "";
});

const ttsCheck = $("#tts-check");
if (ttsAvailable()) {
  ttsCheck.checked = store.ttsOn;
  ttsCheck.addEventListener("change", () => setTTS(ttsCheck.checked));
} else {
  $("#tts-toggle").hidden = true;
  $("#voice-pick").hidden = true;
}

// Voice picker: browsers load their voice list asynchronously, so it is
// (re)built whenever the list changes rather than once at start-up.
const voiceSelect = $("#voice-select");
let voiceListKey = "";
function renderVoices(s) {
  const all = listVoices();
  const key = all.map((v) => v.name).join("|");
  if (key !== voiceListKey) {
    voiceListKey = key;
    voiceSelect.innerHTML = `<option value="">Automatic (Indian English if available)</option>` +
      all.map((v) => `<option value="${esc(v.name)}">${esc(v.name)} — ${esc(v.lang)}</option>`).join("");
  }
  voiceSelect.value = all.some((v) => v.name === s.voiceName) ? s.voiceName : "";
  $("#voice-pick").dataset.off = String(!s.ttsOn);
}
voiceSelect.addEventListener("change", () => setVoice(voiceSelect.value));

$("#answer").addEventListener("click", (e) => {
  const chip = e.target.closest("[data-ask]");
  if (chip) ask(chip.dataset.ask);
});

function emptyAnswerHTML() {
  return `<div class="answer-empty"><div>${motifSVG("wheel")}
    <h3>Your answer will appear here</h3>
    <p>Say “Nakshatra”, then ask — or tap a suggestion.</p></div></div>`;
}

function answerHTML(m) {
  if (m && m.error) {
    return `<div class="answer-empty"><div>${motifSVG("mandala")}<h3>The server did not answer</h3>
      <p>Check that <code>backend/cloud_server.py</code> is running, then try again.</p></div></div>`;
  }
  if (!m) {
    const picks = TRY_IDS.slice(0, 4).map((id) => KB.topics.find((t) => t.id === id)).filter(Boolean);
    return `<span class="answer-cat">No match</span>
      <h3>That is not in my archive yet</h3>
      <p class="answer-text">I could not connect ${store.question ? `“${esc(store.question)}”` : "that"} to a topic I know. Try naming a monument, a festival, a dance form or a craft.</p>
      <div class="answer-related"><span>Try one of these</span>
        <div class="chips">${picks.map((t) => `<button class="chip" type="button" data-ask="${esc(t.question)}">${esc(t.question)}</button>`).join("")}</div></div>`;
  }
  const words = esc(m.answer).split(" ").map((w, i) => `<span class="w" style="animation-delay:${i * 38}ms">${w}</span>`).join(" ");
  const meta = [m.location && `${m.location.place}, ${m.location.state}`, m.era].filter(Boolean);
  const related = (m.related || []).map((r) => `<button class="chip" type="button" data-ask="${esc(r.question)}">${esc(r.name)}</button>`).join("");
  return `${motifSVG(motifOf(m), "answer-art")}
    <span class="answer-cat">${esc(m.category_name || "")}</span>
    <h3>${esc(m.name)}</h3>
    <div class="answer-meta">${meta.map((x) => `<span>${esc(x)}</span>`).join("")}</div>
    <p class="answer-text">${words}</p>
    ${related ? `<div class="answer-related"><span>Related</span><div class="chips">${related}</div></div>` : ""}`;
}

const ms = (v) => (v == null || Number.isNaN(v) ? null : Math.max(0, Math.round(v)));
function telemetryHTML(s) {
  const L = s.latency || {};
  const wakeToAudio = L.t1 != null && L.t2 != null ? ms(L.t2 - L.t1) : null;
  const total = L.t1 != null && L.t5 != null ? ms(L.t5 - L.t1) : null;
  const m = s.answer && !s.answer.error ? s.answer : null;
  const tiles = [
    ["Edge device", s.edge ? "Connected" : s.server ? "Offline" : "No server"],
    ["Wake confidence", s.wakeProb != null ? `${Math.round(s.wakeProb * 100)}<small>%</small>` : s.source === "typed" ? "Typed" : "—"],
    s.source === "typed"
      ? ["Match time", s.matchMs != null ? `${s.matchMs}<small>ms</small>` : "—"]
      : ["Wake → answer", total != null ? `${(total / 1000).toFixed(1)}<small>s</small>${wakeToAudio != null ? `<small>· audio in ${wakeToAudio} ms</small>` : ""}` : "—"],
    ["Matched by", m ? `${esc(m.matched_by)}<small>${Math.round(m.confidence * 100)}%</small>` : "—"],
  ];
  return tiles.map(([label, value]) => `<div class="tele"><span class="tele-label">${label}</span><span class="tele-value">${value}</span></div>`).join("");
}

let shownAnswer;          // identity of the answer currently rendered
const answerEl = $("#answer");
answerEl.innerHTML = emptyAnswerHTML();

function render(s) {
  const copy = STATE_COPY[s.state];
  const offline = s.state === "idle" && !s.server;
  const lineText = offline ? "Connecting to the server…" : copy.line;
  const hintText = offline ? "Start it with: python backend/cloud_server.py" : copy.hint;
  $$("[data-voice-line]").forEach((el) => { el.textContent = lineText; });
  $$("[data-voice-hint]").forEach((el) => { el.textContent = hintText; });

  const at = STATE_ORDER.indexOf(s.state);
  $$(".step").forEach((el, i) => {
    el.classList.toggle("active", i === at);
    el.classList.toggle("done", i < at && at > 1);
  });

  const nav = $("#nav-status");
  nav.classList.toggle("on", s.server && s.edge);
  $("#nav-status-text").textContent = !s.server ? "Server offline" : s.edge ? "Edge device connected" : "Edge device offline";

  const hearing = ["wake", "listening", "processing"].includes(s.state);
  const heardText = s.heard || s.question;
  $("#heard").hidden = !(heardText && (hearing || s.state === "answering"));
  $("#heard-label").textContent = s.state === "listening" || s.state === "wake" ? "Hearing" : s.source === "typed" ? "You asked" : "Heard";
  $("#heard-text").textContent = heardText ? `“${heardText}”` : "";
  $("[data-heard-inline]").textContent = hearing && s.heard ? `“${s.heard}”` : "";

  answerEl.classList.toggle("stale", hearing && shownAnswer !== undefined);
  if (s.state === "answering" && s.answer !== shownAnswer) {
    shownAnswer = s.answer;
    answerEl.innerHTML = answerHTML(s.answer);
    answerEl.classList.remove("enter"); void answerEl.offsetWidth; answerEl.classList.add("enter");
    // Single-column layouts put the answer below the console: bring it into view.
    if (matchMedia("(max-width: 1040px)").matches) answerEl.scrollIntoView({ behavior: "smooth", block: "start" });
  } else if (s.answer && s.answer.error && shownAnswer !== s.answer) {
    shownAnswer = s.answer;
    answerEl.innerHTML = answerHTML(s.answer);
  }

  $("#telemetry").innerHTML = telemetryHTML(s);
  renderVoices(s);
}

// --------------------------------------------------------------------------
// Waveform: the real input level reported by the edge / derived from its audio
// --------------------------------------------------------------------------

function startWave() {
  const canvas = $("#wave");
  const ctx = canvas.getContext("2d");
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  let accent = "#e9b44c";
  let frame = 0;
  const smooth = new Float32Array(store.levels.length);

  function draw() {
    if (frame++ % 20 === 0) accent = getComputedStyle(document.documentElement).getPropertyValue("--accent").trim() || accent;
    const { width: w, height: h } = canvas;
    ctx.clearRect(0, 0, w, h);
    const n = store.levels.length;
    const bar = w / n;
    const live = store.state !== "idle";
    ctx.fillStyle = accent;
    for (let i = 0; i < n; i++) {
      smooth[i] += (store.levels[i] - smooth[i]) * 0.35;
      const amp = live ? Math.max(0.02, smooth[i]) : 0.02;
      const bh = Math.min(h, amp * h * 0.96);
      ctx.globalAlpha = 0.25 + 0.75 * (i / n);
      ctx.fillRect(i * bar + bar * 0.2, (h - bh) / 2, bar * 0.6, bh);
    }
    ctx.globalAlpha = 1;
    if (!reduce && !document.hidden) requestAnimationFrame(draw);
  }
  draw();
  document.addEventListener("visibilitychange", () => { if (!document.hidden && !reduce) requestAnimationFrame(draw); });
}

// --------------------------------------------------------------------------
// Boot
// --------------------------------------------------------------------------

subscribe(render);
start();
startWave();

loadKB()
  .then((kb) => {
    KB = kb;
    renderCounts();
    renderCategories();
    renderFeatured();
    renderTryChips();
    renderMap($("#map-svg"), KB, { legend: $("#map-legend"), card: $("#map-card"), onAsk: askFromPage });
    // library.html links here as index.html?ask=<question>#ask
    const pending = new URLSearchParams(location.search).get("ask");
    if (pending) { history.replaceState(null, "", location.pathname + "#ask"); askFromPage(pending); }
  })
  .catch((err) => {
    console.error("could not load the knowledge base", err);
    $("#cat-grid").innerHTML = `<p class="section-lede">The knowledge base could not be loaded. Is the server running? (<code>python backend/cloud_server.py</code>)</p>`;
  });
