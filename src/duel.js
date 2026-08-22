// Duel: event-sourced sync with self-healing, over a pluggable transport.
//
// The design that makes this cheap: sync EVENTS, not state. Each side scores its own reps and
// broadcasts one tiny message per rep. Boss HP is a set reduction over the deduplicated union
// of all events — order-independent, duplicate-safe, and identical whether there are two
// players or eight.
//
// Every message carries a `recent` tail of the sender's last 8 events, so a dropped packet is
// repaired by the next message with no acknowledgements and no retransmit timers. A receiver
// would have to miss eight consecutive reps to lose data permanently. That single trick is
// worth more than a reliability layer and costs about 64 bytes.
//
// docs/07-MULTIPLAYER-SPEC.md

export const LinkState = {
  IDLE: 'IDLE', ADVERTISING: 'ADVERTISING', SEARCHING: 'SEARCHING',
  LINKED: 'LINKED', LOST: 'LOST',
};

const TAIL = 8;
const HEARTBEAT_MS = 1200;
const LOST_AFTER_MS = 4000;

export function newPlayerId() {
  return Math.random().toString(36).slice(2, 6).toUpperCase();
}

/** Same-machine transport for two browser tabs. The Android build swaps in hotspot sockets
 *  behind this identical interface — that is the whole point of having an interface. */
export class BroadcastChannelTransport {
  constructor(room = 'clashfit-duel') {
    this.room = room;
    this.listeners = new Set();
    this.ch = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(room) : null;
    if (this.ch) this.ch.onmessage = (e) => this.listeners.forEach((f) => f(e.data));
  }
  send(msg) { this.ch?.postMessage(msg); }
  onMessage(fn) { this.listeners.add(fn); return () => this.listeners.delete(fn); }
  close() { this.ch?.close(); this.ch = null; this.listeners.clear(); }
  get available() { return !!this.ch; }
}

/** In-memory transport pair, for tests and for a deterministic replay of a duel. */
export class LoopbackTransport {
  constructor(bus) { this.bus = bus; this.listeners = new Set(); bus.add(this); }
  send(msg) { for (const t of this.bus) if (t !== this) t.#deliver(msg); }
  #deliver(msg) { this.listeners.forEach((f) => f(structuredClone(msg))); }
  onMessage(fn) { this.listeners.add(fn); return () => this.listeners.delete(fn); }
  close() { this.bus.delete(this); this.listeners.clear(); }
  get available() { return true; }
}

export class DuelSession {
  /**
   * @param {object} transport anything with send/onMessage/close
   * @param {object} opts { playerId, onRemote, onState, now }
   */
  constructor(transport, opts = {}) {
    this.t = transport;
    this.playerId = opts.playerId ?? newPlayerId();
    this.onRemote = opts.onRemote ?? (() => {});
    this.onState = opts.onState ?? (() => {});
    this.now = opts.now ?? (() => Date.now());

    this.seq = 0;
    this.sent = [];                 // our own recent events, for the tail
    this.applied = new Set();       // "player:seq" — the dedupe key
    this.peers = new Map();         // playerId -> { lastSeenMs, damage, band, name }
    this.state = LinkState.SEARCHING;
    this.unsub = this.t.onMessage((m) => this.#receive(m));
    this.#announce();
  }

  #announce() { this.t.send({ v: 1, type: 'HELLO', playerId: this.playerId, tMs: this.now() }); }

  /** Broadcast one local rep. */
  sendRep({ damage, formScore, exercise, fatigueBand }) {
    this.seq += 1;
    const ev = { seq: this.seq, damage };
    this.sent.push(ev);
    if (this.sent.length > TAIL) this.sent.shift();
    this.t.send({
      v: 1, type: 'REP', playerId: this.playerId, seq: this.seq, tMs: this.now(),
      exercise, formScore, damage, fatigueBand,
      recent: this.sent.map((e) => [e.seq, e.damage]),
    });
  }

  /** Heartbeat so a silent peer is detected as lost rather than assumed present. */
  tick() {
    const t = this.now();
    if (t - (this.lastBeat ?? 0) > HEARTBEAT_MS) {
      this.lastBeat = t;
      this.t.send({ v: 1, type: 'PING', playerId: this.playerId, tMs: t });
    }
    let anyLive = false;
    for (const [, p] of this.peers) if (t - p.lastSeenMs < LOST_AFTER_MS) anyLive = true;
    const next = this.peers.size === 0 ? LinkState.SEARCHING : anyLive ? LinkState.LINKED : LinkState.LOST;
    if (next !== this.state) { this.state = next; this.onState(next); }
    return this.state;
  }

  #receive(m) {
    if (!m || m.playerId === this.playerId) return;
    const peer = this.peers.get(m.playerId) ?? { damage: 0, band: 'FRESH' };
    peer.lastSeenMs = this.now();
    this.peers.set(m.playerId, peer);

    // HELLO must not be answered with another HELLO — two peers would announce at each other
    // forever. A new peer says HELLO once; anyone who already knows the room replies WELCOME
    // with their tail, and WELCOME is never answered.
    if (m.type === 'HELLO') { this.#welcome(); return; }
    if (m.type === 'PING') return;
    if (m.type !== 'REP' && m.type !== 'WELCOME') return;

    peer.band = m.fatigueBand ?? peer.band;
    // Apply this event and the whole tail. Idempotent by construction, so replaying the tail
    // is free and it is what repairs a dropped message.
    for (const [seq, damage] of m.recent ?? [[m.seq, m.damage]]) this.#apply(m.playerId, seq, damage);
  }

  #welcome() {
    this.t.send({
      v: 1, type: 'WELCOME', playerId: this.playerId, seq: this.seq, tMs: this.now(),
      recent: this.sent.map((e) => [e.seq, e.damage]),
    });
  }

  #apply(playerId, seq, damage) {
    const key = `${playerId}:${seq}`;
    if (this.applied.has(key)) return;
    this.applied.add(key);
    const peer = this.peers.get(playerId);
    if (peer) peer.damage += damage;
    this.onRemote(playerId, seq, damage);
  }

  get remoteDamage() { return [...this.peers.values()].reduce((a, p) => a + p.damage, 0); }
  get peerCount() { return this.peers.size; }

  close() { this.unsub?.(); this.t.close?.(); }
}
