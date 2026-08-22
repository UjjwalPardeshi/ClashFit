// Landmark trace recorder and replay.
// The highest-value hour in the project: lets the whole engine be exercised with no camera,
// no body and no floor space — for regression testing, for tuning without doing 400 squats,
// and as a camera-free demo fallback when detection fails in the hall.
// docs/14-TEST-PLAN.md §1

const KEEP = [11,12,13,14,15,16,23,24,25,26,27,28,29,30,31,32];  // only what we use

export class TraceRecorder {
  constructor(meta = {}) { this.meta = meta; this.frames = []; this.recording = false; this.t0 = null; }

  start(meta = {}) {
    this.meta = { ...this.meta, ...meta, startedAt: new Date().toISOString() };
    this.frames = []; this.recording = true; this.t0 = null;
  }
  stop() { this.recording = false; return this.frames.length; }

  /** @param {Array} world worldLandmarks @param {number} tMs */
  push(world, tMs) {
    if (!this.recording || !world) return;
    if (this.t0 === null) this.t0 = tMs;
    const lm = [];
    for (const i of KEEP) {
      const p = world[i];
      lm.push(p ? [r4(p.x), r4(p.y), r4(p.z ?? 0), r2(p.visibility ?? 1)] : null);
    }
    this.frames.push({ t: Math.round(tMs - this.t0), lm });
  }

  /** JSON Lines: one header, then one frame per line. Diffable, streamable, trivially parsed. */
  toJsonl() {
    const head = JSON.stringify({ type: 'clashfit-trace', v: 1, keep: KEEP, meta: this.meta, frames: this.frames.length });
    return [head, ...this.frames.map((f) => JSON.stringify(f))].join('\n') + '\n';
  }

  download(name) {
    const blob = new Blob([this.toJsonl()], { type: 'application/x-ndjson' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name ?? `trace-${(this.meta.exercise ?? 'set')}-${Date.now()}.jsonl`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  }
}

/** Rehydrate a trace into a full 33-entry landmark array, so downstream code is unchanged. */
export function parseTrace(text) {
  const lines = text.split('\n').filter(Boolean);
  const head = JSON.parse(lines[0]);
  const keep = head.keep ?? KEEP;
  const frames = lines.slice(1).map((l) => {
    const f = JSON.parse(l);
    const full = Array.from({ length: 33 }, () => ({ x: 0, y: 0, z: 0, visibility: 0 }));
    keep.forEach((idx, i) => {
      const p = f.lm[i];
      if (p) full[idx] = { x: p[0], y: p[1], z: p[2], visibility: p[3] };
    });
    return { t: f.t, world: full };
  });
  return { meta: head.meta ?? {}, frames };
}

const r4 = (v) => Math.round(v * 1e4) / 1e4;
const r2 = (v) => Math.round(v * 100) / 100;
