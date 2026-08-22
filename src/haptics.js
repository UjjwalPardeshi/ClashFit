// Haptics. Cheap, immediately noticeable, and the kind of polish that separates first from third.
//
// The player is face-down, or two metres away, or has their eyes closed in a pose. Audio already
// carries most of the load; haptics carry it further — a tempo you can feel is the entire
// interface in Tempo Trial, and a rep tick tells you the rep registered without looking.
// docs/21-SENSOR-PLAYBOOK.md §4

/** The motor is injected rather than reached for, so it is testable without patching a global —
 *  and `navigator` is read-only in modern Node, so patching would not work anyway. */
const defaultMotor = () =>
  (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function')
    ? (p) => navigator.vibrate(p)
    : null;

export class Haptics {
  constructor(cfg = {}, motor = defaultMotor()) {
    this.enabled = (cfg.repTick ?? true) || (cfg.tempoMetronome ?? true);
    this.cfg = { repTick: true, tempoMetronome: true, framingLostPattern: [0, 60, 80, 60], ...cfg };
    this.motor = motor;
    this.available = !!motor;
    this.metronome = null;
  }

  #buzz(pattern) {
    if (!this.available || !this.enabled) return false;
    try { return this.motor(pattern); } catch { return false; }
  }

  repClean() { if (this.cfg.repTick) this.#buzz(18); }
  repShallow() { if (this.cfg.repTick) this.#buzz([12, 40, 12]); }
  comboMilestone() { this.#buzz([0, 20, 40, 20, 40, 30]); }
  framingLost() { this.#buzz(this.cfg.framingLostPattern); }
  bossDown() { this.#buzz([0, 90, 60, 140]); }
  countdown(last = false) { this.#buzz(last ? 120 : 30); }

  /** A pulse on the eccentric beat. In Tempo Trial this is the whole interface. */
  startMetronome(bpm = 40) {
    this.stopMetronome();
    if (!this.cfg.tempoMetronome) return;
    const period = Math.max(200, Math.round(60000 / bpm));
    this.metronome = setInterval(() => this.#buzz(24), period);
  }
  stopMetronome() { if (this.metronome) { clearInterval(this.metronome); this.metronome = null; } }

  /** Slow rise and fall, for box breathing. */
  breathe(inSec = 4, outSec = 4) {
    this.#buzz([0, inSec * 1000 * 0.06, inSec * 1000 * 0.94, outSec * 1000 * 0.06]);
  }

  setEnabled(on) { this.enabled = on; if (!on) this.stopMetronome(); }
}
