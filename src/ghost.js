// Ghost racing: replay a recorded rep timeline as an opponent.
//
// The cheapest high-value feature in the project, because it costs almost nothing: a ghost is
// fed through combat.onRemoteDamage() — the exact code path the two-phone duel already uses.
// We are not building a feature, we are pointing an existing pipe at a file.
//
// It solves four problems at once: head-to-head with no second person, an instant fallback when
// duel pairing fails in the hall (it looks identical on screen), a solo demo when your teammate
// is with a mentor, and the cheapest retention mechanic in fitness apps — beat yesterday's you.
// docs/17-GAME-MODES.md §4

export class GhostSource {
  constructor(ghost) {
    this.meta = ghost.meta ?? {};
    this.events = [...(ghost.events ?? [])].sort((a, b) => a.t - b.t);
    this.reset();
  }
  reset() { this.i = 0; this.t0 = null; }
  start(tMs) { this.t0 = tMs; this.i = 0; }

  /** Events due by now, as {seq, damage}. Idempotent downstream via (playerId, seq). */
  due(tMs) {
    if (this.t0 === null) return [];
    const elapsed = tMs - this.t0;
    const out = [];
    while (this.i < this.events.length && this.events[this.i].t <= elapsed) {
      out.push({ seq: this.i + 1, damage: this.events[this.i].damage });
      this.i++;
    }
    return out;
  }

  get finished() { return this.i >= this.events.length; }
  get totalDamage() { return this.events.reduce((a, e) => a + e.damage, 0); }
  get durationMs() { return this.events.length ? this.events[this.events.length - 1].t : 0; }
}

/** Turn a completed set into a ghost. Times are relative to the first rep of the run. */
export function ghostFromReps(reps, meta = {}) {
  if (!reps.length) return { type: 'clashfit-ghost', v: 1, meta, events: [] };
  const t0 = reps[0].tStartMs;
  return {
    type: 'clashfit-ghost',
    v: 1,
    meta: {
      ...meta,
      reps: reps.length,
      totalDamage: reps.reduce((a, r) => a + (r.damage ?? 0), 0),
      meanForm: +(reps.reduce((a, r) => a + r.formScore, 0) / reps.length).toFixed(3),
      recordedAt: new Date().toISOString(),
    },
    events: reps.map((r) => ({ t: Math.round(r.tEndMs - t0), damage: r.damage ?? 0 })),
  };
}

export function parseGhost(text) {
  const g = typeof text === 'string' ? JSON.parse(text) : text;
  if (g.type !== 'clashfit-ghost') throw new Error('not a ClashFit ghost file');
  return g;
}

export function downloadGhost(ghost, name) {
  const blob = new Blob([JSON.stringify(ghost, null, 2)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name ?? `ghost-${ghost.meta?.exercise ?? 'set'}-${Date.now()}.json`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}
