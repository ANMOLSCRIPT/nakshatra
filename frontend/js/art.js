/**
 * art.js -- procedural line art for the site.
 *
 * Every motif is drawn in a 200 x 200 box with `currentColor` strokes, so the
 * same drawing works as a small category glyph, a large card illustration and
 * the voice orb. No image files are needed to run the demo; a topic can still
 * override its card with a photograph via the `image` field in the knowledge
 * base (see frontend/assets/heritage/README.md).
 */

const TAU = Math.PI * 2;
const f = (n) => Number(n.toFixed(2));
const polar = (cx, cy, r, a) => [f(cx + r * Math.cos(a)), f(cy + r * Math.sin(a))];
const circle = (cx, cy, r, extra = "") => `<circle cx="${cx}" cy="${cy}" r="${r}" ${extra}/>`;
const line = (x1, y1, x2, y2, extra = "") => `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" ${extra}/>`;
const path = (d, extra = "") => `<path d="${d}" ${extra}/>`;
const THIN = 'stroke-width="1"';

/** A pointed petal growing from (cx, cy) in direction `deg` (0 = up). */
function petal(cx, cy, deg, len, width) {
  const d = `M0 0 C${width} ${-len * 0.3} ${width} ${-len * 0.72} 0 ${-len} C${-width} ${-len * 0.72} ${-width} ${-len * 0.3} 0 0Z`;
  return `<path d="${d}" transform="translate(${cx} ${cy}) rotate(${deg})"/>`;
}

/** The Konark wheel: 8 broad spokes with medallions, 8 slender ones, a beaded rim. */
function wheel() {
  let s = circle(100, 100, 84) + circle(100, 100, 74) + circle(100, 100, 66, THIN) + circle(100, 100, 21) + circle(100, 100, 9);
  for (let i = 0; i < 16; i++) {
    const a = (i / 16) * TAU - Math.PI / 2;
    const [x1, y1] = polar(100, 100, 21, a);
    const [x2, y2] = polar(100, 100, 66, a);
    if (i % 2 === 0) {
      const [mx, my] = polar(100, 100, 45, a);
      s += line(x1, y1, x2, y2, 'stroke-width="2.6"') + circle(mx, my, 6.5);
    } else {
      s += line(x1, y1, x2, y2, THIN);
    }
  }
  for (let i = 0; i < 32; i++) {
    const [x, y] = polar(100, 100, 79, (i / 32) * TAU);
    s += circle(x, y, 1.7, 'fill="currentColor" stroke="none"');
  }
  return s;
}

function dome() {
  return (
    line(100, 14, 100, 30) + circle(100, 12, 2.4) +
    path("M64 100C52 70 78 46 100 30C122 46 148 70 136 100Z") +
    path("M50 100H150V162H50Z") +
    path("M86 162V132a14 14 0 0 1 28 0V162") +
    path("M60 162V140a7 7 0 0 1 14 0V162M126 162V140a7 7 0 0 1 14 0V162", THIN) +
    path("M54 100a10 10 0 0 1 20 0M126 100a10 10 0 0 1 20 0") +
    path("M22 162V74h12V162M166 162V74h12V162") +
    path("M20 74a8 8 0 0 1 16 0M164 74a8 8 0 0 1 16 0") +
    line(28, 66, 28, 58) + line(172, 66, 172, 58) +
    line(22, 104, 34, 104, THIN) + line(166, 104, 178, 104, THIN) +
    line(10, 162, 190, 162) + line(24, 172, 176, 172, THIN)
  );
}

function gopuram() {
  let s = "";
  const tiers = 6;
  for (let k = 0; k < tiers; k++) {
    const yb = 166 - k * 20;
    const yt = yb - 20;
    const hb = 58 - k * 7.5;
    const ht = hb - 5;
    s += path(`M${100 - hb} ${yb}L${100 - ht} ${yt}H${100 + ht}L${100 + hb} ${yb}Z`);
    s += line(100 - ht + 6, yb - 10, 100 + ht - 6, yb - 10, THIN);
  }
  s += path("M78 46V36a22 12 0 0 1 44 0V46Z");
  for (const x of [84, 92, 100, 108, 116]) s += line(x, 24, x, 17);
  s += path("M90 166V148a10 10 0 0 1 20 0V166") + line(30, 166, 170, 166);
  return s;
}

function chaitya() {
  let s = path("M40 164V102a60 60 0 0 1 120 0V164") + path("M56 164V102a44 44 0 0 1 88 0V164");
  s += path("M72 164V104a28 28 0 0 1 56 0V164", THIN);
  s += path("M84 164V140a16 16 0 0 1 32 0V164") + line(100, 124, 100, 112) + path("M94 112h12");
  for (let i = 1; i < 8; i++) {
    const a = Math.PI + (i / 8) * Math.PI;
    const [x1, y1] = polar(100, 102, 44, a);
    const [x2, y2] = polar(100, 102, 60, a);
    s += line(x1, y1, x2, y2, THIN);
  }
  s += line(24, 164, 176, 164) + path("M30 164V150M170 164V150", THIN);
  return s;
}

function shikhara() {
  let s = path("M72 166C72 104 88 62 100 36C112 62 128 104 128 166Z");
  s += path("M84 166C84 114 93 78 100 58C107 78 116 114 116 166", THIN);
  s += line(100, 58, 100, 166, THIN);
  s += `<ellipse cx="100" cy="33" rx="11" ry="4.5"/>` + path("M100 28V16") + circle(100, 14, 2.4);
  for (const y of [146, 126, 106, 88]) {
    const half = 28 - (166 - y) * 0.13;
    s += path(`M${f(100 - half)} ${y}Q100 ${y + 5} ${f(100 + half)} ${y}`, THIN);
  }
  s += path("M34 166C34 132 42 112 48 98C54 112 62 132 62 166Z") + `<ellipse cx="48" cy="96" rx="6" ry="2.6"/>`;
  s += path("M138 166C138 132 146 112 152 98C158 112 166 132 166 166Z") + `<ellipse cx="152" cy="96" rx="6" ry="2.6"/>`;
  s += line(22, 166, 178, 166);
  return s;
}

function stupa() {
  let s = path("M36 150H164V166H36Z") + path("M50 150a50 50 0 0 1 100 0");
  s += path("M90 100V88h20v12") + line(100, 88, 100, 58);
  s += `<ellipse cx="100" cy="80" rx="14" ry="3.4"/><ellipse cx="100" cy="72" rx="10" ry="2.8"/><ellipse cx="100" cy="65" rx="6" ry="2.2"/>`;
  s += path("M84 166V122M116 166V122");
  s += path("M74 124Q100 116 126 124M74 132Q100 124 126 132M76 140Q100 133 124 140");
  for (let x = 44; x <= 156; x += 8) if (x < 82 || x > 118) s += line(x, 150, x, 166, THIN);
  s += line(24, 166, 176, 166);
  return s;
}

function stepwell() {
  const left = [], right = [];
  for (let i = 0; i < 6; i++) {
    const y = 46 + i * 19;
    const hw = 84 - i * 13;
    const hwNext = 84 - (i + 1) * 13;
    left.push(`${100 - hw} ${y}`, `${100 - hwNext} ${y}`);
    right.push(`${100 + hw} ${y}`, `${100 + hwNext} ${y}`);
  }
  let s = path("M" + left.join("L")) + path("M" + right.join("L"));
  for (let i = 1; i < 6; i++) {
    const y = 46 + i * 19;
    const hw = 84 - i * 13;
    s += line(100 - hw, y - 19, 100 - hw, y) + line(100 + hw, y - 19, 100 + hw, y);
    s += line(100 - hw + 4, y - 10, 100 + hw - 4, y - 10, 'stroke-width="1" stroke-dasharray="2 5"');
  }
  s += path("M82 160q4.5-5 9 0t9 0t9 0t9 0") + line(10, 46, 16, 46) + line(184, 46, 190, 46);
  return s;
}

function vihara() {
  const pts = [];
  for (let i = 0; i < 6; i++) {
    const y = 166 - i * 20;
    const hw = 82 - i * 13;
    const hwNext = 82 - (i + 1) * 13;
    pts.push(`${100 - hw} ${y}`, `${100 - hw} ${y - 20}`, `${100 - hwNext} ${y - 20}`);
  }
  const mirrored = pts.map((p) => { const [x, y] = p.split(" "); return `${200 - Number(x)} ${y}`; }).reverse();
  let s = path("M" + pts.join("L") + "L" + mirrored.join("L"));
  s += path("M92 166L96 46M108 166L104 46");
  for (let y = 158; y > 50; y -= 10) s += line(f(92 + (166 - y) * 0.033), y, f(108 - (166 - y) * 0.033), y, THIN);
  s += line(10, 166, 190, 166);
  return s;
}

function fort() {
  const teeth = (x0, x1, y) => {
    let d = `M${x0} ${y + 10}`;
    for (let x = x0, up = true; x < x1; x += 8, up = !up) d += `V${up ? y : y + 10}H${Math.min(x + 8, x1)}`;
    return d + `V${y + 10}`;
  };
  let s = path(teeth(52, 148, 84)) + path("M52 94V166M148 94V166");
  s += path(teeth(20, 52, 60)) + path("M20 70V166M52 70V94") + path(teeth(148, 180, 60)) + path("M180 70V166M148 70V94");
  s += path("M82 166V128a18 18 0 0 1 36 0V166") + path("M90 166V130a10 10 0 0 1 20 0V166", THIN);
  s += path("M30 96v14M42 96v14M158 96v14M170 96v14", THIN) + path("M64 112h8M128 112h8M64 124h8M128 124h8", THIN);
  s += line(10, 166, 190, 166);
  return s;
}

function minar() {
  let s = path("M80 168L90 44H110L120 168Z");
  for (const y of [138, 108, 78]) {
    const hw = 10 + (168 - 44 - (168 - y)) * 0.0 + ((y - 44) / 124) * 10 + 5;
    s += path(`M${f(100 - hw)} ${y}H${f(100 + hw)}M${f(100 - hw + 2)} ${y + 5}H${f(100 + hw - 2)}`);
  }
  s += path("M92 168L96 44M100 168V44M108 168L104 44", THIN);
  s += path("M90 44a10 12 0 0 1 20 0") + line(100, 32, 100, 20) + circle(100, 18, 2.2);
  s += line(40, 168, 160, 168);
  return s;
}

function lamp() {
  let s = path("M36 116C58 162 142 162 164 116Z") + path("M36 116C70 126 130 126 164 116", THIN);
  s += path("M100 108C82 88 96 68 100 46C104 68 118 88 100 108Z") + path("M100 104C93 94 98 84 100 74C102 84 107 94 100 104Z", THIN);
  for (let i = 0; i < 9; i++) {
    const a = Math.PI + ((i + 0.5) / 9) * Math.PI;
    const [x1, y1] = polar(100, 78, 42, a);
    const [x2, y2] = polar(100, 78, i % 2 ? 50 : 56, a);
    s += line(x1, y1, x2, y2, THIN);
  }
  s += path("M70 164h60M82 172h36");
  return s;
}

/** The pleated fan of a dancer's costume, with a row of ankle bells beneath. */
function fan() {
  const cx = 100, cy = 142;
  const arc = (r) => {
    const [x1, y1] = polar(cx, cy, r, (200 / 360) * TAU);
    const [x2, y2] = polar(cx, cy, r, (340 / 360) * TAU);
    return `M${x1} ${y1}A${r} ${r} 0 0 1 ${x2} ${y2}`;
  };
  let s = path(arc(96)) + path(arc(84), THIN) + path(arc(26));
  for (let d = 200; d <= 340; d += 10) {
    const a = (d / 360) * TAU;
    const [x1, y1] = polar(cx, cy, 26, a);
    const [x2, y2] = polar(cx, cy, 96, a);
    s += line(x1, y1, x2, y2, d % 20 === 0 ? "" : THIN);
  }
  for (let i = 0; i < 7; i++) s += circle(58 + i * 14, 170, 4.6) + line(58 + i * 14, 174.6, 58 + i * 14, 177, THIN);
  s += path("M50 163Q100 156 150 163", THIN);
  return s;
}

function veena() {
  let s = circle(62, 138, 32) + circle(62, 138, 20, THIN) + circle(62, 138, 4);
  s += path("M82 112L160 34M90 120L168 42") + path("M86 116L164 38", THIN);
  s += circle(140, 76, 15) + circle(140, 76, 7, THIN);
  for (let i = 0; i < 4; i++) {
    const x = 128 + i * 9, y = 66 - i * 9;
    s += line(x, y, x - 9, y - 9) + circle(x - 11, y - 11, 2.2);
  }
  s += line(48, 152, 76, 124) + path("M168 34l8-8M160 26l8-8", THIN);
  return s;
}

function fish() {
  let s = path("M26 100C58 58 122 58 152 100C122 142 58 142 26 100Z");
  s += path("M152 100L180 74L172 100L180 126Z") + circle(50, 94, 5) + circle(50, 94, 1.6, 'fill="currentColor"');
  s += path("M66 76Q76 100 66 124", THIN);
  for (let col = 0; col < 4; col++) {
    const x = 82 + col * 16;
    const span = 20 - col * 3;
    for (let y = 100 - span; y <= 100 + span; y += 12) s += path(`M${x} ${y - 6}q8 6 0 12`, THIN);
  }
  s += path("M80 62Q96 46 112 62M84 138Q98 152 112 138");
  for (let i = 0; i < 9; i++) s += circle(22 + i * 19.5, 164, 1.8, 'fill="currentColor" stroke="none"');
  return s;
}

function pot() {
  let s = path("M76 46H124") + path("M82 46C82 64 60 74 56 104C52 142 76 166 100 166C124 166 148 142 144 104C140 74 118 64 118 46");
  s += path("M57 98Q100 112 143 98M56 118Q100 132 144 118");
  s += path("M62 110l8 8l8-8l8 8l8-8l8 8l8-8l8 8l8-8l8 8", THIN);
  s += path("M70 80Q100 90 130 80", THIN) + path("M70 148Q100 158 130 148", THIN);
  s += path("M100 46V34M92 38l8-8l8 8", THIN) + path("M78 166H122");
  return s;
}

/** Nested diamonds, as in an ikat or brocade repeat. */
function weave() {
  let s = "";
  for (const h of [84, 62, 40, 18]) s += path(`M100 ${100 - h}L${100 + h} 100L100 ${100 + h}L${100 - h} 100Z`, h === 62 ? THIN : "");
  for (const [x, y] of [[36, 36], [164, 36], [36, 164], [164, 164]]) {
    s += path(`M${x} ${y - 16}L${x + 16} ${y}L${x} ${y + 16}L${x - 16} ${y}Z`) + circle(x, y, 2.4, 'fill="currentColor" stroke="none"');
  }
  s += path("M100 16V184M16 100H184", 'stroke-width="1" stroke-dasharray="3 6"') + circle(100, 100, 5);
  return s;
}

function thali() {
  let s = circle(100, 100, 84) + circle(100, 100, 76, THIN);
  for (let i = 0; i < 5; i++) {
    const [x, y] = polar(100, 100, 50, Math.PI + ((i + 0.5) / 5) * Math.PI);
    s += circle(x, y, 15) + circle(x, y, 9, THIN);
  }
  s += path("M66 120Q100 96 134 120Q100 136 66 120Z") + circle(100, 150, 13) + path("M92 150h16M100 142v16", THIN);
  return s;
}

function lotus() {
  let s = "";
  for (const [deg, len, w] of [[-72, 62, 18], [72, 62, 18], [-40, 78, 20], [40, 78, 20], [0, 92, 22]]) s += petal(100, 138, deg, len, w);
  s += path("M40 138Q100 170 160 138") + path("M54 150Q100 176 146 150", THIN);
  s += path("M22 172q9-7 18 0t18 0t18 0t18 0t18 0t18 0t18 0t18 0", THIN);
  return s;
}

function mandala() {
  let s = circle(100, 100, 12) + circle(100, 100, 4, 'fill="currentColor"') + circle(100, 100, 86) + circle(100, 100, 78, THIN);
  for (let i = 0; i < 8; i++) s += petal(100, 100, i * 45, 44, 13);
  for (let i = 0; i < 16; i++) {
    const deg = i * 22.5 + 11.25;
    const [x, y] = polar(100, 100, 46, ((deg - 90) / 360) * TAU);
    s += petal(x, y, deg, 30, 8);
  }
  for (let i = 0; i < 24; i++) {
    const [x, y] = polar(100, 100, 82, (i / 24) * TAU);
    s += circle(x, y, 1.6, 'fill="currentColor" stroke="none"');
  }
  return s;
}

function ghat() {
  let s = path("M24 66H56V84H78V102H100V120H122V138H176");
  s += path("M30 66C30 46 36 34 40 24C44 34 50 46 50 66") + line(40, 24, 40, 14) + circle(40, 12, 2);
  s += circle(150, 50, 16) + path("M150 26v-8M150 82v-8M126 50h-8M182 50h-8M133 33l-5-5M172 72l-5-5M167 33l5-5M128 72l5-5", THIN);
  s += path("M110 152q9-6 18 0t18 0t18 0t18 0") + path("M24 164q9-6 18 0t18 0t18 0t18 0t18 0t18 0t18 0t18 0", THIN);
  s += path("M60 150q12 10 34 0Z") + line(77, 150, 77, 136) + path("M77 136l10 6l-10 4", THIN);
  s += path("M24 176q9-6 18 0t18 0t18 0t18 0t18 0t18 0t18 0t18 0", THIN);
  return s;
}

function drum() {
  let s = `<ellipse cx="52" cy="104" rx="15" ry="44"/><ellipse cx="148" cy="104" rx="15" ry="44"/>`;
  s += path("M52 60Q100 48 148 60M52 148Q100 160 148 148");
  s += path("M56 64L76 146L96 54L116 154L136 56L146 144", THIN);
  s += `<ellipse cx="52" cy="104" rx="6" ry="20" stroke-width="1"/>`;
  s += path("M30 40l34 30M170 40l-34 30") + circle(28, 38, 3.4) + circle(172, 38, 3.4);
  s += path("M40 176Q100 164 160 176", THIN);
  return s;
}

function sundial() {
  let s = path("M48 166H140V40Z") + path("M60 166L140 58", THIN);
  for (let i = 1; i < 9; i++) {
    const x = 48 + i * 10.2, y = 166 - i * 14;
    s += line(f(x), f(y), f(x + 6), f(y), THIN);
  }
  s += path("M140 166A70 70 0 0 0 186 106") + path("M48 166A70 70 0 0 1 20 112");
  for (let i = 0; i < 6; i++) {
    const [x1, y1] = polar(126, 104, 66, (i / 5) * 0.86 - 0.02 + 0);
    const [x2, y2] = polar(126, 104, 72, (i / 5) * 0.86 - 0.02 + 0);
    s += line(x1, y1 + 30, x2, y2 + 30, THIN);
  }
  s += circle(164, 42, 12) + path("M164 22v-8M184 42h8M178 28l6-6", THIN) + line(10, 166, 190, 166);
  return s;
}

const MOTIFS = { wheel, dome, gopuram, chaitya, shikhara, stupa, stepwell, vihara, fort, minar, lamp, dancer: fan, veena, fish, pot, loom: weave, thali, lotus, mandala, ghat, mask: drum, sundial };

/** Inline SVG for a motif; `cls` is added to the root element. */
export function motifSVG(name, cls = "") {
  const draw = MOTIFS[name] || MOTIFS.mandala;
  return `<svg class="motif ${cls}" viewBox="0 0 200 200" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${draw()}</svg>`;
}

/** The voice orb: a Konark wheel inside concentric rings that animate by state. */
export function orbSVG() {
  let ticks = "";
  for (let i = 0; i < 60; i++) {
    const a = (i / 60) * TAU;
    const [x1, y1] = polar(100, 100, i % 5 ? 95 : 93, a);
    const [x2, y2] = polar(100, 100, 98, a);
    ticks += line(x1, y1, x2, y2);
  }
  return `<svg class="orb-svg" viewBox="0 0 200 200" fill="none" stroke="currentColor" stroke-linecap="round" aria-hidden="true">
    <g class="orb-ticks" stroke-width="0.8">${ticks}</g>
    <circle class="orb-arc" cx="100" cy="100" r="90" stroke-width="2.2" pathLength="100"/>
    <g class="orb-wheel" stroke-width="1.5" transform="translate(13 13) scale(0.87)">${wheel()}</g>
  </svg>`;
}

/** Category -> two-stop gradient used behind card art. */
export const PALETTE = {
  monuments: ["#6e3418", "#c8762b"],
  festivals: ["#7d1a28", "#e2782a"],
  "classical-dance": ["#531844", "#c0397a"],
  "classical-music": ["#1b3360", "#3f7fb5"],
  "traditional-arts": ["#70280f", "#d4562a"],
  handicrafts: ["#523417", "#b98a3d"],
  textiles: ["#511856", "#a8459c"],
  cuisine: ["#70440a", "#dfa32a"],
  yoga: ["#124b47", "#2f9c8c"],
  unesco: ["#272b62", "#5a63c2"],
  universities: ["#43280f", "#9c6a2c"],
  cities: ["#61203a", "#c96a78"],
  traditions: ["#702613", "#e08a3c"],
  regional: ["#19434f", "#3aa0a8"],
  folk: ["#631c1c", "#d64b3a"],
};

/** Distinct jewel tones for the featured cards, so a row of monuments is not one colour. */
const FEATURED = [
  ["#7a2d12", "#d9792b"], ["#17425a", "#3d93a8"], ["#5a1a3c", "#bf4f7a"], ["#3d2a6b", "#8a6fd0"],
  ["#70440a", "#d9a02a"], ["#12504a", "#35a08e"], ["#232a66", "#5661c4"], ["#6b1f1f", "#cf5a3a"],
];
export function featuredGradient(i) {
  const [a, b] = FEATURED[i % FEATURED.length];
  return `linear-gradient(160deg, ${b} 0%, ${a} 78%)`;
}

export function gradientFor(categoryId) {
  const [a, b] = PALETTE[categoryId] || PALETTE.monuments;
  return `linear-gradient(155deg, ${a} 0%, ${b} 100%)`;
}
