# 02 · App Flow

Every screen, every transition, and the state machine that governs a fight. If a transition is not
in this document it does not exist in the build.

---

## 1. Screen graph

```
                            ┌──────────┐
                            │  SPLASH  │  check auth, model warm-up, config load, TTS init
                            └────┬─────┘
                                 │
              ┌──────────────────┴──────────────────┐
              │                                     │
          signed out                            signed in
              │                                     │
              ▼                                     ▼
        ┌──────────────┐                      ┌──────────────┐
        │   ONBOARDING │  three-page welcome  │     HOME     │  Train tab (Modes / ExercisePicker)
        └────┬─────────┘                      └──────┬───────┘
             │                                       │
             ▼                                       ├──────► LIBRARY   (Library tab)
        ┌──────────────┐                            │
        │   SIGN UP    │  email + password           ├──────► PROGRESS  (Progress tab)
        │   SIGN IN    │  or password reset          │
        │ RESET PWD    │                             └──────► YOU      (You tab: Account, Settings,
        └────┬─────────┘                                       Privacy, Achievements, Leaderboard,
             │                                                 Friends, Weekly Challenge, etc.)
             ▼
        ┌──────────────┐
        │ PROFILE SETUP│  avatar colour, goal, favourite exercise
        └────┬─────────┘
             │
             ▼
        ┌──────────────┐
        │CAMERA PRIMER │  orientation, lighting, focus
        └────┬─────────┘
             │
             ▼
           HOME ─────────► DUEL LOBBY  (host / join, calibrate both, fight)
             │
             ├──────► CALIBRATION ───────► FIGHT ───────► REST ───────┬─────► VICTORY/DEFEAT
             │                              (duel: one screen)        │
             │                                                   boss alive
             │                                                        │
             │◄───────────────────────────────────────────────────────┘
             │
             └──────► SUMMARY ──────► HOME
```

---

## 2. Screen-by-screen

### SPLASH
**Job:** check authentication and absorb the LLM cold load so the first fight never waits.
- Checks if the player is signed in (via Firebase Auth state or local account).
- Loads config files, initialises TTS, kicks off `LlmEngine.warmUp()` on a background dispatcher.
- Shows a determinate progress line with honest labels: "waking the coach", not a spinner.
- **Timeout at 25s** → proceed to next screen in *degraded mode* (template coach, no LLM). Never block.
- If signed out, navigates to ONBOARDING. If signed in, navigates to HOME.
- Exit condition: config loaded + TTS ready + auth state known. LLM readiness is reported separately and does not gate.

### ONBOARDING
**Job:** welcome, sign up or sign in, set profile, primer.
- Three-page Welcome carousel: product promise, why accounts help (leaderboards, progression), call to action.
- SIGN UP or SIGN IN screen: email + password form, password reset link.
- PROFILE SETUP: avatar colour picker, goal (Get Stronger / Build Habit / Play with Friends / Move Better), favourite exercise.
- CAMERA PRIMER: confirm device orientation, check lighting, check focus, explain the pose system.
- **Exit to HOME is automatic** after completion. Never show these screens again (gate behind `onboarded` pref).

### HOME
**Job:** get into a fight in one tap.
- This is the TRAIN tab, one of four (Train / Library / Progress / You).
- Primary action: **FIGHT** (largest element on screen).
- Secondary: **DUEL**, modes picker, exercise picker (Squats / Push-ups).
- Shows a small status row: coach state (ready / warming / offline), camera mode, config version.
- Back arrow and account icon top-left and top-right respectively.

### CALIBRATION
**Job:** get the player framed correctly in under 30 seconds, and refuse to start until they are.

States:
| State | Trigger | Player sees |
|---|---|---|
| `SEARCHING` | no pose detected | Silhouette guide + "step into frame" |
| `PARTIAL` | pose found, required joints below confidence | Named cue: "I can't see your knees — step back" |
| `TOO_CLOSE` / `TOO_FAR` | landmark bounding box outside target band | Arrow + distance hint |
| `HOLDING` | all required joints visible ≥ conf threshold | 2-second countdown ring |
| `READY` | held for 2 continuous seconds | Ready chime, auto-advance |

Also captured here, once per exercise per session: **the player's baseline range of motion.** One
slow rep, recorded, used to normalise the ROM sub-score (see `05-POSE-ENGINE-SPEC.md` §5). This is
what makes form scoring fair across body types instead of punishing tall people.

**Exit to FIGHT is automatic.** No tap — the player is already in position and cannot reach the
phone.

### FIGHT
**Job:** the product. See `03-UI-UX-SPEC.md` §3 for layout.

Runs the fight state machine (§3 below). Player performs reps; each rep produces a hit. No
interactive controls except a large **PAUSE** target in a corner and a voice-triggered "stop".

Framing loss handling: if required joints drop below confidence for >1.5s, the fight **pauses**
(boss freezes, timer holds, audio cue) and an inline banner shows the calibration cue. It does not
navigate away — navigating away mid-set is infuriating. Resume is automatic when framing returns.

### REST
**Job:** the moment the LLM earns its place.

- Camera drops to 5fps or pauses entirely.
- `TelemetrySummariser` builds the set payload; `LlmEngine` generates two outputs in one pass:
  a coaching line (persona COACH) and a taunt (persona BOSS).
- **TTS speaks the coach line.** The player is on the floor and may not be looking at the screen.
- Screen shows: reps, average form, the fatigue meter, the coach line as text, and the boss's taunt.
- Rest length is **fatigue-derived**, not fixed: 30s fresh → 75s gassed. Skippable with a voice
  command or a tap.
- If the LLM has not returned in 5s, the template fallback fires and the pass is abandoned. The
  player never sees a loading state here.

### VICTORY / DEFEAT
- Victory: boss death animation, damage total, best rep replayed as a landmark-skeleton loop.
- Defeat exists only as "you stopped" — there is no fail state for being tired. See PRD principle 2.

### SUMMARY
- Fatigue curve across the session (the single most novel-looking artifact we can put on screen).
- Form trend per rep as a sparkline.
- Best rep and worst rep, each with the specific number that made it so.
- **This screen is what we show the jury when the demo is over.** It is evidence, not decoration.

### DUEL LOBBY
- Host creates, guest joins. Transport-agnostic (see `07-MULTIPLAYER-SPEC.md`).
- Shows connection state explicitly: `SEARCHING → LINKED → READY`. Never a bare spinner.
- Both players calibrate independently; the fight starts only when both report `READY`.
- **Casual mode toggle** lives here — low rep target, forgiving scoring, for the judge who is
  player two.

---

## 3. Fight state machine

```
  IDLE
    │ start
    ▼
  ARMED ──────────────► FRAMING_LOST ──┐
    │ first valid frame        ▲       │ framing returns
    ▼                          │       ▼
  ACTIVE ──── joints lost ─────┘    ACTIVE
    │  (>1.5s)
    │
    ├── rep completed ──► emit RepEvent ──► damage ──► [boss HP ≤ 0] ──► BOSS_DEAD
    │
    ├── no rep for 12s ──────────────────────────────────────────────► SET_ENDED
    │
    ├── rep target reached ──────────────────────────────────────────► SET_ENDED
    │
    └── pause pressed ──► PAUSED ──► resume ──► ACTIVE
```

**`SET_ENDED` conditions, in priority order:**
1. Boss HP reached zero → `BOSS_DEAD`
2. Rep target reached (target is fatigue-adjusted — see `04-GAME-DESIGN.md` §5)
3. 12 seconds with no rep and the player still framed → they stopped voluntarily
4. 30 seconds with no valid frame → they walked away; end the set, save what we have

**Invariants that must hold:**
- A `RepEvent` is emitted exactly once per completed rep. Never on a partial.
- No damage is ever applied outside `ACTIVE`.
- `FRAMING_LOST` freezes the fatigue baseline — a pause must not be read as fatigue.
- Every state transition is logged with a timestamp. When the demo misbehaves we need the trace.

---

## 4. Duel flow

```
 HOST                                    GUEST
  │ create session                        │
  │ start advertising ───────────────────►│ discover
  │◄────────────────────── join request ──│
  │ LINKED                                │ LINKED
  │ calibrate                             │ calibrate
  │ READY ───────────────────────────────►│
  │◄────────────────────────────── READY ─│
  │                                        │
  │ ══════════ 3-2-1 countdown (host clock) ══════════
  │                                        │
  │ rep → local damage → broadcast ───────►│ apply
  │◄─────────── broadcast ← local damage ← │ rep
  │                                        │
  │ boss HP = f(union of all events)      │  (same function, both sides)
  │                                        │
  │ boss dead → both show result           │
```

**Link loss:** both sides keep playing, a banner reads *"opponent disconnected — scoring locally"*,
and events reconcile on reconnect. The fight never stalls. See `07-MULTIPLAYER-SPEC.md` §5.

---

## 5. Cold-start timing target

| t | Event |
|---|---|
| 0.0s | App launch |
| 0.4s | Splash visible, config loaded |
| 0.5s | LLM warm-up begins in background |
| 1.2s | HOME interactive — **the LLM is still loading and that is fine** |
| +tap | CALIBRATION, camera live in ≤800ms |
| +~15s | Player framed, fight starts |

The player is fighting within ~20 seconds of tapping the icon. If a jury has to wait longer than
that, we have lost the demo before the first rep.
