# Competitive Landscape

**Purpose:** defend the 20% novelty score. This document exists so that when a judge says *"isn't
this Ring Fit?"*, the answer is immediate, specific, and shows we did the work — instead of a
flinch.

> ⚠️ **Do your own 30-minute search before finalising deck slide 2.** The list below is a starting
> map from memory, not a verified survey. Confirm each entry, and check specifically whether anyone
> is already doing fatigue-adaptive difficulty from pose.

---

## 1. The honest position

**Camera-based rep counting with form feedback is a crowded, well-trodden category.** It is also one
of the most common hackathon projects of the last several years — MediaPipe push-up counters are
practically a genre on GitHub.

The claim in an early draft of our pitch — *"nobody has cleanly merged real camera-verified
exertion with a game progression loop"* — **is false**, and saying it in front of a judge who knows
the category costs us credibility in the first sixty seconds.

**Say the opposite instead.** Name the field, then name the specific thing none of them do. A team
that clearly knows its competitors reads as serious; a team that claims an empty field reads as one
that did not look.

---

## 2. The field

### Movement games with real exertion
| Product | What it did | Gap |
|---|---|---|
| **Kinect** — *Your Shape Fitness Evolved*, *Nike+ Kinect Training* (2010–2012) | Depth-camera pose tracking with genuine form feedback and game framing. The most direct ancestor of what we are building. | Console + dedicated depth sensor. Fixed difficulty curves. Dead platform. |
| **Ring Fit Adventure** (Nintendo) | Exercise as RPG combat, with real resistance. The best-executed product in this space. | Not camera-based — a Joy-Con and a resistance ring. No form analysis. Requires a Switch and an accessory. |
| **Nex Playground** | Camera-based movement games on a TV. | Kids' movement games, not strength training. Dedicated hardware. |
| **Supernatural**, VR fitness generally | Immersive, high retention. | Headset required. No form scoring on strength movements. |

### Camera-based form coaching
| Product | What it did | Gap |
|---|---|---|
| **Onyx** | Phone-camera workouts with form feedback and rep counting. Very close to our perception layer. | No game layer. Discontinued. Cloud-era architecture. |
| **Kemtai** | Browser-based exercise tracking with detailed form correction, used clinically. | Clinical/B2B. No game. Server-side inference. |
| **Zenia** | Yoga pose recognition and correction. | Yoga-specific, no strength work, no game. |
| **Vay** | Motion-analysis SDK licensed to others. | Infrastructure, not a consumer product. |
| **Peloton Guide** | Camera device for form feedback and rep tracking. | Dedicated hardware, subscription, TV-anchored. |
| **Tempo** | 3D sensor + weights, real form coaching. | Expensive hardware. |

### The hackathon genre
Dozens of "MediaPipe push-up counter" and "AI gym trainer" projects. Almost all stop at rep counting
plus a simple angle threshold. **This is the crowd we are most likely to be compared against in the
room on the day** — so the differentiation has to be visible in a 40-second demo, not in the README.

---

## 3. What none of them do

Rank-ordered by how defensible each is under questioning.

### 1. Fatigue-adaptive difficulty from measured movement
Every product above uses a **preset** difficulty curve, or asks the user to self-report effort. None
reads accumulated fatigue from the movement itself — concentric velocity decay, range-of-motion
collapse, inter-rep pause growth — and changes the fight in response.

This is grounded in real sports science (velocity-based training uses velocity loss as a fatigue
proxy), it is computable from data we already have, and **it is demonstrable to a judge in forty
seconds**: ask them to keep going and watch the meter move.

> *"The boss can't outlast you, but it makes you earn the ending."*

### 2. Fully on-device, including a language model
The perception layer being on-device is now table stakes. **A 2B-parameter language model generating
the coaching and the antagonist's dialogue, running on the phone's NPU, not on a server**
is not.

The privacy argument here is genuine rather than rhetorical: this is a camera pointed at a person
mid-workout in their bedroom. Every competitor listed above with a camera either uploads the stream
or requires dedicated hardware in the room. The camera pipeline and the coach both run on-device,
with the network used only for accounts and leaderboards. **We can demonstrate airplane mode on stage.**

### 3. Zero hardware
Kinect, Ring Fit, Peloton Guide, Tempo, Nex, VR — every strong product in this space needs a device
you must buy. Ours needs a phone that is already in the room.

### 4. Fairness by construction
Every score is normalised against **the player's own calibration rep**, not a population average.
Most rep counters use fixed angle thresholds, which quietly penalise people with different limb
proportions and mobility. This is a small technical decision with a real inclusion argument behind
it, and it pre-empts the obvious objection.

---

## 4. Deck slide 2 — the framing

> **Slide title:** "This category is crowded. Here's what nobody does."
>
> Left column — the field: Kinect (2010), Ring Fit, Onyx, Kemtai, Peloton Guide.
> Every one needs hardware, a preset difficulty curve, or a server.
>
> Right column — the gap: **nothing reads fatigue from your movement, and nothing runs the coach on
> the device.**

Naming your competitors on slide 2 is a confidence move. It also inoculates you: a judge who was
going to raise Ring Fit now cannot, because you raised it first.

---

## 5. Risk: someone else at Pune brings a pose project

Non-trivial probability — call it one in four to one in three. If it happens and both teams pitch
"AI fitness form checker", the novelty score suffers for both.

**Mitigation:** our one-liner must not contain the words "form" or "rep counter". It must lead with
**fatigue** and **offline**. If a neighbouring team is demoing a rep counter, that is an argument to
lean *harder* into the fatigue and privacy framing, not to abandon it.

**Do the 30-minute search this week**, and again on Saturday morning by walking the floor and
looking at what is on other teams' screens. Knowing before Eval R1 is worth a lot.
