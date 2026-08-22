# 21 · Sensor Playbook

Creative phone use is **15% of the score and it is measured by HackTracker as counts and
durations**, not argued in a pitch. It is also the criterion where "the device must not be treated
merely as a display screen" is explicitly enforced.

This document is the full inventory of what an iQOO 15 can sense and how each capability becomes
gameplay. **Sections 2 and 3 are the ideas that make this project look like a winner rather than a
good entry** — read those first.

---

## 1. Inventory

| Sensor | Gameplay use | Cost | Verdict |
|---|---|---|---|
| Front camera | Pose (core), rPPG heart rate, breathing from shoulder rise | — | **Core** |
| Rear ultra-wide | Arena Mode framing, meal photos | low | **Ship** |
| **Proximity** | **Push-up depth, phone on the floor under your chest** | low | **Ship — see §2** |
| **Accelerometer + gyro** | **Plank hip-sag, phone on your lower back**; jump height; tremor | low | **Ship — see §2** |
| **NFC** | **Tap-to-pair for duels and raids** | low | **Ship — see §3** |
| Haptics | Tempo metronome you can feel; rep confirmation | very low | **Ship — see §4** |
| Microphone | Voice commands; breathing quality; ambient calm detection | med | Tier 3 |
| Barometer | Stair-climb detection (floors, not steps) | low | Roadmap |
| Ambient light | Wind-down / circadian quests; "train in daylight" | very low | Roadmap |
| Magnetometer | Direction-based cardio drills | low | Skip |
| Step counter | Cardio — **useless on a loaner phone handed over that morning** | — | Cut |
| Wi-Fi Direct / BLE | Duel and raid transport | — | **Core** |
| Thermal API | Throttle-aware LLM scheduling | low | **Ship** |
| 144Hz display | HUD smoothness | — | Free |

---

## 2. Three sensor ideas that are genuinely novel

These are the ones to build. Each solves a real problem the camera alone cannot, and each is
demonstrable in under a minute.

### 2.1 Proximity sensor for push-up depth

**The problem:** push-ups need a side-view camera at floor level. At a judging table there is no
floor and no room. It is why push-ups got demoted.

**The fix:** put the phone screen-up on the floor, directly under the player's chest. The proximity
sensor reports distance to the chest through the whole rep. Depth becomes a direct measurement
rather than an inference from a difficult camera angle.

```
depth = 1 − clamp( d_min / d_standing , 0 , 1 )
rep   = the same hysteretic FSM, on distance instead of angle
```

Cheap, robust, immune to lighting and background clutter, and it works in a space the size of a
yoga mat. **It also reframes the phone as equipment rather than a screen** — which is exactly what
the event says it wants.

*Caveat:* Android proximity sensors on many devices report a coarse near/far binary rather than a
continuous distance. **Verify on the loaner in the first hour.** If it is binary, it still gives a
reliable bottom-of-rep trigger, which is enough to gate depth.

### 2.2 IMU on the lower back for plank and hip sag

**The problem:** hip sag during a plank is the alignment measure most likely to be noisy from pose
alone, because the torso line is nearly parallel to the camera axis.

**The fix:** place the second phone on the player's lower back. Accelerometer plus gyro give torso
pitch directly, in degrees, at 100Hz. A human trainer literally lays a stick across your back to
check this — we are doing the same thing with better resolution.

```
sag = |pitch − pitch_at_start|      // degrees, direct measurement
```

Works for plank, glute bridge, push-up torso line, and hip hinge quality on good mornings.

### 2.3 Two-device sensor fusion — "Precision Mode"

**You are given one phone per person, so a two-person team has two iQOO 15s.** Everyone else will
use them as one-per-player. Use them as **one system**:

> **Phone A is the eye. Phone B is the inner ear.**
> A is propped and runs pose at 30fps. B is strapped to the thigh or lower back and streams IMU over
> the same local link the duel already uses.

What fusion buys:
- **Robustness** — when the camera loses the player, the IMU carries the rep count. Framing loss
  stops being fatal.
- **Precision** — jump height from hip-Y is decent; jump height from IMU double-integration plus
  camera correction is genuinely good.
- **Tremor and sag** measured directly rather than inferred.

This is a strong technical-depth answer and it is a legitimately unusual use of the loaner hardware.
It trades against Duel Mode — a mode selector choice, not an architecture conflict, and the transport
is already built.

---

## 3. NFC tap-to-pair — the fix for the biggest demo risk

**The problem:** the highest-probability live failure is the duel refusing to pair in a hall with
several hundred contested radios ([13-RISK-REGISTER](13-RISK-REGISTER.md) R6).

**The fix:** two phones touch. NFC hands over the hotspot SSID, password and a session token. The
socket connects immediately afterwards. No discovery, no scanning, no list of nearby devices.

```
tap → NDEF exchange { ssid, psk, sessionId, playerId } → guest joins → socket up
```

On stage this is also a *better moment* than a lobby screen: two people touch phones together and
the fight begins. It reads as magic and it takes one second.

Keep the manual hotspot join as the fallback beneath it.

---

## 4. Haptics — eyes-free tempo

The player is face-down, or two metres away, or has their eyes closed in a yoga pose. Audio already
carries the load ([03-UI-UX-SPEC](03-UI-UX-SPEC.md) §5). Haptics carry it further:

- **Tempo metronome you can feel** — a pulse on the eccentric beat. In Tempo Trial this is the
  entire interface.
- **Rep confirmation** — a short tick per counted rep, so you know it registered without looking.
- **Warning pattern** — a distinct double-buzz for framing loss.
- **Breath pacing** — a slow rise-and-fall pattern for box breathing.

Very cheap, immediately noticeable, and it is the kind of polish that separates first from third.

---

## 5. On-device AI beyond pose

The language model already runs locally ([06-AI-COACH-SPEC](06-AI-COACH-SPEC.md)). Three extensions,
all on-device, all reinforcing the privacy thesis:

| Capability | Use | Cost |
|---|---|---|
| **Gemma 3n multimodal — image input** | Feed the single **worst-rep frame** to the model and get a grounded natural-language critique: *"your left knee is collapsing inward at the bottom."* Vision-grounded coaching, not just telemetry-phrasing. | med — **strong Tier 3 candidate** |
| Same, for meals | Photograph a plate, identify the foods, log it. Never uploaded. | med — roadmap |
| **Android `SpeechRecognizer`, offline mode** | Voice commands: "stop", "next", "rest". The player cannot reach the phone; voice is the only mid-set input that works. | low — **Tier 2** |
| Audio classification | Breathing quality, ambient calm detection for mindfulness | med — roadmap |

**The vision critique is the highest-value extension in this document.** It turns the model from a
phrasing layer into something that actually *looks* at you — and it is a far more impressive answer
to "what is the AI doing" than "it rewrites numbers into sentences."

---

## 6. What we deliberately do not use

| | Why |
|---|---|
| Step counter, Health Connect | Empty on a phone handed over at 08:00 Saturday. Demos as a blank screen. |
| Magnetometer, battery, fingerprint | No honest gameplay mapping. Using them would be metric-farming. |
| GPS | Outdoor cardio is roadmap, and a location permission undercuts the privacy pitch. |

**One line on honesty.** Creative phone use is measured as duration, and it would be easy to leave
the camera running to inflate a number. **Do not.** Every sensor in §1 through §5 is used because it
makes the product better; the telemetry is a consequence, not the motive. If an organiser asks why
the camera ran for six hours, the answer has to be "because the game watches you", and it has to be
true.

---

## 7. Weekend tiering

| Tier | Sensor work |
|---|---|
| **Tier 0** | Front camera + pose. Thermal API for LLM scheduling. |
| **Tier 1** | Rear ultra-wide (Arena Mode). Wi-Fi Direct / sockets for the duel. |
| **Tier 2** | **NFC tap-to-pair** (~1h, and it de-risks the duel). **Haptic rep + tempo** (~30 min). Voice commands via offline `SpeechRecognizer` (~45 min). |
| **Tier 3, committed 22 Aug** | **Proximity push-up depth** (~1h). **Gemma vision critique of the worst rep** (~1.5h). **IMU plank sag** (~1.5h, and it needs `IsometricHoldDetector` first — realistically the one that slips). |
| **Roadmap / deck** | Two-device fusion, barometer, meal photos, audio classification. |

**All four differentiators were committed on 22 August.** The honest arithmetic: they total roughly
5 hours against a 19-hour budget that already carries Tier 0 plus two parallel Tier-1 lanes.
Realistically **two of the four land at the event** unless you cut the event cost beforehand — which
you can:

| Item | Event cost if unprepared | Event cost if prototyped 22–26 Aug |
|---|---|---|
| NFC tap-to-pair | ~1 h | **~20 min** — it replaces manual pairing rather than adding to it |
| Proximity push-up depth | ~1 h | **~30 min** — the sensor behaviour is the unknown, and that is a pre-event test |
| Gemma vision critique | ~1.5 h | **~45 min** — the prompt is the work, and prompts are pre-event copy |
| IMU plank sag | ~3 h (needs `IsometricHoldDetector` first) | ~1.5 h |

**Prototype NFC and proximity during the 22–26 August window.** They are exactly the kind of thing
that is a two-day discovery at the event and a two-hour experiment beforehand.

Order at the event: **NFC tap-to-pair** (removes your largest demo risk) → **haptic tempo** (~30 min,
the cheapest "this feels finished" signal available) → **proximity depth** → **vision critique** →
**IMU sag** last, and expect to cut it.
