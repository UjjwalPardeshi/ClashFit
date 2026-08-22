# 07 · Multiplayer (Duel) Specification

Two players, two iQOO phones, one boss, no server, no internet.

**Tier 1 as of 21 Aug** — team decision. The duel runs as a parallel lane on Sunday alongside the
on-device coach, one owner each. It is not a stretch goal. It is still cut before the LLM if only
one can ship (§10).

---

## 1. The design that makes this cheap

> **Sync events, not state.**

Each phone runs its own complete pipeline — camera, pose, scoring, damage — and broadcasts one tiny
message per rep. Both phones compute boss HP as a pure function of the deduplicated union of all
events received.

```
bossHp = maxHp − Σ { e.damage : e ∈ dedupe(localEvents ∪ remoteEvents) }
```

Because the function is a set reduction over an idempotent key, this design gives us for free:
- **No authoritative host.** Both sides converge on the same number.
- **No lockstep, no rollback netcode.** Order does not matter.
- **Duplicates are harmless.** Dedupe by `(playerId, seq)`.
- **Late arrivals self-correct.** A message from three seconds ago still lands correctly.

This turns the hardest-sounding feature in the project into roughly two hours of work.

---

## 2. Wire format

```kotlin
data class DuelMessage(
  val v: Int = 1,
  val playerId: String,      // 4-char random, generated at lobby
  val seq: Int,              // monotonic per player, from 1
  val tMs: Long,             // sender clock, for display ordering only
  val exercise: String,      // "squat" | "pushup"
  val formScore: Float,      // 0..1, for the opponent's feed
  val damage: Int,
  val fatigueBand: String,   // shown on the opponent chip
  val recent: List<CompactEvent>   // see §3
)
```

JSON is fine — the payload is ~120 bytes and we send at most 1/second per player. Do not spend time
on a binary format.

Control messages reuse the same envelope with `seq = 0` and a `type` field: `HELLO`, `READY`,
`START`, `BYE`.

---

## 3. Self-healing without ACKs

Every message carries a **`recent` tail: the last 8 events from the sender**, compacted to
`(seq, damage)`.

A single dropped packet is repaired by the next message that arrives, with no acknowledgements, no
retransmit timers, and no reliability layer. At one message per rep, a receiver would have to miss
eight consecutive reps to lose data permanently.

Cost: ~64 extra bytes per message. This is the single best trade in the whole feature.

---

## 4. Transport abstraction

Chosen at runtime, decided by measurement — see [ADR-004](adr/ADR-004-duel-transport.md).

```kotlin
interface DuelTransport {
  val state: StateFlow<LinkState>          // IDLE|ADVERTISING|SEARCHING|LINKED|LOST
  suspend fun host(): Result<Unit>
  suspend fun join(): Result<Unit>
  fun send(msg: DuelMessage)
  val incoming: SharedFlow<DuelMessage>
  fun close()
}
```

| Implementation | Role | Notes |
|---|---|---|
| `HotspotSocketTransport` | **primary** | Host enables a local hotspot with a fixed SSID; guest joins; plain TCP on a fixed port. Ten seconds of manual setup, and it is immune to the discovery failures that plague a hall with hundreds of contested radios. Boring and it always works. |
| `NearbyTransport` | secondary | Google Nearby Connections, `P2P_POINT_TO_POINT`. Nicer story — devices find each other. Discovery is the failure point in a crowded venue. |
| `BleTransport` | fallback | GATT notify. Our payload is tiny, so bandwidth is a non-issue. Slow to connect but very robust. |

**The interface exists so we can swap transports at the venue in ten minutes** once we find out what
actually works in that room. Build `HotspotSocketTransport` first; add others only if there is time.

---

## 5. Link loss

When `state` goes `LOST`:

1. Both phones **keep playing.** Local reps still land, local damage still applies.
2. A banner appears: *"opponent disconnected — scoring locally"*. Not a dialog, not a spinner.
3. Outgoing events queue (bounded, 64 entries).
4. On reconnect, the queue flushes and the `recent` tails reconcile both sides automatically.
5. If the fight ends while disconnected, both sides show their own result and a note that scores
   were not merged.

**The judges must never see the app stall.** Ten minutes of work for the single most likely live
failure.

---

## 6. Clock

No clock synchronisation. `tMs` is used only for ordering the opponent's rep feed visually.
Boss HP is order-independent by construction (§1), so drift is irrelevant.

The countdown at fight start uses the host's clock, broadcast as `START` with a 3-second offset.
Half a second of skew between two phones is not perceptible in a duel.

---

## 7. Lobby UX

```
SEARCHING → LINKED → both READY → 3-2-1 → FIGHT
```

Every state is named on screen. Never a bare spinner — a judge watching an unexplained spinner
assumes it is broken, and they are usually right.

**Casual mode toggle lives here.** When player two is a judge, this is what makes the demo enjoyable
rather than embarrassing: damage ×1.6, form floor 0.6, boss HP ×0.5. See `04-GAME-DESIGN.md` §7.

---

## 8. Demo staging

Covered fully in `11-DEMO-SCRIPT.md`. The essentials:

- **Squats, not push-ups.** Two people doing push-ups needs floor space that does not exist at a
  judging table.
- **Recruit a judge as player two.** It proves the system works on a body it has never seen, and
  judges enjoy being in the demo.
- **Mirror one phone to the laptop over Office Kit** so the room watches the shared boss HP on a
  large display while the players' phones face the players.
- Rehearse the pairing sequence until it takes under 30 seconds. Practise it with the phones in
  flight mode and the hotspot pre-configured.

---

## 9. Test plan

| Test | Pass condition |
|---|---|
| Two phones, clean link, 20 reps each | Both show identical final boss HP |
| Kill the link mid-fight, restore after 15s | Both converge to identical HP within 2s of reconnect |
| Kill the link and never restore | Both finish, both show "scored locally", neither hangs |
| Drop 30% of packets artificially | Final HP still identical — `recent` tails repair it |
| Duplicate every message | Final HP unchanged — dedupe holds |
| Start with one phone already in a fight | Late joiner is rejected cleanly with a named message |

The packet-drop and duplicate tests can be run entirely on the laptop against two emulator
instances before the event. Do that in the eleven days, not on Sunday morning.

---

## 10. Cut order

If Sunday runs short, cut in this order and stop when you are safe:

1. `NearbyTransport` and `BleTransport` — ship hotspot only
2. The opponent's live rep feed — show only the shared HP bar
3. The whole duel → fall back to **pass-the-phone**: player A does 30 seconds, hand over, player B
   does 30 seconds, same device, zero networking. On stage this is arguably a *better* demo, because
   the whole room watches one screen instead of splitting attention.

**Cut multiplayer before you cut the on-device model.** Multiplayer is a feature; the local LLM is
the thesis. Tier 1 status raises the duel's priority against *everything else* — it does not raise
it above the coach.

**Gate G5b (Sun 05:30):** two phones linked, 20 reps each, identical final boss HP. If that has not
happened by 05:30, stop and fall back to pass-the-phone. Do not carry transport debugging into the
06:30 freeze.
