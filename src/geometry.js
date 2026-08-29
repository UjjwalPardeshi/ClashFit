// Joint geometry over MediaPipe Pose landmarks.
// Angles come from WORLD landmarks (metric, hip-centred) — image-space angles distort
// with camera tilt, and the phone is propped at a bad angle by design.
// Spec: docs/05-POSE-ENGINE-SPEC.md §3, docs/24-BUILD-SETUP.md §5

export const LM = {
  NOSE: 0,
  LEFT_SHOULDER: 11, RIGHT_SHOULDER: 12,
  LEFT_ELBOW: 13, RIGHT_ELBOW: 14,
  LEFT_WRIST: 15, RIGHT_WRIST: 16,
  LEFT_HIP: 23, RIGHT_HIP: 24,
  LEFT_KNEE: 25, RIGHT_KNEE: 26,
  LEFT_ANKLE: 27, RIGHT_ANKLE: 28,
  LEFT_HEEL: 29, RIGHT_HEEL: 30,
  LEFT_FOOT_INDEX: 31, RIGHT_FOOT_INDEX: 32,
};

/** Joint name -> [leftIndex, rightIndex]. Config records use these names. */
export const JOINT = {
  SHOULDER: [LM.LEFT_SHOULDER, LM.RIGHT_SHOULDER],
  ELBOW:    [LM.LEFT_ELBOW, LM.RIGHT_ELBOW],
  WRIST:    [LM.LEFT_WRIST, LM.RIGHT_WRIST],
  HIP:      [LM.LEFT_HIP, LM.RIGHT_HIP],
  KNEE:     [LM.LEFT_KNEE, LM.RIGHT_KNEE],
  ANKLE:    [LM.LEFT_ANKLE, LM.RIGHT_ANKLE],
  HEEL:     [LM.LEFT_HEEL, LM.RIGHT_HEEL],
  FOOT_INDEX: [LM.LEFT_FOOT_INDEX, LM.RIGHT_FOOT_INDEX],
};

export const SIDE = { LEFT: 0, RIGHT: 1 };

/** Angle at b, in degrees, from three 3D points. */
export function angle3(a, b, c) {
  const v1x = a.x - b.x, v1y = a.y - b.y, v1z = (a.z ?? 0) - (b.z ?? 0);
  const v2x = c.x - b.x, v2y = c.y - b.y, v2z = (c.z ?? 0) - (b.z ?? 0);
  const n1 = Math.hypot(v1x, v1y, v1z);
  const n2 = Math.hypot(v2x, v2y, v2z);
  if (n1 < 1e-9 || n2 < 1e-9) return NaN;         // degenerate — caller treats as invalid
  let cos = (v1x * v2x + v1y * v2y + v1z * v2z) / (n1 * n2);
  cos = Math.min(1, Math.max(-1, cos));
  return (Math.acos(cos) * 180) / Math.PI;
}

/** Mean visibility of the required joints on one side. */
export function sideVisibility(lms, jointNames, side) {
  let sum = 0;
  for (const j of jointNames) sum += lms[JOINT[j][side]]?.visibility ?? 0;
  return sum / jointNames.length;
}

/**
 * Pick the side to score from: whichever has higher mean visibility.
 * If both clear the threshold we average their angles; if neither does the frame is invalid.
 * Spec: docs/05-POSE-ENGINE-SPEC.md §8 "one side occluded".
 */
export function chooseSide(lms, jointNames, threshold) {
  const l = sideVisibility(lms, jointNames, SIDE.LEFT);
  const r = sideVisibility(lms, jointNames, SIDE.RIGHT);
  const bothOk = l >= threshold && r >= threshold;
  const best = l >= r ? SIDE.LEFT : SIDE.RIGHT;
  const bestVis = Math.max(l, r);
  return { side: best, both: bothOk, visibility: bestVis, valid: bestVis >= threshold };
}

/** Primary angle for an exercise, in degrees. NaN if the frame is unusable. */
export function primaryAngle(lms, primary, jointNames, threshold) {
  const sel = chooseSide(lms, jointNames, threshold);
  if (!sel.valid) return { angle: NaN, left: NaN, right: NaN, ...sel };
  const at = (name, side) => lms[JOINT[name][side]];
  const one = (side) => angle3(at(primary.a, side), at(primary.b, side), at(primary.c, side));
  const left = one(SIDE.LEFT);
  const right = one(SIDE.RIGHT);
  const angle = sel.both ? (left + right) / 2 : one(sel.side);
  // Both sides were always being computed and then averaged away. The DIFFERENCE between them is
  // the single most clinically meaningful thing this pipeline can see, and it was being discarded.
  return { angle, left, right, ...sel };
}

/** Which required joints are not visible enough, by name. A named cue — "I can't see your
 *  knees" — beats "pose not detected" by a wide margin: it tells the player what to change.
 *  docs/03-UI-UX-SPEC.md §6 */
export function missingJoints(lms, jointNames, threshold, side) {
  const out = [];
  for (const j of jointNames) {
    const v = lms[JOINT[j][side]]?.visibility ?? 0;
    if (v < threshold) out.push(j);
  }
  return out;
}

const READABLE = {
  SHOULDER: 'shoulders', ELBOW: 'elbows', WRIST: 'wrists', HIP: 'hips',
  KNEE: 'knees', ANKLE: 'ankles', HEEL: 'heels', FOOT_INDEX: 'feet',
};

export function jointPhrase(names) {
  const words = names.map((n) => READABLE[n] ?? n.toLowerCase());
  if (words.length === 0) return '';
  if (words.length === 1) return words[0];
  if (words.length === 2) return `${words[0]} or ${words[1]}`;
  return `${words.slice(0, -1).join(', ')} or ${words[words.length - 1]}`;
}

/** Normalised height of the landmark bounding box — drives the framing hint. */
export function boxHeight(imageLms) {
  let min = Infinity, max = -Infinity;
  for (const p of imageLms) { if (p.y < min) min = p.y; if (p.y > max) max = p.y; }
  return max - min;
}

/** Horizontal knee-to-ankle offset, normalised by shin length. Squat alignment. */
export function kneeTracking(lms, side) {
  const knee = lms[JOINT.KNEE[side]], ankle = lms[JOINT.ANKLE[side]];
  const shin = Math.hypot(knee.x - ankle.x, knee.y - ankle.y, (knee.z ?? 0) - (ankle.z ?? 0));
  if (shin < 1e-6) return NaN;
  return Math.abs(knee.x - ankle.x) / shin;
}

/** Straight-line distance in metres between the outer two joints of the primary angle.
 *  Shortens as the rep deepens, so max-minus-min across a rep is depth travel in real units. */
export function spanMetres(lms, primary, side) {
  const a = lms[JOINT[primary.a][side]], c = lms[JOINT[primary.c][side]];
  if (!a || !c) return NaN;
  return Math.hypot(a.x - c.x, a.y - c.y, (a.z ?? 0) - (c.z ?? 0));
}

/** Torso line angle shoulder-hip-ankle. Push-up sag. */
export function torsoLine(lms, side) {
  return angle3(lms[JOINT.SHOULDER[side]], lms[JOINT.HIP[side]], lms[JOINT.ANKLE[side]]);
}
