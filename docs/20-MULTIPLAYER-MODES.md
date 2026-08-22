# 20 · Solo, Versus, Co-op and Community

Every mode ClashFit supports, from one person alone to a room full of people to an offline
community. **Weekend reality is §6** — kept separate so scope decisions stay yours.

---

## 1. Why this is nearly free

The duel protocol in [07-MULTIPLAYER-SPEC](07-MULTIPLAYER-SPEC.md) syncs **events, not state**:

```
bossHp = maxHp − Σ { e.damage : e ∈ dedupe(all events from all players) }
```

That is a set reduction over an idempotent key. It does not care whether there are two players or
eight. **The duel design is already a raid design** — going from 2 to N is a topology change in the
transport, not a change to the game.

For N players use a **star topology**: one phone hosts (hotspot or `P2P_STAR`) and relays events to
everyone. The host is a relay, not an authority — every phone still computes boss HP itself, so
there is no desync risk and no host-migration problem if someone drops.

---

## 2. Solo

| Mode | Rules |
|---|---|
| **Boss Fight** | The core. Reps damage a boss with phases and fatigue-adaptive behaviour. |
| **Time Attack** | 60 seconds, maximum damage. The demo-shaped mode. |
| **Ghost Race** | Race a recorded rep timeline — your past self, a friend's set, or a shipped pacer. |
| **Survival** | Endless waves, fatigue mercy disabled. Fatigue genuinely ends the run. |
| **Boss Rush** | Three bosses back to back, no rest. |
| **Siege / Sigil / Pursuit / Breaker** | Family games for holds, yoga, cardio and jumps — [19-EXERCISE-LIBRARY](19-EXERCISE-LIBRARY.md) §3. |

**Ghost Race is the bridge between solo and social.** A ghost is a small file. Export it, send it to
someone, they race it — asynchronous competition with no server and no simultaneous presence. See §5.

---

## 3. Two players

| Mode | Rules | Notes |
|---|---|---|
| **Duel** | Two phones, one boss. Highest total damage wins — **not** highest rep count. | Tier 1. Best played under Time Attack rules: 60 seconds, bounded. |
| **Tug of War** | One bar between two players. Your damage pushes it toward them. | Extremely readable on a mirrored screen — the audience understands it instantly. |
| **Pass-the-phone** | One device. 30 seconds each, alternating. | Zero networking. The duel fallback, and on stage arguably a better demo because the whole room watches one screen. |
| **Mirror Match** | Both players must match a target tempo. Closest to the target wins the round. | Uses the tempo sub-score. Rewards control over speed. |

---

## 4. Group — 3 to 8 people in one room

This is where the product becomes social rather than competitive, and it is the strongest
"would someone keep using it" answer we have.

### RAID — co-operative, the flagship
One boss, `maxHp × playerCount`. Everyone hits it together. Shared HP bar on every screen.
Contribution shown per player at the end.

Nearly free given event sourcing. **A raid is a duel with more senders.**

*Later depth (roadmap, not weekend):* soft roles that emerge from what people are already doing —
whoever holds the highest combo becomes the **Striker** (damage bonus), whoever is holding a plank
is the **Anchor** (team shield), whoever keeps cadence highest is the **Runner** (regenerates team
stamina). Roles from behaviour rather than from a lobby menu.

### RELAY — N people, few phones
Teams take turns on the same device. 30 seconds each, boss HP carries across the handover.

**Practically the most important group mode for the event**, because it needs no extra hardware.
Eight people can play on two phones.

### LAST STANDING — elimination by fatigue
Everyone performs the same exercise simultaneously. **When your fatigue band hits `GASSED`, you are
out.** Last player still in wins.

This is the mode that only we can build. Every other fitness app would eliminate on rep count;
ours eliminates on *measured physiological fatigue*. It is our novelty turned into a party game, and
it is a genuinely strong pitch line.

### TEAM VS TEAM
Two teams, two bosses, first team to kill theirs wins. Or one boss and a tug-of-war bar.

### CIRCUIT — a room on a timer
A prescribed sequence across families — 10 squats, 30s plank, 30s high knees, one asana — everyone
moving through it together, scores aggregated. This is the gym-class / office-break mode.

---

## 5. Community without a server

We have no backend and we are not building one — the offline claim is the product ([00-PRD](00-PRD.md)
principle 3). So community features must be **local-first**. That constraint produces a better story
than a cloud leaderboard would:

> **"Community without a server."**

| Feature | How it works offline |
|---|---|
| **Ghost sharing** | A ghost is a small file. Share it over any channel — chat, email, a QR code for short ones. The recipient races it. Asynchronous competition, zero infrastructure. |
| **Challenge cards** | **Built.** A finished run encodes to a ~300-character code: `CF1:` + payload + checksum, with the timeline delta-encoded in base36. Send it through anything that carries text; the recipient races exactly what you did. A test asserts `challenge.js` never reaches for fetch, storage or a URL — if it did, the offline claim would quietly stop being true. QR is just a transport for the same string, and on Android that is one zxing call. |
| **Crew ledger** | Each phone keeps its own crew results. Merge ledgers whenever two phones are in the same room. Eventually-consistent leaderboards, no server. |
| **Local room leaderboard** | Persisted per device, for relay and pass-the-phone sessions. |

**Needs a backend, therefore roadmap only** — say so plainly if a judge asks: daily city boss with a
global damage pool, cross-city guilds, and worldwide leaderboards. The city boss is a strong deck
slide precisely because it mirrors iQOO's own city-battle structure, but it is not something we can
ship offline.

---

## 6. Weekend reality

| | Weekend | Why |
|---|---|---|
| **Ships** | Solo modes + **Duel** (2 phones) + **Pass-the-phone** + **Ghost Race** | Duel is Tier 1; the other two are near-free given the same code path. |
| **If ahead** | **Relay** (~30 min) and **Last Standing pass-the-phone** (~30 min) | Both work on the hardware you actually have. |
| **Hardware limit** | You have **two loaner phones**, one per person. | A 4+ player raid needs 4 devices. |
| **Raid demo** | Only if you sideload onto your own Android phones — and **confirm with organisers first**, since the rule is that the final demo must be presented on an iQOO device. | Do not discover this constraint on stage. |
| **Deck** | The full group and community architecture | This is the retention story, and it is where the 30% lives. |

**Built in the prototype:** Pass the phone, Last Standing and Circuit all work on one device,
with the roster and turn logic in `src/roster.js` and 7 assertions covering turn cycling,
elimination-by-fatigue, skipping eliminated players, and the winner being decided on damage rather
than rep count. Circuit walks a cross-family sequence and a test asserts the default one spans at
least four movement families.

**The practical group demo, with two phones:**
- **Relay** — hand the phone between judges, boss HP carries. Works with any number of people.
- **Last Standing, pass-the-phone** — three judges take turns, fatigue eliminates them one by one.
  One device. And it puts *our* fatigue system at the centre of the moment.

**The line for the deck:** *"Two players or eight, the sync is the same three lines of maths — and
none of it needs a server."*
