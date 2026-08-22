// Guided breathing, and the recovery mechanic that makes it part of the fight.
//
// Detection is nearly free: shoulder-Y oscillation IS a breathing signal, and the cadence
// detector already extracts periodic motion from a landmark axis. It only needed different gates.
//
// The important design point is the mechanic. Mindfulness in fitness apps is usually a tab
// nobody opens. Here, breathing between sets measurably recovers a fatigue band — so it is
// mechanically useful mid-fight rather than a virtue you are told to practise.
// docs/22-HEALTH-DOMAINS.md §3

export const Phase = { IN: 'IN', HOLD_IN: 'HOLD_IN', OUT: 'OUT', HOLD_OUT: 'HOLD_OUT' };

const LABEL = { IN: 'Breathe in', HOLD_IN: 'Hold', OUT: 'Breathe out', HOLD_OUT: 'Hold' };

/** Box breathing, 4-4-4-4 by default. Wind-down uses a longer exhale, which is the pattern that
 *  actually shifts you toward rest. */
export class BoxBreathing {
  constructor({ inSec = 4, holdInSec = 4, outSec = 4, holdOutSec = 4, cycles = 4 } = {}) {
    this.plan = [
      [Phase.IN, inSec], [Phase.HOLD_IN, holdInSec],
      [Phase.OUT, outSec], [Phase.HOLD_OUT, holdOutSec],
    ].filter(([, sec]) => sec > 0);
    this.cycles = cycles;
    this.reset();
  }

  static windDown(cycles = 4) { return new BoxBreathing({ inSec: 4, holdInSec: 0, outSec: 8, holdOutSec: 0, cycles }); }

  reset() { this.startMs = null; this.cycle = 0; this.phaseIndex = 0; this.done = false; }
  start(tMs) { this.reset(); this.startMs = tMs; }

  get phase() { return this.plan[this.phaseIndex][0]; }
  get label() { return LABEL[this.phase]; }
  get totalSec() { return this.plan.reduce((a, [, s]) => a + s, 0) * this.cycles; }

  /** @returns {{phase, label, phaseProgress, cycle, done, changed}} */
  tick(tMs) {
    if (this.startMs === null || this.done) {
      return { phase: this.phase, label: this.label, phaseProgress: 0,
               cycle: this.cycle, done: this.done, changed: false };
    }
    let elapsed = (tMs - this.startMs) / 1000;
    const cycleSec = this.plan.reduce((a, [, s]) => a + s, 0);
    const cycle = Math.floor(elapsed / cycleSec);
    if (cycle >= this.cycles) { this.done = true; return { phase: this.phase, label: 'Done', phaseProgress: 1, cycle: this.cycles, done: true, changed: true }; }

    let within = elapsed - cycle * cycleSec;
    let i = 0;
    for (; i < this.plan.length; i++) {
      if (within < this.plan[i][1]) break;
      within -= this.plan[i][1];
    }
    const changed = i !== this.phaseIndex || cycle !== this.cycle;
    this.phaseIndex = Math.min(i, this.plan.length - 1);
    this.cycle = cycle;
    return {
      phase: this.phase, label: this.label,
      phaseProgress: within / this.plan[this.phaseIndex][1],
      cycle, cycles: this.cycles, done: false, changed,
    };
  }
}

/**
 * How much fatigue a completed breathing session recovers.
 *
 * Bounded on purpose: breathing helps, it does not undo a set. If it recovered everything the
 * fatigue system would stop meaning anything, and the mechanic that makes the product different
 * would become a button you press to skip it.
 */
export function recoveryFraction({ cyclesCompleted, targetCycles, rateInBand = true }) {
  const completion = Math.max(0, Math.min(1, cyclesCompleted / Math.max(1, targetCycles)));
  const quality = rateInBand ? 1 : 0.6;
  return Math.min(0.35, 0.35 * completion * quality);
}
