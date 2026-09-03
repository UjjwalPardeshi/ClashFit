# 33 · Outbreak — the outdoor chase

Decided 30 Aug 2026. Declared in `config/combat.json` as `modes.OUTBREAK`, `enabled: false`.

> **Update (3 Sep 2026):** The app now declares `INTERNET` and `ACCESS_NETWORK_STATE` permissions
> for Firebase Auth, Firestore profiles, and cloud leaderboards. The tradeoff this document weighs —
> adding a network permission — has already been taken. Outbreak remains the only mode that requests
> location or downloads map tiles; the analysis below is still valid as a record of that decision.

Every other mode in ClashFit runs with the radio off. **This one does not, and that has to be said
plainly everywhere it appears** — see [§ What it costs us](#what-it-costs-us) below, which is the
part of this document that actually matters.

---

## What it is

A real-world chase, on a real map.

1. You start the mode. The map centres on where you are.
2. You get a **sixty-second head start**.
3. Pursuers spawn around you, inside a 400 m radius.
4. You run. Ammo caches are scattered on the map; reaching one arms you.
5. A pursuer that closes to within 12 m catches you.

The chase is not a metaphor and the distance is not simulated. You are outside, moving, and the
thing following you is drawn at a real coordinate.

## How it connects to the rest of the engine

Outbreak is not a second product bolted on. It reuses what already exists:

| Piece | Reused from |
| --- | --- |
| Pace and cadence | The `CADENCE` detector family that already drives **Pursuit** indoors |
| Fatigue bands | The same estimator, the same four bands, the same latching |
| The coach | The same between-sets summary and the same validator |
| Session record | The same store, the same export |

Position comes from GPS. **Effort still comes from the phone's own motion sensing**, which is why a
pursuer closes when your cadence drops rather than only when your coordinate stops moving.

## Configuration

```jsonc
"OUTBREAK": {
  "enabled": false,          // not in the current build
  "requiresNetwork": true,   // map tiles
  "requiresLocation": true,  // GPS
  "headStartSec": 60,
  "spawnRadiusM": 400,
  "captureRadiusM": 12,
  "ammoPickups": 6
}
```

---

## What it costs us

This is the entry that future-us will want to have read.

The core privacy guarantee is clear: **the camera pipeline never touches the network**. Frames,
landmarks, and rep timelines stay on the device. That is still true and still the competitive
point.

**Outbreak adds a new network use**: a map is tiles fetched from a server and a map centred on you
is a request that carries where you are. Within the larger product, which now declares `INTERNET`
for accounts and leaderboards, Outbreak is the one feature that also requests location.

So the claim changes shape. It does not get quietly dropped, and it does not get weasel-worded.

### The claim, before and after

| Before | After |
| --- | --- |
| "The camera pipeline never touches the network." | Still true, and unchanged. Outbreak is the one mode that goes online, but the camera stays offline. |
| "0 frames uploaded" | Unchanged: still literally true in every mode. **No video, no landmarks, no biometrics ever leave the device, in any mode, including this one.** |
| "Works in airplane mode" | Every mode still does, except Outbreak. This is now load-bearing: you can turn off location and network at the mode screen. |

### Rules this mode must follow

1. **Location is requested at the mode, not at install.** The permission prompt belongs to Outbreak,
   and declining it costs you exactly one mode.
2. **The camera never opens during Outbreak.** There is no frame to leak, so the video promise holds
   without qualification even here.
3. **The route is stored on the device**, like `#run`. It is not uploaded, not shared, not synced.
4. **The mode announces itself.** A one-line notice before the head start: this mode uses your
   location and downloads map tiles. Nobody should discover that from a permission dialog.
5. **Nothing about position reaches the coach.** The between-sets summary keeps its twenty-three
   fields; no coordinate is added to it.

### Why we accepted the trade

Because the product is a fitness product, and the largest cohort of people who train do it outside,
running. Pursuit already covers cardio indoors; refusing to go outdoors on principle would be
choosing a slogan over the users. The privacy promise that actually matters to somebody pointing a
camera at themselves in a bedroom is *the camera*, and that promise is untouched.

But we should be honest that it is a trade. The camera pipeline remains completely offline, and that is
the core promise. Where we go online, we do so deliberately: sign-in, leaderboards, and now Outbreak.

## Open questions

- Which tile source? Anything self-hostable keeps the request under our control; a commercial SDK
  does not, and would need its own line in this document.
- Does an Outbreak session feed the same streak as a verified indoor set, given the effort is
  measured differently?
- Offline map caching would let a pre-downloaded area run with the radio off again. Worth costing.
