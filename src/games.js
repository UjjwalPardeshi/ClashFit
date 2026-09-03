// Family games: a game shaped like the movement.
//
// Reps map to discrete damage, so REP_CYCLE gets a boss fight. The other four families do not,
// and reskinning a boss fight for all of them would read as exactly that. Each of these takes
// the same scored event stream and turns it into a mechanic whose shape matches the movement.
//
//   SIEGE   holds     your hold IS the shield; the boss attacks continuously
//   PURSUIT cardio    sustained cadence is distance; drop and it closes
//   BREAKER jumps     each jump is an impact; height is force
//   SIGIL   yoga      no combat at all, deliberately
//
// docs/19-EXERCISE-LIBRARY.md §3

export const GameOutcome = { RUNNING: 'RUNNING', WON: 'WON', LOST: 'LOST' };

/** SIEGE — the boss attacks while you hold. Quality is your shield strength; a broken hold lets
 *  a hit through. You win by outlasting it, which is what a hold actually is. */
export class SiegeGame {
  constructor(cfg = {}) {
    this.c = { playerHp: 100, bossHp: 1200, dpsPerQuality: 26, hitOnBreak: 18, ...cfg };
    this.reset();
  }
  reset() {
    this.playerHp = this.c.playerHp;
    this.bossHp = this.c.bossHp;
    this.holds = 0;
    this.totalHeldSec = 0;
    this.outcome = GameOutcome.RUNNING;
  }
  /** @param ev an ISOMETRIC_HOLD event */
  onEvent(ev) {
    if (this.outcome !== GameOutcome.RUNNING) return this.state();
    this.holds += 1;
    this.totalHeldSec += ev.holdSec;
    const dealt = Math.round(ev.holdSec * this.c.dpsPerQuality * ev.quality);
    this.bossHp = Math.max(0, this.bossHp - dealt);
    if (!ev.completed) this.playerHp = Math.max(0, this.playerHp - this.c.hitOnBreak);
    if (this.bossHp <= 0) this.outcome = GameOutcome.WON;
    else if (this.playerHp <= 0) this.outcome = GameOutcome.LOST;
    return { ...this.state(), lastDamage: dealt };
  }
  state() {
    return { game: 'SIEGE', playerHp: this.playerHp, playerHpPct: this.playerHp / this.c.playerHp,
             bossHp: this.bossHp, bossHpPct: this.bossHp / this.c.bossHp,
             holds: this.holds, totalHeldSec: this.totalHeldSec, outcome: this.outcome };
  }
}

/** PURSUIT — something is chasing you. Distance is the integral of cadence and amplitude, so a
 *  drop in either lets it close. Continuous motion maps to continuous distance. */
export class PursuitGame {
  constructor(cfg = {}) {
    this.c = { startGapM: 12, escapeAtM: 200, pursuerMps: 2.6, metresPerCycle: 1.6, ...cfg };
    this.reset();
  }
  reset() {
    this.distance = 0;
    this.pursuer = -this.c.startGapM;
    this.cycles = 0;
    this.lastTMs = null;
    this.outcome = GameOutcome.RUNNING;
  }
  /** The pursuer advances on the clock whether you move or not. */
  tick(tMs) {
    if (this.outcome !== GameOutcome.RUNNING) return this.state();
    if (this.lastTMs !== null) {
      this.pursuer += (this.c.pursuerMps * (tMs - this.lastTMs)) / 1000;
      if (this.pursuer >= this.distance) this.outcome = GameOutcome.LOST;
    }
    this.lastTMs = tMs;
    return this.state();
  }
  onEvent(ev) {
    if (this.outcome !== GameOutcome.RUNNING) return this.state();
    this.cycles += 1;
    this.distance += this.c.metresPerCycle * Math.min(1.4, ev.amplitude) * (0.5 + 0.5 * ev.formScore);
    if (this.distance >= this.c.escapeAtM) this.outcome = GameOutcome.WON;
    return this.state();
  }
  get gapM() { return Math.max(0, this.distance - this.pursuer); }
  state() {
    return { game: 'PURSUIT', distance: this.distance, pursuer: this.pursuer, gapM: this.gapM,
             cycles: this.cycles, progress: Math.min(1, this.distance / this.c.escapeAtM),
             outcome: this.outcome };
  }
}

/** BREAKER — each jump is an impact and height is force. Landing softness matters because a
 *  stiff landing is how knees get hurt, so it gates the damage rather than decorating it. */
export class BreakerGame {
  constructor(cfg = {}) {
    this.c = { floors: 12, cmPerFloor: 14, ...cfg };
    this.reset();
  }
  reset() { this.broken = 0; this.jumps = 0; this.bestCm = 0; this.outcome = GameOutcome.RUNNING; }
  onEvent(ev) {
    if (this.outcome !== GameOutcome.RUNNING) return this.state();
    this.jumps += 1;
    if (ev.heightCm > this.bestCm) this.bestCm = ev.heightCm;
    // A stiff landing scores the jump but does not break through — the force went into you.
    const force = (ev.heightCm / this.c.cmPerFloor) * ev.softness;
    const floors = Math.max(0, Math.floor(force));
    this.broken = Math.min(this.c.floors, this.broken + floors);
    if (this.broken >= this.c.floors) this.outcome = GameOutcome.WON;
    return { ...this.state(), lastFloors: floors };
  }
  state() {
    return { game: 'BREAKER', broken: this.broken, floors: this.c.floors, jumps: this.jumps,
             bestCm: this.bestCm, progress: this.c.floors > 0 ? this.broken / this.c.floors : 0, outcome: this.outcome };
  }
}

/** SIGIL — no boss, no damage, no ranking, deliberately.
 *
 *  Yoga framed as combat is tonally wrong and would read as a reskin. Each asana lights a
 *  segment of a constellation, and accuracy decides how brightly. Having one non-combat mode is
 *  also what makes the product look like a product rather than one mechanic wearing hats. */
export class SigilGame {
  constructor(cfg = {}) {
    this.c = { segments: 8, ...cfg };
    this.reset();
  }
  reset() { this.lit = []; this.outcome = GameOutcome.RUNNING; }
  onEvent(ev) {
    if (this.outcome !== GameOutcome.RUNNING) return this.state();
    this.lit.push({ brightness: Math.max(0.15, ev.accuracy), heldSec: ev.heldSec, index: this.lit.length });
    if (this.lit.length >= this.c.segments) this.outcome = GameOutcome.WON;
    return this.state();
  }
  get meanBrightness() {
    return this.lit.length ? this.lit.reduce((a, s) => a + s.brightness, 0) / this.lit.length : 0;
  }
  state() {
    return { game: 'SIGIL', lit: this.lit.length, segments: this.c.segments,
             brightness: this.meanBrightness, progress: this.c.segments > 0 ? this.lit.length / this.c.segments : 0,
             segmentsDetail: this.lit, outcome: this.outcome };
  }
}

export function makeFamilyGame(mode, cfg) {
  switch (mode) {
    case 'SIEGE': return new SiegeGame(cfg?.SIEGE);
    case 'PURSUIT': return new PursuitGame(cfg?.PURSUIT);
    case 'BREAKER': return new BreakerGame(cfg?.BREAKER);
    case 'SIGIL': return new SigilGame(cfg?.SIGIL);
    default: return null;
  }
}
