// Combat: damage, combo, boss phases, and the fatigue-adaptive behaviour that is the
// mechanic nobody else has.
// Pure and time-free by design — no internal timers — so it unit-tests in milliseconds.
// Spec: docs/04-GAME-DESIGN.md

import { Band } from './fatigue.js';

export class ComboTracker {
  constructor(cfg) { this.cfg = cfg; this.reset(); }
  reset() { this.streak = 0; this.graceUsed = false; }

  onRep(formScore) {
    if (formScore >= this.cfg.threshold) {
      this.streak += 1;
      this.graceUsed = false;
    } else if (this.streak >= (this.cfg.graceAtStreak ?? Infinity) && !this.graceUsed) {
      this.graceUsed = true;                       // one forgiven rep at a long streak
    } else {
      this.streak = 0;
      this.graceUsed = false;
    }
    return this.multiplier;
  }

  get multiplier() {
    if (this.streak <= 0) return 1;
    return Math.min(1 + this.cfg.step * (this.streak - 1), this.cfg.cap);
  }
}

export class CombatEngine {
  /** @param {object} cfg combat.json @param {object} opts { casual } */
  constructor(cfg, opts = {}) {
    this.cfg = cfg;
    this.casual = !!opts.casual;
    this.combo = new ComboTracker(cfg.combo);
    this.reset();
  }

  reset(bossOverride) {
    const boss = bossOverride ?? this.cfg.boss;
    this.boss = boss;
    this.maxHp = Math.round(boss.maxHp * (this.casual ? this.cfg.casual.bossHpMultiplier : 1));
    this.hp = this.maxHp;
    this.reps = 0;
    this.totalDamage = 0;
    this.combo.reset();
    this.staggerRepsLeft = 0;
    this.mercyActive = false;
    this.dead = false;
    this.events = new Map();               // duel dedupe: "player:seq" -> damage
    this.recent = [];                      // last few local damages, for the mercy estimate
    this.log = [];
  }

  get hpPct() { return this.maxHp > 0 ? this.hp / this.maxHp : 0; }

  phaseModifier() {
    const p = this.boss.phases;
    let mod = 1, label = 'phase1';
    for (const ph of p) if (this.hpPct <= ph.fromHpPct) { mod = ph.modifier; label = ph.label; }
    return { mod, label };
  }

  damageFor(formScore, band) {
    const floor = this.casual ? this.cfg.casual.formFloor : this.cfg.formFloor;
    const formFactor = floor + (1 - floor) * Math.pow(Math.max(0, formScore), this.cfg.formExponent);
    const fr = this.cfg.fatigueResponse[band] ?? {};
    const fatigueMod = this.staggerRepsLeft > 0
      ? (this.cfg.fatigueResponse.FADING.modifier ?? 1)
      : (fr.modifier ?? 1);
    const base = this.cfg.baseDamage * (this.casual ? this.cfg.casual.damageMultiplier : 1);
    return Math.round(base * formFactor * this.combo.multiplier * this.phaseModifier().mod * fatigueMod);
  }

  /** Apply one local rep. Returns what the HUD needs. */
  onRep(formScore, band = Band.WORKING) {
    if (this.dead) return this.state();
    this.combo.onRep(formScore);
    const damage = this.damageFor(formScore, band);
    this.#applyDamage(damage);
    this.reps += 1;

    // Fresh players get a boss that shrugs it off; the regen is what makes "go harder" true.
    const fr = this.cfg.fatigueResponse[band] ?? {};
    if (fr.regenPerRep && !this.dead) this.hp = Math.min(this.maxHp, this.hp + fr.regenPerRep);
    if (this.staggerRepsLeft > 0) this.staggerRepsLeft -= 1;

    this.recent.push(damage / Math.max(1, this.combo.multiplier));   // combo-neutral
    if (this.recent.length > 5) this.recent.shift();
    this.log.push({ rep: this.reps, formScore, band, damage, hp: this.hp });
    return this.state(damage);
  }

  /** Duel/raid: damage from another player. Idempotent by (playerId, seq) — order and
   *  duplicates are irrelevant by construction. docs/07-MULTIPLAYER-SPEC.md §1 */
  onRemoteDamage(playerId, seq, damage) {
    const key = `${playerId}:${seq}`;
    if (this.events.has(key)) return this.state();
    this.events.set(key, damage);
    this.#applyDamage(damage);
    return this.state(damage);
  }

  #applyDamage(d) {
    this.totalDamage += d;
    this.hp = Math.max(0, this.hp - d);
    if (this.hp <= 0) this.dead = true;
  }

  /**
   * Fatigue-adaptive boss. The line: the boss cannot outlast you, but it makes you earn
   * the ending. Also the safest possible demo behaviour — a judge who tires mid-demo
   * still reaches a victory screen. docs/04-GAME-DESIGN.md §5
   */
  /** Rolling mean of recent COMBO-NEUTRAL damage. A caller-supplied estimate computed while
   *  a streak was hot produces a mercy cap the player cannot reach once it breaks — this is
   *  the safe default, and it errs toward resolving the fight sooner. */
  recentMeanDamage() {
    if (!this.recent.length) return this.cfg.baseDamage * this.cfg.formFloor;
    return this.recent.reduce((a, b) => a + b, 0) / this.recent.length;
  }

  onFatigueBand(band, expectedDamagePerRep = this.recentMeanDamage()) {
    if (band === Band.FADING && this.staggerRepsLeft === 0) {
      this.staggerRepsLeft = this.cfg.fatigueResponse.FADING.staggerReps ?? 5;
    }
    if (band === Band.GASSED && !this.mercyActive) {
      this.mercyActive = true;
      const n = this.cfg.fatigueResponse.GASSED.mercyRepsToFinish ?? 4;
      const cap = Math.max(1, Math.round(n * expectedDamagePerRep));
      this.hp = Math.min(this.hp, cap);
      if (this.hp <= 0) this.dead = true;
    }
    return this.state();
  }

  state(lastDamage = null) {
    return {
      bossId: this.boss.id, bossName: this.boss.name,
      hp: this.hp, maxHp: this.maxHp, hpPct: this.hpPct,
      phase: this.phaseModifier().label,
      reps: this.reps, totalDamage: this.totalDamage,
      comboStreak: this.combo.streak, comboMultiplier: this.combo.multiplier,
      staggered: this.staggerRepsLeft > 0, mercyActive: this.mercyActive,
      dead: this.dead, lastDamage,
    };
  }
}
