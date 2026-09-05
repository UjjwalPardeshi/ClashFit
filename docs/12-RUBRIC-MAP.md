# 12 · Rubric Map

The published scoring weights for the iQOO Hackathon 2026 City Battles, mapped to exactly what
earns them and where it lives in the build. **A quarter of the score is machine-measured, not
argued.**

---

## 1. The weights

| Weight | Criterion | Scored by |
|---|---|---|
| **30%** | End product quality — "does it work, is it useful, would someone keep using it" | Jury |
| **20%** | Novelty and impact | Jury |
| **15%** | Creative phone use — camera, voice, on-device AI | **HackTracker device data** |
| **15%** | Technical depth — architecture, code quality, robustness, real use of hardware | Jury |
| **10%** | Office Kit usage | **HackTracker device data** |
| **10%** | Demo and presentation | Jury |

Two additional targets: **special honours** and a **Most iQOO Usage** award.

---

## 2. Mapping

### 30% · End product quality
| Earns it | Where | Status gate |
|---|---|---|
| A fight that never crashes and always resolves | `10-BUILD-RUNBOOK.md` golden APK rule | G3 |
| Rep counting a judge can verify by counting aloud | `05-POSE-ENGINE-SPEC.md` §4 | G2 |
| Sub-20-second time from launch to first rep | `02-APP-FLOW.md` §5 | G3 |
| Fatigue mercy so every player reaches an ending | `04-GAME-DESIGN.md` §5 | G4 |
| Retention story (ghosts, city boss, forgiving streaks) | `00-PRD.md` §7 — **deck only** | — |

> **Polish beats breadth here.** One boss that feels excellent outscores four that feel unfinished.

### 20% · Novelty and impact
| Earns it | Where |
|---|---|
| **Fatigue-adaptive difficulty** from measured velocity loss and ROM collapse | `05-POSE-ENGINE-SPEC.md` §6, `04-GAME-DESIGN.md` §5 |
| **On-device** pose + LLM — a checkable privacy claim: the permission set is locked by the build (`checkPermissions<Variant>`), and only a score, a name and a level ever use the network | `01-TRD.md` §7, `30-ANDROID-APP.md` §1 |
| Coach and boss as the same on-device model | `06-AI-COACH-SPEC.md` §4 |
| Accessibility ladders as first-class exercises | `03-UI-UX-SPEC.md` §9 |

> **Do not lead with form-weighted damage.** It reads as an obvious increment on a crowded category
> and it costs you this 20%. Lead with fatigue and privacy. See `research/COMPETITIVE-LANDSCAPE.md`.

### 15% · Creative phone use — *telemetry, not argument*
| Earns it | Where |
|---|---|
| Camera live for hours — the single strongest signal we can generate | inherent to the product |
| Microphone / speaker via TTS coach | `06-AI-COACH-SPEC.md` §7 |
| NPU/GPU for pose **and** a language model | `01-TRD.md` §3 |
| Rear ultra-wide in Arena Mode | `01-TRD.md` §5 |
| Sensors: none used, deliberately — Health Connect is useless on a phone handed over that morning | `00-PRD.md` §5 |

> **Our strongest column.** HackTracker records counts and durations. Almost no other team will have
> the camera live for six hours. Keep it running whenever plausible.

### 15% · Technical depth
| Earns it | Where |
|---|---|
| Hysteretic FSM with dwell guards, not naive thresholding | `05-POSE-ENGINE-SPEC.md` §4 |
| One Euro filtering, with a stated reason over EMA | `05-POSE-ENGINE-SPEC.md` §2 |
| Fatigue estimator with baselines and band latching | `05-POSE-ENGINE-SPEC.md` §6 |
| Two models sharing one thermal envelope, scheduled apart | `01-TRD.md` §3 |
| Event-sourced duel sync with self-healing tails, no ACKs | `07-MULTIPLAYER-SPEC.md` §3 |
| Frozen module contracts enabling true parallel work | `09-MODULE-CONTRACTS.md` |
| **Five ADRs showing the decisions were reasoned, not stumbled into** | `adr/` |

> Bring the ADRs up if a judge goes technical. Showing *why* MoveNet was rejected is a stronger
> signal than showing that MediaPipe works.

### 10% · Office Kit — *telemetry, not argument*
| Earns it | Where |
|---|---|
| Gemma weights moved phone↔laptop over file transfer | `06-AI-COACH-SPEC.md` §1 |
| Screen mirror as the demo display for the duel | `11-DEMO-SCRIPT.md` §5 |
| Remote control driving the phone through every Red block | `10-BUILD-RUNBOOK.md` §5 |
| Kept paired and connected all weekend | standing rule 6 |

### 10% · Demo and presentation
| Earns it | Where |
|---|---|
| Judge as player two | `11-DEMO-SCRIPT.md` §4 |
| Airplane mode shown, not narrated | `11-DEMO-SCRIPT.md` §3 |
| Rehearsed failure playbook — calm diagnosis reads as senior | `11-DEMO-SCRIPT.md` §7 |
| Pre-tested Q&A harvested from Eval R1 | `11-DEMO-SCRIPT.md` §1 |

---

## 3. Where the free points are

| Free points | Why most teams miss them |
|---|---|
| **15% creative phone use** | Most projects touch the camera for seconds. Ours runs it for hours. |
| **10% Office Kit** | Most teams pair it once at the teach-in and forget. It is measured as duration. |
| **Scope honesty on the deck** | "Idea and scope fit for 30 hours" is an explicit shortlisting criterion, and a roadmap slide that separates shipped from planned reads as maturity. |

**25% of the total is logged, not pitched.** Plan for it on Saturday morning, not Sunday afternoon.

---

## 4. Self-scoring checkpoint — run this Sunday 06:30

Score honestly out of 100. Anything below 70 means the pitch needs to shift toward what actually
shipped.

- [ ] End product (30) — does a stranger complete a fight without help?
- [ ] Novelty (20) — can you state the differentiator in one sentence that isn't "gamified fitness"?
- [ ] Phone use (15) — how many hours has the camera been live?
- [ ] Tech depth (15) — can you defend the fatigue math for two minutes under questioning?
- [ ] Office Kit (10) — has it been connected all weekend?
- [ ] Demo (10) — have you run the full script three times without stopping?
