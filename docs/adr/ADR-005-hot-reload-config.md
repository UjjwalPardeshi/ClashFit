# ADR-005 · Hot-reloadable configuration

**Status:** Accepted
**Date:** 21 Aug 2026

## Context

**55% of the build window is Red Light: phone only, laptops closed as build machines.** That is
roughly 10.5 of 19 focused hours. A native Gradle project cannot be iterated from a phone — so
without a deliberate answer, more than half the build time is dead.

This is the single strongest argument against a native stack ([ADR-001](ADR-001-stack.md)). It needs
a real solution, not a hope.

## Decision

**Every tunable value in the application is loaded from plain files on device external storage and
re-read on every `onResume`.** Nothing tunable is a compiled constant.

```
Android/data/<pkg>/files/
├── config/
│   ├── pose.json        thresholds, dwell times, filter params, sub-score weights
│   ├── combat.json      damage, combo curve, boss HP, phase and fatigue responses
│   ├── ui.json          flash durations, rest lengths, band labels
│   └── prompts/         system.txt, coach.txt, boss.txt
└── assets/              boss frames, HUD art, audio — hot-swappable
```

`ConfigStore` (`09-MODULE-CONTRACTS.md` §7) exposes each as a `StateFlow`. Screens observe the
version counter and recompose. The tuning loop during Red Light is: **edit the file with a text
editor on the phone → background the app → foreground it → the change is live.** No laptop, no
Gradle, no rebuild.

`ConfigStore` is written in the **first Green block, before either lane starts**, because both lanes
depend on it.

## Consequences

**Red Light becomes the most productive tuning time of the weekend**, not the least — because we are
tuning on the real device, with a real body, in front of the real camera. That is strictly better
feedback than a laptop simulation would give.

Specifically, these all become Red Light work:
- Rep thresholds and dwell guards, tuned by doing actual squats
- Damage curve, combo step and cap, tuned by playing
- Boss HP and fatigue response, tuned against real fatigue
- LLM prompts — pure text, and the most quotable part of the demo
- Art and audio assets, swapped in place

Costs and rules:
- Malformed JSON must **never crash the app.** Parse defensively; on failure keep the last good
  config and surface a small banner. Test this deliberately — you will fat-finger a comma at 03:00.
- Every config file ships with a compiled-in default, so a missing file is not a failure.
- Config files must be copied to both phones at check-in and kept in sync. Copy via Office Kit.
- Before every judging round, verify the config files are the tuned versions and not a debug
  variant. It is on the pre-demo ritual (`14-TEST-PLAN.md` §6).

**If this is not working by 13:00 Saturday, we have lost 10.5 hours.** Treat the first Green block as
having exactly one non-negotiable deliverable, and this is it.
