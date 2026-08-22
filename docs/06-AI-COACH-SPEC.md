# 06 · AI Coach Specification

The on-device language model is the second half of the novelty claim. It is also the component most
likely to be slow, hot, or absent — so it is designed from the start to be **entirely optional at
runtime**. The demo must never depend on it.

---

## 1. Model and runtime

| | |
|---|---|
| Runtime | MediaPipe **LLM Inference API** for Android |
| Model | **Gemma 3n E2B, int4**, `.task` bundle (~2–3 GB) |
| Delegate | GPU |
| Lifecycle | One `LlmInference` instance, created at splash, held for app lifetime |
| Sessions | One `LlmInferenceSession` per generation, discarded after |

Alternatives and rationale in [ADR-003](adr/ADR-003-on-device-llm.md). Short version: Gemma 3n E2B
is designed for on-device with a small effective memory footprint, it is explicitly named in the
event's own stack guidance, and it ships as a first-class MediaPipe bundle.

**Model delivery:** too large for an APK. Side-loaded to app external files at first run,
transferred laptop→phone over **Office Kit file transfer** — which is both the practical route and
a legitimately scoreable use of the bridge (10% of the rubric, telemetry-measured). Say so during
the demo.

---

## 2. The hard scheduling rule

> **The LLM never runs while the camera loop is active.**

Pose inference at 30fps and a 2B-parameter generation contend for the same GPU and the same thermal
envelope. Running them together drops frames, which destroys the only thing the product must never
get wrong.

Sequence:
```
set ends → camera to 5fps (or paused) → summarise → generate → TTS speaks → camera resumes
```

Generation is wrapped in a **5-second timeout**. On timeout: cancel, fall through to templates,
never show a loading state to the player.

---

## 3. Input — telemetry, not landmarks

The model never sees raw pose data. `TelemetrySummariser` produces a compact payload, ~150 tokens:

```json
{
  "exercise": "squat",
  "reps": 11,
  "form_mean": 0.71,
  "form_first3": 0.86,
  "form_last3": 0.54,
  "depth_drop_cm": 4.2,
  "velocity_loss_pct": 31,
  "rom_loss_pct": 18,
  "fatigue_band": "FADING",
  "best_rep": { "index": 3, "form": 0.93 },
  "worst_rep": { "index": 10, "form": 0.41, "reason": "depth" },
  "combo_max": 6,
  "boss_hp_pct": 34,
  "session_set_index": 2
}
```

**`worst_rep.reason`** is computed deterministically as the lowest-scoring sub-score of that rep
(`depth` / `rom` / `tempo` / `alignment`). We do not ask the model to diagnose — we ask it to
*phrase* a diagnosis we already made. That is what keeps the output truthful.

---

## 4. Two personas, one pass

Both outputs are generated in a single call to avoid a second load of the KV cache.

### System prompt (shared)

```
You are the voice of a fitness combat game. You will be given measured telemetry from a
set of exercise. Everything you say must be grounded in those numbers.

Rules:
- Never invent a number. Only cite values present in the telemetry.
- Never comment on the player's body, weight, or appearance.
- Never apologise, never use exclamation marks, never use emoji.
- Maximum two sentences per output.
```

### COACH persona

```
Voice: a competent training partner. Warm, terse, specific.
Always cite one concrete number from the telemetry.
If fatigue_band is FADING or GASSED, prescribe a rest in seconds. Do not tell them to stop.
Output one line only.
```

Target output:
> "Your last three reps lost four centimetres of depth. Take forty seconds, then finish it."

### BOSS persona

```
Voice: THE PACEMAKER, a mechanical construct that mirrors the player's tempo.
Taunt in character. Reference the same fact the coach used, from the machine's point of view.
Never insult the player's body or fitness level — mock their tempo, their depth, their resolve.
Output one line only.
```

Target output:
> "Your knees are negotiating. I do not negotiate."

### Decoding

| Persona | temperature | topK | maxTokens |
|---|---|---|---|
| COACH | 0.35 | 20 | 60 |
| BOSS | 0.85 | 40 | 50 |

Prompts live in `config/prompts/coach.txt` and `config/prompts/boss.txt`, hot-reloaded. **Prompt
tuning is the ideal Red Light activity** — it is pure on-device text editing, requires no compile,
and directly improves the most quotable part of the demo.

---

## 5. Output validation

Generated text is never trusted straight to screen. `PersonaRouter` rejects and falls back if:

- Output exceeds 2 sentences or 180 characters
- It contains a numeral not present in the telemetry payload (regex-extract digits and set-compare)
- It contains any term from a small blocklist: weight, fat, skinny, lazy, calories, diet
- It is empty, or the timeout fired

Rejection is silent — the template fires and the player sees no difference. **Log every rejection**;
if the rejection rate is high on the day, lower the temperature in config rather than debugging the
model.

---

## 6. Template fallback — must be good enough to ship alone

If the model never loads, the product must still feel coached. Build the template bank **first**,
before touching the LLM, and make it genuinely decent.

Templates are keyed on `(fatigue_band, worst_rep.reason, trend)`:

| Key | Coach line |
|---|---|
| `WORKING/depth/declining` | "Depth is slipping — you're {depth_drop} centimetres short of your first reps. Reset and go lower." |
| `FADING/tempo/declining` | "You're dropping into the bottom instead of controlling it. Take {rest}s." |
| `FADING/rom/declining` | "Range is down {rom_loss} percent since rep one. {rest} seconds, then finish." |
| `GASSED/*/*` | "That's real fatigue, not weakness. {rest} seconds — four more reps ends this." |
| `FRESH/*/improving` | "Clean. {combo} in a row. It hasn't felt anything yet." |

Placeholders are filled from the same telemetry object, so the fallback cites real numbers too.
A judge cannot tell which path fired — and that is the point.

---

## 7. Speech

`SpeechOut` wraps Android `TextToSpeech`.

- **The coach line is always spoken.** During push-ups the player is face-down and will not read
  anything. Speech is the primary channel, not an accessibility extra.
- The boss taunt is spoken with a lowered pitch (0.75) and slightly slower rate (0.9).
- Music bed ducks 12dB while speaking.
- The queue is flushed on new set start — a coach line arriving mid-set is worse than no coach line.
- Language: English (India) locale if available, `Locale.US` fallback.

**Telemetry note:** TTS exercises the speaker and, combined with the camera, strengthens the
"creative phone use" signal that HackTracker measures. This is a legitimate benefit, not the reason
to do it — but it is worth knowing.

---

## 8. Failure modes

| Failure | Behaviour |
|---|---|
| Model file missing | Splash reports "coach: offline", app runs on templates. No error dialog. |
| Load exceeds 25s | Proceed to HOME; LLM continues loading in background and joins when ready. |
| Generation exceeds 5s | Cancel, template fires. |
| Out of memory | Release the `LlmInference` instance, switch permanently to templates for the session, log it. |
| Device thermally throttled | `LlmEngine` checks a thermal flag before generating; skips to templates above threshold. |
| Output fails validation | Template fires, silently. |

**None of these are visible to a judge.** That is the design requirement.

---

## 8b. Extensions — more of the model, and more of the phone

Specified in [21-SENSOR-PLAYBOOK](21-SENSOR-PLAYBOOK.md) §5. Summary:

| Extension | What it adds | Tier |
|---|---|---|
| **Gemma 3n vision critique** — feed the single worst-rep frame to the multimodal model | Grounded coaching from an actual image: *"your left knee is collapsing inward at the bottom."* Turns the model from a phrasing layer into something that **looks** at you. Much stronger answer to "what is the AI doing". | **Tier 3 — highest-value extension** |
| **Offline voice commands** via Android `SpeechRecognizer` | "stop", "next", "rest". The player is two metres away and cannot reach the phone; voice is the only mid-set input that works. | Tier 2 |
| Meal photos via the same multimodal path | Extends the privacy thesis to food. Roadmap. | Roadmap |
| Audio classification for breathing quality | Roadmap. | Roadmap |

---

## 9. What we say about it in the pitch

> "The coach and the villain are the same model, running on the phone's NPU. It never sees your
> video — it sees eleven numbers describing how your movement changed across a set. And it works
> in airplane mode, which we can show you right now."

Then show airplane mode on in the status bar while a fight runs. Do not narrate the icon — let them
see it.
