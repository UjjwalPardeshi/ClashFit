// Camera + MediaPipe wiring and the render loop.
// Loaded from CDN so the prototype runs with no build step and no npm install — one command
// (`npm start`) and a browser. If the pinned CDN version ever 404s, swap to @latest.

import { PoseLandmarker, FilesetResolver } from
  'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@latest/vision_bundle.mjs';

import { loadConfig, reloadInto } from './config.js';
import { SessionEngine, Phase, Mode } from './engine.js';
import { ghostFromReps, parseGhost, downloadGhost } from './ghost.js';
import { Audio } from './audio.js';
import { Speech } from './speech.js';
import { drawSummary, exportPng, exportCsv } from './summary.js';
import { DuelSession, BroadcastChannelTransport, LinkState, newPlayerId } from './duel.js';
import { Haptics } from './haptics.js';
import { Voice } from './voice.js';
import { Roster, RosterMode, Circuit, DEFAULT_CIRCUIT } from './roster.js';
import { Store } from './store.js';
import { BoxBreathing, recoveryFraction } from './breathing.js';
import { preflight, summariseChecks, Status } from './preflight.js';
import { encodeChallenge, decodeChallenge, describeChallenge, challengeFromSession } from './challenge.js';
import { Renderer, C } from './render.js';
import { TraceRecorder } from './trace.js';

const MODEL =
  'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task';
const WASM = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@latest/wasm';

const $ = (id) => document.getElementById(id);
const el = {
  stage: $('stage'), cam: $('cam'), boot: $('boot'), err: $('err'),
  phaseText: $('phaseText'), dot: $('dot'), cue: $('cue'),
  reps: $('reps'), band: $('band'), verdict: $('verdict'), combo: $('combo'),
  pips: document.querySelectorAll('#pips .pip'), dbg: $('dbg'),
  exercise: $('exercise'), start: $('start'), casual: $('casual'), reset: $('reset'),
  reload: $('reload'), rec: $('rec'), dl: $('dl'), dbgBtn: $('dbgBtn'),
  mode: $('mode'), ghost: $('ghost'), saveGhost: $('saveGhost'),
  timerTile: $('timerTile'), timer: $('timer'),
  race: $('race'), raceYou: $('raceYou'), raceThem: $('raceThem'), raceFill: $('raceFill'),
  over: $('over'), overKicker: $('overKicker'), overTitle: $('overTitle'),
  overBody: $('overBody'), again: $('again'),
  rest: $('rest'), restSet: $('restSet'), restBand: $('restBand'), restCoach: $('restCoach'),
  restBoss: $('restBoss'), restStats: $('restStats'), nextSet: $('nextSet'),
  waveTile: $('waveTile'), wave: $('wave'),
  sum: $('sum'), sumCanvas: $('sumCanvas'), sumBtn: $('sumBtn'),
  sumPng: $('sumPng'), sumCsv: $('sumCsv'), sumClose: $('sumClose'),
  link: $('link'),
  arena: $('arena'), voice: $('voice'), haptic: $('haptic'), motion: $('motion'),
  roster: $('roster'), rosterKicker: $('rosterKicker'), rosterTitle: $('rosterTitle'),
  rosterNames: $('rosterNames'), rosterHint: $('rosterHint'), rosterGo: $('rosterGo'),
  turn: $('turn'), turnName: $('turnName'), turnMeta: $('turnMeta'),
  streak: $('streak'),
  breathe: $('breathe'), breathBox: $('breathBox'), breathLabel: $('breathLabel'),
  breathFill: $('breathFill'), breathMeta: $('breathMeta'),
  modes: $('modes'), modeGrid: $('modeGrid'), modesBtn: $('modesBtn'), modesClose: $('modesClose'),
  challengeBtn: $('challengeBtn'), acceptBtn: $('acceptBtn'),
  challengeBox: $('challengeBox'), challengeMsg: $('challengeMsg'),
  pre: $('pre'), preList: $('preList'), preVerdict: $('preVerdict'),
  preBtn: $('preBtn'), preRun: $('preRun'), preClose: $('preClose'),
};

/** docs/17-GAME-MODES.md §9: show this to a judge even if you only demo one mode. It takes four
 *  seconds and it changes what the product looks like. */
const MODE_TILES = [
  { group: 'Solo', id: 'BOSS_FIGHT', name: 'Boss Fight', desc: 'The core loop. Reps deal damage, the boss adapts to your fatigue.' },
  { group: 'Solo', id: 'TIME_ATTACK', name: 'Time Attack', desc: 'Sixty seconds, maximum damage. Fits a rotating judge.' },
  { group: 'Solo', id: 'SURVIVAL', name: 'Survival', desc: 'Endless waves, no mercy rule. Fatigue genuinely ends the run.' },
  { group: 'Solo', id: 'BOSS_RUSH', name: 'Boss Rush', desc: 'Three bosses back to back, no rest.' },
  { group: 'Solo', id: 'TEMPO_TRIAL', name: 'Tempo Trial', desc: 'Match the beat. Scores how you move, not that you moved.' },
  { group: 'Versus', id: 'GHOST_RACE', name: 'Ghost Race', desc: 'Race a recorded run — a pacer, your past self, or a friend’s file.' },
  { group: 'Versus', id: 'DUEL', name: 'Duel', desc: 'Two devices, one boss, no server. Events sync, not state.' },
  { group: 'Group', id: 'PASS_THE_PHONE', name: 'Pass the Phone', desc: 'Turns on one device. The boss keeps its damage between players.' },
  { group: 'Group', id: 'LAST_STANDING', name: 'Last Standing', desc: 'Out when your measured fatigue hits GASSED. Not when you run out of reps.' },
  { group: 'Group', id: 'CIRCUIT', name: 'Circuit', desc: 'A prescribed sequence across four movement families.' },
  { group: 'Family', id: 'SIEGE', name: 'Siege', desc: 'Holds. Your plank is the shield; break form and a hit lands.', needs: 'holds' },
  { group: 'Family', id: 'PURSUIT', name: 'Pursuit', desc: 'Cardio. Distance is cadence; stop and it closes on you.', needs: 'cardio' },
  { group: 'Family', id: 'BREAKER', name: 'Breaker', desc: 'Jumps. Height is force, and a stiff landing does not break through.', needs: 'jumps' },
  { group: 'Family', id: 'SIGIL', name: 'Sigil', desc: 'Yoga. No boss, no damage, no ranking — a constellation you light.', needs: 'yoga' },
  { group: 'Clinic', id: 'CLINIC_STS', name: '30s Sit-to-Stand', desc: 'A published functional assessment. Not a medical device.' },
];

let breath = null;
let breathTimer = null;

const store2 = new Store();
let sessionStartMs = null;

let roster = null;
let circuit = null;
const GROUP_MODES = ['PASS_THE_PHONE', 'LAST_STANDING'];

let haptics = null;
let voice = null;
let arenaMode = false;

let duel = null;
const MY_ID = newPlayerId();

const audio = new Audio();
const speech = new Speech();
let lastCombo = 1;

const PACERS = ['pacer_bronze', 'pacer_silver', 'pacer_gold'];
let ghosts = {};

const store = {};
let landmarker = null, engine = null, renderer = null, recorder = new TraceRecorder();
let running = false, lastVideoTime = -1, latest = null, casual = false;
let fps = 0, fpsT0 = performance.now(), fpsN = 0, inferMs = 0;

const BAND_COLOR = { FRESH: C.clean, WORKING: C.system, FADING: C.shallow, GASSED: C.damage };

async function boot() {
  try {
    Object.assign(store, await loadConfig());
  } catch (e) {
    return fatal(`Could not load config: ${e.message}. Serve this folder over HTTP — open it with "npm start", not by double-clicking the file.`);
  }

  // Grouped by movement family — five detectors, forty-eight exercises.
  const FAM_LABEL = {
    REP_CYCLE: 'Strength · reps', ISOMETRIC_HOLD: 'Holds', CADENCE: 'Cardio',
    BALLISTIC: 'Jumps', POSE_MATCH: 'Yoga',
  };
  const groups = {};
  for (const e of store.manifest.exercises) (groups[e.family] ??= []).push(e);
  el.exercise.innerHTML = Object.entries(groups).map(([fam, list]) =>
    `<optgroup label="${FAM_LABEL[fam] ?? fam}">` +
    list.map((x) => `<option value="${x.id}">${x.name}</option>`).join('') +
    '</optgroup>').join('');
  el.exercise.value = 'squat';

  // Shipped pacers, so a fresh install can race something immediately.
  ghosts = Object.fromEntries((await Promise.all(PACERS.map(async (id) => {
    try { return [id, parseGhost(await (await fetch(`config/ghosts/${id}.json`)).text())]; }
    catch { return null; }
  }))).filter(Boolean));
  el.ghost.innerHTML = Object.entries(ghosts)
    .map(([id, g]) => `<option value="${id}">${g.meta.name} · ${g.meta.reps} reps</option>`).join('')
    + '<option value="__file">Load ghost file…</option>';

  paintStreak();
  haptics = new Haptics(store.ui?.haptics);
  el.haptic.classList.toggle('on', haptics.available && haptics.enabled);
  el.haptic.disabled = !haptics.available;

  voice = new Voice({ onCommand: onVoiceCommand });
  el.voice.disabled = !voice.available;
  if (!voice.available) el.voice.title = 'This browser has no speech recognition.';

  renderer = new Renderer(el.stage);
  renderer.resize();
  addEventListener('resize', () => renderer.resize());

  makeEngine('squat');
  wire();
  el.boot.style.display = 'none';
  tick();
}

const FAMILY_GAME = {
  REP_CYCLE: null, ISOMETRIC_HOLD: Mode.SIEGE, CADENCE: Mode.PURSUIT,
  BALLISTIC: Mode.BREAKER, POSE_MATCH: Mode.SIGIL,
};

function makeEngine(id) {
  let mode = el.mode.value;
  // The protocol prescribes the movement — you do not get to pick it.
  if (mode === Mode.CLINIC_STS) { id = store.clinic.sit_to_stand_30s.exercise; el.exercise.value = id; }

  // A game shaped like the movement: holds get Siege, cardio gets Pursuit, jumps get Breaker,
  // yoga gets Sigil. Only rep-based movements can be played in the rep-based modes.
  const family = store.exercises[id]?.family ?? 'REP_CYCLE';
  const forced = FAMILY_GAME[family];
  if (forced && mode !== forced) { mode = forced; el.mode.value = forced; }
  if (!forced && [Mode.SIEGE, Mode.PURSUIT, Mode.BREAKER, Mode.SIGIL].includes(mode)) {
    mode = Mode.BOSS_FIGHT; el.mode.value = mode;
  }
  el.mode.disabled = !!forced;
  engine = new SessionEngine(store, id, {
    casual,
    mode,
    onEnd: (reason, s) => {
      if (reason === 'BOSS_DOWN' || reason === 'GAME_WON') { audio.bossDeath(); haptics?.bossDown(); }
      showResult(reason, s);
    },
    onSetEnd: (telemetry, coach) => { if (!roster) showRest(telemetry, coach); },
    onBand: (band) => {
      // Fatigue is the referee. Every other fitness app would eliminate on rep count.
      if (roster?.mode === RosterMode.LAST_STANDING && band === 'GASSED') eliminateAndAdvance();
    },
    onRep: (rep, combat) => {
      renderer.hit(rep.verdict, rep.damage);
      el.verdict.textContent = rep.verdict;
      el.verdict.style.color =
        rep.verdict === 'CLEAN' ? C.clean : rep.verdict === 'OK' ? C.system : C.shallow;

      duel?.sendRep({ damage: rep.damage, formScore: rep.formScore,
                      exercise: el.exercise.value, fatigueBand: rep.fatigue.band });
      if (rep.verdict === 'SHALLOW') { audio.repShallow(); haptics?.repShallow(); }
      else { audio.repClean(combat.comboMultiplier); haptics?.repClean(); }
      const m = combat.comboMultiplier;
      if (Math.floor(m) > Math.floor(lastCombo) && m > 1) { audio.comboMilestone(m); haptics?.comboMilestone(); }
      lastCombo = m;
    },
  });
  if (mode === Mode.GHOST_RACE) {
    const g = ghosts[el.ghost.value] ?? Object.values(ghosts)[0];
    if (g) engine.loadGhost(g);
  }

  duel?.close();
  duel = null;
  if (mode === Mode.DUEL) {
    const t = new BroadcastChannelTransport();
    if (!t.available) { el.cue.textContent = 'This browser has no BroadcastChannel.'; }
    else {
      duel = new DuelSession(t, {
        playerId: MY_ID,
        onRemote: (pid, seq, dmg) => { engine.combat.onRemoteDamage(pid, seq, dmg); engine.ghostDamage += dmg; },
        onState: (st) => paintLink(st),
      });
      paintLink(LinkState.SEARCHING);
    }
  }
  el.link.textContent = mode === Mode.DUEL ? '' : '';
  el.exercise.title = `${family.replace('_', ' ').toLowerCase()} · ${forced ?? mode}`;
  const timed = mode === Mode.TIME_ATTACK || mode === Mode.CLINIC_STS || mode === Mode.DUEL;
  el.timerTile.style.display = timed ? '' : 'none';
  el.waveTile.style.display = mode === Mode.SURVIVAL ? '' : 'none';
  el.race.classList.toggle('show', mode === Mode.GHOST_RACE || mode === Mode.DUEL);
  el.ghost.style.display = mode === Mode.GHOST_RACE ? '' : 'none';
  el.exercise.disabled = mode === Mode.CLINIC_STS || mode === 'CIRCUIT';

  // Tempo Trial is driven by a metronome you can hear and feel, so it works eyes-free.
  haptics?.stopMetronome();
  if (mode === Mode.TEMPO_TRIAL) {
    const target = store.combat.modes?.TEMPO_TRIAL?.targetEccSec ?? 0.55;
    const fullDescentSec = target * 3;                  // measured window is ~1/3 of the descent
    haptics?.startMetronome(Math.round(60 / fullDescentSec));
    el.cue.textContent = `Match the beat — about ${fullDescentSec.toFixed(1)}s down, every rep.`;
  }

  roster = null; circuit = null;
  el.turn.classList.remove('show');
  if (GROUP_MODES.includes(mode)) {
    el.rosterKicker.textContent = mode === 'LAST_STANDING' ? 'Last Standing' : 'Pass the phone';
    el.rosterHint.textContent = mode === 'LAST_STANDING'
      ? "Everyone does the same movement. You're out when your measured fatigue reaches GASSED — not when you run out of reps. One device, any number of people."
      : 'Each player gets 30 seconds. The boss keeps its damage between turns, so it is one shared fight.';
    el.roster.classList.add('show');
  } else if (mode === 'CIRCUIT') {
    circuit = new Circuit(DEFAULT_CIRCUIT);
    circuit.start(performance.now());
    el.exercise.value = circuit.current.exerciseId;
    engine.setExercise(circuit.current.exerciseId);
    el.turn.classList.add('show');
  } else {
    el.roster.classList.remove('show');
  }
  el.over.classList.remove('show');
  el.rest.classList.remove('show');
}

/** Never a bare spinner. A judge watching an unexplained spinner assumes it is broken, and they
 *  are usually right. docs/07-MULTIPLAYER-SPEC.md §7 */
function paintLink(st) {
  const TXT = {
    [LinkState.SEARCHING]: 'searching for opponent…',
    [LinkState.LINKED]: 'linked',
    [LinkState.LOST]: 'opponent disconnected — scoring locally',
  };
  el.link.textContent = TXT[st] ?? '';
  el.link.style.color = st === LinkState.LINKED ? C.clean : st === LinkState.LOST ? C.damage : C.mute;
  if (st === LinkState.LOST) el.cue.textContent = 'Opponent disconnected — scoring locally.';
}

/** The player is two metres away and cannot reach the phone. Voice is the only mid-set input. */
function eliminateAndAdvance() {
  if (!roster) return;
  roster.record({ reps: engine.setReps.length, damage: engine.playerDamage });
  roster.eliminateCurrent('GASSED');
  audio.repShallow(); haptics?.framingLost();
  if (roster.finished) return showRosterResult();
  advanceTurn();
}

function advanceTurn() {
  if (!roster) return;
  roster.record({ reps: engine.setReps.length, damage: engine.playerDamage });
  const next = roster.next(performance.now());
  if (!next) return showRosterResult();
  engine.nextSet?.();
  engine.reset();
  speech.flush();
  speech.say(`${next.name}, you're up.`);
  audio.countdown(true);
}

function showRosterResult() {
  const w = roster.finish();
  el.overKicker.textContent = roster.mode === RosterMode.LAST_STANDING ? 'Last standing' : 'Round over';
  el.overTitle.textContent = w ? w.name.toUpperCase() : '—';
  el.overTitle.style.color = C.clean;
  el.overBody.innerHTML = roster.leaderboard()
    .map((p, i) => `${i + 1}. ${p.name} — ${p.damage} damage · ${p.reps} reps` +
                   (p.out ? ` <span style="color:${C.damage}">out (${p.outReason})</span>` : ''))
    .join('<br>');
  el.over.classList.add('show');
  el.turn.classList.remove('show');
  openSummaryLater();
}

function onVoiceCommand(cmd) {
  if (cmd === 'stop') { engine.reset(); el.cue.textContent = 'Stopped.'; }
  if (cmd === 'next' && el.rest.classList.contains('show')) el.nextSet.click();
  if (cmd === 'next' && el.over.classList.contains('show')) el.again.click();
  if (cmd === 'rest') { el.cue.textContent = 'Resting.'; }
  if (cmd === 'start' && !running) el.start.click();
}

/** Breathing between sets measurably recovers a fatigue band, so it is mechanically useful
 *  mid-fight rather than a virtue tab nobody opens. docs/22-HEALTH-DOMAINS.md §3 */
function startBreathing() {
  stopBreathing();
  const gassed = engine.fatigue.state().value > 0.45;
  breath = gassed ? BoxBreathing.windDown(4) : new BoxBreathing({ cycles: 4 });
  breath.start(performance.now());
  el.breathBox.classList.add('show');
  el.breathe.classList.add('on');
  speech.flush();
  speech.say(gassed ? 'Long exhale. Follow the bar.' : 'Box breathing. Follow the bar.');

  breathTimer = setInterval(() => {
    const b = breath.tick(performance.now());
    el.breathLabel.textContent = b.label;
    el.breathFill.style.width = `${Math.round(b.phaseProgress * 100)}%`;
    el.breathMeta.textContent = `cycle ${Math.min(b.cycle + 1, breath.cycles)} of ${breath.cycles}`;
    if (b.changed && !b.done) haptics?.breathe();
    if (b.done) finishBreathing();
  }, 100);
}

function finishBreathing() {
  if (!breath) return;
  const frac = recoveryFraction({ cyclesCompleted: breath.cycle, targetCycles: breath.cycles });
  const before = engine.fatigue.state();
  const after = engine.recoverFatigue(frac);
  stopBreathing();
  const moved = after.band !== before.band;
  el.restBand.textContent = after.band;
  el.restBand.style.color = BAND_COLOR[after.band];
  el.cue.textContent = moved
    ? `Recovered to ${after.band}.`
    : `Fatigue down ${Math.round((before.value - after.value) * 100)} points.`;
  speech.say(moved ? `Recovered to ${after.band.toLowerCase()}.` : 'Good. Back to it.');
}

function stopBreathing() {
  if (breathTimer) { clearInterval(breathTimer); breathTimer = null; }
  breath = null;
  el.breathBox.classList.remove('show');
  el.breathe.classList.remove('on');
}

function showRest(telemetry, coach) {
  el.restSet.textContent = telemetry.session_set_index;
  el.restBand.textContent = telemetry.fatigue_band;
  el.restBand.style.color = BAND_COLOR[telemetry.fatigue_band];
  el.restCoach.textContent = coach.coachLine;
  el.restBoss.textContent = `"${coach.bossLine}"`;
  el.restStats.innerHTML =
    `${telemetry.reps} reps · mean form ${telemetry.form_mean_pct}% · ` +
    `velocity −${telemetry.velocity_loss_pct}% · range −${telemetry.rom_loss_pct}%` +
    (telemetry.depth_drop_cm ? ` · depth −${telemetry.depth_drop_cm}cm` : '') +
    `<br>source: ${coach.source.toLowerCase()}`;
  stopBreathing();
  el.rest.classList.add('show');
  audio.duck(0.12, 4000);
  speech.sayPair(coach.coachLine, coach.bossLine);      // the player is on the floor, not reading
}

/** A clinical assessment gets a result card, never a victory screen. No boss, no damage, no
 *  ranking — a count, the protocol it came from, and the line that keeps this honest.
 *  docs/25-CLINIC-MODE.md §6 */
function showClinicResult(s) {
  const p = store.clinic.sit_to_stand_30s;
  el.overKicker.textContent = p.name;
  el.overTitle.textContent = String(s.reps);
  el.overTitle.style.color = C.system;
  const norms = p.reporting.showNormComparison && p.norms.source;
  el.overBody.innerHTML =
    `stands in ${p.durationSec} seconds · measures ${p.measures}<br>` +
    (norms ? '' : `${p.reporting.phrasing.noNorms}<br>`) +
    `<span style="opacity:.75">${p.notNested}</span>`;
  el.over.classList.add('show');
}

/** Each family game reports in its own terms. A yoga session does not get a victory screen with
 *  a damage total on it. */
function showFamilyResult(reason, s) {
  const g = s.gameState;
  const won = reason === 'GAME_WON';
  const T = {
    SIEGE: {
      kicker: won ? 'Siege held' : 'Shield broken',
      title: won ? 'HELD' : 'BROKEN',
      body: `${g.holds} holds · ${g.totalHeldSec?.toFixed(0)}s under tension · shield ${g.playerHp}`,
    },
    PURSUIT: {
      kicker: won ? 'Escaped' : 'Caught',
      title: won ? 'ESCAPED' : 'CAUGHT',
      body: `${g.distance?.toFixed(0)}m over ${g.cycles} cycles`,
    },
    BREAKER: {
      kicker: won ? 'Tower down' : 'Run over',
      title: `${g.broken}/${g.floors}`,
      body: `${g.jumps} jumps · best ${g.bestCm?.toFixed(0)}cm`,
    },
    SIGIL: {
      kicker: 'Sigil complete',
      title: 'COMPLETE',
      body: `${g.lit} of ${g.segments} lit · mean accuracy ${Math.round((g.brightness ?? 0) * 100)}%`,
    },
  }[g.game] ?? { kicker: 'Session', title: 'DONE', body: '' };

  el.overKicker.textContent = T.kicker;
  el.overTitle.textContent = T.title;
  el.overTitle.style.color = g.game === 'SIGIL' ? C.fatigue : won ? C.clean : C.damage;
  el.overBody.innerHTML = `${T.body}<br>peak fatigue ${s.fatigue.band}`;
  el.over.classList.add('show');
  openSummaryLater();
}

function openModes() {
  const groups = {};
  for (const m of MODE_TILES) (groups[m.group] ??= []).push(m);
  el.modeGrid.innerHTML = Object.entries(groups).map(([g, list]) =>
    list.map((m) => `
      <button class="modeTile${m.id === el.mode.value ? ' sel' : ''}" data-mode="${m.id}">
        <b>${m.name}</b><span>${m.desc}</span>
        <em>${g}${m.needs ? ' · needs ' + m.needs : ''}</em>
      </button>`).join('')).join('');
  el.modeGrid.querySelectorAll('.modeTile').forEach((b) => {
    b.onclick = () => {
      const id = b.dataset.mode;
      // A family game needs a movement from that family; pick the first one that fits.
      const needFamily = { SIEGE: 'ISOMETRIC_HOLD', PURSUIT: 'CADENCE',
                           BREAKER: 'BALLISTIC', SIGIL: 'POSE_MATCH' }[id];
      if (needFamily) {
        const first = store.manifest.exercises.find((e) => e.family === needFamily);
        if (first) el.exercise.value = first.id;
      }
      el.mode.value = id;
      el.modes.classList.remove('show');
      makeEngine(el.exercise.value);
    };
  });
  el.modes.classList.add('show');
}

async function runPreflight() {
  const results = await preflight({
    store, store2, engine, audio, haptics, speech, voice,
    running, arenaMode, duel, fps, inferMs,
    exerciseId: el.exercise.value,
    hasTraces: recorder.frames.length > 0,
  });
  const sum = summariseChecks(results);
  el.preVerdict.textContent = sum.verdict;
  el.preVerdict.style.color = sum.FAIL ? C.damage : sum.WARN ? C.shallow : C.clean;
  const COLOR = { PASS: C.clean, WARN: C.shallow, FAIL: C.damage, SKIP: C.mute };
  el.preList.innerHTML = results.map((r) => `
    <div class="preRow">
      <i style="color:${COLOR[r.status]}">${r.status}</i>
      <b>${r.label}</b><span>${r.detail}</span>
    </div>`).join('');
  el.pre.classList.add('show');
  return sum;
}

function openSummary() {
  drawSummary(el.sumCanvas, engine.reps, store.pose.fatigue.bands, {
    exercise: el.exercise.value,
    title: engine.reps.length ? 'Fatigue across the set' : 'No reps yet',
  });
  el.sum.classList.add('show');
}

function paintStreak() {
  const st = store2.streak;
  const last = store2.lastSession;
  el.streak.textContent = st.current
    ? `${store2.streakLabel()} · best ${st.best}` +
      (last ? ` · last ${last.totalReps} reps` : '')
    : '';
}

/** Persist on every completed session. Everything stays on the device, same as the rest. */
function persistSession(reason, s) {
  if (!engine.reps.length) return null;
  const rec = store2.saveSession({
    startedAt: sessionStartMs ?? Date.now() - 60000,
    endedAt: Date.now(),
    mode: s.mode,
    exerciseId: el.exercise.value,
    outcome: reason,
    reps: engine.reps,
  });
  if (engine.topRef) {
    store2.saveCalibration(el.exercise.value,
      { topRef: engine.topRef, romBaseline: engine.romBaselineU });
  }
  paintStreak();
  return rec;
}

function showResult(reason, s) {
  persistSession(reason, s);
  if (s.mode === Mode.CLINIC_STS) return showClinicResult(s);
  if (s.gameState) return showFamilyResult(reason, s);
  const win = s.mode === Mode.GHOST_RACE ? s.playerDamage >= s.ghostDamage : null;
  el.overKicker.textContent =
    reason === 'BOSS_DOWN' ? 'Boss down' : reason === 'TIME' ? "Time" : 'Set over';
  el.overTitle.textContent =
    s.mode === Mode.GHOST_RACE ? (win ? 'YOU WIN' : 'GHOST WINS')
    : s.mode === Mode.TIME_ATTACK ? String(s.playerDamage)
    : 'VICTORY';
  el.overTitle.style.color = win === false ? C.damage : C.clean;
  const mean = engine.reps.length
    ? engine.reps.reduce((a, r) => a + r.formScore, 0) / engine.reps.length : 0;
  const bests = store2.lastSession ? store2.personalBestsIn(store2.lastSession) : [];
  el.overBody.innerHTML =
    `${s.reps} reps · mean form ${mean.toFixed(2)} · ${s.playerDamage} damage<br>` +
    `peak fatigue ${s.fatigue.band}` +
    (s.mode === Mode.GHOST_RACE ? ` · ghost ${s.ghostDamage}` : '') +
    (bests.length ? `<br><span style="color:${C.clean}">personal best: ${bests.map(b => b.key).join(' and ')}</span>` : '');
  el.over.classList.add('show');
  openSummaryLater();
}

/** Draw the summary in the background so it is already there when they hit the button. */
function openSummaryLater() {
  try { drawSummary(el.sumCanvas, engine.reps, store.pose.fatigue.bands, { exercise: el.exercise.value }); }
  catch { /* chart is never allowed to break the fight */ }
}

function wire() {
  el.start.onclick = () => { audio.ensure(); (running ? stopCam() : startCam()); };
  el.exercise.onchange = () => makeEngine(el.exercise.value);
  el.mode.onchange = () => makeEngine(el.exercise.value);
  el.ghost.onchange = () => {
    if (el.ghost.value !== '__file') return makeEngine(el.exercise.value);
    const inp = document.createElement('input');
    inp.type = 'file'; inp.accept = '.json,application/json';
    inp.onchange = async () => {
      const f = inp.files?.[0]; if (!f) return;
      try {
        const g = parseGhost(await f.text());
        const id = `file:${f.name}`;
        ghosts[id] = g;
        el.ghost.insertAdjacentHTML('afterbegin',
          `<option value="${id}">${g.meta.name ?? f.name} · ${g.events.length} reps</option>`);
        el.ghost.value = id;
        makeEngine(el.exercise.value);
      } catch (e) { el.cue.textContent = `Not a ghost file: ${e.message}`; }
    };
    inp.click();
  };
  el.saveGhost.onclick = () => {
    if (!engine.reps.length) { el.cue.textContent = 'Do a set first, then save it as a ghost.'; return; }
    downloadGhost(ghostFromReps(engine.reps, { exercise: el.exercise.value, name: 'My run' }));
  };
  el.breathe.onclick = () => startBreathing();
  el.nextSet.onclick = () => {
    stopBreathing();
    speech.flush(); el.rest.classList.remove('show'); engine.nextSet();
  };
  el.again.onclick = () => { speech.flush(); engine.reset(); el.over.classList.remove('show'); el.rest.classList.remove('show'); };
  el.reset.onclick = () => { engine.reset(); el.over.classList.remove('show'); };
  el.casual.onclick = () => {
    casual = !casual;
    el.casual.classList.toggle('on', casual);
    makeEngine(el.exercise.value);
  };
  el.reload.onclick = async () => {
    const r = await reloadInto(store);
    el.cue.textContent = r.ok ? 'Config reloaded.' : `Config error — keeping last good. ${r.error}`;
    if (r.ok) makeEngine(el.exercise.value);
  };
  el.rec.onclick = () => {
    if (recorder.recording) {
      const n = recorder.stop();
      el.rec.classList.remove('on'); el.rec.textContent = '● Record trace';
      el.dl.disabled = n === 0;
      el.cue.textContent = `Recorded ${n} frames.`;
    } else {
      recorder.start({ exercise: el.exercise.value });
      el.rec.classList.add('on'); el.rec.textContent = '■ Stop';
      el.dl.disabled = true;
    }
  };
  el.dl.onclick = () => recorder.download();
  el.rosterGo.onclick = () => {
    const names = el.rosterNames.value.split(',').map((n) => n.trim()).filter(Boolean);
    if (names.length < 2) { el.rosterHint.textContent = 'Two names minimum.'; return; }
    roster = new Roster(names, {
      mode: el.mode.value === 'LAST_STANDING' ? RosterMode.LAST_STANDING : RosterMode.PASS_THE_PHONE,
      turnSec: 30,
    });
    roster.startTurn(performance.now());
    engine.reset();
    el.roster.classList.remove('show');
    el.turn.classList.add('show');
    audio.countdown(true);
  };
  el.arena.onclick = async () => {
    arenaMode = !arenaMode;
    el.arena.classList.toggle('on', arenaMode);
    renderer.mirror = !arenaMode;          // only a selfie view should be mirrored
    if (running) { stopCam(); await startCam(); }
    el.cue.textContent = arenaMode
      ? 'Arena Mode — rear camera, wider view. Mirror the phone to a laptop for the demo.'
      : 'Solo Mode — front camera.';
  };
  el.voice.onclick = () => {
    const on = voice.toggle();
    el.voice.classList.toggle('on', on);
    el.cue.textContent = on ? 'Listening — say stop, next, or rest.' : 'Voice off.';
  };
  el.haptic.onclick = () => {
    haptics.setEnabled(!haptics.enabled);
    el.haptic.classList.toggle('on', haptics.enabled);
  };
  el.motion.onclick = () => {
    renderer.reducedMotion = !renderer.reducedMotion;
    el.motion.classList.toggle('on', renderer.reducedMotion);
  };
  el.challengeBtn.onclick = () => {
    const c = challengeFromSession({
      reps: engine.reps, exerciseId: el.exercise.value,
      name: (store2.state?.profile?.name) || 'Me', mode: 'GHOST_RACE',
    });
    if (!c) { el.challengeMsg.textContent = 'Do a set first, then share it.'; return; }
    const code = encodeChallenge(c);
    el.challengeBox.classList.add('show');
    el.challengeBox.value = code;
    el.challengeBox.select();
    navigator.clipboard?.writeText(code).catch(() => {});
    el.challengeMsg.textContent =
      `${describeChallenge(c)} · ${code.length} characters. Copied — send it however you like. No server involved.`;
  };
  el.acceptBtn.onclick = () => {
    el.challengeBox.classList.add('show');
    const raw = el.challengeBox.value.trim();
    if (!raw) { el.challengeMsg.textContent = 'Paste a challenge code above, then press Accept again.'; return; }
    try {
      const c = decodeChallenge(raw);
      if (c.ghost) {
        if (store.exercises[c.exerciseId]) { el.exercise.value = c.exerciseId; }
        el.mode.value = Mode.GHOST_RACE;
        makeEngine(el.exercise.value);
        engine.loadGhost(c.ghost);
        el.sum.classList.remove('show');
        el.challengeMsg.textContent = '';
        el.cue.textContent = `Challenge loaded — ${describeChallenge(c)}`;
      } else el.challengeMsg.textContent = 'That challenge has no run attached.';
    } catch (e) {
      el.challengeMsg.textContent = e.message;      // already written to be readable
    }
  };
  el.modesBtn.onclick = () => openModes();
  el.modesClose.onclick = () => el.modes.classList.remove('show');
  el.preBtn.onclick = () => runPreflight();
  el.preRun.onclick = () => runPreflight();
  el.preClose.onclick = () => el.pre.classList.remove('show');
  el.sumBtn.onclick = () => openSummary();
  el.sumClose.onclick = () => el.sum.classList.remove('show');
  el.sumPng.onclick = () => exportPng(el.sumCanvas,
    `clashfit-${el.exercise.value}-${engine.reps.length}reps.png`);
  el.sumCsv.onclick = () => exportCsv(engine.reps,
    `clashfit-${el.exercise.value}-${engine.reps.length}reps.csv`);
  el.dbgBtn.onclick = () => {
    el.dbg.classList.toggle('show');
    el.dbgBtn.classList.toggle('on', el.dbg.classList.contains('show'));
  };
  if (store.pose.debugOverlay) el.dbgBtn.click();
}

async function startCam() {
  try {
    el.start.disabled = true; el.start.textContent = 'Loading…';
    if (!landmarker) {
      const files = await FilesetResolver.forVisionTasks(WASM);
      landmarker = await PoseLandmarker.createFromOptions(files, {
        baseOptions: { modelAssetPath: MODEL, delegate: 'GPU' },
        runningMode: 'VIDEO',
        numPoses: 1,
        minPoseDetectionConfidence: store.pose.detector.minPoseDetectionConfidence,
        minPosePresenceConfidence: store.pose.detector.minPosePresenceConfidence,
        minTrackingConfidence: store.pose.detector.minTrackingConfidence,
      });
    }
    const stream = await navigator.mediaDevices.getUserMedia({
      video: {
        width: { ideal: 1280 }, height: { ideal: 720 },
        // Arena Mode: rear camera, wider field of view, phone low and mirrored to a laptop.
        // Solo needs the screen facing the player; Arena buys framing at a table.
        facingMode: arenaMode ? { ideal: 'environment' } : 'user',
      },
      audio: false,
    });
    el.cam.srcObject = stream;
    await el.cam.play();
    running = true;
    el.start.disabled = false; el.start.textContent = 'Stop camera'; el.start.classList.remove('go');
  } catch (e) {
    el.start.disabled = false; el.start.textContent = 'Start camera';
    fatal(`Camera or model failed: ${e.message}`);
  }
}

function stopCam() {
  running = false;
  el.cam.srcObject?.getTracks().forEach((t) => t.stop());
  el.cam.srcObject = null;
  el.start.textContent = 'Start camera'; el.start.classList.add('go');
}

function tick() {
  requestAnimationFrame(tick);
  const now = performance.now();

  if (running && landmarker && el.cam.readyState >= 2 && el.cam.currentTime !== lastVideoTime) {
    lastVideoTime = el.cam.currentTime;
    sessionStartMs ??= Date.now();
    const t0 = performance.now();
    const res = landmarker.detectForVideo(el.cam, now);
    inferMs = inferMs * 0.9 + (performance.now() - t0) * 0.1;

    const world = res.worldLandmarks?.[0] ?? null;
    const image = res.landmarks?.[0] ?? null;
    latest = image;
    recorder.push(world, now);
    engine.frame(world, image, now);

    fpsN++;
    if (now - fpsT0 > 500) { fps = (fpsN * 1000) / (now - fpsT0); fpsN = 0; fpsT0 = now; }
  }

  const s = engine ? engine.state() : null;
  if (s) { renderer.draw(el.cam, s, latest); paintHud(s); }
}

function paintHud(s) {
  el.phaseText.textContent = running ? s.phase.toLowerCase() : 'idle';
  el.dot.style.background =
    !running ? C.mute :
    s.phase === Phase.FRAMING_LOST ? C.damage :
    s.phase === Phase.CALIBRATING ? C.shallow : C.clean;

  if (s.phase === Phase.FRAMING_LOST && !paintHud.lostAnnounced) {
    audio.framingLost(); haptics?.framingLost(); paintHud.lostAnnounced = true;
  } else if (s.phase !== Phase.FRAMING_LOST) paintHud.lostAnnounced = false;

  if (s.cue) el.cue.textContent = s.cue;
  else if (s.combat.dead) el.cue.textContent = 'Boss down.';
  else if (s.phase === Phase.FIGHTING && s.reps === 0) el.cue.textContent = 'Go.';

  const COUNT_LABEL = { REP_CYCLE: 'Reps', ISOMETRIC_HOLD: 'Holds', CADENCE: 'Cycles',
                        BALLISTIC: 'Jumps', POSE_MATCH: 'Poses' };
  const lbl = el.reps.previousElementSibling;
  if (lbl) lbl.textContent = COUNT_LABEL[s.family] ?? 'Reps';
  el.reps.textContent = s.reps;
  el.combo.textContent = `×${s.combat.comboMultiplier.toFixed(1)}`;

  if (roster) {
    const cur = roster.current;
    el.turnName.textContent = cur?.name ?? '—';
    const left = roster.turnLeftMs(performance.now());
    el.turnMeta.textContent = roster.mode === RosterMode.LAST_STANDING
      ? `${roster.standing.length} still in · ${s.fatigue.band}`
      : `${Math.ceil((left ?? 0) / 1000)}s left · ${roster.standing.length} players`;
    if (left === 0) advanceTurn();
  }
  if (circuit) {
    const left = circuit.leftMs(performance.now());
    el.turnName.textContent = circuit.current?.label ?? 'Done';
    el.turnMeta.textContent = `step ${circuit.index + 1} of ${circuit.steps.length} · ${Math.ceil(left / 1000)}s`;
    if (left === 0 && !circuit.finished) {
      const nxt = circuit.advance(performance.now(), { reps: engine.setReps.length });
      if (nxt) { el.exercise.value = nxt.exerciseId; engine.setExercise(nxt.exerciseId); engine.reset(); audio.countdown(true); }
      else { circuit = null; el.turn.classList.remove('show'); el.cue.textContent = 'Circuit complete.'; }
    }
  }
  if (s.mode === Mode.TEMPO_TRIAL && s.lastRep) {
    const acc = s.lastRep.tempoAccuracy;
    el.verdict.textContent = acc === undefined ? '—'
      : acc > 0.8 ? 'ON BEAT' : acc > 0.5 ? 'CLOSE' : 'OFF BEAT';
    el.verdict.style.color = acc > 0.8 ? C.clean : acc > 0.5 ? C.system : C.shallow;
  }
  if (s.mode === Mode.SURVIVAL) el.wave.textContent = s.wave;
  if (s.mode === Mode.DUEL) {
    duel?.tick();
    const you = s.playerDamage, them = duel?.remoteDamage ?? 0, tot = you + them;
    el.raceYou.textContent = `YOU ${you}`;
    el.raceThem.textContent = `${them} OPPONENT`;
    el.raceFill.style.width = `${tot ? (you / tot) * 100 : 50}%`;
  }
  if (s.mode === Mode.TIME_ATTACK || s.mode === Mode.CLINIC_STS || s.mode === Mode.DUEL) {
    const left = s.timeLeftMs ?? engine.durationMs;
    el.timer.textContent = Math.ceil(left / 1000);
    el.timer.style.color = left < 10000 ? C.damage : '';
  }
  if (s.mode === Mode.GHOST_RACE) {
    const you = s.playerDamage, them = s.ghostDamage, tot = you + them;
    el.raceYou.textContent = `YOU ${you}`;
    el.raceThem.textContent = `${them} ${s.ghostMeta?.name?.toUpperCase() ?? 'GHOST'}`;
    el.raceFill.style.width = `${tot ? (you / tot) * 100 : 50}%`;
  }
  el.band.textContent = s.fatigue.band;
  el.band.style.color = BAND_COLOR[s.fatigue.band];
  const lit = s.fatigue.bandIndex + 1;
  el.pips.forEach((p, i) => {
    p.classList.toggle('on', i < lit);
    p.style.background = i < lit ? BAND_COLOR[s.fatigue.band] : '';
  });

  if (!el.dbg.classList.contains('show')) return;
  const r = s.lastRep;
  el.dbg.innerHTML =
`<b>fps</b> ${fps.toFixed(0)}   <b>infer</b> ${inferMs.toFixed(1)}ms   <b>framing</b> ${s.framing ?? '—'}
<b>angle</b> ${Number.isFinite(s.angle) ? s.angle.toFixed(1) + '°' : '—'}   <b>fsm</b> ${s.fsmState}   <b>topRef</b> ${s.topRef ? s.topRef.toFixed(1) + '°' : '—'}
<b>fatigue</b> ${s.fatigue.value.toFixed(3)}  vLoss ${s.fatigue.velocityLoss.toFixed(2)}  romLoss ${s.fatigue.romLoss.toFixed(2)}  pause ${s.fatigue.pauseGrowth.toFixed(2)}
<b>hp</b> ${s.combat.hp}/${s.combat.maxHp}  <b>dmg total</b> ${s.combat.totalDamage}  <b>phase</b> ${s.combat.phase}${s.combat.mercyActive ? '  MERCY' : ''}${s.combat.staggered ? '  STAGGER' : ''}
${r ? `<b>last</b> #${r.repIndex} ${r.verdict} form ${r.formScore.toFixed(3)} dmg ${r.damage}
      depth ${r.depth.toFixed(2)} rom ${r.rom.toFixed(2)} tempo ${r.tempo.toFixed(2)} align ${r.alignment.toFixed(2)}  worst: ${r.reason}
      ecc ${r.tEccSec.toFixed(2)}s pause ${r.tBottomSec.toFixed(2)}s con ${r.tConSec.toFixed(2)}s  vel ${r.concentricVelocity.toFixed(0)}°/s` : '<b>last</b> —'}`;
}

function fatal(msg) {
  el.boot.style.display = 'grid';
  el.err.textContent = msg;
}

boot();
