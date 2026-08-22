// F5 · BALLISTIC — airborne phase, jump height, landing softness.
//
// The catch worth knowing: MediaPipe world landmarks are HIP-CENTRED, so they cannot see
// vertical translation at all — during flight the body's internal geometry barely changes and
// the origin travels with you. Jump height has to come from image-space hip Y.
//
// To get that in real units we calibrate a scale from a segment we can measure both ways: the
// hip-to-ankle distance is metric in world space and normalised in image space, so their ratio
// converts image displacement into metres. That is what makes "you jumped 31 centimetres" a
// measurement rather than a pixel count.
//
// Landing softness is a genuine injury-prevention measure, not a game gimmick: a stiff landing
// is how knees get hurt.
// docs/19-EXERCISE-LIBRARY.md §4 (F5)

import { angle3, JOINT } from '../geometry.js';

export class BallisticDetector {
  static family = 'BALLISTIC';

  constructor(spec) {
    this.spec = spec;
    this.d = spec.detector;
    this.reset();
  }

  reset() {
    this.repIndex = 0;
    this.groundY = null;        // image-space hip Y while standing
    this.scale = null;          // metres per normalised image unit
    this.airborne = false;
    this.tTakeoff = null;
    this.peakY = null;
    this.settle = [];
    this.landing = null;
  }

  #scaleFrom(world, image, side) {
    const wh = world[JOINT.HIP[side]], wa = world[JOINT.ANKLE[side]];
    const ih = image[JOINT.HIP[side]], ia = image[JOINT.ANKLE[side]];
    if (!wh || !wa || !ih || !ia) return null;
    const wd = Math.hypot(wh.x - wa.x, wh.y - wa.y, (wh.z ?? 0) - (wa.z ?? 0));
    const id = Math.hypot(ih.x - ia.x, ih.y - ia.y);
    return id > 1e-4 ? wd / id : null;
  }

  /** @param world metric landmarks @param image normalised landmarks @param tMs @param side */
  onFrame(world, image, tMs, side) {
    if (!image) return null;
    const hip = image[JOINT.HIP[side]];
    if (!hip) return null;

    if (this.scale === null) this.scale = this.#scaleFrom(world, image, side);

    // Establish the ground reference from a settled standing pose.
    this.settle.push({ t: tMs, y: hip.y });
    while (this.settle.length && tMs - this.settle[0].t > 900) this.settle.shift();
    if (this.groundY === null && this.settle.length > 20) {
      const ys = this.settle.map((p) => p.y);
      if (Math.max(...ys) - Math.min(...ys) < 0.012) this.groundY = ys.reduce((a, b) => a + b, 0) / ys.length;
      return null;
    }
    if (this.groundY === null) return null;

    const rise = this.groundY - hip.y;                     // image y grows downward
    const takeoff = this.d.takeoffRise ?? 0.035;

    if (!this.airborne && rise > takeoff) {
      this.airborne = true; this.tTakeoff = tMs; this.peakY = hip.y; this.landing = null;
      return null;
    }
    if (this.airborne) {
      if (hip.y < this.peakY) this.peakY = hip.y;
      if (rise <= takeoff * 0.4) {                         // back on the ground
        this.airborne = false;
        this.landing = { t: tMs, kneeAtContact: this.#knee(world, side), minKnee: this.#knee(world, side) };
        return null;
      }
      return null;
    }

    // Landing window: watch knee flexion for 300ms after contact, then score the jump.
    if (this.landing) {
      const k = this.#knee(world, side);
      if (Number.isFinite(k) && k < this.landing.minKnee) this.landing.minKnee = k;
      if (tMs - this.landing.t >= (this.d.landingWindowMs ?? 300)) return this.#complete(tMs);
    }
    return null;
  }

  #knee(world, side) {
    return angle3(world[JOINT.HIP[side]], world[JOINT.KNEE[side]], world[JOINT.ANKLE[side]]);
  }

  #complete(tMs) {
    const l = this.landing;
    this.landing = null;
    const riseNorm = this.groundY - this.peakY;
    const heightCm = this.scale ? riseNorm * this.scale * 100 : NaN;
    if (!(heightCm > 3)) return null;                       // a shuffle is not a jump

    const stand = this.d.standingKnee ?? 170;
    const full = this.d.softLandingFlexionDeg ?? 35;
    const softness = Number.isFinite(l.minKnee)
      ? Math.max(0, Math.min(1, (stand - l.minKnee) / full)) : 0.5;

    this.repIndex += 1;
    const target = this.d.targetHeightCm ?? 25;
    const heightScore = Math.max(0, Math.min(1, heightCm / target));
    return {
      family: 'BALLISTIC',
      repIndex: this.repIndex,
      tStartMs: this.tTakeoff, tEndMs: tMs,
      heightCm, softness, flightMs: l.t - this.tTakeoff,
      formScore: Math.max(0, Math.min(1, 0.55 * heightScore + 0.45 * softness)),
      reason: softness < 0.5 ? 'landing' : heightScore < 0.6 ? 'height' : 'none',
      signals: { height: heightCm, softness },
    };
  }
}
