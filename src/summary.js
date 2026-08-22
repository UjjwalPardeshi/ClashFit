// Session summary: the fatigue curve and the form trend, drawn to a canvas that exports as PNG.
//
// This screen is not decoration. It is the evidence we hand a judge after the demo — "here is
// what we actually measured" — and it is the source image for the novelty slide in the deck.
// docs/02-APP-FLOW.md §2, docs/26-DECK-COPY.md slide 4

import { C } from './render.js';

const BAND_LINES = [
  { key: 'working', label: 'WORKING' },
  { key: 'fading', label: 'FADING' },
  { key: 'gassed', label: 'GASSED' },
];

/**
 * @param {HTMLCanvasElement} cv
 * @param {Array} reps  engine.reps
 * @param {object} bands pose.fatigue.bands
 * @param {object} meta  { exercise, mode, title }
 */
export function drawSummary(cv, reps, bands, meta = {}) {
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  const W = 960, H = 460;
  cv.width = W * dpr; cv.height = H * dpr;
  cv.style.width = '100%'; cv.style.maxWidth = W + 'px';
  const ctx = cv.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

  ctx.fillStyle = C.ground; ctx.fillRect(0, 0, W, H);
  if (!reps.length) { note(ctx, W, H, 'No reps recorded.'); return; }

  const pad = { l: 56, r: 132, t: 62, b: 46 };
  const pw = W - pad.l - pad.r, ph = H - pad.t - pad.b;
  const x = (i) => pad.l + (reps.length === 1 ? pw / 2 : (i / (reps.length - 1)) * pw);
  const y = (v) => pad.t + ph - v * ph;

  // --- band regions, so the reader sees the thresholds rather than being told about them
  const stops = [
    { from: 0, to: bands.working, color: C.clean, label: 'FRESH' },
    { from: bands.working, to: bands.fading, color: C.system, label: 'WORKING' },
    { from: bands.fading, to: bands.gassed, color: C.shallow, label: 'FADING' },
    { from: bands.gassed, to: 1, color: C.damage, label: 'GASSED' },
  ];
  for (const s of stops) {
    ctx.fillStyle = hexA(s.color, 0.07);
    ctx.fillRect(pad.l, y(s.to), pw, y(s.from) - y(s.to));
    ctx.fillStyle = hexA(s.color, 0.85);
    ctx.font = '600 10px ui-monospace, monospace';
    ctx.textAlign = 'left';
    ctx.fillText(s.label, pad.l + pw + 10, y((s.from + s.to) / 2) + 3);
  }
  for (const b of BAND_LINES) {
    ctx.strokeStyle = 'rgba(255,255,255,0.10)'; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(pad.l, y(bands[b.key])); ctx.lineTo(pad.l + pw, y(bands[b.key])); ctx.stroke();
  }

  // --- form trend, quiet, behind the headline
  ctx.strokeStyle = hexA(C.mute, 0.55); ctx.lineWidth = 1.5;
  ctx.setLineDash([3, 4]);
  ctx.beginPath();
  reps.forEach((r, i) => (i ? ctx.lineTo(x(i), y(r.formScore)) : ctx.moveTo(x(i), y(r.formScore))));
  ctx.stroke(); ctx.setLineDash([]);

  // --- fatigue curve, the headline
  ctx.beginPath();
  ctx.moveTo(x(0), y(0));
  reps.forEach((r, i) => ctx.lineTo(x(i), y(r.fatigue.value)));
  ctx.lineTo(x(reps.length - 1), y(0));
  ctx.closePath();
  const g = ctx.createLinearGradient(0, pad.t, 0, pad.t + ph);
  g.addColorStop(0, hexA(C.fatigue, 0.42)); g.addColorStop(1, hexA(C.fatigue, 0.02));
  ctx.fillStyle = g; ctx.fill();

  ctx.strokeStyle = C.fatigue; ctx.lineWidth = 3; ctx.lineJoin = 'round';
  ctx.beginPath();
  reps.forEach((r, i) => (i ? ctx.lineTo(x(i), y(r.fatigue.value)) : ctx.moveTo(x(i), y(r.fatigue.value))));
  ctx.stroke();

  // --- rep markers, coloured by verdict
  reps.forEach((r, i) => {
    ctx.fillStyle = r.verdict === 'CLEAN' ? C.clean : r.verdict === 'OK' ? C.system : C.shallow;
    ctx.beginPath(); ctx.arc(x(i), y(r.fatigue.value), 3.5, 0, Math.PI * 2); ctx.fill();
  });

  // --- emphasised endpoint
  const last = reps[reps.length - 1];
  ctx.fillStyle = C.fatigue;
  ctx.beginPath(); ctx.arc(x(reps.length - 1), y(last.fatigue.value), 6, 0, Math.PI * 2); ctx.fill();
  ctx.strokeStyle = C.ground; ctx.lineWidth = 2; ctx.stroke();

  // --- axes
  ctx.fillStyle = C.mute; ctx.font = '500 10px ui-monospace, monospace';
  ctx.textAlign = 'right';
  for (const v of [0, 0.25, 0.5, 0.75, 1]) ctx.fillText(v.toFixed(2), pad.l - 10, y(v) + 3);
  ctx.textAlign = 'center';
  const stepN = Math.max(1, Math.ceil(reps.length / 12));
  reps.forEach((r, i) => { if (i % stepN === 0 || i === reps.length - 1)
    ctx.fillText(String(r.repIndex), x(i), pad.t + ph + 18); });
  ctx.fillText('REP', pad.l + pw / 2, H - 10);

  // --- title block
  ctx.textAlign = 'left';
  ctx.fillStyle = C.ink; ctx.font = '800 24px ui-sans-serif, system-ui, sans-serif';
  ctx.fillText(meta.title ?? 'Fatigue across the set', pad.l, 34);
  ctx.fillStyle = C.mute; ctx.font = '500 11px ui-monospace, monospace';
  const mean = reps.reduce((a, r) => a + r.formScore, 0) / reps.length;
  ctx.fillText(
    `${meta.exercise ?? 'squat'} · ${reps.length} reps · mean form ${Math.round(mean * 100)}% · ` +
    `velocity −${Math.round(last.fatigue.velocityLoss * 100)}% · range −${Math.round(last.fatigue.romLoss * 100)}%`,
    pad.l, 50);

  // --- legend
  ctx.textAlign = 'left'; ctx.font = '500 10px ui-monospace, monospace';
  ctx.fillStyle = C.fatigue; ctx.fillText('■ fatigue', pad.l + pw + 10, pad.t - 18);
  ctx.fillStyle = C.mute;   ctx.fillText('-- form',  pad.l + pw + 10, pad.t - 4);
}

function note(ctx, W, H, text) {
  ctx.fillStyle = C.mute; ctx.font = '500 14px ui-sans-serif, system-ui, sans-serif';
  ctx.textAlign = 'center'; ctx.fillText(text, W / 2, H / 2);
}

function hexA(hex, a) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

/** Straight into the deck. */
export function exportPng(cv, name = 'clashfit-fatigue-curve.png') {
  cv.toBlob((blob) => {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  }, 'image/png');
}

/** Per-rep CSV — the raw evidence, for a judge who asks or for tuning in a spreadsheet. */
export function exportCsv(reps, name = 'clashfit-session.csv') {
  const head = ['rep','form','verdict','depth','rom','tempo','alignment','depth_cm',
                'ecc_s','pause_s','con_s','vel_deg_s','fatigue','band','damage','combo'];
  const rows = reps.map((r) => [
    r.repIndex, r.formScore.toFixed(3), r.verdict, r.depth.toFixed(3), r.rom.toFixed(3),
    r.tempo.toFixed(3), r.alignment.toFixed(3),
    Number.isFinite(r.depthCm) ? r.depthCm.toFixed(1) : '',
    r.tEccSec.toFixed(2), r.tBottomSec.toFixed(2), r.tConSec.toFixed(2),
    r.concentricVelocity.toFixed(1), r.fatigue.value.toFixed(3), r.fatigue.band,
    r.damage, (r.combo ?? 1).toFixed(2),
  ]);
  const csv = [head.join(','), ...rows.map((r) => r.join(','))].join('\n') + '\n';
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
  a.download = name; a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}
