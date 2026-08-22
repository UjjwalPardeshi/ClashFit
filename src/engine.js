// SessionEngine — the integration layer. Owns calibration, the rep machine, form scoring,
// fatigue, and the combat engine, and exposes one frame() entry point.
// This is deliberately the shape the Kotlin PoseEngine + CombatEngine pair will take, so the
// event build is a port rather than a redesign. docs/09-MODULE-CONTRACTS.md

import { LandmarkFilter } from './oneEuro.js';
import { primaryAngle, boxHeight, spanMetres, JOINT, SIDE } from './geometry.js';
import { RepStateMachine } from './repFsm.js';
import { scoreRep, verdict, alignmentSample } from './formScorer.js';
import { FatigueEstimator, Band } from './fatigue.js';
import { CombatEngine } from './combat.js';
import { GhostSource } from './ghost.js';
import { summarise, coachFor } from './coach.js';

export const Phase = { CALIBRATING: 'CALIBRATING', FIGHTING: 'FIGHTING', FRAMING_LOST: 'FRAMING_LOST', REST: 'REST', DEAD: 'DEAD' };
export const Mode = {
  BOSS_FIGHT: 'BOSS_FIGHT',
  TIME_ATTACK: 'TIME_ATTACK',
  GHOST_RACE: 'GHOST_RACE',
  SURVIVAL: 'SURVIVAL',
  BOSS_RUSH: 'BOSS_RUSH',
  CLINIC_STS: 'CLINIC_STS',
};

/** Modes scored on a clock rather than on boss HP. */
const TIMED = new Set([Mode.TIME_ATTACK, Mode.CLINIC_STS]);

export class SessionEngine {
  constructor(cfg, exerciseId = 'squat', opts = {}) {
    this.cfg = cfg;
    this.setExercise(exerciseId);
    this.combat = new CombatEngine(cfg.combat, { casual: !!opts.casual });
    this.onRep = opts.onRep ?? (() => {});
    this.onBand = opts.onBand ?? (() => {});
    this.onEnd = opts.onEnd ?? (() => {});
    this.onSetEnd = opts.onSetEnd ?? (() => {});
    this.llm = opts.llm ?? null;              // the seam Gemma slots into at the event
    this.mode = opts.mode ?? Mode.BOSS_FIGHT;
    this.ghost = null;
    this.reset();
  }

  setMode(mode) { this.mode = mode; this.reset(); }

  /** A ghost is just a recorded rep timeline. It rides the duel's own code path. */
  loadGhost(ghostData) {
    this.ghostData = ghostData;
    this.ghost = new GhostSource(ghostData);
    this.mode = Mode.GHOST_RACE;
    this.reset();
  }

  setExercise(id) {
    this.exercise = this.cfg.exercises[id];
    this.jointNames = this.exercise.detector.requiredJoints;
    this.fsm = new RepStateMachine(this.exercise.detector);
    this.filter = new LandmarkFilter(this.cfg.pose.filter);
    this.fatigue = new FatigueEstimator(this.cfg.pose.fatigue);
    this.romBaselineU = null;
    this.topRefSamples = [];
  }

  reset() {
    this.phase = Phase.CALIBRATING;
    this.invalidFrames = 0;
    this.angle = NaN;
    this.lastRep = null;
    this.lastScore = null;
    this.reps = [];
    this.fsm.reset();
    this.fatigue.reset();
    this.combat.reset();
    this.romBaselineU = null;
    this.topRefSamples = [];
    this.filter.reset();

    this.fightStartMs = null;
    this.ended = false;
    this.endReason = null;
    this.playerDamage = 0;
    this.ghostDamage = 0;
    this.ghost?.reset();
    this.spanSamples = [];
    this.wave = 1;
    this.rushIndex = 0;
    this.formThresholdBonus = 0;
    this.setIndex = 1;
    this.setReps = [];
    this.lastRepEndMs = null;
    this.coach = null;
    this.ghost?.reset();

    // Modes scored on a clock must not have the boss die out from under them.
    if (TIMED.has(this.mode)) {
      this.combat.maxHp = 10_000_000;
      this.combat.hp = 10_000_000;
    }
    if (this.mode === Mode.SURVIVAL) {
      const w = this.cfg.combat.modes?.SURVIVAL ?? {};
      this.combat.maxHp = w.hpPerWave ?? 900;
      this.combat.hp = this.combat.maxHp;
    }
  }

  get durationMs() {
    if (this.mode === Mode.CLINIC_STS)
      return (this.cfg.clinic?.sit_to_stand_30s?.durationSec ?? 30) * 1000;
    return (this.cfg.combat.modes?.TIME_ATTACK?.durationSec ?? 60) * 1000;
  }

  get timeLeftMs() {
    if (!TIMED.has(this.mode) || this.fightStartMs === null) return null;
    return Math.max(0, this.durationMs - (this.lastTMs - this.fightStartMs));
  }

  #end(reason) {
    if (this.ended) return;
    this.ended = true;
    this.endReason = reason;
    this.onEnd(reason, this.state());
  }

  /**
   * @param {Array} world worldLandmarks (metric, hip-centred)
   * @param {Array} image normalised image landmarks, for framing only
   * @param {number} tMs
   */
  frame(world, image, tMs) {
    this.lastTMs = tMs;
    if (!world || !world.length) return this.#lost(tMs);

    const lms = this.filter.apply(world, tMs);
    const thr = this.cfg.pose.visibilityThreshold;
    const sel = primaryAngle(lms, this.exercise.detector.primaryAngle, this.jointNames, thr);
    this.angle = sel.angle;
    this.side = sel.side;
    this.framing = image ? this.#framing(image) : 'OK';

    if (!Number.isFinite(sel.angle)) return this.#lost(tMs);

    // Recovered.
    if (this.phase === Phase.FRAMING_LOST) { this.phase = Phase.FIGHTING; this.fatigue.unfreeze(); }
    this.invalidFrames = 0;

    // Calibration: settle at rest, take the median as the reference position.
    if (this.phase === Phase.CALIBRATING) {
      this.topRefSamples.push(sel.angle);
      if (this.topRefSamples.length > 30) this.topRefSamples.shift();
      if (this.topRefSamples.length >= 30 && this.#settled()) {
        const med = [...this.topRefSamples].sort((a, b) => a - b)[15];
        this.fsm.setTopRef(med);
        this.topRef = med;
        this.phase = Phase.FIGHTING;
        this.fightStartMs = tMs;
        this.ghost?.start(tMs);
      }
      return this.state();
    }

    if (this.phase === Phase.FIGHTING || this.phase === Phase.REST) {
      const span = spanMetres(lms, this.exercise.detector.primaryAngle, sel.side);
      if (Number.isFinite(span)) {
        this.spanSamples.push([tMs, span]);
        if (this.spanSamples.length > 900) this.spanSamples.shift();   // ~30s at 30fps
      }
    }

    // A set ends when the player stops, not on a timer they have to beat.
    if (this.phase === Phase.FIGHTING && this.setReps.length && this.lastRepEndMs !== null) {
      const idleSec = (tMs - this.lastRepEndMs) / 1000;
      if (idleSec >= (this.cfg.combat.setEnd?.noRepTimeoutSec ?? 12)) this.#endSet();
    }

    if (!this.ended) {
      // A ghost's damage enters through the same idempotent path a remote player's does.
      if (this.mode === Mode.GHOST_RACE && this.ghost) {
        for (const e of this.ghost.due(tMs)) {
          this.combat.onRemoteDamage('GHOST', e.seq, e.damage);
          this.ghostDamage += e.damage;
        }
      }
      if (TIMED.has(this.mode) && this.timeLeftMs === 0) this.#end('TIME');
    }

    const raw = this.ended ? null : this.fsm.onFrame(sel.angle, tMs);
    if (raw) this.#completeRep(raw, lms);
    return this.state();
  }

  /** Depth travel for one rep, in centimetres, from the metric span samples. */
  #depthCm(raw) {
    const win = this.spanSamples.filter(([t]) => t >= raw.tStartMs && t <= raw.tEndMs).map(([, v]) => v);
    if (win.length < 3) return NaN;
    return (Math.max(...win) - Math.min(...win)) * 100;
  }

  async #endSet() {
    if (this.phase !== Phase.FIGHTING || !this.setReps.length) return;
    this.phase = Phase.REST;
    const restSec = this.#restSeconds();
    const telemetry = summarise(this.setReps, this.combat.state(), this.exercise, this.setIndex, restSec);
    this.telemetry = telemetry;
    const coach = await coachFor(telemetry, this.llm);
    this.coach = coach;
    this.onSetEnd(telemetry, coach, restSec);
  }

  /** Survival keeps going with a harder boss; Boss Rush advances the sequence; everything else
   *  ends. Survival deliberately disables the mercy rule — this is the one mode where fatigue
   *  genuinely ends the run, and that is the point of it. */
  #onBossDown() {
    if (this.mode === Mode.SURVIVAL) {
      const w = this.cfg.combat.modes?.SURVIVAL ?? {};
      this.wave += 1;
      this.formThresholdBonus += w.formThresholdStep ?? 0.03;
      const hp = Math.round((w.hpPerWave ?? 900) * (1 + 0.35 * (this.wave - 1)));
      this.combat.reset({ ...this.cfg.combat.boss, maxHp: hp });
      this.combat.mercyDisabled = !!w.mercyDisabled;
      return;
    }
    if (this.mode === Mode.BOSS_RUSH) {
      const seq = this.cfg.combat.modes?.BOSS_RUSH?.sequence ?? [];
      this.rushIndex += 1;
      if (this.rushIndex < seq.length) {
        this.combat.reset({ ...this.cfg.combat.boss, id: seq[this.rushIndex],
                            name: seq[this.rushIndex].replace(/_/g, ' ').toUpperCase(),
                            maxHp: Math.round(this.cfg.combat.boss.maxHp * (1 + 0.2 * this.rushIndex)) });
        return;
      }
    }
    this.phase = Phase.DEAD;
    this.#end('BOSS_DOWN');
  }

  #restSeconds() {
    const r = this.cfg.combat.rest ?? { freshSeconds: 30, gassedSeconds: 75 };
    const v = this.fatigue.state().value;
    return Math.round(r.freshSeconds + (r.gassedSeconds - r.freshSeconds) * Math.min(1, v / 0.5));
  }

  /** Begin the next set. Fatigue baselines reset; boss HP and combo carry. */
  nextSet() {
    if (this.phase !== Phase.REST) return;
    this.setIndex += 1;
    this.setReps = [];
    this.lastRepEndMs = null;
    this.coach = null;
    this.fatigue.reset();
    this.fsm.reset();
    this.fsm.setTopRef(this.topRef);
    this.phase = Phase.FIGHTING;
  }

  #completeRep(raw, lms) {
    // First completed rep sets the ROM baseline — every score is relative to this player.
    if (this.romBaselineU === null) this.romBaselineU = raw.uMax - raw.uMin;

    const align = alignmentSample(lms, this.side ?? SIDE.LEFT, this.exercise.form.alignment?.type);
    const score = scoreRep(raw, this.exercise, this.romBaselineU, align);
    if (this.formThresholdBonus > 0) {
      this.combat.combo.cfg = { ...this.combat.combo.cfg,
        threshold: Math.min(0.95, (this.cfg.combat.combo.threshold ?? 0.75) + this.formThresholdBonus) };
    }
    const f = this.fatigue.onRep(raw);

    const prevBand = this.combatBand ?? Band.FRESH;
    this.combatBand = f.band;

    const c = this.combat.onRep(score.formScore, f.band);
    if (f.band !== prevBand) { this.combat.onFatigueBand(f.band); this.onBand(f.band); }

    const rec = {
      ...raw, ...score,
      depthCm: this.#depthCm(raw),
      verdict: verdict(score.formScore, this.cfg.ui?.verdictBands),
      fatigue: f, damage: c.lastDamage, combo: c.comboMultiplier,
    };
    this.reps.push(rec);
    this.setReps.push(rec);
    this.lastRepEndMs = raw.tEndMs;
    this.lastRep = rec;
    this.lastScore = score;
    this.playerDamage += rec.damage ?? 0;
    if (this.combat.dead) this.#onBossDown();
    this.onRep(rec, this.combat.state());
  }

  #settled() {
    const s = this.topRefSamples;
    const min = Math.min(...s), max = Math.max(...s);
    return max - min < 8;                       // roughly still for a second
  }

  #framing(image) {
    const h = boxHeight(image);
    const f = this.cfg.pose.framing;
    if (h < f.targetBoxHeightMin) return 'TOO_FAR';
    if (h > f.targetBoxHeightMax) return 'TOO_CLOSE';
    return 'OK';
  }

  #lost(tMs) {
    this.invalidFrames++;
    if (this.invalidFrames >= this.cfg.pose.framingLostFrames && this.phase === Phase.FIGHTING) {
      this.phase = Phase.FRAMING_LOST;
      this.fatigue.freeze();                    // a pause must never read as fatigue
    }
    return this.state();
  }

  state() {
    return {
      mode: this.mode,
      ended: this.ended,
      endReason: this.endReason,
      timeLeftMs: this.timeLeftMs,
      playerDamage: this.playerDamage,
      ghostDamage: this.ghostDamage,
      ghostMeta: this.ghost?.meta ?? null,
      ghostFinished: this.ghost?.finished ?? null,
      wave: this.wave,
      rushIndex: this.rushIndex,
      setIndex: this.setIndex,
      setReps: this.setReps.length,
      coach: this.coach,
      telemetry: this.telemetry ?? null,
      phase: this.phase,
      angle: this.angle,
      fsmState: this.fsm.state,
      framing: this.framing,
      topRef: this.topRef,
      reps: this.reps.length,
      lastRep: this.lastRep,
      fatigue: this.fatigue.state(),
      combat: this.combat.state(),
      cue: this.#cue(),
    };
  }

  #cue() {
    const c = this.exercise.cues ?? {};
    if (this.phase === Phase.FRAMING_LOST) return c.framing ?? 'I lost you — step back into frame.';
    if (this.phase === Phase.CALIBRATING) return c.enter ?? 'Stand still for a moment.';
    if (this.framing === 'TOO_FAR') return 'Come closer.';
    if (this.framing === 'TOO_CLOSE') return 'Step back.';
    const r = this.lastRep;
    if (r && r.verdict === 'SHALLOW') {
      if (r.reason === 'tempo') return c.tooFast ?? 'Control the way down.';
      return c.tooHigh ?? 'Go lower.';
    }
    return null;
  }
}
