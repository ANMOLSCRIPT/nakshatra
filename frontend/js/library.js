/**
 * library.js -- the archive page: every topic, every accepted phrasing and the
 * answer, grouped by theme, with search and a theme filter. Built entirely
 * from GET /api/kb.
 */

import { loadKB } from "./api.js";
import { motifSVG } from "./art.js";

const $ = (sel, el = document) => el.querySelector(sel);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

document.querySelectorAll("[data-motif]").forEach((el) => { el.innerHTML = motifSVG(el.dataset.motif); });

let KB = { categories: [], topics: [] };
let activeCat = "all";
const synth = "speechSynthesis" in window ? window.speechSynthesis : null;

const haystack = (t) => [t.name, t.question, ...(t.alternative_questions || []), ...(t.aliases || []),
  ...(t.keywords || []), t.answer, t.location?.place, t.location?.state].filter(Boolean).join(" ").toLowerCase();

function itemHTML(t, motif) {
  const where = [t.location && `${t.location.place}, ${t.location.state}`, t.era].filter(Boolean).join(" · ");
  return `<details class="lib-item" id="${esc(t.id)}">
    <summary>${motifSVG(t.motif || motif)}<span class="lib-q">${esc(t.question)}</span></summary>
    <div class="lib-body">
      <p>${esc(t.answer)}</p>
      ${where ? `<p class="lib-alt">${esc(where)}</p>` : ""}
      <ul class="lib-alt" aria-label="Other ways to ask">${(t.alternative_questions || []).map((q) => `<li>${esc(q)}</li>`).join("")}</ul>
      <div class="lib-actions">
        ${synth ? `<button class="btn btn-ghost btn-sm" type="button" data-say="${esc(t.answer)}">Read aloud</button>` : ""}
        <a class="btn btn-gold btn-sm" href="index.html?ask=${encodeURIComponent(t.question)}#ask">Ask on the console</a>
      </div>
    </div>
  </details>`;
}

function render() {
  const q = $("#lib-search").value.trim().toLowerCase();
  const terms = q.split(/\s+/).filter(Boolean);
  let shown = 0;
  const html = KB.categories.map((c) => {
    if (activeCat !== "all" && activeCat !== c.id) return "";
    const topics = KB.topics.filter((t) => t.category === c.id && terms.every((term) => t._hay.includes(term)));
    if (!topics.length) return "";
    shown += topics.length;
    return `<div class="lib-group"><h2>${esc(c.name)}</h2>${topics.map((t) => itemHTML(t, c.motif)).join("")}</div>`;
  }).join("");
  $("#lib-list").innerHTML = html || `<p class="lib-empty">Nothing in the archive matches “${esc(q)}”.</p>`;
  if (terms.length && shown <= 3) document.querySelectorAll(".lib-item").forEach((d) => { d.open = true; });
}

loadKB().then((kb) => {
  KB = kb;
  KB.topics.forEach((t) => { t._hay = haystack(t); });
  const questions = KB.topics.reduce((n, t) => n + 1 + (t.alternative_questions || []).length, 0);
  $("#lib-summary").textContent = `${KB.topics.length} topics across ${KB.categories.length} themes, reachable through ${questions} listed phrasings — and many more, because the answer engine matches by name, description and sound.`;
  $("#lib-filters").innerHTML = [`<button class="chip" type="button" data-cat="all" aria-pressed="true">All</button>`,
    ...KB.categories.map((c) => `<button class="chip" type="button" data-cat="${esc(c.id)}" aria-pressed="false">${esc(c.short || c.name)}</button>`)].join("");
  render();
  if (location.hash) {
    const el = document.getElementById(decodeURIComponent(location.hash.slice(1)));
    if (el) { el.open = true; el.scrollIntoView({ block: "center" }); }
  }
}).catch((err) => {
  console.error(err);
  $("#lib-summary").textContent = "The knowledge base could not be loaded. Is the server running?";
});

$("#lib-search").addEventListener("input", render);
$("#lib-filters").addEventListener("click", (e) => {
  const chip = e.target.closest("[data-cat]");
  if (!chip) return;
  activeCat = chip.dataset.cat;
  document.querySelectorAll("#lib-filters .chip").forEach((c) => c.setAttribute("aria-pressed", String(c === chip)));
  render();
});
$("#lib-list").addEventListener("click", (e) => {
  const btn = e.target.closest("[data-say]");
  if (!btn || !synth) return;
  synth.cancel();
  const u = new SpeechSynthesisUtterance(btn.dataset.say);
  let chosen = "";
  try { chosen = localStorage.getItem("nakshatra.voice") || ""; } catch { /* private mode */ }
  const voice = synth.getVoices().find((v) => v.name === chosen);
  if (voice) { u.voice = voice; u.lang = voice.lang; } else { u.lang = "en-IN"; }
  u.rate = 0.96;
  synth.speak(u);
});
