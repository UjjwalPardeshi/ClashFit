// Camera + MediaPipe wiring and the render loop.
// Loaded from CDN so the prototype runs with no build step and no npm install — one command
// (`npm start`) and a browser. If the pinned CDN version ever 404s, swap to @latest.

import { PoseLandmarker, FilesetResolver } from
  'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@latest/vision_bundle.mjs';

import { loadConfig, reloadInto } from './config.js';
import { SessionEngine, Phase } from './engine.js';
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
};

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

  el.exercise.innerHTML = Object.values(store.exercises)
    .map((x) => `<option value="${x.id}">${x.name}</option>`).join('');
  el.exercise.value = 'squat';

  renderer = new Renderer(el.stage);
  renderer.resize();
  addEventListener('resize', () => renderer.resize());

  makeEngine('squat');
  wire();
  el.boot.style.display = 'none';
  tick();
}

function makeEngine(id) {
  engine = new SessionEngine(store, id, {
    casual,
    onRep: (rep) => {
      renderer.hit(rep.verdict, rep.damage);
      el.verdict.textContent = rep.verdict;
      el.verdict.style.color =
        rep.verdict === 'CLEAN' ? C.clean : rep.verdict === 'OK' ? C.system : C.shallow;
    },
  });
}

function wire() {
  el.start.onclick = () => (running ? stopCam() : startCam());
  el.exercise.onchange = () => { makeEngine(el.exercise.value); };
  el.reset.onclick = () => engine.reset();
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

  if (s.cue) el.cue.textContent = s.cue;
  else if (s.combat.dead) el.cue.textContent = 'Boss down.';
  else if (s.phase === Phase.FIGHTING && s.reps === 0) el.cue.textContent = 'Go.';

  el.reps.textContent = s.reps;
  el.combo.textContent = `×${s.combat.comboMultiplier.toFixed(1)}`;
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
