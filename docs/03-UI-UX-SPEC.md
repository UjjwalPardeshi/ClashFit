# 03 · UI / UX Specification

---

## 1. The constraint that governs every decision

**The player is 2 to 2.5 metres from the screen, on the floor, possibly upside down, sweating, and
cannot touch the phone.**

Every other fitness app is designed for a device held at 40cm. Ours is not. This one fact
invalidates most standard mobile UI instinct:

| Normal mobile UI | ClashFit |
|---|---|
| 14–16sp body text | **Nothing below 28sp.** Critical numbers 80–140sp. |
| Tap targets 48dp | No mid-set tap targets at all. Voice or automatic. |
| Subtle state changes | Full-screen colour flashes. State must read peripherally. |
| Visual-first feedback | **Audio-first.** The screen is a secondary channel. |
| Dense information | Three things on screen during a set. Maximum. |

If a design decision would look good in a screenshot but fail at 2 metres, it fails.

---

## 2. Art direction

**Direction: Material 3 dark.** Layered surfaces, never pure black, rounded corners on the Material
scale (8/12/16/20/28 dp), tonal cards, and a single hot accent. Reads at distance, works on any
Android device, and establishes a clean, modern identity that scales beyond a single weekend.

### Tokens

The app uses Material 3 on dark background with custom token overrides for fitness contexts:

**Surfaces** (layered, never pure black):
- `Ground` #0E0F12: background, lowest elevation
- `Ground2` #131418: bottom bar, lowest container
- `Panel` #1B1C21: cards, default container
- `PanelLift` #24262C: card-on-card, text inputs
- `PanelTop` #2D3037: highest container, pressed state

**Text** (always maintains 9:1 or better contrast):
- `Ink` #F5F2EC: primary text
- `InkMuted` #B8F5F2EC (72%): secondary text, labels
- `InkFaint` #80F5F2EC (50%): hints, disabled

**Accents**:
- `Ember` #FF5A2C: primary (damage, selected states) — 6.2:1 on Ground
- `EmberLift` #FF7A52: pressed, hover
- `EmberDeep` #C93E14: deep variant
- `EmberTint` #2E1810: container for selected states

**Semantic**:
- `Success` #5ED28A: positive feedback
- `Clean` #8FD18A: clean rep
- `Ok` #F2C14E: acceptable rep
- `Shallow` #FF8A3D: shallow rep
- `Bronze` #CD8B5A, `Silver` #C9CDD4, `Gold` #F2C14E: achievement tiers

**UI Elements**:
- Buttons: pill-shaped (full height border radius) with tonal background, Material weight
- Navigation bar: bottom, four tabs (Train / Leaderboard / Progress / You), tonal.
  The social screens were promoted out of the profile page on 4 Sep 2026: leaderboards, friends,
  badges and challenges sat halfway down a scroll between your own stats and the privacy policy,
  which put "race your friends" in the same list as "what version is this". The tab is named
  Leaderboard because that is what people open it for. Library left the bar on 5 Sep and moved
  under Train — choosing what to train is part of training, and a permanent tab for a list you
  visit weekly was spending a fifth of the bar on browsing. On a display wider than 600dp the
  same four become a navigation rail
- Top app bar: every screen has a back arrow on the left (or menu), title centre, icons right
- Cards: round corners (16 dp), tonal background, subtle elevation

### Type

| Role | Face | Use |
|---|---|---|
| Numerals | Big Shoulders Display, 900 weight | damage, rep count, levels — 80–140sp |
| Headline | Big Shoulders Display, 700 weight | boss name, verdict words ("CLEAN", "SHALLOW") |
| Body | Barlow, 400–600 weight | coach lines, calibration cues, labels — never below 28sp |
| Mono | IBM Plex Mono | telemetry readout, debug overlay only |

---

## 3. The fight HUD

Three zones. Nothing else on screen.

```
┌──────────────────────────────────────────────┐
│  BOSS NAME                    ▓▓▓▓▓▓▓░░░ 68% │  ← top: boss identity + HP
│  ──────────────────────────────────────────  │
│                                              │
│                                              │
│              [ BOSS KEY ART ]                │  ← centre: the boss, and
│                                              │     the hit flash surface
│                  ⚡ 142                       │  ← damage numeral, on hit only
│                                              │
│                                              │
│  ┌────────┐                     ┌──────────┐ │
│  │   17   │      ●●●●○○         │  ×2.4    │ │  ← bottom: reps · fatigue · combo
│  │  REPS  │      FADING         │  COMBO   │ │
│  └────────┘                     └──────────┘ │
└──────────────────────────────────────────────┘
```

**Per-rep feedback sequence (must complete inside 100ms of the rep landing):**
1. Full-screen edge flash — `--clean` or `--shallow`, 120ms, ease-out
2. Damage numeral punches in at the boss, scales 1.0 → 1.25 → 1.0, drifts up, fades over 700ms
3. Boss HP bar animates down over 250ms with a slight overshoot
4. Rep counter increments with a scale pop
5. **Audio fires first**, not last — see §5

**Camera preview:** a small, dim, corner-inset skeleton overlay — *not* a full video feed. Two
reasons. The full feed competes with the boss for attention, and a live video of yourself
mid-workout is unpleasant to look at. Show the landmark skeleton only, at 30% opacity. It also
makes the technology visible to a jury, which the raw video does not.

**Fatigue meter:** four pips with a named band, not a percentage. `FRESH · WORKING · FADING ·
GASSED`. Numbers invite argument; a band communicates.

---

## 4. Push-up orientation

During push-ups the player is face-down, head toward the phone, eyes 30cm from the floor.

- The HUD switches to a **compressed bottom-anchored strip** — HP bar and rep count in the lower
  third of the screen, which is what a prone player can actually see.
- Boss key art moves to the upper portion and shrinks.
- **Audio becomes primary.** The player will not reliably see anything. Every rep must be legible
  through sound alone.
- Detected automatically from the exercise selection, not from device orientation sensors.

---

## 5. Audio design — not optional

The player's eyes are unavailable for roughly half of all reps. Audio carries the product.

| Event | Sound | Notes |
|---|---|---|
| Rep clean | short bright impact | pitch rises with combo multiplier — the player *hears* the streak building |
| Rep shallow | duller, detuned impact | must be obviously worse, not merely different |
| Combo milestone (×2, ×3) | ascending chime | |
| Boss phase change | low sustained hit | |
| Boss death | the one big moment | |
| Framing lost | soft descending two-tone | paired with a spoken cue |
| Countdown | 3-2-1 ticks | |

**Spoken cues via TTS:**
- Calibration: "step back", "I can't see your knees"
- Mid-set, sparingly: nothing except framing loss. Do not coach mid-rep; it breaks tempo.
- Rest: the full coach line, spoken. This is the primary delivery channel for the LLM output.

Speech ducks the music bed by 12dB and never overlaps itself — flush the TTS queue on new set start.

---

## 6. Calibration UX

The single highest-risk screen in the product, because it is the first thing a judge touches and
the place a live demo most plausibly dies.

- **Silhouette guide**, drawn to the target framing box, in `--system`.
- **Distance hint** driven by the landmark bounding-box height: an up-arrow for "step back", down
  for "come closer". No numbers.
- **Named cues, never generic errors.** "I can't see your knees" beats "pose not detected" by a
  wide margin — it tells the player exactly what to change.
- **2-second hold ring** before starting, so the player has time to settle rather than being
  ambushed by the countdown.
- **Baseline ROM capture**: one slow guided rep, narrated by TTS. Doubles as a tutorial.

---

## 7. Motion

Fast, physical, never decorative. Everything on the fight screen resolves inside 300ms.

| Element | Motion |
|---|---|
| Hit flash | 120ms ease-out, no ease-in — impact has no wind-up |
| Damage numeral | spring, stiffness high, damping 0.7 |
| HP bar | 250ms with 8% overshoot |
| Screen transitions | 200ms cross-fade. No slides, no shared-element choreography. |
| Boss idle | slow 3s breathing loop, so the screen is never dead |
| Boss phase change | 400ms colour shift + one screen shake, 6px, 180ms |

Respect `prefers-reduced-motion` equivalents: an accessibility toggle that disables shake and
flash and substitutes a border pulse. Someone with vestibular sensitivity should still be able to
play, and it is a good answer if a judge asks about accessibility.

---

## 8. Copy voice

Two voices, deliberately distinct, both terse.

**COACH** — specific, warm, always cites a number.
> "Last three reps lost four centimetres of depth. Take forty seconds."

**BOSS** — in character, references the same fact, never insulting the player's body.
> "Your knees are negotiating. I don't negotiate."

Never: emoji in the fight UI, exclamation marks in coach lines, "Oops!", "Great job!!", or any
phrasing about weight, appearance, or calories.

---

## 9. Accessibility

Not a nice-to-have — it is a differentiator the jury will notice, and it costs almost nothing.

- **Chair squats and knee push-ups as first-class exercises**, not an "easy mode". Same boss, same
  scoring, thresholds calibrated per variant.
- Reduced-motion toggle (§7).
- Colour is never the sole carrier of meaning — the verdict word ("CLEAN"/"SHALLOW") always
  accompanies the flash colour.
- All critical feedback available through audio alone.
- One-side detection: if only one side of the body is reliably visible, score from that side and
  say so, rather than refusing to start.

---

## 10. Screen inventory for the asset brief

| Screen | Assets needed |
|---|---|
| Splash | logo lockup, progress line |
| Home | boss thumbnail ×1, two icons |
| Calibration | silhouette guide (squat, push-up), arrows |
| Fight | boss key art (idle, hit, phase-2, death) ×1 boss, HP frame, damage numeral style, flash overlays |
| Rest | coach portrait or glyph, fatigue meter |
| Victory | death frame, laurel/flourish |
| Summary | chart styles only, no bespoke art |
| Duel | two player chips, link-state icons |

Full list with counts and sourcing plan in `15-ASSET-BRIEF.md`.
