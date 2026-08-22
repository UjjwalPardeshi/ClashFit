// Audio feedback, synthesised — no asset files, nothing to source or license.
//
// This is not polish. The player is two metres away, or face-down doing push-ups, or has their
// eyes closed in a yoga pose. Roughly half of all reps happen with the screen unavailable, so
// audio is the primary feedback channel and the screen is the secondary one.
//
// The single cheapest piece of game feel in the project: rep pitch rises with the combo
// multiplier, so the player HEARS the streak building without looking.
// docs/03-UI-UX-SPEC.md §5

export class Audio {
  constructor() { this.ctx = null; this.enabled = true; this.master = null; }

  /** Browsers require a user gesture before audio can start. */
  ensure() {
    if (this.ctx) { if (this.ctx.state === 'suspended') this.ctx.resume(); return this.ctx; }
    const AC = window.AudioContext ?? window.webkitAudioContext;
    if (!AC) { this.enabled = false; return null; }
    this.ctx = new AC();
    this.master = this.ctx.createGain();
    this.master.gain.value = 0.5;
    this.master.connect(this.ctx.destination);
    return this.ctx;
  }

  duck(amount = 0.25, ms = 220) {
    if (!this.master) return;
    const t = this.ctx.currentTime;
    this.master.gain.cancelScheduledValues(t);
    this.master.gain.setTargetAtTime(amount, t, 0.05);
    setTimeout(() => this.master?.gain.setTargetAtTime(0.5, this.ctx.currentTime, 0.15), ms);
  }

  #tone({ freq, dur = 0.12, type = 'triangle', gain = 0.3, sweep = 0, delay = 0 }) {
    const ctx = this.ensure();
    if (!ctx || !this.enabled) return;
    const t0 = ctx.currentTime + delay;
    const osc = ctx.createOscillator();
    const g = ctx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, t0);
    if (sweep) osc.frequency.exponentialRampToValueAtTime(Math.max(20, freq + sweep), t0 + dur);
    g.gain.setValueAtTime(0, t0);
    g.gain.linearRampToValueAtTime(gain, t0 + 0.008);       // no wind-up: impact is instant
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    osc.connect(g).connect(this.master);
    osc.start(t0); osc.stop(t0 + dur + 0.02);
  }

  #noise({ dur = 0.09, gain = 0.18, hp = 1200, delay = 0 }) {
    const ctx = this.ensure();
    if (!ctx || !this.enabled) return;
    const t0 = ctx.currentTime + delay;
    const n = Math.floor(ctx.sampleRate * dur);
    const buf = ctx.createBuffer(1, n, ctx.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < n; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / n) ** 2;
    const src = ctx.createBufferSource(); src.buffer = buf;
    const f = ctx.createBiquadFilter(); f.type = 'highpass'; f.frequency.value = hp;
    const g = ctx.createGain(); g.gain.value = gain;
    src.connect(f).connect(g).connect(this.master);
    src.start(t0);
  }

  /** Clean rep. Pitch climbs with the combo multiplier — the streak is audible. */
  repClean(comboMultiplier = 1) {
    const semitones = Math.round((comboMultiplier - 1) * 12);
    const freq = 440 * Math.pow(2, semitones / 12);
    this.#tone({ freq, dur: 0.11, type: 'triangle', gain: 0.32, sweep: freq * 0.35 });
    this.#noise({ dur: 0.06, gain: 0.14, hp: 2200 });
  }

  /** Shallow rep. Must be obviously worse, not merely different. */
  repShallow() {
    this.#tone({ freq: 196, dur: 0.16, type: 'sawtooth', gain: 0.20, sweep: -60 });
    this.#noise({ dur: 0.05, gain: 0.07, hp: 700 });
  }

  comboMilestone(mult) {
    const base = 523.25;
    [0, 4, 7].forEach((s, i) =>
      this.#tone({ freq: base * Math.pow(2, s / 12) * (mult > 2 ? 1.5 : 1),
                   dur: 0.16, type: 'sine', gain: 0.18, delay: i * 0.055 }));
  }

  phaseChange() {
    this.#tone({ freq: 110, dur: 0.6, type: 'sawtooth', gain: 0.26, sweep: -35 });
    this.#tone({ freq: 220, dur: 0.35, type: 'square', gain: 0.10, delay: 0.04 });
  }

  bossDeath() {
    [0, 0.09, 0.2, 0.34].forEach((d, i) =>
      this.#tone({ freq: 330 - i * 60, dur: 0.5, type: 'sawtooth', gain: 0.3 - i * 0.05, sweep: -80, delay: d }));
    this.#noise({ dur: 0.7, gain: 0.22, hp: 300, delay: 0.05 });
  }

  framingLost() {
    this.#tone({ freq: 392, dur: 0.13, type: 'sine', gain: 0.16 });
    this.#tone({ freq: 294, dur: 0.18, type: 'sine', gain: 0.16, delay: 0.13 });
  }

  countdown(last = false) {
    this.#tone({ freq: last ? 880 : 587, dur: last ? 0.28 : 0.1, type: 'square', gain: 0.22 });
  }

  timeWarning() { this.#tone({ freq: 740, dur: 0.08, type: 'square', gain: 0.2 }); }
}
