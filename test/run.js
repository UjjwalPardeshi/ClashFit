import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { angle3 } from '../src/geometry.js';
import { RepStateMachine } from '../src/repFsm.js';
import { scoreRep, verdict, clamp01 } from '../src/formScorer.js';
import { FatigueEstimator, Band } from '../src/fatigue.js';
import { CombatEngine, ComboTracker } from '../src/combat.js';
import { synthRep, synthSet } from './synth.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const cfg = (p) => JSON.parse(readFileSync(join(HERE, '..', 'config', p), 'utf8'));
const pose = cfg('pose.json');
const combatCfg = cfg('combat.json');
const squat = cfg('exercises/squat.json');
const calf = cfg('exercises/calf_raise.json');

let pass = 0, fail = 0;
const results = [];
function t(name, fn) {
  try { fn(); pass++; results.push(['PASS', name, '']); }
  catch (e) { fail++; results.push(['FAIL', name, e.message]); }
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

t('F2 · shallow squats mean formScore < 0.50', () => {
  // shallow AND rushed — which is how a bad rep actually looks
  const reps = runFsm(synthSet({ reps: 10, top: 170, bottom: 118, eccSec: 0.45, pauseSec: 0.05 }), squat, 170);
  ok(reps.length === 10, `expected 10 reps, got ${reps.length}`);
  const m = reps.map(r => scoreRep(r, squat, romBaseline, { kneeOffset: 0.40 }).formScore)
               .reduce((a,b)=>a+b,0) / reps.length;
  ok(m < 0.50, `mean ${m.toFixed(3)}`);
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

// ---------- report ----------
const w = Math.max(...results.map(r => r[1].length));
for (const [s, n, m] of results) {
  const mark = s === 'PASS' ? '\x1b[32m✓\x1b[0m' : '\x1b[31m✗\x1b[0m';
  console.log(`${mark} ${n.padEnd(w)} ${m}`);
}
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
