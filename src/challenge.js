// Challenge cards — community that works with no server.
//
// We have no backend and are not building one; the offline claim IS the product. So social has
// to be local-first, and that constraint produces a better story than a cloud leaderboard: a
// challenge is a short string. Send it however you like — chat, email, a QR someone scans — and
// the recipient races exactly what you did.
//
// A ghost is already just a recorded rep timeline, so a challenge is that timeline plus what it
// was recorded doing. Encoding it compactly is the whole feature.
//
// docs/20-MULTIPLAYER-MODES.md §5, docs/23-META-PROGRESSION.md §7

const PREFIX = 'CF1:';
const B36 = 36;

/** URL-safe base64 that works in a browser and in Node without a polyfill. */
function toB64(str) {
  const bytes = new TextEncoder().encode(str);
  let bin = '';
  for (const b of bytes) bin += String.fromCharCode(b);
  const b64 = typeof btoa === 'function' ? btoa(bin) : Buffer.from(bytes).toString('base64');
  return b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function fromB64(s) {
  const b64 = s.replace(/-/g, '+').replace(/_/g, '/');
  const bin = typeof atob === 'function' ? atob(b64) : Buffer.from(b64, 'base64').toString('binary');
  const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

/** Simple checksum. Not security — it catches a truncated or mangled paste, which is the
 *  failure that actually happens when a code travels through a chat app. */
function checksum(s) {
  let h = 5381;
  for (let i = 0; i < s.length; i++) h = ((h * 33) ^ s.charCodeAt(i)) >>> 0;
  return h.toString(B36);
}

/** Delta-encode the timeline: gaps and damages are small numbers, and base36 keeps them short. */
function packEvents(events) {
  let last = 0;
  return events.map((e) => {
    const dt = Math.max(0, Math.round(e.t - last));
    last = e.t;
    return `${dt.toString(B36)}.${Math.round(e.damage).toString(B36)}`;
  }).join('~');
}

function unpackEvents(packed) {
  if (!packed) return [];
  let t = 0;
  return packed.split('~').filter(Boolean).map((chunk) => {
    const [dt, dmg] = chunk.split('.');
    t += parseInt(dt, B36) || 0;
    return { t, damage: parseInt(dmg, B36) || 0 };
  });
}

/**
 * @param {object} c { kind, exerciseId, mode, name, target, ghost }
 * @returns {string} a short shareable code
 */
export function encodeChallenge(c) {
  const body = JSON.stringify({
    v: 1,
    k: c.kind ?? 'GHOST',
    e: c.exerciseId ?? 'squat',
    m: c.mode ?? 'GHOST_RACE',
    n: (c.name ?? '').slice(0, 24),
    t: c.target ?? null,
    g: c.ghost ? packEvents(c.ghost.events ?? []) : '',
  });
  return PREFIX + toB64(body) + '.' + checksum(body);
}

export function decodeChallenge(code) {
  const raw = String(code ?? '').trim();
  if (!raw.startsWith(PREFIX)) throw new Error('Not a ClashFit challenge code.');
  const rest = raw.slice(PREFIX.length);
  const dot = rest.lastIndexOf('.');
  if (dot < 0) throw new Error('Challenge code is incomplete.');
  const body = fromB64(rest.slice(0, dot));
  if (checksum(body) !== rest.slice(dot + 1)) {
    throw new Error('Challenge code is damaged — it was probably truncated in transit.');
  }
  const p = JSON.parse(body);
  if (p.v !== 1) throw new Error(`Challenge code is version ${p.v}, this build reads version 1.`);
  return {
    kind: p.k, exerciseId: p.e, mode: p.m, name: p.n || null, target: p.t ?? null,
    ghost: p.g
      ? { type: 'clashfit-ghost', v: 1,
          meta: { name: p.n || 'Challenge', exercise: p.e, fromChallenge: true },
          events: unpackEvents(p.g) }
      : null,
  };
}

/** A one-line human summary, for showing before someone accepts. */
export function describeChallenge(c) {
  const reps = c.ghost?.events?.length ?? 0;
  const dmg = c.ghost?.events?.reduce((a, e) => a + e.damage, 0) ?? 0;
  const who = c.name ? `${c.name}: ` : '';
  if (c.kind === 'TARGET' && c.target != null) {
    return `${who}beat ${c.target} on ${c.exerciseId.replace(/_/g, ' ')}`;
  }
  return `${who}${reps} reps · ${dmg} damage on ${c.exerciseId.replace(/_/g, ' ')}`;
}

/** Built from a finished run — the only place a challenge should come from. */
export function challengeFromSession({ reps, exerciseId, name, mode }) {
  if (!reps?.length) return null;
  const t0 = reps[0].tStartMs;
  return {
    kind: 'GHOST', exerciseId, mode: mode ?? 'GHOST_RACE', name,
    ghost: { events: reps.map((r) => ({ t: Math.round(r.tEndMs - t0), damage: r.damage ?? 0 })) },
  };
}
