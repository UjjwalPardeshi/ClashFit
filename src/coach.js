// Coaching: telemetry summarising, the deterministic template bank, and the output validator.
//
// The template bank is built FIRST and must be good enough to ship alone. If Gemma does not
// land at the event, this is what speaks — and a judge watching for forty seconds must not be
// able to tell which path fired. That is the design requirement, not an aspiration.
// docs/06-AI-COACH-SPEC.md §3, §5, §6

import { Band } from './fatigue.js';
import { summariseAsymmetry } from './asymmetry.js';

// ---------------------------------------------------------------- telemetry

/** Compact payload for the model — ~150 tokens. The model never sees raw landmarks.
 *  Note worst_rep.reason is computed by us: we do not ask the model to diagnose, we ask it
 *  to phrase a diagnosis we already made. That is what keeps the output truthful. */
export function summarise(reps, combatState, exercise, setIndex = 1) {
  if (!reps.length) {
    return { exercise: exercise.id, reps: 0, fatigue_band: Band.FRESH,
             session_set_index: setIndex, boss_hp_pct: Math.round((combatState?.hpPct ?? 1) * 100) };
  }
  const mean = (arr, k) => arr.reduce((a, r) => a + r[k], 0) / arr.length;
  const first3 = reps.slice(0, 3);
  const last3 = reps.slice(-3);
  const best = reps.reduce((a, r) => (r.formScore > a.formScore ? r : a));
  const worst = reps.reduce((a, r) => (r.formScore < a.formScore ? r : a));
  const f = reps[reps.length - 1].fatigue;

  const depthFirst = mean(first3, 'depthCm');
  const depthLast = mean(last3, 'depthCm');
  const drop = Number.isFinite(depthFirst) && Number.isFinite(depthLast)
    ? Math.max(0, depthFirst - depthLast) : null;

  return {
    exercise: exercise.id,
    reps: reps.length,
    form_mean: round2(mean(reps, 'formScore')),
    form_first3: round2(mean(first3, 'formScore')),
    form_last3: round2(mean(last3, 'formScore')),
    form_mean_pct: Math.round(mean(reps, 'formScore') * 100),
    form_first3_pct: Math.round(mean(first3, 'formScore') * 100),
    form_last3_pct: Math.round(mean(last3, 'formScore') * 100),
    depth_cm: drop !== null ? Math.round(depthLast) : null,
    depth_drop_cm: drop !== null ? Math.round(drop) : null,
    velocity_loss_pct: Math.round((f?.velocityLoss ?? 0) * 100),
    rom_loss_pct: Math.round((f?.romLoss ?? 0) * 100),
    fatigue_band: f?.band ?? Band.FRESH,
    best_rep: { index: best.repIndex, form: round2(best.formScore) },
    worst_rep: { index: worst.repIndex, form: round2(worst.formScore), reason: worst.reason },
    combo_max: Math.max(...reps.map((r) => Math.round((r.combo ?? 1) * 100) / 100)),
    combo_reps: longestStreak(reps),
    boss_hp_pct: Math.round((combatState?.hpPct ?? 1) * 100),
    session_set_index: setIndex,
    trend: trendOf(reps),
    // The model gets the ratio, never a verdict. It phrases an observation we measured.
    asymmetry_pct: (() => { const a = summariseAsymmetry(reps); return a.enough && a.consistent ? Math.round(a.deficitPct) : null; })(),
    weaker_side: (() => { const a = summariseAsymmetry(reps); return a.enough && a.consistent ? a.weakerSide : null; })(),
  };
}

function trendOf(reps) {
  if (reps.length < 4) return 'flat';
  const a = reps.slice(0, Math.ceil(reps.length / 2));
  const b = reps.slice(-Math.ceil(reps.length / 2));
  const m = (x) => x.reduce((s, r) => s + r.formScore, 0) / x.length;
  const d = m(b) - m(a);
  return d > 0.05 ? 'improving' : d < -0.05 ? 'declining' : 'flat';
}

function longestStreak(reps) {
  let best = 0, cur = 0;
  for (const r of reps) { if (r.formScore >= 0.75) { cur++; best = Math.max(best, cur); } else cur = 0; }
  return best;
}

const round2 = (v) => Math.round(v * 100) / 100;

// ---------------------------------------------------------------- templates

/** Keyed on (fatigue_band, worst reason, trend). Placeholders fill from the same telemetry the
 *  model would get, so the fallback cites real numbers too. docs/reference/PROMPT-PACK.md */
const COACH = [
  { band: 'FRESH',   reason: '*',        trend: 'improving', line: "Clean — {combo_reps} in a row. It hasn't felt anything yet." },
  { band: 'FRESH',   reason: 'depth',    trend: '*',         line: "You're strong enough to go lower. Reset and drive deeper." },
  { band: 'FRESH',   reason: 'tempo',    trend: '*',         line: "You're rushing the way down. Give it a full second." },
  { band: 'FRESH',   reason: 'alignment',trend: '*',         line: "Watch the knee track on rep {worst_index}. Everything else was clean." },
  { band: 'FRESH',   reason: '*',        trend: '*',         line: "Holding at {form_mean_pct} percent across {reps} reps. Keep it there." },

  { band: 'WORKING', reason: 'depth',    trend: 'declining', line: "Depth is slipping — {depth_drop_cm} centimetres short of your first reps. Reset and go lower." },
  { band: 'WORKING', reason: 'depth',    trend: '*',         line: "Consistent depth across {reps} reps. That's the hard part." },
  { band: 'WORKING', reason: 'rom',      trend: '*',         line: "Range is down {rom_loss_pct} percent. Shorten the set before the form goes." },
  { band: 'WORKING', reason: 'tempo',    trend: '*',         line: "You're speeding up as you tire. Slow the eccentric back down." },
  { band: 'WORKING', reason: 'alignment',trend: '*',         line: "Knees drifted on the last two. Push them out as you stand." },
  { band: 'WORKING', reason: '*',        trend: 'improving', line: "That got better as you went — {form_last3_pct} percent on the last three." },
  { band: 'WORKING', reason: '*',        trend: '*',         line: "{reps} reps at {form_mean_pct} percent. Velocity is down {velocity_loss_pct} percent." },

  { band: 'FADING',  reason: 'depth',    trend: '*',         line: "Real fatigue now — {depth_drop_cm} centimetres of depth gone." },
  { band: 'FADING',  reason: 'rom',      trend: '*',         line: "Range is down {rom_loss_pct} percent since rep one. Finish it." },
  { band: 'FADING',  reason: 'tempo',    trend: '*',         line: "You're dropping into the bottom instead of controlling it. Control the descent." },
  { band: 'FADING',  reason: 'alignment',trend: '*',         line: "Form is going before your strength is. Keep it honest." },
  { band: 'FADING',  reason: '*',        trend: '*',         line: "Velocity is down {velocity_loss_pct} percent — that's the set talking." },

  { band: 'GASSED',  reason: 'depth',    trend: '*',         line: "You've lost {depth_drop_cm} centimetres and that's honest. Finish it." },
  { band: 'GASSED',  reason: '*',        trend: '*',         line: "That's real fatigue, not weakness. Four more reps ends this." },
  { band: 'GASSED',  reason: '*',        trend: 'declining', line: "Velocity is down {velocity_loss_pct} percent and you've done the work." },
];

const BOSS = [
  "Your knees are negotiating. I do not negotiate.",
  "{depth_drop_cm} centimetres. That is the distance between us.",
  "You kept the pace. Briefly.",
  "I have counted every one of them. So has the floor.",
  "Your first three reps were a different person.",
  "Slower, and again. I have time.",
  "Range is a promise you stopped keeping.",
  "You are getting quicker. That is not the same as getting better.",
  "Rest. I will still be here at {boss_hp_pct} percent.",
  "That one counted. Barely.",
  "Your tempo is drifting. Mine does not.",
  "Something in you gave up at rep {worst_index}. Find it.",
  "You are tired and I am a machine. Guess how this ends.",
  "Fine. That one hurt.",
];

const ASYM = {
  coach: "Your {weaker_side} side moved through {asymmetry_pct} percent less range than the other, across the whole set.",
  boss: "One of your sides is carrying the other. I noticed.",
};

const SPECIAL = {
  zero: { coach: "Nothing counted that time. Check your framing and go again.", boss: "I felt nothing." },
  first: { coach: "Baseline set. {reps} reps at {form_mean_pct} percent — that's the number everything else is measured against.",
           boss: "Noted. I will remember that number." },
  lowHp: { coach: "It's at {boss_hp_pct} percent. One more set.", boss: "You are closer than I would like." },
};

/** Deterministic given the telemetry, except for the boss line, which rotates by set index so
 *  a demo across several sets does not repeat itself. */
export function templateFor(t) {
  if (!t.reps) return out(SPECIAL.zero.coach, SPECIAL.zero.boss, t);

  // Priority order matters. Real fatigue always outranks a progress nudge — it is the thing we
  // uniquely measure, and it is the thing the player most needs to hear.
  const tired = t.fatigue_band === Band.FADING || t.fatigue_band === Band.GASSED;

  // A consistent bilateral lean outranks a depth or tempo nudge. Depth you can fix next rep; a
  // side carrying the other is the thing that leads somewhere worse, and it is the observation a
  // physiotherapist would actually want. It still yields to real fatigue, which is more urgent.
  if (!tired && t.asymmetry_pct != null && t.asymmetry_pct >= 10)
    return out(ASYM.coach, ASYM.boss, t);

  if (!tired && t.session_set_index === 1 && t.fatigue_band === Band.FRESH)
    return out(SPECIAL.first.coach, pickBoss(t), t);
  if (!tired && t.boss_hp_pct <= 20) return out(SPECIAL.lowHp.coach, SPECIAL.lowHp.boss, t);

  const reason = t.worst_rep?.reason ?? '*';
  const match =
    COACH.find((c) => c.band === t.fatigue_band && c.reason === reason && c.trend === t.trend) ??
    COACH.find((c) => c.band === t.fatigue_band && c.reason === reason && c.trend === '*') ??
    COACH.find((c) => c.band === t.fatigue_band && c.reason === '*' && c.trend === t.trend) ??
    COACH.find((c) => c.band === t.fatigue_band && c.reason === '*' && c.trend === '*') ??
    COACH[COACH.length - 1];

  return out(match.line, pickBoss(t), t);
}

function pickBoss(t) {
  const usable = BOSS.filter((l) => fillable(l, t));
  return usable[(t.session_set_index + t.reps) % usable.length] ?? BOSS[0];
}

function fillable(line, t) {
  return [...line.matchAll(/\{(\w+)\}/g)].every(([, k]) => resolve(k, t) !== null);
}

function resolve(key, t) {
  const map = {
    asymmetry_pct: t.asymmetry_pct, weaker_side: t.weaker_side,
    reps: t.reps, form_mean: t.form_mean, form_last3: t.form_last3, form_first3: t.form_first3,
    form_mean_pct: t.form_mean_pct, form_last3_pct: t.form_last3_pct, form_first3_pct: t.form_first3_pct,
    depth_cm: t.depth_cm, depth_drop_cm: t.depth_drop_cm,
    velocity_loss_pct: t.velocity_loss_pct, rom_loss_pct: t.rom_loss_pct,
    combo_reps: t.combo_reps, boss_hp_pct: t.boss_hp_pct,
    worst_index: t.worst_rep?.index, best_index: t.best_rep?.index,
  };
  const v = map[key];
  return v === undefined || v === null || Number.isNaN(v) ? null : v;
}

/** An unresolved placeholder on screen in front of a jury is worse than no coach at all,
 *  so fill() drops any line it cannot complete rather than rendering a brace. */
export function fill(line, t) {
  if (!fillable(line, t)) return null;
  return line.replace(/\{(\w+)\}/g, (_, k) => String(resolve(k, t)));
}

function out(coachLine, bossLine, t) {
  return {
    coachLine: fill(coachLine, t) ?? "Good set. Go again when you're ready.",
    bossLine: fill(bossLine, t) ?? "Again.",
    source: 'TEMPLATE',
  };
}

// ---------------------------------------------------------------- validation

const BLOCK = /\b(weight|fat|skinny|lazy|calorie|calories|diet|obese|fail(ed|ure)?)\b/i;

/** Never trust generated text straight to screen. Rejection is silent — the template fires and
 *  the player sees no difference. Log the rate; if it is high on the day, lower temperature in
 *  config rather than debugging the model at hour 22. docs/06-AI-COACH-SPEC.md §5 */
export function validateOutput(text, telemetry) {
  if (!text || !text.trim()) return { ok: false, why: 'empty' };
  const s = text.trim();
  if (s.length > 180) return { ok: false, why: 'too long' };
  if ((s.match(/[.!?]+(?=\s|$)/g) ?? []).length > 2) return { ok: false, why: 'more than two sentences' };
  if (BLOCK.test(s)) return { ok: false, why: 'blocklist term' };

  const allowed = new Set(
    JSON.stringify(telemetry).match(/\d+/g)?.map(Number) ?? []
  );
  for (const n of s.match(/\d+/g) ?? []) {
    if (!allowed.has(Number(n))) return { ok: false, why: `hallucinated number ${n}` };
  }
  return { ok: true };
}

/** The seam Gemma slots into at the event. Today it always falls through to templates —
 *  which is exactly the behaviour we want if the model does not land. */
export async function coachFor(telemetry, llm = null, timeoutMs = 5000) {
  if (llm) {
    try {
      const res = await Promise.race([
        llm(telemetry),
        new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), timeoutMs)),
      ]);
      const a = validateOutput(res?.coachLine, telemetry);
      const b = validateOutput(res?.bossLine, telemetry);
      if (a.ok && b.ok) return { ...res, source: 'LLM' };
    } catch { /* fall through, silently */ }
  }
  return templateFor(telemetry);
}
