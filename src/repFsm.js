// Hysteretic rep state machine.
// Naive thresholding double-counts on every tremor at the threshold. Two thresholds per
// transition plus minimum dwell times fix it.
// Spec: docs/05-POSE-ENGINE-SPEC.md §4
//
// DIRECTION: some exercises increase their primary angle toward the effort position
// (calf raise, glute bridge) instead of decreasing it (squat, push-up). We normalise by
// working in signed coordinates u = s * theta, where s = +1 for decreasing exercises and
// -1 for increasing ones. In u-space TOP is always high and BOTTOM is always low, so every
// comparison below is written once.
// Getting this wrong is how half an exercise library silently counts nothing.

export const State = { TOP: 'TOP', DESCENDING: 'DESCENDING', BOTTOM: 'BOTTOM', ASCENDING: 'ASCENDING' };

export class RepStateMachine {
  /** @param {object} det exercise.detector block from config */
  constructor(det) {
    this.d = det;
    this.s = det.topEnter > det.bottomEnter ? 1 : -1;   // +1 decreasing, -1 increasing
    this.u = {
      topEnter: this.s * det.topEnter,
      topExit: this.s * det.topExit,
      bottomEnter: this.s * det.bottomEnter,
      bottomExit: this.s * det.bottomExit,
      target: this.s * det.targetAngle,
    };
    this.reset();
  }

  reset() {
    this.state = State.TOP;
    this.repIndex = 0;
    this.stateEnteredMs = 0;
    this.rep = null;
    this.lastRepEndMs = null;
    this.topRefU = null;      // calibrated or observed rest position, in u-space
    this.seenAnyValid = false;
  }

  /** Set the calibrated rest position (degrees). */
  setTopRef(angleDeg) { this.topRefU = this.s * angleDeg; }

  toU(deg) { return this.s * deg; }
  toDeg(u) { return this.s * u; }

  #startRep(tMs, u) {
    this.rep = {
      tStartMs: tMs,
      uMin: u, uMax: u,
      tDescendStart: tMs, tBottomStart: null, tBottomEnd: null,
      uAtBottomExit: null,
      frames: 0, validFrames: 0,
    };
  }

  #enter(state, tMs) { this.state = state; this.stateEnteredMs = tMs; }

  /**
   * Feed one frame.
   * @param {number} angleDeg primary angle, NaN if the frame is invalid
   * @param {number} tMs
   * @returns {object|null} a completed rep's raw measurements, or null
   */
  onFrame(angleDeg, tMs) {
    const valid = Number.isFinite(angleDeg);
    if (this.rep) { this.rep.frames++; if (valid) this.rep.validFrames++; }
    if (!valid) return null;                       // invalid frames never move the machine

    const u = this.toU(angleDeg);
    if (!this.seenAnyValid) { this.seenAnyValid = true; this.stateEnteredMs = tMs; }
    if (this.topRefU === null) this.topRefU = u;   // fall back to first observed pose

    if (this.rep) {
      if (u < this.rep.uMin) this.rep.uMin = u;
      if (u > this.rep.uMax) this.rep.uMax = u;
    }

    const dwell = tMs - this.stateEnteredMs;

    switch (this.state) {
      case State.TOP:
        if (u >= this.u.topEnter && u > this.topRefU) this.topRefU = u;   // track a better rest pose
        if (u < this.u.topExit) { this.#startRep(tMs, u); this.#enter(State.DESCENDING, tMs); }
        break;

      case State.DESCENDING:
        if (u >= this.u.topEnter) { this.rep = null; this.#enter(State.TOP, tMs); break; }  // aborted
        if (u <= this.u.bottomEnter && dwell >= this.d.minDescendMs) {
          this.rep.tBottomStart = tMs;
          this.#enter(State.BOTTOM, tMs);
        }
        break;

      case State.BOTTOM:
        if (u > this.u.bottomExit && dwell >= this.d.minBottomMs) {
          this.rep.tBottomEnd = tMs;
          this.rep.uAtBottomExit = u;
          this.#enter(State.ASCENDING, tMs);
        }
        break;

      case State.ASCENDING: {
        if (u <= this.u.bottomEnter) { this.#enter(State.BOTTOM, tMs); break; }  // sank back down
        if (u < this.u.topEnter) break;
        const out = this.#complete(tMs, u);
        this.#enter(State.TOP, tMs);
        this.rep = null;
        if (out) return out;
        break;
      }
    }
    return null;
  }

  #complete(tMs, u) {
    const r = this.rep;
    if (!r) return null;
    const dur = tMs - r.tStartMs;

    // Validity guards — a rep failing any of these is discarded silently.
    if (dur < this.d.minRepMs || dur > this.d.maxRepMs) return null;
    if (r.tBottomStart === null || r.tBottomEnd === null) return null;
    const validRatio = r.frames > 0 ? r.validFrames / r.frames : 0;
    if (validRatio < 0.9) return null;

    const tEcc = (r.tBottomStart - r.tStartMs) / 1000;
    const tBottom = (r.tBottomEnd - r.tBottomStart) / 1000;
    const tCon = (tMs - r.tBottomEnd) / 1000;

    // Concentric angular velocity, deg/s, magnitude.
    const concentricVelocity = tCon > 0 ? Math.abs(u - r.uAtBottomExit) / tCon : 0;

    this.repIndex += 1;
    const gapSec = this.lastRepEndMs === null ? 0 : (r.tStartMs - this.lastRepEndMs) / 1000;
    this.lastRepEndMs = tMs;

    return {
      repIndex: this.repIndex,
      tStartMs: r.tStartMs, tEndMs: tMs,
      thetaMin: this.toDeg(this.s > 0 ? r.uMin : r.uMax),   // deepest point, in degrees
      thetaMax: this.toDeg(this.s > 0 ? r.uMax : r.uMin),   // shallowest point, in degrees
      uMin: r.uMin, uMax: r.uMax, uTopRef: this.topRefU, uTarget: this.u.target,
      tEccSec: tEcc, tBottomSec: tBottom, tConSec: tCon, durationMs: dur,
      concentricVelocity,
      gapSec,
      validFrameRatio: validRatio,
    };
  }
}
