// Detector factory. One interface, five movement families — which is the mechanism behind
// "five detectors, sixty-one exercises, one fatigue model". Adding an exercise is a config
// record; adding a family is one class.
// docs/09-MODULE-CONTRACTS.md §3b

import { IsometricHoldDetector } from './isometric.js';
import { CadenceDetector } from './cadence.js';
import { BallisticDetector } from './ballistic.js';
import { PoseMatchDetector } from './poseMatch.js';

export const FAMILY = {
  REP_CYCLE: 'REP_CYCLE',
  ISOMETRIC_HOLD: 'ISOMETRIC_HOLD',
  CADENCE: 'CADENCE',
  BALLISTIC: 'BALLISTIC',
  POSE_MATCH: 'POSE_MATCH',
};

/** REP_CYCLE stays inside SessionEngine, which already owns the rep FSM and the form scorer. */
export function makeDetector(spec) {
  switch (spec.family) {
    case FAMILY.ISOMETRIC_HOLD: return new IsometricHoldDetector(spec);
    case FAMILY.CADENCE: return new CadenceDetector(spec);
    case FAMILY.BALLISTIC: return new BallisticDetector(spec);
    case FAMILY.POSE_MATCH: return new PoseMatchDetector(spec);
    case FAMILY.REP_CYCLE: return null;
    default: throw new Error(`unknown family: ${spec.family}`);
  }
}

/** Which game a family's movements are naturally shaped for. docs/19-EXERCISE-LIBRARY.md §3 */
export const FAMILY_GAME = {
  REP_CYCLE: 'BOSS_FIGHT',
  ISOMETRIC_HOLD: 'SIEGE',
  CADENCE: 'PURSUIT',
  BALLISTIC: 'BREAKER',
  POSE_MATCH: 'SIGIL',
};

export { IsometricHoldDetector, CadenceDetector, BallisticDetector, PoseMatchDetector };
