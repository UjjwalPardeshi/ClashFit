// Canvas rendering: camera feed, skeleton overlay, the boss, hit flashes and damage numerals.
// Neon tactical per docs/03-UI-UX-SPEC.md §2. The boss is drawn geometrically — the documented
// fallback in docs/15-ASSET-BRIEF.md §1, and exactly right for a throwaway prototype.

export const C = {
  ground: '#07090C', surface: '#10141B', ink: '#F2F5F8', mute: '#8A94A4',
  damage: '#FF3B5C', clean: '#22D3A0', shallow: '#F5A524', system: '#38BDF8', fatigue: '#A78BFA',
  hpFull: '#34D399', hpMid: '#FBBF24', hpLow: '#F43F5E',
};

const EDGES = [
  [11,12],[11,23],[12,24],[23,24],
  [11,13],[13,15],[12,14],[14,16],
  [23,25],[25,27],[24,26],[26,28],
  [27,31],[28,32],[27,29],[28,30],
];

function hexA(hex, a) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

export class Renderer {
  constructor(canvas) {
    this.cv = canvas;
    this.ctx = canvas.getContext('2d');
    this.flash = null;          // {color, until}
    this.pops = [];             // floating damage numerals
    this.shake = 0;
    this.mirror = true;
  }

  resize() {
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const r = this.cv.getBoundingClientRect();
    this.cv.width = Math.round(r.width * dpr);
    this.cv.height = Math.round(r.height * dpr);
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    this.w = r.width; this.h = r.height;
  }

  hit(verdictName, damage) {
    const color = verdictName === 'SHALLOW' ? C.shallow : C.clean;
    this.flash = { color, until: performance.now() + 120 };
    this.pops.push({ text: String(damage), t0: performance.now(), color: C.damage });
    this.shake = verdictName === 'SHALLOW' ? 2 : 6;
  }

  draw(video, state, landmarks) {
    const { ctx, w, h } = this;
    const now = performance.now();

    ctx.save();
    if (this.shake > 0) {
      ctx.translate((Math.random() - 0.5) * this.shake, (Math.random() - 0.5) * this.shake);
      this.shake *= 0.82;
      if (this.shake < 0.3) this.shake = 0;
    }

    ctx.fillStyle = C.ground;
    ctx.fillRect(-10, -10, w + 20, h + 20);

    this.#video(video, w, h);
    if (landmarks) this.#skeleton(landmarks, w, h, state);
    if (state.phase === 'CALIBRATING') this.#calibration(state, w, h, now);
    else if (state.gameState) this.#familyGame(state, w, h, now);
    else this.#boss(state, w, h, now);
    this.#pops(now, w, h);

    if (this.flash && now < this.flash.until) this.#edgeFlash(this.flash.color, w, h);
    ctx.restore();
  }

  #video(video, w, h) {
    if (!video || !video.videoWidth) return;
    const { ctx } = this;
    const vw = video.videoWidth, vh = video.videoHeight;
    const scale = Math.max(w / vw, h / vh);
    const dw = vw * scale, dh = vh * scale;
    ctx.save();
    ctx.globalAlpha = 0.5;
    if (this.mirror) { ctx.translate(w, 0); ctx.scale(-1, 1); }
    ctx.drawImage(video, (w - dw) / 2, (h - dh) / 2, dw, dh);
    ctx.restore();
    this.vidBox = { scale, dw, dh, ox: (w - dw) / 2, oy: (h - dh) / 2 };
  }

  #px(p, w, h) {
    const b = this.vidBox;
    let x = p.x, y = p.y;
    if (!b) return { x: x * w, y: y * h };
    const px = b.ox + x * b.dw, py = b.oy + y * b.dh;
    return { x: this.mirror ? w - px : px, y: py };
  }

  #skeleton(lms, w, h, state) {
    const { ctx } = this;
    const dim = state.phase === 'FRAMING_LOST';
    ctx.save();
    ctx.globalAlpha = dim ? 0.25 : 0.9;
    ctx.lineWidth = 3;
    ctx.strokeStyle = dim ? C.mute : C.system;
    ctx.beginPath();
    for (const [a, b] of EDGES) {
      const pa = lms[a], pb = lms[b];
      if (!pa || !pb || (pa.visibility ?? 1) < 0.4 || (pb.visibility ?? 1) < 0.4) continue;
      const A = this.#px(pa, w, h), B = this.#px(pb, w, h);
      ctx.moveTo(A.x, A.y); ctx.lineTo(B.x, B.y);
    }
    ctx.stroke();
    ctx.fillStyle = dim ? C.mute : C.ink;
    for (const i of [11,12,13,14,15,16,23,24,25,26,27,28]) {
      const p = lms[i]; if (!p || (p.visibility ?? 1) < 0.4) continue;
      const P = this.#px(p, w, h);
      ctx.beginPath(); ctx.arc(P.x, P.y, 4, 0, Math.PI * 2); ctx.fill();
    }
    ctx.restore();
  }

  #boss(state, w, h, now) {
    const { ctx } = this;
    const c = state.combat;
    const cx = w * 0.72, cy = h * 0.42;
    const breathe = 1 + Math.sin(now / 1500) * 0.02;
    const R = Math.min(w, h) * 0.17 * breathe;

    const pct = c.hpPct;
    const col = pct > 0.6 ? C.hpFull : pct > 0.25 ? C.hpMid : C.hpLow;

    ctx.save();
    ctx.translate(cx, cy);
    if (c.staggered) ctx.rotate(Math.sin(now / 90) * 0.05);

    // outer ring
    ctx.strokeStyle = col; ctx.lineWidth = 3; ctx.globalAlpha = c.dead ? 0.25 : 0.85;
    ctx.beginPath();
    for (let i = 0; i <= 6; i++) {
      const a = (i / 6) * Math.PI * 2 - Math.PI / 2;
      const p = { x: Math.cos(a) * R, y: Math.sin(a) * R };
      i ? ctx.lineTo(p.x, p.y) : ctx.moveTo(p.x, p.y);
    }
    ctx.closePath(); ctx.stroke();

    // core, scaled by remaining HP
    ctx.globalAlpha = c.dead ? 0.15 : 0.5;
    ctx.fillStyle = col;
    ctx.beginPath();
    const r2 = R * (0.25 + 0.55 * pct);
    for (let i = 0; i <= 3; i++) {
      const a = (i / 3) * Math.PI * 2 + now / 4000;
      const p = { x: Math.cos(a) * r2, y: Math.sin(a) * r2 };
      i ? ctx.lineTo(p.x, p.y) : ctx.moveTo(p.x, p.y);
    }
    ctx.closePath(); ctx.fill();
    ctx.restore();

    // HP bar
    const bw = Math.min(w * 0.42, 460), bx = cx - bw / 2, by = cy + R + 34;
    ctx.save();
    ctx.fillStyle = 'rgba(255,255,255,0.07)';
    ctx.fillRect(bx, by, bw, 16);
    ctx.fillStyle = col;
    ctx.fillRect(bx, by, bw * pct, 16);
    ctx.strokeStyle = 'rgba(255,255,255,0.18)'; ctx.lineWidth = 1;
    ctx.strokeRect(bx + 0.5, by + 0.5, bw - 1, 15);
    ctx.font = '600 12px ui-monospace, monospace';
    ctx.fillStyle = C.mute; ctx.textAlign = 'left';
    ctx.fillText(c.bossName ?? 'BOSS', bx, by - 8);
    ctx.textAlign = 'right';
    ctx.fillText(`${Math.round(pct * 100)}%  ·  ${c.phase}`, bx + bw, by - 8);
    ctx.restore();
  }

  /** A game shaped like the movement gets a visual shaped like the game. Sigil in particular
   *  must not look like a boss fight — it is non-combat by design. */
  #familyGame(state, w, h, now) {
    const g = state.gameState;
    const { ctx } = this;
    const cx = w * 0.72, cy = h * 0.42;
    const R = Math.min(w, h) * 0.17;

    if (g.game === 'SIGIL') {
      // A constellation, not a boss. Each asana lights one point; accuracy sets its brightness.
      ctx.save();
      ctx.translate(cx, cy);
      const N = g.segments;
      const pts = Array.from({ length: N }, (_, i) => {
        const a = (i / N) * Math.PI * 2 - Math.PI / 2;
        return { x: Math.cos(a) * R, y: Math.sin(a) * R };
      });
      ctx.strokeStyle = hexA(C.system, 0.18); ctx.lineWidth = 1;
      ctx.beginPath();
      pts.forEach((p, i) => (i ? ctx.lineTo(p.x, p.y) : ctx.moveTo(p.x, p.y)));
      ctx.closePath(); ctx.stroke();

      ctx.strokeStyle = hexA(C.fatigue, 0.9); ctx.lineWidth = 2.5;
      ctx.beginPath();
      for (let i = 0; i < g.lit; i++) (i ? ctx.lineTo(pts[i].x, pts[i].y) : ctx.moveTo(pts[i].x, pts[i].y));
      if (g.lit > 1) ctx.stroke();

      pts.forEach((p, i) => {
        const seg = g.segmentsDetail?.[i];
        ctx.globalAlpha = seg ? 0.35 + 0.65 * seg.brightness : 0.14;
        ctx.fillStyle = seg ? C.fatigue : C.mute;
        ctx.beginPath(); ctx.arc(p.x, p.y, seg ? 7 + 4 * seg.brightness : 4, 0, Math.PI * 2); ctx.fill();
      });
      ctx.globalAlpha = 1;
      ctx.restore();
      this.#label(cx, cy + R + 40, `${g.lit} / ${g.segments} lit`, C.fatigue, w);
      return;
    }

    if (g.game === 'SIEGE') {
      this.#bar(cx - Math.min(w * 0.21, 230), cy - R - 46, Math.min(w * 0.42, 460),
                g.bossHpPct, C.hpFull, 'SIEGE', `${Math.round(g.bossHpPct * 100)}%`);
      this.#bar(cx - Math.min(w * 0.21, 230), cy + R + 34, Math.min(w * 0.42, 460),
                g.playerHpPct, g.playerHpPct > 0.4 ? C.system : C.damage,
                'YOUR SHIELD', `${g.playerHp}`);
      this.#shieldRing(cx, cy, R, g.playerHpPct, now);
      return;
    }

    if (g.game === 'PURSUIT') {
      const bw = Math.min(w * 0.42, 460), bx = cx - bw / 2, by = cy;
      ctx.save();
      ctx.strokeStyle = hexA(C.mute, 0.35); ctx.lineWidth = 2;
      ctx.beginPath(); ctx.moveTo(bx, by); ctx.lineTo(bx + bw, by); ctx.stroke();
      const you = Math.min(1, g.progress);
      const them = Math.max(0, Math.min(1, g.pursuer / (g.distance || 1) * you));
      ctx.fillStyle = C.clean;
      ctx.beginPath(); ctx.arc(bx + bw * you, by, 8, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = C.damage;
      ctx.beginPath(); ctx.arc(bx + bw * them, by, 8, 0, Math.PI * 2); ctx.fill();
      ctx.restore();
      this.#label(cx, by + 42, `${g.distance.toFixed(0)}m · gap ${g.gapM.toFixed(0)}m`,
                  g.gapM < 6 ? C.damage : C.ink, w);
      return;
    }

    if (g.game === 'BREAKER') {
      const cols = 4, rows = Math.ceil(g.floors / cols);
      const cw = Math.min(w * 0.09, 62), ch = 20, gap = 6;
      const x0 = cx - (cols * (cw + gap) - gap) / 2;
      const y0 = cy - (rows * (ch + gap) - gap) / 2;
      ctx.save();
      for (let i = 0; i < g.floors; i++) {
        const r = Math.floor(i / cols), c = i % cols;
        const broken = i < g.broken;
        ctx.globalAlpha = broken ? 0.15 : 0.9;
        ctx.fillStyle = broken ? C.mute : C.damage;
        ctx.fillRect(x0 + c * (cw + gap), y0 + r * (ch + gap), cw, ch);
      }
      ctx.restore();
      this.#label(cx, y0 + rows * (ch + gap) + 26,
                  `${g.broken} / ${g.floors} floors · best ${g.bestCm.toFixed(0)}cm`, C.damage, w);
    }
  }

  /** The calibration guide: a target frame, a distance arrow, and a hold ring. Never a spinner.
   *  docs/03-UI-UX-SPEC.md §6 */
  #calibration(state, w, h, now) {
    const { ctx } = this;
    const bw = Math.min(w * 0.30, 300), bh = Math.min(h * 0.78, 620);
    const bx = (w - bw) / 2, by = (h - bh) / 2;

    const ok = state.calib === 'HOLDING' || state.calib === 'READY';
    const color = ok ? C.clean : state.calib === 'SEARCHING' ? C.mute : C.shallow;

    ctx.save();
    ctx.strokeStyle = hexA(color, 0.75);
    ctx.lineWidth = 2;
    ctx.setLineDash(ok ? [] : [10, 8]);
    const corner = 36;
    for (const [cx0, cy0, dx, dy] of [
      [bx, by, 1, 1], [bx + bw, by, -1, 1], [bx, by + bh, 1, -1], [bx + bw, by + bh, -1, -1],
    ]) {
      ctx.beginPath();
      ctx.moveTo(cx0, cy0 + dy * corner); ctx.lineTo(cx0, cy0); ctx.lineTo(cx0 + dx * corner, cy0);
      ctx.stroke();
    }
    ctx.setLineDash([]);

    // distance hint, driven by the landmark bounding box
    if (state.framing === 'TOO_FAR' || state.framing === 'TOO_CLOSE') {
      const up = state.framing === 'TOO_CLOSE';
      const ax = w / 2, ay = by + bh + 34;
      ctx.strokeStyle = C.shallow; ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.moveTo(ax, ay + (up ? 16 : 0)); ctx.lineTo(ax, ay + (up ? 0 : 16));
      ctx.moveTo(ax - 8, ay + (up ? 8 : 8)); ctx.lineTo(ax, ay + (up ? 0 : 16));
      ctx.lineTo(ax + 8, ay + 8);
      ctx.stroke();
    }

    // hold ring — 2 seconds of stillness before the fight starts
    if (state.calib === 'HOLDING' || state.calib === 'READY') {
      const r = 46, cx0 = w / 2, cy0 = by + bh + 78;
      ctx.strokeStyle = hexA(C.ink, 0.14); ctx.lineWidth = 6;
      ctx.beginPath(); ctx.arc(cx0, cy0, r, 0, Math.PI * 2); ctx.stroke();
      ctx.strokeStyle = C.clean; ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.arc(cx0, cy0, r, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * (state.calibProgress ?? 0));
      ctx.stroke(); ctx.lineCap = 'butt';
    }
    ctx.restore();
  }

  #bar(x, y, wid, pct, color, left, right) {
    const { ctx } = this;
    ctx.save();
    ctx.fillStyle = 'rgba(255,255,255,0.07)'; ctx.fillRect(x, y, wid, 16);
    ctx.fillStyle = color; ctx.fillRect(x, y, wid * Math.max(0, Math.min(1, pct)), 16);
    ctx.strokeStyle = 'rgba(255,255,255,0.18)'; ctx.lineWidth = 1;
    ctx.strokeRect(x + 0.5, y + 0.5, wid - 1, 15);
    ctx.font = '600 12px ui-monospace, monospace'; ctx.fillStyle = C.mute;
    ctx.textAlign = 'left'; ctx.fillText(left, x, y - 8);
    ctx.textAlign = 'right'; ctx.fillText(right, x + wid, y - 8);
    ctx.restore();
  }

  #shieldRing(cx, cy, R, pct, now) {
    const { ctx } = this;
    ctx.save();
    ctx.translate(cx, cy);
    ctx.strokeStyle = hexA(pct > 0.4 ? C.system : C.damage, 0.55 + 0.25 * Math.sin(now / 400));
    ctx.lineWidth = 5;
    ctx.beginPath();
    ctx.arc(0, 0, R * 0.8, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * Math.max(0.02, pct));
    ctx.stroke();
    ctx.restore();
  }

  #label(cx, y, text, color, w) {
    const { ctx } = this;
    ctx.save();
    ctx.font = '600 13px ui-monospace, monospace';
    ctx.textAlign = 'center'; ctx.fillStyle = color;
    ctx.fillText(text, cx, y);
    ctx.restore();
  }

  #pops(now, w, h) {
    const { ctx } = this;
    this.pops = this.pops.filter((p) => now - p.t0 < 700);
    for (const p of this.pops) {
      const k = (now - p.t0) / 700;
      const scale = k < 0.15 ? 1 + (0.15 - k) * 1.7 : 1;
      ctx.save();
      ctx.globalAlpha = 1 - k;
      ctx.translate(w * 0.72, h * 0.42 - k * 70);
      ctx.scale(scale, scale);
      ctx.font = '800 46px ui-sans-serif, system-ui, sans-serif';
      ctx.textAlign = 'center'; ctx.fillStyle = p.color;
      ctx.fillText(p.text, 0, 0);
      ctx.restore();
    }
  }

  #edgeFlash(color, w, h) {
    const { ctx } = this;
    const g = ctx.createRadialGradient(w / 2, h / 2, Math.min(w, h) * 0.3, w / 2, h / 2, Math.max(w, h) * 0.72);
    g.addColorStop(0, 'rgba(0,0,0,0)');
    g.addColorStop(1, color);
    ctx.save(); ctx.globalAlpha = 0.5; ctx.fillStyle = g; ctx.fillRect(0, 0, w, h); ctx.restore();
  }
}
