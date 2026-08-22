// Turn-taking for group play on the hardware you actually have.
//
// You get two loaner phones. A four-player raid needs four devices; relay and pass-the-phone
// need one or two, which is why these are the group modes that will actually happen at a
// judging table. docs/20-MULTIPLAYER-MODES.md §6
//
// LAST_STANDING is the one only we can build: every other fitness app would eliminate on rep
// count, ours eliminates on measured physiological fatigue. It is the novelty turned into a
// party game.

export const RosterMode = {
  PASS_THE_PHONE: 'PASS_THE_PHONE',   // turns of fixed length, boss HP carries
  LAST_STANDING: 'LAST_STANDING',     // eliminated when your fatigue reaches GASSED
  RELAY: 'RELAY',                     // teams take turns, boss HP carries
};

export class Roster {
  /**
   * @param {string[]} names
   * @param {object} opts { mode, turnSec, teams }
   */
  constructor(names, opts = {}) {
    this.mode = opts.mode ?? RosterMode.PASS_THE_PHONE;
    this.turnSec = opts.turnSec ?? 30;
    this.players = names.map((name, i) => ({
      id: `P${i + 1}`, name, team: opts.teams?.[i] ?? null,
      reps: 0, damage: 0, turns: 0, bestForm: 0, out: false, outReason: null,
    }));
    this.index = 0;
    this.turnStartMs = null;
    this.finished = false;
    this.winner = null;
  }

  get current() { return this.players[this.index] ?? null; }
  get standing() { return this.players.filter((p) => !p.out); }

  startTurn(tMs) { this.turnStartMs = tMs; this.current && (this.current.turns += 1); }

  turnLeftMs(tMs) {
    if (this.mode === RosterMode.LAST_STANDING) return null;   // a turn ends when you stop
    if (this.turnStartMs === null) return this.turnSec * 1000;
    return Math.max(0, this.turnSec * 1000 - (tMs - this.turnStartMs));
  }

  record({ reps = 0, damage = 0, bestForm = 0 } = {}) {
    const p = this.current;
    if (!p) return;
    p.reps += reps; p.damage += damage;
    if (bestForm > p.bestForm) p.bestForm = bestForm;
  }

  /** Fatigue is the referee. This is the mechanic no other fitness app can offer. */
  eliminateCurrent(reason = 'GASSED') {
    const p = this.current;
    if (!p || p.out) return;
    p.out = true; p.outReason = reason;
    if (this.standing.length <= 1) {
      this.finished = true;
      this.winner = this.standing[0] ?? null;
    }
  }

  /** Advance to the next player who is still in. Returns null when the round is over. */
  next(tMs) {
    if (this.finished) return null;
    const n = this.players.length;
    for (let step = 1; step <= n; step++) {
      const i = (this.index + step) % n;
      if (!this.players[i].out) {
        this.index = i;
        this.startTurn(tMs);
        return this.current;
      }
    }
    this.finished = true;
    this.winner = this.standing[0] ?? null;
    return null;
  }

  /** Highest total damage, not highest rep count — the whole point of the product. */
  finish() {
    this.finished = true;
    if (!this.winner) {
      this.winner = [...this.players].sort((a, b) => b.damage - a.damage)[0] ?? null;
    }
    return this.winner;
  }

  leaderboard() {
    return [...this.players].sort((a, b) =>
      Number(a.out) - Number(b.out) || b.damage - a.damage || b.reps - a.reps);
  }

  state() {
    return {
      mode: this.mode,
      current: this.current ? { ...this.current } : null,
      players: this.players.map((p) => ({ ...p })),
      standing: this.standing.length,
      finished: this.finished,
      winner: this.winner ? { ...this.winner } : null,
    };
  }
}

/** CIRCUIT — a prescribed sequence across families. The gym-class and office-break mode, and
 *  the only thing in the product that exercises several detectors in one session.
 *  docs/20-MULTIPLAYER-MODES.md §4 */
export class Circuit {
  constructor(steps) {
    this.steps = steps;                 // [{ exerciseId, seconds, label }]
    this.index = 0;
    this.startedMs = null;
    this.results = [];
  }
  get current() { return this.steps[this.index] ?? null; }
  get finished() { return this.index >= this.steps.length; }

  start(tMs) { this.startedMs = tMs; }
  leftMs(tMs) {
    if (this.startedMs === null || !this.current) return 0;
    return Math.max(0, this.current.seconds * 1000 - (tMs - this.startedMs));
  }
  advance(tMs, result = {}) {
    if (this.current) this.results.push({ ...this.current, ...result });
    this.index += 1;
    this.startedMs = tMs;
    return this.current;
  }
  state() {
    return { step: this.index, total: this.steps.length, current: this.current,
             finished: this.finished, results: this.results };
  }
}

/** A default circuit that touches four of the five families in about four minutes. */
export const DEFAULT_CIRCUIT = [
  { exerciseId: 'squat', seconds: 45, label: 'Squats' },
  { exerciseId: 'plank', seconds: 45, label: 'Plank' },
  { exerciseId: 'high_knees', seconds: 40, label: 'High knees' },
  { exerciseId: 'jump_squat', seconds: 30, label: 'Jump squats' },
  { exerciseId: 'utkatasana', seconds: 30, label: 'Chair pose' },
  { exerciseId: 'chair_squat', seconds: 45, label: 'Chair squats' },
];
