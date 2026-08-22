// Local persistence and progression.
//
// Everything stays on the device, which is the same guarantee as the rest of the product. In the
// Android build this is Room; here it is localStorage with the same shape, so the schema in
// docs/08-DATA-MODEL.md is what gets ported rather than reinvented.
//
// Per-rep telemetry is kept because it is what the summary chart, the coach payload, and any
// judge asking "what did you actually measure" all read from. Older sessions are compacted to
// their aggregates so a phone never fills up.
//
// The progression rules here are deliberate and contrarian in one place: a rest day EARNS
// consistency rather than breaking it. Punitive streaks are a leading cause of churn in fitness
// apps, and breaking someone's streak because they had flu is a product choosing to lose a user.
// docs/23-META-PROGRESSION.md §6

const KEY = 'clashfit:v1';
const KEEP_FULL_SESSIONS = 20;
const DAY_MS = 86_400_000;
const MAX_FREEZE_GAP_DAYS = 3;

const emptyState = () => ({
  version: 1,
  profile: { createdAt: null, calibration: {}, preferences: {} },
  sessions: [],
  streak: { current: 0, best: 0, lastDayKey: null, freezes: 1, restDaysUsedThisWeek: 0, weekKey: null },
  bests: {},                 // exerciseId -> { reps, formScore, depthCm, heightCm, holdSec }
  ladder: {},                // ladderId -> current rung index
});

const dayKey = (ms) => new Date(ms).toISOString().slice(0, 10);
const weekKey = (ms) => {
  const d = new Date(ms);
  const onejan = new Date(d.getUTCFullYear(), 0, 1);
  const week = Math.ceil(((d - onejan) / DAY_MS + onejan.getDay() + 1) / 7);
  return `${d.getUTCFullYear()}-W${week}`;
};

export class Store {
  /** @param backend anything with getItem/setItem — injected so it is testable without a DOM. */
  constructor(backend = (typeof localStorage !== 'undefined' ? localStorage : null)) {
    this.backend = backend;
    this.state = this.#read();
  }

  #read() {
    if (!this.backend) return emptyState();
    try {
      const raw = this.backend.getItem(KEY);
      if (!raw) return emptyState();
      const parsed = JSON.parse(raw);
      return { ...emptyState(), ...parsed };
    } catch {
      return emptyState();          // corrupt storage must never take the app down
    }
  }

  #write() {
    if (!this.backend) return false;
    try { this.backend.setItem(KEY, JSON.stringify(this.state)); return true; }
    catch { return false; }         // quota or private mode — the fight still works
  }

  // ---------------------------------------------------------------- calibration

  saveCalibration(exerciseId, { topRef, romBaseline }) {
    this.state.profile.createdAt ??= Date.now();
    this.state.profile.calibration[exerciseId] = { topRef, romBaseline, at: Date.now() };
    this.#write();
  }

  calibrationFor(exerciseId) { return this.state.profile.calibration[exerciseId] ?? null; }

  // ---------------------------------------------------------------- sessions

  /** @param session { startedAt, endedAt, mode, exerciseId, outcome, reps } */
  saveSession(session) {
    const record = {
      id: `s${this.state.sessions.length + 1}_${session.endedAt ?? Date.now()}`,
      startedAt: session.startedAt ?? Date.now(),
      endedAt: session.endedAt ?? Date.now(),
      mode: session.mode ?? 'BOSS_FIGHT',
      exerciseId: session.exerciseId ?? 'squat',
      outcome: session.outcome ?? null,
      totalReps: session.reps?.length ?? 0,
      totalDamage: (session.reps ?? []).reduce((a, r) => a + (r.damage ?? 0), 0),
      formMean: session.reps?.length
        ? +(session.reps.reduce((a, r) => a + r.formScore, 0) / session.reps.length).toFixed(3) : 0,
      peakFatigue: session.reps?.length ? session.reps[session.reps.length - 1].fatigue?.value ?? 0 : 0,
      peakBand: session.reps?.length ? session.reps[session.reps.length - 1].fatigue?.band ?? 'FRESH' : 'FRESH',
      reps: (session.reps ?? []).map(compactRep),
    };
    this.state.sessions.push(record);
    this.#compact();
    this.#updateStreak(record.endedAt);
    this.#updateBests(record);
    this.#write();
    return record;
  }

  /** Keep full telemetry for recent sessions; older ones survive as aggregates only. */
  #compact() {
    const s = this.state.sessions;
    for (let i = 0; i < s.length - KEEP_FULL_SESSIONS; i++) {
      if (s[i].reps) { s[i].repCount = s[i].reps.length; delete s[i].reps; }
    }
  }

  get sessions() { return this.state.sessions; }
  get lastSession() { return this.state.sessions[this.state.sessions.length - 1] ?? null; }

  // ---------------------------------------------------------------- streaks

  #updateStreak(atMs) {
    const st = this.state.streak;
    const today = dayKey(atMs);
    const wk = weekKey(atMs);
    if (st.weekKey !== wk) { st.weekKey = wk; st.restDaysUsedThisWeek = 0; }
    if (st.lastDayKey === today) return;

    if (st.lastDayKey === null) { st.current = 1; }
    else {
      const gapDays = Math.round((Date.parse(today) - Date.parse(st.lastDayKey)) / DAY_MS);
      if (gapDays === 1) st.current += 1;
      else if (gapDays === 2 && st.restDaysUsedThisWeek < 1) {
        // One protected rest day per week. Rest is training, and breaking a streak because
        // someone took a day off is how fitness apps lose people.
        st.restDaysUsedThisWeek += 1;
        st.current += 1;
      } else if (gapDays > 1 && gapDays <= MAX_FREEZE_GAP_DAYS && st.freezes > 0) {
        // A freeze covers a missed day or two. It does not cover a month away — claiming an
        // unbroken streak someone did not earn is dishonest, and they will notice.
        st.freezes -= 1;
        st.current += 1;
      } else st.current = 1;
    }
    st.lastDayKey = today;
    if (st.current > st.best) st.best = st.current;
    if (st.current > 0 && st.current % 10 === 0) st.freezes = Math.min(3, st.freezes + 1);
  }

  /** A broken streak never shows a number you lost. */
  streakLabel() {
    const st = this.state.streak;
    if (!st.current) return 'Back at it';
    return st.current === 1 ? 'Day 1' : `${st.current} day streak`;
  }

  get streak() { return { ...this.state.streak }; }

  // ---------------------------------------------------------------- personal bests

  #updateBests(record) {
    const b = (this.state.bests[record.exerciseId] ??= {});
    const better = (k, v) => { if (Number.isFinite(v) && (!(k in b) || v > b[k])) { b[k] = v; return true; } return false; };
    better('reps', record.totalReps);
    better('formScore', record.formMean);
    for (const r of record.reps ?? []) {
      better('depthCm', r.depthCm);
      better('heightCm', r.heightCm);
      better('holdSec', r.holdSec);
    }
  }

  bestsFor(exerciseId) { return this.state.bests[exerciseId] ?? {}; }

  /** What actually improved, phrased for the coach. Nothing to say is a valid answer. */
  personalBestsIn(record) {
    const b = this.state.bests[record.exerciseId] ?? {};
    const out = [];
    if (record.totalReps >= (b.reps ?? 0)) out.push({ key: 'reps', value: record.totalReps });
    if (record.formMean >= (b.formScore ?? 0)) out.push({ key: 'form', value: record.formMean });
    return out;
  }

  // ---------------------------------------------------------------- trends

  /** Per-session series for one exercise, oldest first — what the trend chart reads. */
  trend(exerciseId, limit = 20) {
    return this.state.sessions
      .filter((s) => s.exerciseId === exerciseId)
      .slice(-limit)
      .map((s) => ({ at: s.endedAt, reps: s.totalReps, form: s.formMean, peakFatigue: s.peakFatigue }));
  }

  // ---------------------------------------------------------------- ladders

  /**
   * Promotion needs a sustained rolling average, not one good day. Demotion is quiet and is never
   * phrased as failure — it just offers a lower rung. docs/19-EXERCISE-LIBRARY.md §7
   */
  ladderCheck(ladderId, rungs, exerciseId, { promoteAt = 0.85, demoteBelow = 0.5, window = 3 } = {}) {
    const idx = this.state.ladder[ladderId] ?? rungs.indexOf(exerciseId);
    if (idx < 0) return { rung: exerciseId, changed: false };
    const recent = this.trend(exerciseId, window);
    if (recent.length < window) return { rung: rungs[idx], changed: false };
    const mean = recent.reduce((a, r) => a + r.form, 0) / recent.length;

    let next = idx;
    if (mean >= promoteAt && idx < rungs.length - 1) next = idx + 1;
    else if (mean < demoteBelow && idx > 0) next = idx - 1;

    if (next !== idx) { this.state.ladder[ladderId] = next; this.#write(); }
    return { rung: rungs[next], changed: next !== idx, promoted: next > idx, mean };
  }

  // ---------------------------------------------------------------- export / reset

  exportJson() { return JSON.stringify(this.state, null, 2); }

  clear() { this.state = emptyState(); this.#write(); }
}

/** Only what the summary, the coach and a trend actually read. */
function compactRep(r) {
  const out = {
    i: r.repIndex, form: round3(r.formScore), verdict: r.verdict,
    damage: r.damage ?? 0, fatigue: round3(r.fatigue?.value ?? 0), band: r.fatigue?.band ?? 'FRESH',
  };
  if (Number.isFinite(r.depthCm)) out.depthCm = round1(r.depthCm);
  if (Number.isFinite(r.heightCm)) out.heightCm = round1(r.heightCm);
  if (Number.isFinite(r.holdSec)) out.holdSec = round1(r.holdSec);
  return out;
}
const round3 = (v) => Math.round(v * 1000) / 1000;
const round1 = (v) => Math.round(v * 10) / 10;

export const LADDERS = {
  PUSH: ['wall_push_up', 'knee_push_up', 'push_up', 'pike_push_up'],
  SQUAT: ['chair_squat', 'squat', 'lunge', 'jump_squat'],
  CORE: ['glute_bridge', 'sit_up', 'hollow_hold'],
  CARDIO: ['torso_twists', 'jumping_jacks', 'high_knees', 'burpee'],
  YOGA: ['tadasana', 'utkatasana', 'vrikshasana', 'natarajasana'],
};
