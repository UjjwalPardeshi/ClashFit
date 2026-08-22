# ADR-004 · Duel transport

**Status:** Proposed — confirm with the 26 Aug field measurement
**Date:** 21 Aug 2026

## Context

Two phones, one shared boss, no server, no internet. The duel is Tier 2 and must never block or
break the demo. The venue is a hall with several hundred contested 2.4/5GHz radios, which is the
worst possible environment for peer discovery.

Payload is tiny: roughly one 120-byte message per rep, at most one per second per player.

## Options

| Option | Connect reliability in a crowded hall | Setup friction | Notes |
|---|---|---|---|
| **Hotspot + TCP sockets** | Very high — no discovery step at all | ~10s manual join, pre-configurable | Boring. Works. No Play Services dependency. |
| **Nearby Connections** (`P2P_POINT_TO_POINT`) | Medium — BLE discovery is exactly what degrades under RF congestion | Zero, when it works | Nicer story: "the phones just find each other". Requires Play Services and location/nearby permissions. |
| **BLE GATT notify** | High | Medium — slow to connect | Bandwidth is a non-issue at our payload size. Very robust once linked. |
| Wi-Fi Direct (raw `WifiP2pManager`) | Medium | High | More code than Nearby for no additional reliability. Rejected. |

## Decision

**`HotspotSocketTransport` as primary. `NearbyTransport` as an optional upgrade if the 26 August
field test shows it connecting reliably. `BleTransport` only if there is spare time.**

All three sit behind the `DuelTransport` interface (`07-MULTIPLAYER-SPEC.md` §4) so the choice can
be changed at the venue in ten minutes once we see what the room actually does.

The reasoning is asymmetric risk. Nearby's advantage is 10 seconds of setup friction. Its failure
mode is a demo that will not start in front of a jury. Ten seconds of pre-rehearsed hotspot joining
costs nothing on stage.

**Independent of transport, the protocol is what makes this safe:** event sourcing with a `recent`
tail (`07-MULTIPLAYER-SPEC.md` §3) means dropped packets self-repair with no ACKs and no retransmit
logic. A receiver would have to miss eight consecutive reps to lose data. That design is what turns
a scary feature into two hours of work.

## Consequences

- Host must be able to enable a hotspot on the loaner. **Verify at check-in** — OriginOS may gate
  this, and if it does, `NearbyTransport` becomes primary by default.
- `NearbyTransport` needs Play Services plus `NEARBY_WIFI_DEVICES` / location permissions. The
  hotspot path does not.
- Cut order if Sunday runs short: extra transports → opponent rep feed → the whole duel, falling
  back to pass-the-phone. See `07-MULTIPLAYER-SPEC.md` §10.
