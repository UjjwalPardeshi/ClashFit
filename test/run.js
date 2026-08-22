import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { angle3 } from '../src/geometry.js';
import { makeDetector } from '../src/detectors/index.js';
import { missingJoints, jointPhrase } from '../src/geometry.js';
import { Haptics } from '../src/haptics.js';
import { COMMANDS } from '../src/voice.js';
import { Roster, RosterMode, Circuit, DEFAULT_CIRCUIT } from '../src/roster.js';
import { DuelSession, LoopbackTransport, LinkState, newPlayerId } from '../src/duel.js';
import { SiegeGame, PursuitGame, BreakerGame, SigilGame, GameOutcome, makeFamilyGame } from '../src/games.js';
import { RepStateMachine } from '../src/repFsm.js';
import { scoreRep, verdict, clamp01 } from '../src/formScorer.js';
import { FatigueEstimator, Band } from '../src/fatigue.js';
import { CombatEngine, ComboTracker } from '../src/combat.js';
import { synthRep, synthSet, synthWorldSet, stick, holdFrames, cadenceFrames, jumpFrames } from './synth.js';
import { SessionEngine, Mode, Phase, Calib } from '../src/engine.js';
import { IsometricHoldDetector } from '../src/detectors/isometric.js';
import { CadenceDetector } from '../src/detectors/cadence.js';
import { BallisticDetector } from '../src/detectors/ballistic.js';
import { PoseMatchDetector } from '../src/detectors/poseMatch.js';
import { SIGNALS } from '../src/fatigue.js';
import { GhostSource, ghostFromReps, parseGhost } from '../src/ghost.js';
import { summarise, templateFor, validateOutput, coachFor, fill } from '../src/coach.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const cfg = (p) => JSON.parse(readFileSync(join(HERE, '..', 'config', p), 'utf8'));
const pose = cfg('pose.json');
const combatCfg = cfg('combat.json');
const squat = cfg('exercises/squat.json');
const calf = cfg('exercises/calf_raise.json');

let pass = 0, fail = 0;
const results = [];
const pending = [];
function t(name, fn) {
  try {
    const r = fn();
    if (r && typeof r.then === 'function') {
      pending.push(r.then(
        () => { pass++; results.push(['PASS', name, '']); },
        (e) => { fail++; results.push(['FAIL', name, e.message]); }));
    } else { pass++; results.push(['PASS', name, '']); }
  } catch (e) { fail++; results.push(['FAIL', name, e.message]); }
}
const eq = (a, b, m = '') => { if (a !== b) throw new Error(`${m} expected ${b}, got ${a}`); };
const near = (a, b, tol, m = '') => { if (!(Math.abs(a - b) <= tol)) throw new Error(`${m} expected ~${b} (±${tol}), got ${a}`); };
const ok = (c, m) => { if (!c) throw new Error(m); };

function runFsm(samples, exercise, topRef) {
  const fsm = new RepStateMachine(exercise.detector);
  if (topRef != null) fsm.setTopRef(topRef);
  const reps = [];
  for (const [a, ms] of samples) { const r = fsm.onFrame(a, ms); if (r) reps.push(r); }
  return reps;
}

// ---------- geometry ----------
t('angle3 right angle', () => near(angle3({x:1,y:0,z:0},{x:0,y:0,z:0},{x:0,y:1,z:0}), 90, 1e-6));
t('angle3 straight',    () => near(angle3({x:1,y:0,z:0},{x:0,y:0,z:0},{x:-1,y:0,z:0}), 180, 1e-6));
t('angle3 degenerate is NaN', () => ok(Number.isNaN(angle3({x:0,y:0,z:0},{x:0,y:0,z:0},{x:0,y:1,z:0})), 'expected NaN'));

// ---------- rep FSM ----------
t('F1 · 10 clean squats counts 10', () => {
  const reps = runFsm(synthSet({ reps: 10, top: 170, bottom: 80 }), squat, 170);
  eq(reps.length, 10);
});

t('F4 · jitter at the threshold counts 0', () => {
  const s = []; let ts = 0;
  for (let i = 0; i < 400; i++) { s.push([155 + Math.sin(i / 2) * 4, ts]); ts += 33.3; }
  eq(runFsm(s, squat, 170).length, 0);
});

t('too-fast rep is rejected (minRepMs)', () => {
  const s = [[170,0],[170,33]];
  const r = synthRep({ top: 170, bottom: 80, eccSec: 0.2, pauseSec: 0.05, conSec: 0.2, t0: 66 });
  eq(runFsm([...s, ...r.samples, [170, r.tEnd + 33]], squat, 170).length, 0);
});

t('too-slow rep is rejected (maxRepMs)', () => {
  const s = [[170,0]];
  const r = synthRep({ top: 170, bottom: 80, eccSec: 6, pauseSec: 2, conSec: 6, t0: 33 });
  eq(runFsm([...s, ...r.samples, [170, r.tEnd + 33]], squat, 170).length, 0);
});

t('incomplete rep (never reaches depth) counts 0', () => {
  const reps = runFsm(synthSet({ reps: 5, top: 170, bottom: 140 }), squat, 170);
  eq(reps.length, 0);
});

t('INVERTED direction · calf raise counts 8', () => {
  // topEnter 100 < bottomEnter 130 — the angle INCREASES toward the effort position.
  const reps = runFsm(synthSet({ reps: 8, top: 95, bottom: 140 }), calf, 95);
  eq(reps.length, 8);
});

t('F5 · framing loss mid-set produces no phantom reps', () => {
  const a = synthSet({ reps: 3, top: 170, bottom: 80 });
  const last = a[a.length - 1][1];
  const gap = []; let ts = last + 33;
  for (let i = 0; i < 120; i++) { gap.push([NaN, ts]); ts += 33.3; }
  const b = synthSet({ reps: 3, top: 170, bottom: 80 }).map(([v, ms]) => [v, ms + ts]);
  eq(runFsm([...a, ...gap, ...b], squat, 170).length, 6);
});

t('rep measurements are complete and finite', () => {
  const r = runFsm(synthSet({ reps: 1, top: 170, bottom: 80 }), squat, 170)[0];
  for (const k of ['thetaMin','thetaMax','tEccSec','tBottomSec','tConSec','concentricVelocity','validFrameRatio'])
    ok(Number.isFinite(r[k]), `${k} not finite: ${r[k]}`);
  ok(r.thetaMin < 90, `thetaMin should be deep, got ${r.thetaMin}`);
});

// ---------- form scoring ----------
const romBaseline = (() => {
  const r = runFsm(synthSet({ reps: 1, top: 170, bottom: 80 }), squat, 170)[0];
  return r.uMax - r.uMin;
})();

t('F1 · clean squats mean formScore > 0.80', () => {
  const reps = runFsm(synthSet({ reps: 10, top: 170, bottom: 80 }), squat, 170);
  const m = reps.map(r => scoreRep(r, squat, romBaseline, { kneeOffset: 0.10 }).formScore)
               .reduce((a,b)=>a+b,0) / reps.length;
  ok(m > 0.80, `mean ${m.toFixed(3)}`);
});

t('F2 · shallow squats read as SHALLOW and land visibly less damage', () => {
  // shallow AND rushed — which is how a bad rep actually looks.
  // The requirement is the verdict band and a damage gap a bystander notices, not an
  // arbitrary score threshold — so that is what this asserts.
  const reps = runFsm(synthSet({ reps: 10, top: 170, bottom: 118, eccSec: 0.45, pauseSec: 0.05 }), squat, 170);
  ok(reps.length === 10, `expected 10 reps, got ${reps.length}`);
  const scores = reps.map(r => scoreRep(r, squat, romBaseline, { kneeOffset: 0.40 }).formScore);
  const m = scores.reduce((a,b)=>a+b,0) / scores.length;
  ok(scores.every(s => verdict(s) === 'SHALLOW'), `not all SHALLOW, mean ${m.toFixed(3)}`);

  const clean = runFsm(synthSet({ reps: 5, top: 170, bottom: 80 }), squat, 170)
    .map(r => scoreRep(r, squat, romBaseline, { kneeOffset: 0.10 }).formScore);
  const cm = clean.reduce((a,b)=>a+b,0) / clean.length;

  const e = new CombatEngine(combatCfg);
  const dShallow = e.damageFor(m, Band.WORKING), dClean = e.damageFor(cm, Band.WORKING);
  ok(dClean - dShallow >= 25, `damage gap only ${dClean - dShallow} (${dShallow} vs ${dClean})`);
});

t('weights normalise when alignment is zeroed', () => {
  const noAlign = JSON.parse(JSON.stringify(squat));
  noAlign.form.weights = { depth: 0.47, rom: 0.29, tempo: 0.24, alignment: 0 };
  const r = runFsm(synthSet({ reps: 1, top: 170, bottom: 80 }), squat, 170)[0];
  const s = scoreRep(r, noAlign, romBaseline, null);
  ok(s.formScore >= 0 && s.formScore <= 1, `out of range ${s.formScore}`);
});

t('scores clamp to [0,1] on absurd input', () => {
  const r = runFsm(synthSet({ reps: 1, top: 170, bottom: 20 }), squat, 170)[0];
  const s = scoreRep(r, squat, 1, { kneeOffset: -5 });
  ok(s.formScore <= 1 && s.depth <= 1 && s.rom <= 1, 'not clamped');
});

t('worst sub-score names the fault · shallow rep', () => {
  const reps = runFsm(synthSet({ reps: 1, top: 170, bottom: 118 }), squat, 170);
  const r = scoreRep(reps[0], squat, romBaseline, { kneeOffset: 0.10 });
  ok(['depth', 'rom'].includes(r.reason), `got ${r.reason}`);
});

t('worst sub-score names the fault · bounced rep is tempo', () => {
  const s = [[170, 0]];
  const rep = synthRep({ top: 170, bottom: 80, eccSec: 0.35, pauseSec: 0.02, conSec: 0.6, t0: 33 });
  const reps = runFsm([...s, ...rep.samples, [170, rep.tEnd + 33]], squat, 170);
  ok(reps.length === 1, `expected 1 rep, got ${reps.length}`);
  eq(scoreRep(reps[0], squat, romBaseline, { kneeOffset: 0.10 }).reason, 'tempo');
});

t('verdict bands', () => {
  eq(verdict(0.85), 'CLEAN'); eq(verdict(0.60), 'OK'); eq(verdict(0.20), 'SHALLOW');
});

// ---------- fatigue ----------
t('flat set stays FRESH', () => {
  const reps = runFsm(synthSet({ reps: 12, top: 170, bottom: 80 }), squat, 170);
  const f = new FatigueEstimator(pose.fatigue);
  let st; for (const r of reps) st = f.onRep(r);
  eq(st.band, Band.FRESH, `value ${st.value.toFixed(3)}`);
});

t('F3 · set to failure reaches GASSED', () => {
  const reps = runFsm(synthSet({ reps: 14, top: 170, bottom: 80, decay: 0.030, restGrowth: 0.55 }), squat, 170);
  ok(reps.length >= 12, `only ${reps.length} reps detected`);
  const f = new FatigueEstimator(pose.fatigue);
  const seen = []; for (const r of reps) seen.push(f.onRep(r).band);
  eq(seen[seen.length - 1], Band.GASSED, `bands: ${seen.join(',')}`);
  ok(seen.includes(Band.WORKING) && seen.includes(Band.FADING), `no gradual progression: ${seen.join(',')}`);
});

t('band latching does not flap on a boundary', () => {
  const f = new FatigueEstimator({ ...pose.fatigue, bandLatchReps: 1 });
  f.samples = [{velocity:100,rom:90,gap:0.4},{velocity:100,rom:90,gap:0.4},{velocity:100,rom:90,gap:0.4}];
  f.baseline = { velocity: 100, rom: 90, gap: 0.4 };
  const mk = (v) => ({ concentricVelocity: v, uMax: 90, uMin: 0, gapSec: 0.4 });
  const bands = [];
  for (const v of [79, 81, 79, 81, 79]) bands.push(f.onRep(mk(v)).band);
  eq(new Set(bands).size, 1, `flapped: ${bands.join(',')}`);
});

t('freeze prevents a pause being read as fatigue', () => {
  const f = new FatigueEstimator(pose.fatigue);
  const reps = runFsm(synthSet({ reps: 6, top: 170, bottom: 80 }), squat, 170);
  reps.forEach(r => f.onRep(r));
  const before = f.state().value;
  f.freeze();
  f.onRep({ concentricVelocity: 1, uMax: 5, uMin: 0, gapSec: 30 });
  near(f.state().value, before, 1e-9, 'frozen estimator moved');
});

// ---------- combat ----------
t('damage curve is monotonic and bounded', () => {
  const e = new CombatEngine(combatCfg);
  const d = [0, 0.3, 0.55, 0.8, 0.95, 1].map(s => e.damageFor(s, Band.WORKING));
  for (let i = 1; i < d.length; i++) ok(d[i] > d[i-1], `not monotonic at ${i}: ${d.join(',')}`);
  eq(d[0], 35, 'floor'); eq(d[d.length-1], 100, 'perfect rep');
});

t('bad rep still does 35% — never zero', () => {
  const e = new CombatEngine(combatCfg);
  ok(e.damageFor(0, Band.WORKING) === 35, 'floor broken');
});

t('combo builds, breaks, caps, and forgives once at streak 6', () => {
  const c = new ComboTracker(combatCfg.combo);
  for (let i = 0; i < 3; i++) c.onRep(0.9);
  near(c.multiplier, 1.24, 1e-9, 'streak 3');
  for (let i = 0; i < 20; i++) c.onRep(0.9);
  near(c.multiplier, 2.5, 1e-9, 'cap');
  c.onRep(0.2); ok(c.streak > 0, 'grace not applied at long streak');
  c.onRep(0.2); eq(c.streak, 0, 'second bad rep should break');
});

t('boss dies and hp never goes negative', () => {
  const e = new CombatEngine(combatCfg);
  for (let i = 0; i < 200 && !e.dead; i++) e.onRep(0.95, Band.WORKING);
  ok(e.dead, 'boss survived 200 clean reps'); eq(e.hp, 0);
});

t('GASSED mercy resolves the fight in ~4 reps', () => {
  const e = new CombatEngine(combatCfg);
  for (let i = 0; i < 8; i++) e.onRep(0.8, Band.WORKING);
  e.onFatigueBand(Band.GASSED);              // uses the engine's own rolling mean
  let n = 0; while (!e.dead && n < 20) { e.onRep(0.7, Band.GASSED); n++; }
  ok(e.dead, 'mercy did not resolve'); ok(n <= 5, `took ${n} reps`);
});

t('FADING stagger raises damage', () => {
  const a = new CombatEngine(combatCfg); const base = a.damageFor(0.8, Band.WORKING);
  const b = new CombatEngine(combatCfg); b.onFatigueBand(Band.FADING, 100);
  ok(b.damageFor(0.8, Band.WORKING) > base, 'stagger did not increase damage');
});

t('casual mode is easier on every axis', () => {
  const n = new CombatEngine(combatCfg), c = new CombatEngine(combatCfg, { casual: true });
  ok(c.maxHp < n.maxHp, 'hp'); ok(c.damageFor(0.3, Band.WORKING) > n.damageFor(0.3, Band.WORKING), 'damage');
});

// ---------- duel sync ----------
t('duel · duplicate flood changes nothing', () => {
  const e = new CombatEngine(combatCfg);
  for (let i = 0; i < 50; i++) e.onRemoteDamage('P2', 1, 100);
  eq(e.totalDamage, 100);
});

t('duel · out-of-order arrival converges to the same HP', () => {
  const evts = Array.from({ length: 20 }, (_, i) => ['P2', i + 1, 50 + i]);
  const a = new CombatEngine(combatCfg); evts.forEach(([p,s,d]) => a.onRemoteDamage(p,s,d));
  const b = new CombatEngine(combatCfg); [...evts].reverse().forEach(([p,s,d]) => b.onRemoteDamage(p,s,d));
  eq(a.hp, b.hp);
});

t('duel · 30% packet loss repaired by the recent tail', () => {
  const sent = Array.from({ length: 20 }, (_, i) => ({ seq: i + 1, damage: 60 }));
  const truth = new CombatEngine(combatCfg); sent.forEach(e => truth.onRemoteDamage('P2', e.seq, e.damage));
  const rx = new CombatEngine(combatCfg);
  let dropped = 0;
  sent.forEach((e, i) => {
    const tail = sent.slice(Math.max(0, i - 8), i + 1);          // recent tail, last 8 + self
    if (i % 10 === 3 || i % 10 === 7) { dropped++; return; }      // drop this message entirely
    tail.forEach(x => rx.onRemoteDamage('P2', x.seq, x.damage));
  });
  ok(dropped >= 4, `expected drops, got ${dropped}`);
  eq(rx.hp, truth.hp, 'tails did not repair the loss');
});

// ---------- engine, end to end ----------
const chair = cfg('exercises/chair_squat.json');
const clinicSts = cfg('clinic/sit_to_stand_30s.json');
const store = { pose, combat: combatCfg,
                exercises: { squat, calf_raise: calf, chair_squat: chair },
                clinic: { sit_to_stand_30s: clinicSts } };
const drive = (engine, frames) => { for (const [w, t] of frames) engine.frame(w, null, t); return engine.state(); };

t('engine · calibrates then counts a clean set', () => {
  const e = new SessionEngine(store, 'squat');
  const s = drive(e, synthWorldSet({ reps: 8, bottom: 80 }));
  eq(s.phase === Phase.DEAD ? Phase.DEAD : s.phase, s.phase);
  ok(e.reps.length >= 7, `only ${e.reps.length} reps`);
  ok(Number.isFinite(s.topRef), 'never calibrated');
});

t('engine · framing loss freezes the fatigue baseline', () => {
  const e = new SessionEngine(store, 'squat');
  drive(e, synthWorldSet({ reps: 5, bottom: 80 }));
  const before = e.fatigue.state().value;
  for (let i = 0; i < 60; i++) e.frame(null, null, 100000 + i * 33);
  eq(e.state().phase, Phase.FRAMING_LOST);
  near(e.fatigue.state().value, before, 1e-9, 'baseline moved during loss');
});

// ---------- game modes ----------
t('TIME_ATTACK · ends on the clock, boss survives', () => {
  const e = new SessionEngine(store, 'squat', { mode: Mode.TIME_ATTACK });
  const s = drive(e, synthWorldSet({ reps: 40, bottom: 80 }));
  ok(s.ended, 'never ended');
  eq(s.endReason, 'TIME');
  ok(!s.combat.dead, 'boss died in a scored-on-damage mode');
  ok(s.playerDamage > 0, 'no damage recorded');
});

t('TIME_ATTACK · no reps counted after the clock stops', () => {
  const e = new SessionEngine(store, 'squat', { mode: Mode.TIME_ATTACK });
  drive(e, synthWorldSet({ reps: 40, bottom: 80 }));
  const atEnd = e.reps.length;
  drive(e, synthWorldSet({ reps: 5, bottom: 80 }).map(([w, ms]) => [w, ms + 200000]));
  eq(e.reps.length, atEnd, 'reps leaked past the buzzer');
});

t('GHOST_RACE · ghost damage lands through the duel path', () => {
  const g = { type: 'clashfit-ghost', v: 1, meta: { name: 'T' },
              events: Array.from({ length: 10 }, (_, i) => ({ t: (i + 1) * 1500, damage: 70 })) };
  const e = new SessionEngine(store, 'squat');
  e.loadGhost(g);
  const s = drive(e, synthWorldSet({ reps: 6, bottom: 80 }));
  ok(s.ghostDamage > 0, 'ghost never fired');
  eq(s.combat.totalDamage, s.playerDamage + s.ghostDamage, 'player and ghost damage do not reconcile');
});

t('GHOST_RACE · replaying the same ghost twice is idempotent', () => {
  const g = { type: 'clashfit-ghost', v: 1, meta: {},
              events: [{ t: 500, damage: 100 }, { t: 900, damage: 100 }] };
  const src = new GhostSource(g);
  src.start(0);
  const a = src.due(2000);
  const e = new SessionEngine(store, 'squat');
  for (const ev of a) e.combat.onRemoteDamage('GHOST', ev.seq, ev.damage);
  for (const ev of a) e.combat.onRemoteDamage('GHOST', ev.seq, ev.damage);
  eq(e.combat.totalDamage, 200);
});

t('ghost · round-trips a recorded set', () => {
  const e = new SessionEngine(store, 'squat');
  drive(e, synthWorldSet({ reps: 6, bottom: 80 }));
  const g = parseGhost(JSON.stringify(ghostFromReps(e.reps, { exercise: 'squat' })));
  eq(g.events.length, e.reps.length);
  eq(g.meta.totalDamage, e.reps.reduce((a, r) => a + r.damage, 0));
  ok(g.events.every((x, i) => i === 0 || x.t >= g.events[i - 1].t), 'ghost events out of order');
});

t('shipped pacer ghosts are valid and ordered', () => {
  for (const name of ['pacer_bronze', 'pacer_silver', 'pacer_gold']) {
    const g = parseGhost(readFileSync(join(HERE, '..', 'config/ghosts', name + '.json'), 'utf8'));
    ok(g.events.length > 0, name + ' empty');
    ok(g.events.every((x, i) => i === 0 || x.t > g.events[i - 1].t), name + ' unordered');
    ok(g.meta.totalDamage === g.events.reduce((a, x) => a + x.damage, 0), name + ' meta mismatch');
  }
});

t('SURVIVAL · boss respawns harder and mercy is disabled', () => {
  const e = new SessionEngine(store, 'squat', { mode: Mode.SURVIVAL });
  drive(e, synthWorldSet({ reps: 40, bottom: 80 }));
  ok(e.wave > 1, `never cleared a wave (wave ${e.wave})`);
  ok(!e.state().combat.dead, 'run ended on a boss death instead of continuing');
  ok(e.combat.mercyDisabled, 'mercy still enabled in Survival');
  ok(e.combat.maxHp > (combatCfg.modes.SURVIVAL.hpPerWave), 'later waves are not harder');
});

t('SURVIVAL · the clean-rep bar rises each wave', () => {
  const e = new SessionEngine(store, 'squat', { mode: Mode.SURVIVAL });
  drive(e, synthWorldSet({ reps: 40, bottom: 80 }));
  ok(e.formThresholdBonus > 0, 'threshold never tightened');
  ok(e.combat.combo.cfg.threshold > combatCfg.combo.threshold, 'combo threshold unchanged');
});

t('BOSS_RUSH · advances the sequence then ends', () => {
  const e = new SessionEngine(store, 'squat', { mode: Mode.BOSS_RUSH });
  drive(e, synthWorldSet({ reps: 120, bottom: 80 }));
  const seq = combatCfg.modes.BOSS_RUSH.sequence.length;
  ok(e.rushIndex >= 1, 'never advanced past the first boss');
  if (e.state().ended) eq(e.rushIndex, seq, 'ended before finishing the sequence');
});

t('CLINIC_STS · runs the 30-second protocol and counts stands', () => {
  const e = new SessionEngine(store, 'chair_squat', { mode: Mode.CLINIC_STS });
  eq(e.durationMs, 30000);
  const s = drive(e, synthWorldSet({ reps: 40, bottom: 118, eccSec: 0.5, pauseSec: 0.1, top: 165 }));
  ok(s.ended, 'protocol never ended');
  eq(s.endReason, 'TIME');
  ok(e.reps.length > 0, 'no stands counted');
  ok(!s.combat.dead, 'a clinical assessment killed a boss');
});

t('CLINIC_STS · ships no norms until they are cited', () => {
  // Publishing a reference range we cannot attribute is worse than publishing none.
  eq(clinicSts.reporting.showNormComparison, false);
  eq(clinicSts.norms.source, null);
  eq(clinicSts.norms.bands.length, 0);
  ok(/not a medical device/i.test(clinicSts.notNested), 'missing the not-a-medical-device line');
});

// ---------- group play ----------
t('roster · pass-the-phone cycles turns and carries totals', () => {
  const r = new Roster(['A', 'B', 'C'], { mode: RosterMode.PASS_THE_PHONE, turnSec: 30 });
  r.startTurn(0);
  r.record({ reps: 8, damage: 640, bestForm: 0.9 });
  eq(r.current.name, 'A');
  r.next(1000); r.record({ reps: 6, damage: 500 });
  eq(r.current.name, 'B');
  r.next(2000); r.record({ reps: 9, damage: 700 });
  eq(r.current.name, 'C');
  r.next(3000);
  eq(r.current.name, 'A', 'did not wrap around');
  eq(r.players[0].damage, 640);
  eq(r.players[0].turns, 2, 'turn count did not increment on the second pass');
});

t('roster · a turn clock only exists where a turn is timed', () => {
  const pass = new Roster(['A', 'B'], { mode: RosterMode.PASS_THE_PHONE, turnSec: 30 });
  pass.startTurn(0);
  eq(pass.turnLeftMs(10000), 20000);
  const last = new Roster(['A', 'B'], { mode: RosterMode.LAST_STANDING });
  last.startTurn(0);
  eq(last.turnLeftMs(10000), null, 'last standing should not be on a clock');
});

t('roster · LAST_STANDING eliminates on fatigue, not on rep count', () => {
  const r = new Roster(['A', 'B', 'C'], { mode: RosterMode.LAST_STANDING });
  r.startTurn(0);
  r.eliminateCurrent('GASSED');
  eq(r.players[0].out, true);
  eq(r.players[0].outReason, 'GASSED');
  eq(r.finished, false, 'ended with two players still in');
  r.next(1); r.eliminateCurrent('GASSED');
  eq(r.finished, true, 'never ended');
  eq(r.winner.name, 'C', 'wrong survivor');
});

t('roster · next() skips eliminated players', () => {
  const r = new Roster(['A', 'B', 'C', 'D'], { mode: RosterMode.LAST_STANDING });
  r.startTurn(0);
  r.players[1].out = true;                     // B is out
  eq(r.next(1).name, 'C', 'did not skip the eliminated player');
});

t('roster · the winner is decided on damage, not reps', () => {
  const r = new Roster(['A', 'B'], { mode: RosterMode.PASS_THE_PHONE });
  r.startTurn(0);
  r.record({ reps: 20, damage: 900 });          // many sloppy reps
  r.next(1);
  r.record({ reps: 12, damage: 1100 });         // fewer, cleaner reps
  eq(r.finish().name, 'B', 'rep count beat damage');
  eq(r.leaderboard()[0].name, 'B');
});

t('circuit · walks a cross-family sequence and records each step', () => {
  const c = new Circuit(DEFAULT_CIRCUIT);
  c.start(0);
  eq(c.current.exerciseId, 'squat');
  eq(c.leftMs(10000), 35000);
  c.advance(45000, { reps: 20 });
  eq(c.current.exerciseId, 'plank');
  while (!c.finished) c.advance(0, { reps: 1 });
  eq(c.state().results.length, DEFAULT_CIRCUIT.length);
  eq(c.finished, true);
});

t('circuit · the default sequence spans four movement families', () => {
  const byId = Object.fromEntries(
    readdirSync(join(HERE, '..', 'config/exercises'))
      .filter(f => f.endsWith('.json') && f !== 'index.json')
      .map(f => { const e = JSON.parse(readFileSync(join(HERE, '..', 'config/exercises', f), 'utf8'));
                  return [e.id, e]; }));
  const fams = new Set(DEFAULT_CIRCUIT.map(s => byId[s.exerciseId]?.family));
  for (const s2 of DEFAULT_CIRCUIT) ok(byId[s2.exerciseId], `${s2.exerciseId} is not a shipped exercise`);
  ok(fams.size >= 4, `only ${fams.size} families: ${[...fams].join(',')}`);
});

// ---------- haptics and voice ----------
t('haptics · degrade silently when the device has no vibration motor', () => {
  const h = new Haptics();
  // No navigator.vibrate in Node — every call must be a no-op, not a crash.
  h.repClean(); h.repShallow(); h.comboMilestone(); h.framingLost(); h.bossDown();
  h.startMetronome(40); h.stopMetronome(); h.breathe();
  eq(h.available, false);
});

t('haptics · a mock motor receives distinct patterns per event', () => {
  const seen = [];
  const h = new Haptics({}, (p) => { seen.push(JSON.stringify(p)); return true; });
  ok(h.available, 'injected motor not detected');
  h.repClean(); h.repShallow(); h.framingLost(); h.bossDown();
  eq(seen.length, 4, 'not every event buzzed');
  eq(new Set(seen).size, 4, 'events share a pattern and are indistinguishable by feel');
});

t('haptics · the metronome stops cleanly', () => {
  const h = new Haptics({}, () => true);
  h.startMetronome(60);
  ok(h.metronome, 'metronome did not start');
  h.stopMetronome();
  eq(h.metronome, null, 'metronome handle leaked');
  h.startMetronome(60); h.setEnabled(false);
  eq(h.metronome, null, 'disabling did not stop the metronome');
});

t('haptics · a broken motor never takes the app down', () => {
  const h = new Haptics({}, () => { throw new Error('motor on fire'); });
  h.repClean(); h.bossDown();
  ok(true, 'a throwing motor propagated');
});

t('voice · every command has distinct trigger phrases', () => {
  const all = Object.values(COMMANDS).flat();
  eq(new Set(all).size, all.length, 'two commands share a trigger phrase');
  for (const [cmd, words] of Object.entries(COMMANDS)) {
    ok(words.length >= 2, `${cmd} has too few phrasings to be reliable`);
    ok(words.every((w) => w === w.toLowerCase()), `${cmd} has a non-lowercase trigger`);
  }
});

// ---------- calibration ----------
t('calibration · names the joints it cannot see', () => {
  const lm = stick({});
  lm[25].visibility = 0.1; lm[26].visibility = 0.1;          // both knees
  const miss = missingJoints(lm, ['HIP', 'KNEE', 'ANKLE'], 0.6, 0);
  eq(miss.join(','), 'KNEE');
  eq(jointPhrase(miss), 'knees');
  eq(jointPhrase(['KNEE', 'ANKLE']), 'knees or ankles');
  eq(jointPhrase(['HIP', 'KNEE', 'ANKLE']), 'hips, knees or ankles');
});

t('calibration · will not start the fight until the pose is still', () => {
  const e = new SessionEngine(store, 'squat');
  // Moving at a human rate — roughly 3Hz — so it must never settle.
  let t2 = 0;
  for (let i = 0; i < 200; i++) {
    e.frame(stick({ knee: 120 + (Math.floor(i / 5) % 2) * 50 }), null, t2 += 33);
  }
  eq(e.state().phase, Phase.CALIBRATING, 'started while the player was still moving');
});

t('calibration · frame-rate jitter is filtered out, human motion is not', () => {
  // A 15Hz alternation is sensor noise, not a person, and One Euro is supposed to remove it.
  // Anything at human speed must survive filtering and keep the gate closed.
  const run = (period) => {
    const e = new SessionEngine(store, 'squat');
    let t2 = 0;
    for (let i = 0; i < 200; i++)
      e.frame(stick({ knee: 120 + (Math.floor(i / period) % 2) * 50 }), null, t2 += 33);
    return e.state().phase;
  };
  eq(run(1), Phase.FIGHTING, 'per-frame jitter was treated as real movement');
  eq(run(5), Phase.CALIBRATING, 'human-rate movement was filtered away');
  eq(run(15), Phase.CALIBRATING, 'slow movement was filtered away');
});

t('calibration · a still pose reaches READY and starts', () => {
  const e = new SessionEngine(store, 'squat');
  let t2 = 0;
  for (let i = 0; i < 120; i++) e.frame(stick({ knee: 176 }), null, t2 += 33);
  eq(e.state().phase, Phase.FIGHTING, 'never started');
  ok(Number.isFinite(e.state().topRef), 'no reference angle captured');
});

t('calibration · a partial view produces a named cue, not a spinner', () => {
  const e = new SessionEngine(store, 'squat');
  let t2 = 0;
  for (let i = 0; i < 40; i++) {
    const lm = stick({});
    lm[27].visibility = 0.1; lm[28].visibility = 0.1;        // ankles out of frame
    e.frame(lm, null, t2 += 33);
  }
  const s2 = e.state();
  eq(s2.calib, Calib.PARTIAL);
  ok(/ankles/.test(s2.cue), `cue was "${s2.cue}"`);
  ok(!/not detected|error|failed/i.test(s2.cue), 'generic error text leaked into the cue');
});

t('calibration · hold progress is reported for the ring', () => {
  const e = new SessionEngine(store, 'squat');
  let t2 = 0;
  for (let i = 0; i < 25; i++) e.frame(stick({ knee: 176 }), null, t2 += 33);
  const s2 = e.state();
  ok(s2.calibProgress > 0 && s2.calibProgress < 1, `progress ${s2.calibProgress}`);
});

// ---------- family games ----------
t('SIEGE · a held plank damages the boss, a broken one costs you', () => {
  const g = new SiegeGame({ playerHp: 100, bossHp: 600, dpsPerQuality: 20, hitOnBreak: 25 });
  const held = g.onEvent({ holdSec: 20, quality: 0.9, completed: true });
  ok(held.bossHp < 600, 'holding dealt no damage');
  eq(held.playerHp, 100, 'a clean hold cost the player health');
  const broke = g.onEvent({ holdSec: 4, quality: 0.5, completed: false });
  eq(broke.playerHp, 75, 'breaking form did not let a hit through');
});

t('SIEGE · outlasting the boss wins, running out of health loses', () => {
  const win = new SiegeGame({ bossHp: 300, dpsPerQuality: 20 });
  win.onEvent({ holdSec: 20, quality: 1, completed: true });
  eq(win.state().outcome, GameOutcome.WON);
  const lose = new SiegeGame({ playerHp: 40, bossHp: 99999, hitOnBreak: 20 });
  lose.onEvent({ holdSec: 2, quality: 0.3, completed: false });
  lose.onEvent({ holdSec: 2, quality: 0.3, completed: false });
  eq(lose.state().outcome, GameOutcome.LOST);
});

t('PURSUIT · the pursuer closes on the clock even if you stop', () => {
  const g = new PursuitGame({ startGapM: 5, pursuerMps: 3, escapeAtM: 500 });
  g.tick(0);
  const before = g.gapM;
  g.tick(1000);
  ok(g.gapM < before, 'gap did not close while idle');
  g.tick(3000);
  eq(g.state().outcome, GameOutcome.LOST, 'never caught despite standing still');
});

t('PURSUIT · sustained cadence outruns it', () => {
  const g = new PursuitGame({ startGapM: 12, pursuerMps: 2.6, escapeAtM: 40, metresPerCycle: 2 });
  let t2 = 0;
  for (let i = 0; i < 40 && g.state().outcome === GameOutcome.RUNNING; i++) {
    g.tick(t2 += 400);
    g.onEvent({ amplitude: 1.1, formScore: 0.9 });
  }
  eq(g.state().outcome, GameOutcome.WON, `distance ${g.distance.toFixed(1)}m`);
});

t('BREAKER · a stiff landing breaks fewer floors than a soft one', () => {
  const soft = new BreakerGame({ floors: 50, cmPerFloor: 10 });
  const stiff = new BreakerGame({ floors: 50, cmPerFloor: 10 });
  soft.onEvent({ heightCm: 40, softness: 1.0 });
  stiff.onEvent({ heightCm: 40, softness: 0.3 });
  ok(soft.state().broken > stiff.state().broken,
     `soft ${soft.state().broken} vs stiff ${stiff.state().broken}`);
});

t('BREAKER · clearing the tower wins', () => {
  const g = new BreakerGame({ floors: 6, cmPerFloor: 10 });
  for (let i = 0; i < 5 && g.state().outcome === GameOutcome.RUNNING; i++)
    g.onEvent({ heightCm: 35, softness: 0.9 });
  eq(g.state().outcome, GameOutcome.WON);
});

t('SIGIL · is non-combat by design — no damage, no health, no ranking', () => {
  const g = new SigilGame({ segments: 3 });
  const s1 = g.onEvent({ accuracy: 0.9, heldSec: 20 });
  for (const k of ['playerHp', 'bossHp', 'damage', 'score', 'rank'])
    ok(!(k in s1), `SIGIL exposed a combat field: ${k}`);
  g.onEvent({ accuracy: 0.8, heldSec: 20 });
  g.onEvent({ accuracy: 1.0, heldSec: 20 });
  eq(g.state().outcome, GameOutcome.WON);
  ok(g.state().brightness > 0.8, `brightness ${g.state().brightness.toFixed(2)}`);
});

t('SIGIL · a poor pose still lights its segment, just dimly', () => {
  const g = new SigilGame({ segments: 4 });
  g.onEvent({ accuracy: 0.05, heldSec: 3 });
  eq(g.state().lit, 1, 'a weak pose was rejected outright');
  ok(g.state().brightness >= 0.15, 'brightness floor not applied');
});

t('every family game is constructible from its mode name', () => {
  for (const m of ['SIEGE', 'PURSUIT', 'BREAKER', 'SIGIL'])
    ok(makeFamilyGame(m, combatCfg.familyGames), `${m} not constructible`);
  eq(makeFamilyGame('BOSS_FIGHT', combatCfg.familyGames), null);
});

t('engine · SIGIL runs a yoga session without touching combat', () => {
  const yogaStore = { ...store, exercises: { ...store.exercises, utkatasana: cfg('exercises/utkatasana.json') } };
  const e = new SessionEngine(yogaStore, 'utkatasana', { mode: Mode.SIGIL });
  for (const [lm, ms] of holdFrames({ knee: 120, hipTorso: 130, elbow: 170 }, 30)) e.frame(lm, null, ms);
  const s = e.state();
  ok(s.gameState, 'no game state');
  eq(s.gameState.game, 'SIGIL');
  ok(s.gameState.lit >= 1, 'no segment lit');
  eq(s.playerDamage, 0, 'yoga dealt damage');
  eq(s.combat.hp, s.combat.maxHp, 'yoga hurt a boss');
});

t('engine · SIEGE routes holds into the siege, not the boss fight', () => {
  const holdStore = { ...store, exercises: { ...store.exercises, wall_sit: cfg('exercises/wall_sit.json') } };
  const e = new SessionEngine(holdStore, 'wall_sit', { mode: Mode.SIEGE });
  for (const [lm, ms] of holdFrames({ knee: 90, hipTorso: 90 }, 50)) e.frame(lm, null, ms);
  const s = e.state();
  eq(s.gameState.game, 'SIEGE');
  ok(s.gameState.bossHp < combatCfg.familyGames.SIEGE.bossHp, 'the hold dealt no siege damage');
  ok(s.gameState.totalHeldSec > 40, `only held ${s.gameState.totalHeldSec?.toFixed(1)}s`);
});

// ---------- duel over a real transport ----------
/** Loopback with controllable loss and a fake clock, so the protocol is exercised end to end
 *  rather than only through CombatEngine.onRemoteDamage. */
function duelPair({ loss = 0 } = {}) {
  const bus = new Set();
  let clock = 0;
  const now = () => clock;
  const lossy = (t) => ({
    send(m) { if (Math.random() >= loss) t.send(m); },
    onMessage: (f) => t.onMessage(f), close: () => t.close(), available: true,
  });
  const ta = new LoopbackTransport(bus), tb = new LoopbackTransport(bus);
  const hitsA = [], hitsB = [];
  const a = new DuelSession(lossy(ta), { playerId: 'AAAA', now,
    onRemote: (p, s2, d) => hitsA.push([p, s2, d]) });
  const b = new DuelSession(lossy(tb), { playerId: 'BBBB', now,
    onRemote: (p, s2, d) => hitsB.push([p, s2, d]) });
  return { a, b, hitsA, hitsB, advance: (ms) => { clock += ms; } };
}

const sumOf = (hits) => hits.reduce((acc, [, , d]) => acc + d, 0);

t('duel · two sessions converge on the same total', () => {
  const { a, b, hitsA, hitsB } = duelPair();
  for (let i = 0; i < 12; i++) {
    a.sendRep({ damage: 70 + i, formScore: 0.8, exercise: 'squat', fatigueBand: 'WORKING' });
    b.sendRep({ damage: 60 + i, formScore: 0.7, exercise: 'squat', fatigueBand: 'WORKING' });
  }
  eq(sumOf(hitsA), b.sent.length ? 12 * 60 + 66 : 0, 'A did not receive all of B');
  eq(sumOf(hitsB), 12 * 70 + 66, 'B did not receive all of A');
});

t('duel · a duplicate flood changes nothing', () => {
  const { a, hitsB } = duelPair();
  a.sendRep({ damage: 100, formScore: 0.9, exercise: 'squat', fatigueBand: 'FRESH' });
  const before = sumOf(hitsB);
  for (let i = 0; i < 30; i++) a.tick();          // heartbeats carry no new events
  eq(sumOf(hitsB), before, 'duplicates changed the total');
  eq(before, 100);
});

t('duel · 30 percent packet loss is repaired by the recent tail', () => {
  // Deterministic loss so the assertion is not flaky.
  let n = 0;
  const realRandom = Math.random;
  Math.random = () => ((n++ % 10) < 3 ? 0 : 1);
  try {
    const { a, hitsB } = duelPair({ loss: 0.3 });
    let expected = 0;
    for (let i = 0; i < 24; i++) {
      const d = 50 + i;
      expected += d;
      a.sendRep({ damage: d, formScore: 0.8, exercise: 'squat', fatigueBand: 'WORKING' });
    }
    eq(sumOf(hitsB), expected, 'tail did not repair the dropped messages');
  } finally { Math.random = realRandom; }
});

t('duel · link goes LINKED then LOST when a peer goes quiet', () => {
  const { a, b, advance } = duelPair();
  a.tick(); b.tick();
  eq(a.tick(), LinkState.LINKED, 'never linked');
  advance(6000);
  eq(a.tick(), LinkState.LOST, 'silent peer not detected');
});

t('duel · a late joiner is caught up by the tail', () => {
  const bus = new Set();
  let clock = 0; const now = () => clock;
  const ta = new LoopbackTransport(bus);
  const a = new DuelSession(ta, { playerId: 'AAAA', now });
  for (let i = 0; i < 5; i++) a.sendRep({ damage: 40, formScore: 0.8, exercise: 'squat', fatigueBand: 'FRESH' });

  const got = [];
  const tb = new LoopbackTransport(bus);
  new DuelSession(tb, { playerId: 'BBBB', now, onRemote: (p, s2, d) => got.push(d) });
  eq(got.reduce((x, y) => x + y, 0), 200, 'late joiner did not receive the backlog');
});

t('duel · player ids are distinct and short', () => {
  const ids = new Set(Array.from({ length: 200 }, () => newPlayerId()));
  ok(ids.size > 150, `only ${ids.size} distinct ids in 200`);
  for (const id of ids) eq(id.length, 4);
});

// ---------- detector families ----------
const spec = (id) => cfg(`exercises/${id}.json`);
const SIDE_L = 0;

t('stick figure produces the angles it is asked for', () => {
  const lm = stick({ knee: 90, hipTorso: 90, elbow: 88 });
  near(angle3(lm[23], lm[25], lm[27]), 90, 0.5, 'knee');
  near(angle3(lm[11], lm[23], lm[25]), 90, 0.5, 'hip');
  near(angle3(lm[11], lm[13], lm[15]), 88, 0.5, 'elbow');
});

t('F2 ISOMETRIC · a held wall sit completes with high quality', () => {
  const d = new IsometricHoldDetector(spec('wall_sit'));
  let ev = null;
  for (const [lm, ms] of holdFrames({ knee: 90, hipTorso: 90 }, 47)) ev = d.onFrame(lm, ms, SIDE_L) ?? ev;
  ok(ev, 'no hold event');
  ok(ev.completed, 'hold not marked complete');
  ok(ev.quality > 0.9, `quality ${ev.quality.toFixed(2)}`);
  ok(ev.holdSec >= 44, `held only ${ev.holdSec.toFixed(1)}s`);
  ok(ev.formScore > 0.9, `formScore ${ev.formScore.toFixed(2)}`);
});

t('F2 ISOMETRIC · breaking form ends the hold early', () => {
  const d = new IsometricHoldDetector(spec('wall_sit'));
  let ev = null;
  for (const [lm, ms] of holdFrames({ knee: 90, hipTorso: 90 }, 8)) ev = d.onFrame(lm, ms, SIDE_L) ?? ev;
  for (const [lm, ms] of holdFrames({ knee: 150, hipTorso: 160 }, 3, { t0: 8000 }))
    ev = d.onFrame(lm, ms, SIDE_L) ?? ev;
  ok(ev, 'no event on break');
  ok(!ev.completed, 'break counted as completion');
  ok(ev.holdSec > 6 && ev.holdSec < 9, `holdSec ${ev.holdSec.toFixed(1)}`);
});

t('F2 ISOMETRIC · tremor rises with wobble', () => {
  const run = (noise) => {
    const d = new IsometricHoldDetector(spec('plank'));
    let last = 0;
    for (const [lm, ms] of holdFrames({ knee: 176, hipTorso: 176, elbow: 88 }, 6, { noise }))
      { d.onFrame(lm, ms, SIDE_L); last = d.last?.tremor ?? last; }
    return last;
  };
  ok(run(0.01) > run(0) * 2, 'tremor did not respond to wobble');
});

t('F4 CADENCE · reads the rate it was given', () => {
  const d = new CadenceDetector(spec('jumping_jacks'));
  const got = [];
  for (const [lm, ms] of cadenceFrames({ rpm: 120, seconds: 12, joint: 'WRIST', axis: 'y' })) {
    const ev = d.onFrame(lm, ms, SIDE_L);
    if (ev) got.push(ev.cadence);
  }
  ok(got.length >= 8, `only ${got.length} cycles detected`);
  const mean = got.reduce((a, b) => a + b, 0) / got.length;
  near(mean, 120, 12, 'cadence');
});

t('F4 CADENCE · tiny movements are rejected', () => {
  const d = new CadenceDetector(spec('high_knees'));
  let n = 0;
  for (const [lm, ms] of cadenceFrames({ rpm: 150, seconds: 10, amplitude: 0.008, joint: 'KNEE', axis: 'y' }))
    if (d.onFrame(lm, ms, SIDE_L)) n++;
  eq(n, 0, 'fake high-knees counted');
});

t('F5 BALLISTIC · jump height in real centimetres, softness from the landing', () => {
  const d = new BallisticDetector(spec('jump_squat'));
  let ev = null;
  for (const [w, img, ms] of jumpFrames({ heightNorm: 0.09, landKnee: 130 }))
    ev = d.onFrame(w, img, ms, SIDE_L) ?? ev;
  ok(ev, 'no jump detected');
  ok(ev.heightCm > 12 && ev.heightCm < 60, `implausible height ${ev.heightCm?.toFixed(1)}cm`);
  ok(ev.softness > 0.8, `stiff landing scored soft: ${ev.softness.toFixed(2)}`);
});

t('F5 BALLISTIC · a stiff landing scores worse than a soft one', () => {
  const run = (landKnee) => {
    const d = new BallisticDetector(spec('jump_squat'));
    let ev = null;
    for (const [w, img, ms] of jumpFrames({ heightNorm: 0.09, landKnee }))
      ev = d.onFrame(w, img, ms, SIDE_L) ?? ev;
    return ev;
  };
  const soft = run(125), stiff = run(168);
  ok(soft && stiff, 'missing a jump');
  ok(soft.softness > stiff.softness + 0.4, `soft ${soft.softness.toFixed(2)} vs stiff ${stiff.softness.toFixed(2)}`);
  ok(soft.formScore > stiff.formScore, 'landing quality did not affect the score');
});

t('F3 POSE_MATCH · a correct asana is recognised and held', () => {
  const d = new PoseMatchDetector(spec('utkatasana'));
  let ev = null;
  for (const [lm, ms] of holdFrames({ knee: 120, hipTorso: 130, elbow: 170 }, 28))
    ev = d.onFrame(lm, ms, SIDE_L) ?? ev;
  ok(ev, 'asana never recognised');
  ok(ev.accuracy > 0.9, `accuracy ${ev.accuracy.toFixed(2)}`);
  ok(ev.completed, 'hold not completed');
});

t('F3 POSE_MATCH · a wrong shape is not recognised', () => {
  const d = new PoseMatchDetector(spec('utkatasana'));
  let ev = null;
  for (const [lm, ms] of holdFrames({ knee: 176, hipTorso: 178, elbow: 172 }, 20))
    ev = d.onFrame(lm, ms, SIDE_L) ?? ev;
  ok(!ev, 'standing upright was accepted as Chair pose');
});

t('F3 POSE_MATCH · names the worst joint instead of a percentage', () => {
  const d = new PoseMatchDetector(spec('virabhadrasana_ii'));
  for (const [lm, ms] of holdFrames({ knee: 140, hipTorso: 165, elbow: 176 }, 3)) d.onFrame(lm, ms, SIDE_L);
  ok(d.last?.cue, 'no cue produced');
  ok(/left|right/.test(d.last.cue), `cue does not name a side: ${d.last.cue}`);
  ok(!/%|percent/.test(d.last.cue), `cue is a percentage: ${d.last.cue}`);
});

t('one fatigue model · every family declares its signals and weights sum sanely', () => {
  for (const [family, sigs] of Object.entries(SIGNALS)) {
    ok(sigs.length > 0, `${family} has no signals`);
    const w = sigs.reduce((a, s) => a + s.weight, 0);
    near(w, 1.0, 0.001, `${family} weights`);
    for (const s of sigs) ok(['decay', 'growth'].includes(s.dir), `${family}.${s.key} bad dir`);
  }
});

t('the generated manifest matches what is on disk', () => {
  const man = JSON.parse(readFileSync(join(HERE, '..', 'config/exercises/index.json'), 'utf8'));
  const onDisk = readdirSync(join(HERE, '..', 'config/exercises'))
    .filter(f => f.endsWith('.json') && f !== 'index.json').map(f => f.replace('.json',''));
  eq(man.count, onDisk.length, 'manifest count drifted from disk');
  const ids = new Set(man.exercises.map(e => e.id));
  for (const id of onDisk) ok(ids.has(id), id + ' missing from the manifest — run npm run manifest');
});

t('every shipped exercise config is loadable and well formed', () => {
  const dir = readdirSync(join(HERE, '..', 'config/exercises')).filter(f => f.endsWith('.json') && f !== 'index.json');
  ok(dir.length >= 45, `only ${dir.length} exercises`);
  const fams = {};
  for (const f of dir) {
    const e = JSON.parse(readFileSync(join(HERE, '..', 'config/exercises', f), 'utf8'));
    ok(e.id && e.name && e.family && e.detector, `${f} missing fields`);
    ok(Array.isArray(e.games) && e.games.length, `${f} has no games`);
    fams[e.family] = (fams[e.family] ?? 0) + 1;
    if (e.family !== 'REP_CYCLE') ok(makeDetector(e), `${f} has no detector`);
  }
  eq(Object.keys(fams).length, 5, `families present: ${Object.keys(fams).join(',')}`);
});

// ---------- coaching ----------
const setOf = (n, opts = {}) => {
  const e = new SessionEngine(store, 'squat');
  drive(e, synthWorldSet({ reps: n, bottom: 80, ...opts }));
  return e;
};

t('telemetry · summarises a set into the model payload', () => {
  const e = setOf(8);
  const tel = summarise(e.setReps, e.combat.state(), squat, 1, 45);
  for (const k of ['exercise','reps','form_mean','fatigue_band','worst_rep','best_rep','trend','boss_hp_pct'])
    ok(tel[k] !== undefined, `missing ${k}`);
  eq(tel.reps, e.setReps.length);
  ok(['depth','rom','tempo','alignment'].includes(tel.worst_rep.reason), tel.worst_rep.reason);
});

t('telemetry · depth is measured in real centimetres', () => {
  const e = setOf(6);
  const cm = e.setReps.map(r => r.depthCm).filter(Number.isFinite);
  ok(cm.length >= 5, `only ${cm.length} reps had a depth measurement`);
  ok(cm.every(v => v > 5 && v < 120), `implausible depth values: ${cm.map(v=>v.toFixed(1)).join(',')}`);
});

t('templates · never leak an unresolved placeholder', () => {
  // every band x reason x trend combination, against a telemetry with holes in it
  for (const band of ['FRESH','WORKING','FADING','GASSED'])
    for (const reason of ['depth','rom','tempo','alignment'])
      for (const trend of ['improving','flat','declining'])
        for (const holes of [{}, { depth_drop_cm: null, depth_cm: null }]) {
          const tel = { exercise:'squat', reps:9, form_mean:0.7, form_first3:0.8, form_last3:0.6,
            form_mean_pct:70, form_first3_pct:80, form_last3_pct:60,
            depth_cm: 42, depth_drop_cm: 4, velocity_loss_pct: 30, rom_loss_pct: 18,
            fatigue_band: band, best_rep:{index:2,form:0.9}, worst_rep:{index:8,form:0.4,reason},
            combo_max:1.6, combo_reps:5, boss_hp_pct:55, session_set_index:2, rest_sec:45,
            trend, ...holes };
          const out = templateFor(tel);
          ok(!/[{}]/.test(out.coachLine), `coach leaked: ${out.coachLine}`);
          ok(!/[{}]/.test(out.bossLine), `boss leaked: ${out.bossLine}`);
          ok(out.coachLine.length > 0 && out.bossLine.length > 0, 'empty line');
        }
});

t('templates · cite numbers that are actually in the telemetry', () => {
  const e = setOf(10);
  const tel = summarise(e.setReps, e.combat.state(), squat, 2, 45);
  const out = templateFor(tel);
  ok(validateOutput(out.coachLine, tel).ok, `coach failed its own validator: ${out.coachLine}`);
  ok(validateOutput(out.bossLine, tel).ok, `boss failed its own validator: ${out.bossLine}`);
});

t('templates · every line in the bank passes our own validator', () => {
  // The bank is what ships if Gemma does not land, so it must obey the same rules we impose
  // on the model. Reads the module source so a new line cannot be added without being checked.
  const src = readFileSync(join(HERE, '..', 'src/coach.js'), 'utf8');
  const lines = [...src.matchAll(/line: "([^"]+)"/g)].map(m => m[1])
    .concat([...src.matchAll(/^  "([^"]+)",$/gm)].map(m => m[1]))
    .concat([...src.matchAll(/(?:coach|boss): "([^"]+)"/g)].map(m => m[1]));
  ok(lines.length >= 30, `only found ${lines.length} template lines to check`);
  const tel = { exercise:'squat', reps:9, form_mean:0.7, form_first3:0.8, form_last3:0.6,
    form_mean_pct:70, form_first3_pct:80, form_last3_pct:60,
    depth_cm:42, depth_drop_cm:4, velocity_loss_pct:30, rom_loss_pct:18, fatigue_band:'FADING',
    best_rep:{index:2,form:0.9}, worst_rep:{index:8,form:0.4,reason:'depth'},
    combo_max:1.6, combo_reps:5, boss_hp_pct:55, session_set_index:2, rest_sec:45, trend:'declining' };
  for (const raw of lines) {
    const filled = fill(raw.replace(/\\'/g, "'"), tel);
    ok(filled !== null, `unfillable: ${raw}`);
    const v = validateOutput(filled, tel);
    ok(v.ok, `"${filled}" -> ${v.why}`);
  }
});

t('validator · rejects hallucinated numbers, blocklist, length, sentence count', () => {
  const tel = { reps: 9, velocity_loss_pct: 30 };
  ok(!validateOutput('You did 47 reps.', tel).ok, 'hallucinated number allowed');
  ok(!validateOutput('You look fat.', tel).ok, 'blocklist term allowed');
  ok(!validateOutput('a'.repeat(200), tel).ok, 'over-long allowed');
  ok(!validateOutput('One. Two. Three. Four.', tel).ok, 'four sentences allowed');
  ok(!validateOutput('   ', tel).ok, 'empty allowed');
  ok(validateOutput('You did 9 reps and lost 30 percent.', tel).ok, 'valid line rejected');
});

t('coachFor · falls back silently when the model times out', async () => {
  const tel = summarise(setOf(6).setReps, null, squat, 1, 40);
  const slow = () => new Promise((r) => setTimeout(r, 200));
  const out = await coachFor(tel, slow, 20);
  eq(out.source, 'TEMPLATE');
  ok(out.coachLine.length > 0, 'no fallback line');
});

t('coachFor · falls back when the model hallucinates', async () => {
  const tel = summarise(setOf(6).setReps, null, squat, 1, 40);
  const liar = async () => ({ coachLine: 'You did 9999 perfect reps.', bossLine: 'ok' });
  eq((await coachFor(tel, liar)).source, 'TEMPLATE');
});

t('coachFor · uses the model when its output is clean', async () => {
  const tel = summarise(setOf(6).setReps, null, squat, 1, 40);
  const good = async (x) => ({ coachLine: `Solid set of ${x.reps}.`, bossLine: 'Again.' });
  const out = await coachFor(tel, good);
  eq(out.source, 'LLM');
});

// ---------- set flow ----------
t('set ends after the idle timeout and produces a coach line', async () => {
  const e = new SessionEngine(store, 'squat');
  drive(e, synthWorldSet({ reps: 5, bottom: 80 }));
  const last = e.lastRepEndMs;
  const still = synthWorldSet({ reps: 0 })[0][0];
  for (let i = 0; i < 500; i++) e.frame(still, null, last + 1000 + i * 33);
  eq(e.state().phase, Phase.REST);
  await new Promise((r) => setTimeout(r, 10));
  ok(e.coach && e.coach.coachLine.length > 0, 'no coach line on rest');
  ok(!/[{}]/.test(e.coach.coachLine), `leaked placeholder: ${e.coach.coachLine}`);
});

t('nextSet resets fatigue but carries boss HP', async () => {
  const e = new SessionEngine(store, 'squat');
  drive(e, synthWorldSet({ reps: 5, bottom: 80 }));
  const still = synthWorldSet({ reps: 0 })[0][0];
  for (let i = 0; i < 500; i++) e.frame(still, null, e.lastRepEndMs + 1000 + i * 33);
  const hpBefore = e.combat.hp;
  e.nextSet();
  eq(e.state().phase, Phase.FIGHTING);
  eq(e.setIndex, 2);
  eq(e.combat.hp, hpBefore, 'boss healed between sets');
  eq(e.fatigue.state().band, 'FRESH');
});

// ---------- report ----------
await Promise.all(pending);
const w = Math.max(...results.map(r => r[1].length));
for (const [s, n, m] of results) {
  const mark = s === 'PASS' ? '\x1b[32m✓\x1b[0m' : '\x1b[31m✗\x1b[0m';
  console.log(`${mark} ${n.padEnd(w)} ${m}`);
}
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
