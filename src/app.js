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
};

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
    onEnd: (reason, s) => { if (reason === 'BOSS_DOWN') audio.bossDeath(); showResult(reason, s); },
    onSetEnd: (telemetry, coach) => showRest(telemetry, coach),
    onRep: (rep, combat) => {
      renderer.hit(rep.verdict, rep.damage);
      el.verdict.textContent = rep.verdict;
      el.verdict.style.color =
        rep.verdict === 'CLEAN' ? C.clean : rep.verdict === 'OK' ? C.system : C.shallow;

      if (rep.verdict === 'SHALLOW') audio.repShallow();
      else audio.repClean(combat.comboMultiplier);
      const m = combat.comboMultiplier;
      if (Math.floor(m) > Math.floor(lastCombo) && m > 1) audio.comboMilestone(m);
      lastCombo = m;
    },
  });
  if (mode === Mode.GHOST_RACE) {
    const g = ghosts[el.ghost.value] ?? Object.values(ghosts)[0];
    if (g) engine.loadGhost(g);
  }
  el.exercise.title = `${family.replace('_', ' ').toLowerCase()} · ${forced ?? mode}`;
  const timed = mode === Mode.TIME_ATTACK || mode === Mode.CLINIC_STS;
  el.timerTile.style.display = timed ? '' : 'none';
  el.waveTile.style.display = mode === Mode.SURVIVAL ? '' : 'none';
  el.race.classList.toggle('show', mode === Mode.GHOST_RACE);
  el.ghost.style.display = mode === Mode.GHOST_RACE ? '' : 'none';
  el.exercise.disabled = mode === Mode.CLINIC_STS;
  el.over.classList.remove('show');
  el.rest.classList.remove('show');
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

function openSummary() {
  drawSummary(el.sumCanvas, engine.reps, store.pose.fatigue.bands, {
    exercise: el.exercise.value,
    title: engine.reps.length ? 'Fatigue across the set' : 'No reps yet',
  });
  el.sum.classList.add('show');
}

function showResult(reason, s) {
  if (s.mode === Mode.CLINIC_STS) return showClinicResult(s);
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
  el.overBody.innerHTML =
    `${s.reps} reps · mean form ${mean.toFixed(2)} · ${s.playerDamage} damage<br>` +
    `peak fatigue ${s.fatigue.band}` +
    (s.mode === Mode.GHOST_RACE ? ` · ghost ${s.ghostDamage}` : '');
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
  el.nextSet.onclick = () => { speech.flush(); el.rest.classList.remove('show'); engine.nextSet(); };
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
      video: { width: { ideal: 1280 }, height: { ideal: 720 }, facingMode: 'user' },
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
    audio.framingLost(); paintHud.lostAnnounced = true;
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

  if (s.mode === Mode.SURVIVAL) el.wave.textContent = s.wave;
  if (s.mode === Mode.TIME_ATTACK || s.mode === Mode.CLINIC_STS) {
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
