# ClashFit — Documentation Index

**Event:** iQOO Hackathon 2026 · Pune City Battle · Working-Professional bucket
**Team:** Omkar Kadam, Ujjwal (2 builders, 2 loaner iQOO 15 devices)
**Phase 1 deadline:** 1 Sep 2026, 23:59 IST (target submission: 29 Aug)
**Battle:** Sat 5 – Sun 6 Sep 2026, Pune (venue TBA)
**Track:** HealthTech (primary) · Open Innovation (fallback)

---

## What this is

Everything that must be decided *before* a line of production code is written. The build window is
~19 focused hours inside a 25-hour clock, ~55% of it phone-only. Decisions made at hour 14 cost
three times what the same decision costs today.

**Rule: nothing in this folder is written during the event.** These are pre-event artifacts. At
check-in we open a fresh repository and implement against these specs.

---

## Reading order

### Before you write anything
| # | Doc | Why |
|---|---|---|
| 00 | [PRD](00-PRD.md) | What we are building and for whom. Feeds the Phase-1 description and deck. |
| 01 | [TRD](01-TRD.md) | Architecture, modules, ownership split, performance budgets. |
| 02 | [App Flow](02-APP-FLOW.md) | Every screen, every transition, the session state machine. |
| 03 | [UI/UX Spec](03-UI-UX-SPEC.md) | Design system, the 2-metre legibility constraint, motion, audio. |

### The technical core (this is where we win or lose)
| # | Doc | Why |
|---|---|---|
| 04 | [Game Design](04-GAME-DESIGN.md) | Damage math, combo system, boss design, progression. |
| 05 | [Pose Engine Spec](05-POSE-ENGINE-SPEC.md) | Rep detection, form scoring, **fatigue detection**. The novelty lives here. |
| 06 | [AI Coach Spec](06-AI-COACH-SPEC.md) | Gemma 3n on-device, prompt design, telemetry→language, TTS. |
| 07 | [Multiplayer Spec](07-MULTIPLAYER-SPEC.md) | Duel protocol, transport abstraction, self-healing event sync. |
| 08 | [Data Model](08-DATA-MODEL.md) | Room schema, config files, hot-reload contract. |
| 09 | [Module Contracts](09-MODULE-CONTRACTS.md) | The interfaces both of us code against in parallel. |

### Execution
| # | Doc | Why |
|---|---|---|
| 10 | [Build Runbook](10-BUILD-RUNBOOK.md) | Hour-by-hour against the Red/Green clock. Who does what. |
| 11 | [Demo Script](11-DEMO-SCRIPT.md) | The pitch, judge-as-player-two, failure fallbacks. |
| 12 | [Rubric Map](12-RUBRIC-MAP.md) | Every scoring line → what earns it → where it is implemented. |
| 13 | [Risk Register](13-RISK-REGISTER.md) | Ranked failure modes, mitigations, kill switches. |
| 14 | [Test Plan](14-TEST-PLAN.md) | Acceptance criteria per tier. What "done" means. |
| 15 | [Asset Brief](15-ASSET-BRIEF.md) | Art, audio, copy — all prepared before the event. |
| 16 | [Pre-Event Checklist](16-PRE-EVENT-CHECKLIST.md) | The eleven days, and the packing list. |
| 17 | [Game Modes](17-GAME-MODES.md) | Time Attack, Ghost Race and friends. Modes are cheap; exercises are not. |
| 18 | [**Event Card**](18-EVENT-CARD.md) | **The one page you read at hour 26.** Gates, cut order, failure playbook, judge Q&A. |
| 19 | [Exercise Library](19-EXERCISE-LIBRARY.md) | 5 detector families, 61 exercises, and a game shaped like each movement. Strength, yoga, cardio. |
| 20 | [Solo / Versus / Co-op / Community](20-MULTIPLAYER-MODES.md) | One player to eight, plus community that works with no server. |
| 21 | [Sensor Playbook](21-SENSOR-PLAYBOOK.md) | Every sensor on the phone as gameplay. NFC pairing, proximity push-ups, IMU plank, two-device fusion. |
| 22 | [Health Domains](22-HEALTH-DOMAINS.md) | Everything health & fitness, gamified onto one character sheet. Roadmap. |
| 23 | [Meta-Progression](23-META-PROGRESSION.md) | Sessions, days, seasons. Economy, streaks done right, retention. Roadmap. |
| 24 | [**Build Setup**](24-BUILD-SETUP.md) | **Read before you type.** Dependencies, file tree, manifest, landmark indices, git, hour-zero. |
| 25 | [Clinic Mode](25-CLINIC-MODE.md) | Validated functional assessments — sit-to-stand, balance, reach. The impact play. |
| 26 | [Deck Copy](26-DECK-COPY.md) | All nine slides, written. Due 27 Aug. |
| 27 | [Impact & Business Case](27-IMPACT-BUSINESS.md) | Who it's for, who pays, why India, and the risks we raise before a judge does. |
| 28 | [**Judge-Proofing**](28-JUDGE-PROOFING.md) | Every claim, its defence, and the evidence to show. Rehearse it. |

### Reference pack — copy-ready data
| File | Contents |
|---|---|
| [CONFIG-PACK](reference/CONFIG-PACK.md) | Complete `pose.json`, `combat.json`, `ui.json` with tuned starting values |
| [EXERCISE-RECORDS](reference/EXERCISE-RECORDS.md) | 8 weekend exercises as JSON records with thresholds and ladders |
| [PROMPT-PACK](reference/PROMPT-PACK.md) | System/coach/boss prompts, 25 template coach lines, 15 taunts |
| [**KOTLIN-CORE**](reference/KOTLIN-CORE.md) | **The verified algorithms in Kotlin.** Retype at the event, do not copy. Port order at the end. |

### Decision records
| ADR | Decision |
|---|---|
| [001](adr/ADR-001-stack.md) | Native Kotlin vs TensorFlow.js vs hybrid |
| [002](adr/ADR-002-pose-model.md) | MediaPipe Pose Landmarker vs MoveNet vs PoseNet |
| [003](adr/ADR-003-on-device-llm.md) | Gemma 3n vs Phi-3.5 vs Sarvam |
| [004](adr/ADR-004-duel-transport.md) | Hotspot sockets vs Nearby Connections vs BLE |
| [005](adr/ADR-005-hot-reload-config.md) | The config layer that makes Red Light productive |

### Research
- [Competitive Landscape](research/COMPETITIVE-LANDSCAPE.md) — honest field survey. Feeds deck slide 2 and defends the novelty score.

---

## The three sentences that matter

1. **Camera-based fitness apps upload the most intimate footage you own. We never do — pose and the LLM coach both run on the Snapdragon NPU, airplane mode on.**
2. **ClashFit reads fatigue from your movement — velocity decay, collapsing range of motion, tempo drift — and the boss adapts in real time.**
3. **The phone is not a screen. It is the sensor, the referee, the coach and the opponent.**

Everything in these docs exists to make those three sentences true and demonstrable in front of a jury.

---

## Decisions locked — 21 Aug 2026

| Decision | Choice |
|---|---|
| Stack | **Native Kotlin + Compose + MediaPipe Tasks** ([ADR-001](adr/ADR-001-stack.md)) |
| Pose model | **MediaPipe Pose Landmarker**, 33 world landmarks ([ADR-002](adr/ADR-002-pose-model.md)) |
| On-device model | **Gemma 3n E2B int4** via MediaPipe LLM Inference ([ADR-003](adr/ADR-003-on-device-llm.md)) |
| Duel transport | **Hotspot + sockets** primary, behind a swappable interface ([ADR-004](adr/ADR-004-duel-transport.md)) |
| Red Light strategy | **Hot-reload config layer**, built in the first Green block ([ADR-005](adr/ADR-005-hot-reload-config.md)) |
| Art direction | **Neon tactical** (`03-UI-UX-SPEC.md` §2) |
| Duel priority | **Tier 1** — parallel lane on Sunday alongside the on-device coach |
| Game modes | Boss Fight + **Time Attack + Ghost Race + Survival + Boss Rush** (`17-GAME-MODES.md`) |
| Exercise library | **5 detector families, 61 exercises**, exercises as config data (`19-EXERCISE-LIBRARY.md`) |
| Social scope | Solo + 2P duel + group raid/relay/last-standing + **server-free community** (`20-MULTIPLAYER-MODES.md`) |
| Sensors | NFC tap-to-pair, haptic tempo, proximity push-ups, IMU plank, two-device fusion (`21-SENSOR-PLAYBOOK.md`) |
| Product vision | Full health platform on one character sheet — **roadmap, not weekend scope** (`22`, `23`) |
| Code | **No implementation before the event.** Specs and config only — team decision, 21 Aug. |
| Camera | Front by default, rear ultra-wide in **Arena Mode** (`01-TRD.md` §5) |
| Phase 1 proficiency | Both builders have shipped on-device inference — tick **"Deployed local LLMs on-device"** |

Still open: the Grand Finale scope question with the organisers (see `16-PRE-EVENT-CHECKLIST.md`).

If a locked decision is overturned, the affected documents are listed in that ADR's *Consequences*.
