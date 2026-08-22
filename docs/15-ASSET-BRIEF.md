# 15 · Asset Brief

**Every asset in this document is produced before the event.** Art, audio and copy are idea-drafting
and preparation, which is permitted; source code is not. Assets are disclosed alongside the
prototype in the Phase 1 pre-existing-components field.

Asset panic at hour 14 has killed more hackathon projects than bugs have. This list exists so that
does not happen.

---

## 1. Boss — THE PACEMAKER

A mechanical construct that mirrors the player's tempo. One boss, done properly.

| Frame | Purpose | Notes |
|---|---|---|
| `idle_01` … `idle_04` | 3s breathing loop | Subtle. The screen must never be dead. |
| `hit` | 120ms on damage | Bright rim, silhouette must still read |
| `phase2` | 60% HP onward | Colour shift, added detail — visibly angrier |
| `phase3` | 25% HP onward | Cracked, unstable |
| `stagger` | fatigue FADING | Open posture — the visual language of "push now" |
| `death_01` … `death_04` | 1.2s sequence | The one big moment in the product |

**Specs:** 1024×1024 PNG, transparent, high contrast against `--ground` (#07090C). Must read at 2
metres on a phone screen — that is the governing constraint, not detail.

**Sourcing:** generate with an image model against a fixed prompt and seed for consistency across
frames, then hand-adjust levels. Keep the prompt in `assets/PROMPTS.md` so frames can be regenerated
consistently.

**Fallback if art isn't ready:** a geometric boss drawn in Compose — a polygon with a phase colour
shift and a shake on hit. Twenty minutes of work, and it does not block the build.

---

## 2. HUD

| Asset | Notes |
|---|---|
| HP bar frame | Segmented, not a smooth gradient — segments read at distance |
| Damage numeral style | Condensed 800 weight, heavy outline for legibility over art |
| Flash overlays | Edge vignettes in `--clean` and `--shallow`, 120ms |
| Fatigue pips | Four states, plus the band label |
| Combo rail | Multiplier chip, scales with streak |
| Calibration silhouettes | Squat and push-up, in `--system` |
| Link-state icons | searching / linked / lost |

All of these can be Compose primitives. Do not source PNGs for anything that is a rectangle.

---

## 3. Audio — not optional

The player's eyes are unavailable for roughly half of all reps. **Audio carries the product.**
Source or synthesise all of it in the eleven days.

| Sound | Character | Notes |
|---|---|---|
| `rep_clean` | bright, short, percussive impact | **Pitch-shifted by combo multiplier** — the player hears the streak building |
| `rep_shallow` | duller, detuned | Must be obviously worse, not merely different |
| `combo_2`, `combo_3` | ascending chimes | |
| `phase_change` | low sustained hit | |
| `boss_death` | the big one | |
| `framing_lost` | soft descending two-tone | Paired with a spoken cue |
| `countdown_tick` ×3 | | |
| `victory` | | |
| `music_bed` | low, driving, loopable, 90s minimum | Must not fight the TTS. Ducks 12dB during speech. |

**Format:** 44.1kHz mono WAV for effects (low latency), OGG for the music bed.

**Licensing:** CC0 or CC-BY only, with attribution recorded in `assets/CREDITS.md`. This matters —
the event rules require attribution for third-party components.

---

## 4. Copy

Written in advance, loaded from `config/`, tunable on-device.

| Copy | Count | Notes |
|---|---|---|
| Calibration cues | ~8 | "step back", "I can't see your knees", "hold still" |
| Template coach lines | **~25** | Keyed on (band × reason × trend). See `06-AI-COACH-SPEC.md` §6. Fill placeholders from real telemetry. |
| Template boss taunts | ~15 | Same keying |
| Verdict words | 3 | CLEAN / OK / SHALLOW |
| Victory and summary strings | ~10 | |
| Boss name and one-line description | 1 | |

**Write the templates properly.** They are the fallback that ships if Gemma does not, and a good
template bank is indistinguishable from model output to a judge who is watching for 40 seconds.

---

## 5. Model file

`gemma-3n-e2b-int4.task`, ~2–3 GB.

- Accept the Gemma licence on Hugging Face **today** — it is gated
- Download to the laptop in the eleven days, never at the venue
- Transfer to the phone at check-in over **Office Kit file transfer** (no size limit, and it scores)
- Keep a copy on a USB drive as a backup

---

## 6. Pitch materials

| Asset | Deadline |
|---|---|
| Deck, 9 slides, PDF ≤ 25 MB | 27 Aug |
| Video walkthrough, 75s | 27 Aug |
| Prototype repo, public and dated | 23 Aug |
| Architecture diagram (deck slide 6) | 27 Aug |
| Screenshots from the prototype | 27 Aug |

---

## 7. Physical kit — pack this

| Item | Why |
|---|---|
| **Flexible phone tripod ×2** | A phone propped against a wall points at the ceiling. This is the single most important physical item and the one most likely to be forgotten. |
| Laptop + charger ×2 | |
| Fast charger + long cable ×2 | Phones will be at 30fps camera inference all weekend |
| Power bank ×2 | |
| USB-C cables ×3 | |
| USB drive with the model file and the golden APK | |
| **Change of clothes** | 30 hours, no shower, then squats in front of a jury |
| Fitted top | Loose clothing degrades pose detection |
| Trainers | |
| Measuring tape or a marked cord | To reproduce the calibrated camera distance at any table |
| Small mat or towel | Push-ups on a venue floor |
| Earphones | For audio testing without disturbing neighbours |
| Paracetamol, electrolytes, caffeine of choice | |
| Both phones' alarms set for the staggered sleep plan | Set on Saturday morning, not at 01:00 |
