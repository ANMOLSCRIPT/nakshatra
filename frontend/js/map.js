/**
 * map.js -- the heritage constellation.
 *
 * Every topic with a `location` in the knowledge base is plotted as a star at
 * its real latitude/longitude (equirectangular projection, longitude scaled by
 * cos(mid-latitude) so distances look right). No political boundary is drawn:
 * the shape of the country emerges from where its heritage lives. Stars are
 * joined by a minimum spanning tree, the way a star chart joins a constellation.
 */

import { PALETTE } from "./art.js";

const LON0 = 67.2, LON1 = 98.2, LAT0 = 6.2, LAT1 = 36.4;
const K = Math.cos((22 * Math.PI) / 180);
const PAD = 1.4;
const W = (LON1 - LON0) * K, H = LAT1 - LAT0;
const project = (lat, lon) => [(lon - LON0) * K, LAT1 - lat];
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const f = (n) => n.toFixed(3);

/** Nudge stars that share a town apart so each can be picked. */
function spread(points) {
  const MIN = 0.62;
  points.forEach((p, i) => {
    let tries = 0;
    while (tries < 24 && points.slice(0, i).some((q) => Math.hypot(p.x - q.x, p.y - q.y) < MIN)) {
      const a = tries * 2.4;
      const r = MIN * (0.75 + tries * 0.12);
      p.x = p.x0 + r * Math.cos(a);
      p.y = p.y0 + r * Math.sin(a);
      tries++;
    }
  });
}

/** Prim's algorithm; returns index pairs. */
function spanningTree(points) {
  const n = points.length;
  if (n < 2) return [];
  const inTree = new Array(n).fill(false);
  const best = new Array(n).fill(Infinity);
  const from = new Array(n).fill(-1);
  const edges = [];
  best[0] = 0;
  for (let k = 0; k < n; k++) {
    let u = -1;
    for (let i = 0; i < n; i++) if (!inTree[i] && (u === -1 || best[i] < best[u])) u = i;
    inTree[u] = true;
    if (from[u] >= 0) edges.push([from[u], u]);
    for (let v = 0; v < n; v++) {
      if (inTree[v]) continue;
      const d = Math.hypot(points[u].x - points[v].x, points[u].y - points[v].y);
      if (d < best[v]) { best[v] = d; from[v] = u; }
    }
  }
  return edges;
}

export function renderMap(svg, kb, { legend, card, onAsk }) {
  const catName = Object.fromEntries(kb.categories.map((c) => [c.id, c.short || c.name]));
  const colour = (cat) => (PALETTE[cat] || PALETTE.monuments)[1];

  const points = kb.topics.filter((t) => t.location).map((t) => {
    const [x, y] = project(t.location.lat, t.location.lon);
    return { topic: t, x, y, x0: x, y0: y };
  });
  spread(points);

  let grat = "";
  for (let lon = 70; lon <= 95; lon += 5) {
    const x = (lon - LON0) * K;
    grat += `<line class="map-grat" x1="${f(x)}" y1="0" x2="${f(x)}" y2="${f(H)}"/><text class="map-grat-label" x="${f(x + 0.15)}" y="${f(H + 0.9)}">${lon}°E</text>`;
  }
  for (let lat = 10; lat <= 35; lat += 5) {
    const y = LAT1 - lat;
    grat += `<line class="map-grat" x1="0" y1="${f(y)}" x2="${f(W)}" y2="${f(y)}"/><text class="map-grat-label" x="${f(-1.25)}" y="${f(y + 0.2)}">${lat}°N</text>`;
  }

  const links = spanningTree(points).map(([a, b]) =>
    `<line class="map-link" x1="${f(points[a].x)}" y1="${f(points[a].y)}" x2="${f(points[b].x)}" y2="${f(points[b].y)}"/>`).join("");

  const stars = points.map((p, i) => `
    <g class="star${i % 3 === 0 ? " star-twinkle" : ""}" tabindex="0" role="button" data-i="${i}" data-cat="${esc(p.topic.category)}"
       aria-label="${esc(p.topic.name)}, ${esc(p.topic.location.place)}" style="--star:${colour(p.topic.category)};animation-delay:${(i % 7) * 0.6}s">
      <circle class="star-hit" cx="${f(p.x)}" cy="${f(p.y)}" r="0.8"/>
      <circle class="star-halo" cx="${f(p.x)}" cy="${f(p.y)}" r="0.7"/>
      <circle class="star-core" cx="${f(p.x)}" cy="${f(p.y)}" r="0.24"/>
    </g>`).join("");

  svg.setAttribute("viewBox", `${-PAD} ${-PAD * 0.5} ${f(W + PAD * 2)} ${f(H + PAD * 1.6)}`);
  svg.innerHTML = `<g>${grat}</g><g>${links}</g><g>${stars}</g>`;

  // ---- detail card ------------------------------------------------------
  let active = -1;
  function show(i, pin) {
    const p = points[i];
    if (!p) return;
    if (pin) {
      active = i;
      svg.querySelectorAll(".star.active").forEach((el) => el.classList.remove("active"));
      svg.querySelector(`.star[data-i="${i}"]`)?.classList.add("active");
    }
    const t = p.topic;
    card.style.setProperty("--star", colour(t.category));
    card.innerHTML = `<small>${esc(catName[t.category] || "")}</small>
      <h3>${esc(t.name)}</h3>
      <p>${esc(t.location.place)}, ${esc(t.location.state)}${t.era ? ` · ${esc(t.era)}` : ""}</p>
      <button class="btn btn-gold btn-sm" type="button" data-ask="${esc(t.question)}">Ask Nakshatra</button>`;
  }
  const indexOf = (e) => { const g = e.target.closest(".star"); return g ? Number(g.dataset.i) : -1; };
  svg.addEventListener("pointerover", (e) => { const i = indexOf(e); if (i >= 0) show(i, false); });
  svg.addEventListener("pointerout", (e) => { if (indexOf(e) >= 0 && active >= 0) show(active, false); });
  svg.addEventListener("focusin", (e) => { const i = indexOf(e); if (i >= 0) show(i, false); });
  svg.addEventListener("click", (e) => { const i = indexOf(e); if (i >= 0) show(i, true); });
  svg.addEventListener("keydown", (e) => {
    if (e.key !== "Enter" && e.key !== " ") return;
    const i = indexOf(e);
    if (i >= 0) { e.preventDefault(); show(i, true); }
  });
  card.addEventListener("click", (e) => { const b = e.target.closest("[data-ask]"); if (b) onAsk(b.dataset.ask); });

  // ---- legend / filter --------------------------------------------------
  const present = kb.categories.filter((c) => points.some((p) => p.topic.category === c.id));
  legend.innerHTML = present.map((c) =>
    `<button class="legend-chip" type="button" aria-pressed="false" data-cat="${esc(c.id)}" style="--star:${colour(c.id)}"><i></i>${esc(c.short || c.name)}</button>`).join("");
  legend.addEventListener("click", (e) => {
    const chip = e.target.closest(".legend-chip");
    if (!chip) return;
    const on = chip.getAttribute("aria-pressed") !== "true";
    legend.querySelectorAll(".legend-chip").forEach((c) => c.setAttribute("aria-pressed", "false"));
    chip.setAttribute("aria-pressed", String(on));
    svg.querySelectorAll(".star").forEach((s) => s.classList.toggle("dim", on && s.dataset.cat !== chip.dataset.cat));
  });

  // Start with something on the card rather than an empty box.
  const first = points.findIndex((p) => p.topic.featured);
  if (first >= 0) show(first, true);
}
