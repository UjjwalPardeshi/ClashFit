# ClashFit Exercise Detection — Source of Truth

**Version:** 1.0  
**Last updated:** 2026-09-05  
**Purpose:** Single authoritative reference for MediaPipe Pose landmark indices, angle conventions, state machines, feature calculation, form validation, and per-exercise configuration used by the ClashFit application.

All numeric thresholds are **initial detector values**. They must be tuned against real landmark traces. They are not universal biomechanical standards.

---

## 1. MediaPipe Pose Landmark Numbering (Official)

Use exactly these indices. Never hard-code magic numbers in business logic; always use the aliases defined below.

| ID | Landmark            | ID | Landmark             |
|----|---------------------|----|----------------------|
| 0  | Nose                | 17 | Left pinky           |
| 1  | Left eye inner      | 18 | Right pinky          |
| 2  | Left eye            | 19 | Left index           |
| 3  | Left eye outer      | 20 | Right index          |
| 4  | Right eye inner     | 21 | Left thumb           |
| 5  | Right eye           | 22 | Right thumb          |
| 6  | Right eye outer     | 23 | Left hip             |
| 7  | Left ear            | 24 | Right hip            |
| 8  | Right ear           | 25 | Left knee            |
| 9  | Left mouth          | 26 | Right knee           |
| 10 | Right mouth         | 27 | Left ankle           |
| 11 | Left shoulder       | 28 | Right ankle          |
| 12 | Right shoulder      | 29 | Left heel            |
| 13 | Left elbow          | 30 | Right heel           |
| 14 | Right elbow         | 31 | Left foot index      |
| 15 | Left wrist          | 32 | Right foot index     |
| 16 | Right wrist         |    |                      |

### Recommended Code Aliases

```js
const LM = {
  NOSE: 0,

  L_EYE_INNER: 1, L_EYE: 2, L_EYE_OUTER: 3,
  R_EYE_INNER: 4, R_EYE: 5, R_EYE_OUTER: 6,
  L_EAR: 7, R_EAR: 8,
  L_MOUTH: 9, R_MOUTH: 10,

  L_SHOULDER: 11, R_SHOULDER: 12,
  L_ELBOW: 13, R_ELBOW: 14,
  L_WRIST: 15, R_WRIST: 16,

  L_PINKY: 17, R_PINKY: 18,
  L_INDEX: 19, R_INDEX: 20,
  L_THUMB: 21, R_THUMB: 22,

  L_HIP: 23, R_HIP: 24,
  L_KNEE: 25, R_KNEE: 26,
  L_ANKLE: 27, R_ANKLE: 28,
  L_HEEL: 29, R_HEEL: 30,
  L_FOOT: 31, R_FOOT: 32
};
```

---

## 2. Angle Convention

Every three-point angle is defined as:

```
angle(A, B, C)   // measured at point B
```

Examples:
- `angle(23, 25, 27)` → Left hip → Left knee → Left ankle → **left knee angle**
- `angle(24, 26, 28)` → Right knee angle
- `angle(11, 23, 25)` → Left shoulder → Left hip → Left knee → **left hip angle**
- `angle(12, 14, 16)` → Right shoulder → Right elbow → Right wrist → **right elbow angle**

Interpretation guide:
- ≈ 180° → approximately straight / extended
- ≈ 90°  → right angle
- < 90°  → strongly flexed

Always compute the **interior** angle at the middle joint.

---

## 3. Critical Tracking Rules

### Never do this
```js
if (angle < 100) rep++;
```

### Always use an explicit state machine

Generic rep-cycle states:

```
IDLE
  ↓
READY
  ↓
PHASE_1_ENTER → PHASE_1_ACTIVE
  ↓
PHASE_2_ENTER → PHASE_2_ACTIVE
  ↓
RETURN_ENTER
  ↓
REP_COMPLETE
  ↓
READY
```

For a classic squat the concrete mapping is:

```
READY
  ↓  (knee falls below ~150°)
DESCENDING
  ↓  (knee reaches ≤105°)
BOTTOM
  ↓  (knee rises above ~150°)
REP_COMPLETE
```

The counter increments **only** on transition into `REP_COMPLETE`.  
The user must complete the full cycle: start → required depth → return.

### Hysteresis (Enter / Exit thresholds)

Never use a single threshold for both entry and exit.

Example for squat bottom:
```json
"bottomEnter": 105,
"bottomExit": 125
```

- Enter BOTTOM only when angle ≤ 105°
- Leave BOTTOM only when angle ≥ 125°

This prevents chatter around the threshold.

### Temporal constraints (minimum durations)

- Minimum phase duration: 100–200 ms
- Minimum full-rep duration: 600–800 ms
- Landing confirmation (ballistic): 100–150 ms
- Pose-match stability: 500–800 ms
- Hold invalid grace period: 500 ms

---

## 4. Recommended Exercise Configuration Structure

Every exercise JSON should follow this shape:

```json
{
  "id": "squat",
  "family": "REP_CYCLE",          // REP_CYCLE | HOLD | CADENCE | BALLISTIC | POSE_MATCH
  "name": "Squat",

  "camera_view": {
    "preferred": "SIDE",
    "allowed": ["SIDE"],
    "fallback": "NONE"
  },

  "required_landmarks": [23, 25, 27, 24, 26, 28, 11, 12],

  "primary_landmarks": {
    "left":  [23, 25, 27],
    "right": [24, 26, 28]
  },

  "secondary_landmarks": {
    "left":  [11, 23, 25],
    "right": [12, 24, 26]
  },

  "primary_angles": {
    "knee": {
      "points_left":  [23, 25, 27],
      "points_right": [24, 26, 28]
    }
  },

  "secondary_angles": {
    "hip": {
      "points_left":  [11, 23, 25],
      "points_right": [12, 24, 26]
    }
  },

  "derived_features": [
    "knee_height_relative_to_hip",
    "ankle_separation",
    "torso_vertical_angle"
  ],

  "start_position": {
    "knee": { "min": 150, "max": 180 }
  },

  "movement_phase_1": {
    "name": "DESCENDING",
    "knee": { "max": 145 }
  },

  "movement_phase_2": {
    "name": "BOTTOM",
    "knee": { "max": 105 }
  },

  "completion_condition": {
    "bottom_reached": true,
    "return_knee_min": 150,
    "return_to_start": true
  },

  "form_checks": [
    "knee_tracking",
    "torso_control",
    "minimum_depth"
  ],

  "invalid_form_conditions": [
    "insufficient_depth",
    "severe_knee_collapse",
    "landmark_confidence_low",
    "rep_started_before_ready"
  ],

  "angle_tolerances": {
    "knee": 8,
    "hip": 15
  },

  "temporal_constraints": {
    "min_phase_ms": 150,
    "min_rep_ms": 700,
    "invalid_grace_ms": 500
  },

  "confidence_policy": {
    "min_visibility": 0.6,
    "side_lock_ms": 400
  },

  "hold_duration": null,

  "state_machine": "STANDARD_REP_CYCLE",

  "rep_count_transition": {
    "from": "BOTTOM",
    "to": "START"
  }
}
```

---

## 5. Confidence & Side Selection

```js
const usableLeft =
  visibility(L_HIP)   >= 0.6 &&
  visibility(L_KNEE)  >= 0.6 &&
  visibility(L_ANKLE) >= 0.6;

const usableRight =
  visibility(R_HIP)   >= 0.6 &&
  visibility(R_KNEE)  >= 0.6 &&
  visibility(R_ANKLE) >= 0.6;
```

Select the side with the higher aggregate confidence score.  
**Lock the side** for at least 300–500 ms. Do not flip on every frame.

---

## 6. Feature Calculator (Computed Every Frame)

### Core Angles
- leftElbow, rightElbow
- leftShoulder, rightShoulder
- leftHip, rightHip
- leftKnee, rightKnee
- leftAnkle, rightAnkle

### Normalized Distances
Normalize everything by body height:

```js
bodyHeight = distance(midShoulder, midAnkle)
normalizedDistance = distance(A, B) / bodyHeight
```

### Useful Derived Features (compute once, reuse)
- shoulderMid, hipMid, ankleMid
- shoulderWidth, hipWidth
- torsoHeight
- leftLegLength, rightLegLength
- leftArmLength, rightArmLength
- torsoVerticalAngle
- shoulderLineAngle, hipLineAngle
- torsoRotation (shoulderLineAngle − hipLineAngle)
- leftKneeHeightRelativeToHip, rightKneeHeightRelativeToHip
- leftHeelToHipDistance, rightHeelToHipDistance
- ankleSeparation, wristSeparation
- wristToAnkleDistance (normalized)

---

## 7. Pipeline Architecture

```
CAMERA
  ↓
MediaPipe Pose 2D (33 landmarks + visibility)
  ↓
Landmark Normalization
  ↓
One-Euro Filter (or equivalent smoother)
  ↓
FeatureCalculator
  ├── Angles
  ├── Distances
  └── Positions
  ↓
ExerciseDetector (state machine)
  ↓
FormValidator
  ↓
┌─────────────┬─────────────┬──────────────┐
│ REPETITION  │    HOLD     │   CADENCE    │
│ BALLISTIC   │ POSE_MATCH  │              │
└─────────────┴─────────────┴──────────────┘
  ↓
RESULT → ClashFit scoring
```

Form validation is **separated** from rep/hold counting.

Three levels of form validation:
1. **Movement validity** – correct body parts moved
2. **Range validity** – sufficient depth / extension
3. **Alignment validity** – supporting geometry acceptable

A rep is only counted when all three pass.

---

## 8. Hold Engine

```
WAITING
  ↓ (pose becomes valid)
VALIDATING
  ↓ (valid continuously for 300–500 ms)
HOLDING
  ↓
  while HOLDING:
    if pose becomes invalid → start 500 ms grace timer
      if pose recovers within 500 ms → continue HOLDING
      else → pause timer (preferred) or reset according to policy
  ↓
  when required duration reached → COMPLETE
```

**Policy recommendation:** Pause the timer during the grace period rather than zeroing accumulated time.

---

## 9. Exercise Families & Specifications

### 9.1 Rep-Cycle Exercises

#### Calf Raise
- **Preferred view:** SIDE
- **Primary signal:** Heel elevation (normalized heel height or heel-to-hip distance).  
  Knee angle is secondary (must stay mostly extended).
- **Knee angles (monitoring only):**  
  Left: 23→25→27 Right: 24→26→28
- **Start:** Knee 160°–180°, heel near baseline
- **Phase 1:** Heel rises
- **Phase 2:** Heel elevated ≥ ~10–15 % of lower-leg length, knee remains >150°
- **Complete:** Heel returns to baseline **and** required height was previously reached
- **Reject:** Large knee bend, excessive lean, tiny heel movement
- **Important:** Do **not** rely on ankle angle alone.

#### Chair Squat
- **Preferred view:** SIDE
- **Primary:** Hip-Knee-Ankle (23-25-27 / 24-26-28)
- **Secondary:** Shoulder-Hip-Knee (11-23-25 / 12-24-26)
- **Start:** Knee >150°, hip relatively extended
- **Phase 1:** Knee <145°, hips move back/down
- **Phase 2:** Knee ≈ 90°–115°
- **Complete:** Bottom reached + return to knee >150°
- **Form:** Knee tracks over foot, torso controlled, feet stable
- **Reject:** Insufficient depth, strong knee collapse inward, starting already crouched

#### Glute Bridge
- **Preferred view:** SIDE
- **Primary:** Shoulder-Hip-Knee (11-23-25 / 12-24-26)
- **Start:** Hip angle roughly 90°–130°
- **Phase 1:** Hips move upward
- **Phase 2:** Hip angle ≥155°
- **Complete:** Top reached + hips return toward start
- **Secondary:** Knee >80°, shoulder-hip-knee stable
- **Reject:** Movement only from lower back, knees collapse, hip never reaches extension

#### Squat
- **Preferred view:** SIDE
- **Primary:** Hip-Knee-Ankle (23-25-27 / 24-26-28)
- **Secondary:** Shoulder-Hip-Knee
- **Start:** Knee ≥150°
- **Phase 1 (DESCENDING):** Knee <145°
- **Phase 2 (BOTTOM):** Knee ≤105° (good target range 80°–105°)
- **Return:** Knee ≥150°
- **Complete:** Descend → bottom threshold → ascend → top threshold
- **Form:** Knee tracks with foot, torso does not collapse excessively
- **Note:** Final thresholds must be calibrated; 105° is an initial value only.

#### Wall Push-up
- **Preferred view:** SIDE
- **Primary:** Shoulder-Elbow-Wrist (11-13-15 / 12-14-16)
- **Start:** Elbow ≥150°
- **Phase 1:** Elbow <140°
- **Phase 2:** Elbow ≤95°
- **Return:** Elbow ≥150°
- **Secondary:** Shoulder-Hip-Ankle (body line)
- **Form:** Body approximately straight, head/torso move together
- **Reject:** Hips stay far behind, only elbows move, insufficient depth

#### Forward Lunge
- **Preferred view:** SIDE or 3/4 (use higher-confidence side)
- **Primary (front leg):** Hip-Knee-Ankle
- **Secondary (rear leg):** Opposite Hip-Knee-Ankle
- **Start:** Front knee >150°, rear knee >140°
- **Phase 1:** Front knee <140°
- **Phase 2:** Front knee 80°–105°
- **Return:** Front knee >150°
- **Complete:** Deep lunge reached + returned to start
- **Form:** Front knee tracks over foot, rear knee bends, torso controlled
- **Alternating:** Count left-leading and right-leading separately. Do not require both legs to satisfy identical angles simultaneously.

#### Knee Push-up
- **Preferred view:** SIDE
- **Primary:** Shoulder-Elbow-Wrist
- **Bottom:** Elbow 75°–105°
- **Top:** Elbow ≥150°
- **Secondary:** Shoulder-Hip-Knee (knees are the support point)

#### Sit-up
- **Preferred view:** SIDE
- **Primary:** Shoulder-Hip-Knee
- **Start:** ~140°–180°
- **Top:** ~45°–80°
- **Return:** >140°
- **Secondary:** Knee angle, nose/shoulder vertical movement
- **Complete:** Start → torso rises past top threshold → returns to start
- **Reject:** Partial crunch, hips doing almost all the work

#### Push-up
- **Preferred view:** SIDE
- **Primary:** Shoulder-Elbow-Wrist
- **Start:** 150°–180°
- **Bottom:** 70°–100°
- **Return:** ≥150°
- **Secondary:** Shoulder-Hip-Ankle
- **Form:** Body line approximately straight
- **Reject:** Hip sag, pike, partial elbow movement

#### Pike Push-up
- **Preferred view:** SIDE
- **Primary:** Shoulder-Elbow-Wrist
- **Top:** Elbow ≥150°
- **Bottom:** Elbow ≈70°–105°
- **Secondary:** Shoulder-Hip-Ankle
- **Pike condition:** Hip angle ≈50°–100°
- **Completion:** Pike start → elbow flexion → bottom → elbow extension

---

### 9.2 Isometric Holds

Holds use a different state machine (see Section 8).  
`invalidGraceMs = 500`.

#### Glute Bridge Hold
- **View:** SIDE
- **Primary:** Shoulder-Hip-Knee → target 155°–180°
- **Secondary:** Knee >80°
- **Invalid:** Hip <145° for >500 ms

#### Forearm Plank
- **View:** SIDE
- **Primary:** Shoulder-Hip-Ankle → 165°–180°
- **Secondary:** Shoulder-Elbow-Wrist ≈80°–110°
- Do not require pixel-perfect wrist-under-elbow.

#### Plank (High Plank)
- **View:** SIDE
- **Primary:** Shoulder-Hip-Ankle → 165°–180°
- **Elbow:** 150°–180°

#### Squat Hold
- **View:** SIDE
- **Primary:** Hip-Knee-Ankle → 80°–110°
- **Secondary:** Shoulder-Hip-Knee → ~50°–100° (style-dependent)
- **Invalid after 500 ms:** Knee leaves range

#### Superman Hold
- **View:** SIDE
- **Primary:** Shoulder-Hip-Knee → 145°–180°
- **Secondary:** Shoulder-Elbow-Wrist >145°, hip/leg extension maintained
- Treat as a range match, not an exact angle.

#### Wall Sit
- **View:** SIDE
- **Primary:** Hip-Knee-Ankle → 80°–110°
- **Secondary:** Shoulder-Hip-Knee → ~70°–110°
- Require torso approximately vertical.  
  2D cannot verify actual back-to-wall contact.

#### Hollow Hold
- **View:** SIDE
- **Primary:** Shoulder-Hip-Knee
- **Secondary:** Knee >150° + relative heights
  - `ankle_y < hip_y + tolerance`
  - shoulder elevated from expected floor level  
  (after body-height normalization)
- Relative heights are more reliable than angles alone.

#### Side Plank (Left / Right)
- **Left:** Primary chain 11→23→27 (body line 165°–180°)  
  Supporting elbow 11→13→15 (80°–110°)
- **Right:** 12→24→28 and 12→14→16

---

### 9.3 Cardio / Cadence Exercises

Do **not** insist on one exact angle. Detect repeated movement cycles.

#### Breathing (proxy only)
MediaPipe 2D cannot measure true respiration.  
Use torso-length proxy:

```js
torsoLength = distance(shoulderMid, hipMid)
```
Smooth the signal and detect expansion → peak → contraction → trough.  
Label internally as `breathing_motion_proxy`, never as true respiratory rate.

#### Standing Torso Twists
- **View:** FRONT
- Shoulder line = 11→12, Hip line = 23→24
- `torsoRotation = shoulderLineAngle − hipLineAngle`
- Left ≤ −20°, Right ≥ +20°
- Cycle: neutral → left → neutral → right → neutral (or alternate)

#### Butt Kicks
- **View:** FRONT or SIDE
- Per leg: Hip-Knee-Ankle + heel-to-hip distance
- Count when heel approaches glute **and** knee flexes substantially  
  Typical: knee <90°–110° and normalized heel-to-hip < ~0.8, then return

#### High Knees
- **View:** FRONT
- Per leg: Shoulder-Hip-Knee
- Target hip angle ≤90°–110° + knee rises near or above hip level
- Count alternating left / right knee lifts

#### Jumping Jacks
- **View:** FRONT
- No single three-point angle is sufficient.
- Arms: mostly extended + `wrist_y < shoulder_y` (overhead)
- Feet: normalized ankle separation  
  - Closed: < ~1.2 × shoulder width  
  - Open: > ~1.8 × shoulder width
- Cycle: CLOSED → OPEN → CLOSED (with hysteresis)

#### Mountain Climbers
- **View:** FRONT / 3/4
- Per leg: Shoulder-Hip-Knee
- Active: knee moves toward torso (`knee_y < hip_y + tolerance`) and hip angle ≈ ≤100°–110°
- Count alternating knee drives

#### Running in Place
- **View:** FRONT
- Ankle vertical oscillation + knee elevation + alternation
- Knee target ~100°–130° (style-dependent)
- Signal is left-lift → right-lift → left… rather than exact angle

#### Seal Jacks
- **View:** FRONT
- Feet: ankle separation change  
- Arms: wrist separation change
- Open / Closed with hysteresis on both conditions

#### Shadow Boxing
- **View:** FRONT
- Per arm: Shoulder-Elbow-Wrist
- Punch: elbow >155° **and** wrist moves substantially away from shoulder
- Cycle: guard → extension → retraction  
  Do not count on elbow angle alone.

#### Skipping (no rope)
- **View:** FRONT
- Ankle_y (and optionally heel_y) oscillation
- Jump phase: both feet rise → airborne → landing
- Count full takeoff → airborne → landing cycles

#### Skater Hops
- **View:** FRONT
- Support knee ~70°–130° + horizontal ankle displacement
- Count each successful side landing (L → R → L…)

---

### 9.4 Ballistic Exercises

**Rule:** Never count on takeoff. Count only after confirmed landing. This dramatically reduces double-counts.

#### Broad Jump
- **View:** SIDE
- States: READY → LOADING → TAKEOFF → AIRBORNE → LANDING → COMPLETE
- Loading: knee <140°
- Takeoff: both ankles rise
- Landing: feet return downward + horizontal displacement secondary

#### Jump Squat
- **View:** SIDE
- Primary: Hip-Knee-Ankle
- Start >150° → Squat ≤105° → Airborne (ankles rise) → Landing
- Count after confirmed landing

#### Lateral Bound
- **View:** FRONT
- Support knee + horizontal ankle displacement
- Count left landing → right landing

#### Squat Thrust
- **View:** SIDE / FRONT
- Phases: STAND → SQUAT → PLANK → SQUAT → STAND
- Squat: knee ≤120°
- Plank: shoulder-hip-ankle ≈165°–180°
- Completion: returns to standing  
  (Do not require a push-up unless explicitly defined as burpee-style)

#### Star Jump
- **View:** FRONT
- Airborne: hands above shoulders **and** ankles significantly apart
- Count on landing

#### Burpee
- **View:** SIDE
- States: STAND → SQUAT → PLANK → RETURN → STAND → JUMP → LAND
- Primary angles: hip-knee-ankle, shoulder-hip-ankle, shoulder-elbow-wrist
- If push-up not required: plank detection only
- Completion: standing jump completed + landing confirmed

#### Tuck Jump
- **View:** SIDE / FRONT
- Preload: knee ≤120°
- Airborne: feet leave baseline
- Tuck: knees rise substantially, hip angle decreases
- Count only after landing

---

### 9.5 Yoga / Pose Matching

Use a **pose classifier**, not a repetition counter:

```
pose candidate
  ↓ geometry match
  ↓ stable for 500–800 ms
  ↓ POSE_VALID
```

#### Balasana (Child’s Pose)
- **View:** SIDE
- Primary: Shoulder-Hip-Knee ≈30°–80°
- Secondary: hip close to heel, head down, knee deeply flexed

#### Marjaryasana (Cat-Cow)
- Two sub-poses (do not force a single angle):
  - Cat: spine curved upward, head down
  - Cow: spine extended, head/chest lifted
- Use relative landmarks (nose, shoulders, hips) and curvature trend

#### Tadasana (Mountain)
- **View:** FRONT
- Ankles under hips, knees ≥165°, hips stacked over ankles, shoulders over hips, head over torso
- Shoulder & hip lines approximately horizontal

#### Adho Mukha Svanasana (Downward Dog)
- **View:** SIDE
- Primary: Shoulder-Hip-Ankle ≈60°–110°
- Secondary: Knee >150°, Elbow >150°, hands below shoulders

#### Bhujangasana (Cobra)
- **View:** SIDE
- Primary: Shoulder-Hip-Knee ≈140°–180°
- Secondary: Shoulder-Elbow-Wrist 100°–180° (height-dependent)

#### Setu Bandha (Bridge)
- **View:** SIDE
- Primary: Shoulder-Hip-Knee 150°–180°
- Secondary: Hip-Knee-Ankle 70°–120°

#### Utkatasana (Chair)
- **View:** SIDE
- Primary: Hip-Knee-Ankle 80°–110°
- Secondary: Shoulder-Hip-Knee 45°–100°
- Additional: arms elevated (wrist above shoulder)

#### Virabhadrasana I (Warrior I)
- **View:** FRONT / 3/4
- Front knee 80°–110°, rear knee >155°
- Arms: elbow >150°, wrists above shoulders
- 2D cannot perfectly verify rear-foot / pelvis rotation → use tolerance

#### Virabhadrasana II (Warrior II)
- **View:** FRONT / 3/4
- Front knee 80°–110°, rear knee >155°
- Arms approximately horizontal (elbow >155°), wrists ≈ shoulder height

#### Trikonasana (Triangle)
- **View:** 3/4 preferred
- Extended front leg knee >155°
- Torso bends sideways; one arm up, one arm down
- Pure front view is ambiguous

#### Vrikshasana (Tree)
- **View:** FRONT
- Support knee >160°
- Lifted knee moves laterally; hip angle of lifted leg <120°
- Lifted ankle approaches inner support leg (distance / proximity required)

#### Garudasana (Eagle)
- **View:** FRONT
- Standing knee ≈70°–130°
- Crossed leg flexed, ankle across support leg
- Arms: elbows flexed, forearms cross
- Pure angles insufficient → wrist-to-wrist and elbow-to-elbow proximity needed

#### Natarajasana (Dancer)
- **View:** SIDE / 3/4
- Support knee >155°
- Raised knee 40°–110°
- Same-side or cross-side hand approaches lifted ankle (normalized wrist-ankle distance)
- Torso inclined forward

#### Ustrasana (Camel)
- **View:** SIDE
- Knees ≈70°–120°
- Torso extension + arms moving backward toward feet
- Wrist-to-ankle proximity + Shoulder-Hip-Knee
- Do not require an exact back-extension angle (camera placement sensitive)

---

## 10. Compact Exercise Matrix (Quick Reference)

| Exercise            | View      | Primary Signal                  | Secondary                     | Main Transition / Target                  |
|---------------------|-----------|---------------------------------|-------------------------------|-------------------------------------------|
| Calf Raise          | Side      | Heel height                     | Knee extension                | heel down → up → down                     |
| Chair Squat         | Side      | Hip-Knee-Ankle                  | Shoulder-Hip-Knee             | standing → 90-115° → standing             |
| Glute Bridge        | Side      | Shoulder-Hip-Knee               | Knee angle                    | low → hip ≥155° → low                     |
| Squat               | Side      | Hip-Knee-Ankle                  | Shoulder-Hip-Knee             | >150° → ≤105° → >150°                     |
| Wall Push-up        | Side      | Shoulder-Elbow-Wrist            | Shoulder-Hip-Ankle            | >150° → ≤95° → >150°                      |
| Forward Lunge       | Side/3/4  | Front knee                      | Rear knee, torso              | standing → ≤105° → standing               |
| Knee Push-up        | Side      | Shoulder-Elbow-Wrist            | Shoulder-Hip-Knee             | >150° → 75-105° → >150°                   |
| Sit-up              | Side      | Shoulder-Hip-Knee               | Knee / vertical movement      | open → 45-80° → open                      |
| Push-up             | Side      | Shoulder-Elbow-Wrist            | Shoulder-Hip-Ankle            | >150° → 70-100° → >150°                   |
| Pike Push-up        | Side      | Shoulder-Elbow-Wrist            | Shoulder-Hip-Ankle + hip      | >150° → ≤105° → >150°                     |
| Glute Bridge Hold   | Side      | Shoulder-Hip-Knee               | Knee                          | hip ≥155°                                 |
| Forearm Plank       | Side      | Shoulder-Hip-Ankle              | Shoulder-Elbow-Wrist          | body 165-180°                             |
| Plank               | Side      | Shoulder-Hip-Ankle              | Elbow                         | body 165-180°                             |
| Squat Hold          | Side      | Hip-Knee-Ankle                  | Torso                         | 80-110°                                   |
| Superman Hold       | Side      | Shoulder-Hip-Knee               | Arm extension                 | extended                                  |
| Wall Sit            | Side      | Hip-Knee-Ankle                  | Torso                         | 80-110°                                   |
| Hollow Hold         | Side      | Shoulder-Hip-Knee + heights     | Knee / ankle height           | fixed geometry                            |
| Side Plank L/R      | Side      | Shoulder-Hip-Ankle              | Supporting elbow              | 165-180°                                  |
| Breathing           | Front     | Torso length proxy              | Shoulder motion               | expansion / contraction                   |
| Torso Twists        | Front     | Shoulder vs hip line            | —                             | left ↔ neutral ↔ right                    |
| Butt Kicks          | Front/Side| Hip-Knee-Ankle + heel-hip       | —                             | heel up / down                            |
| High Knees          | Front     | Shoulder-Hip-Knee + knee height | —                             | alternating                               |
| Jumping Jacks       | Front     | Arm / leg spread                | Wrist & ankle distances       | closed → open → closed                    |
| Mountain Climbers   | Front/3/4 | Hip-Knee + knee-to-chest        | —                             | alternating                               |
| Running in Place    | Front     | Knee / ankle oscillation        | Alternation                   | alternating                               |
| Seal Jacks          | Front     | Arm geometry + ankle spread     | —                             | closed → open                             |
| Shadow Boxing       | Front     | Shoulder-Elbow-Wrist + wrist Δ  | —                             | guard → punch → guard                     |
| Skipping            | Front     | Ankle vertical motion           | Heel                          | jump cycle                                |
| Skater Hops         | Front     | Support knee + lateral ankle    | —                             | L → R                                     |
| Broad Jump          | Side      | Hip/Knee + ankle movement       | Horizontal displacement       | load → flight → landing                   |
| Jump Squat          | Side      | Knee + ankle baseline           | —                             | squat → flight → landing                  |
| Lateral Bound       | Front     | Support knee + lateral ankle    | —                             | jump → landing                            |
| Squat Thrust        | Front/Side| Knee + plank                    | —                             | squat → plank → stand                     |
| Star Jump           | Front     | Arms / legs spread              | —                             | closed → flight → landing                 |
| Burpee              | Side      | Squat / plank / elbow           | Full body                     | stand → plank → jump                      |
| Tuck Jump           | Side/Front| Hip-Knee + knee height          | —                             | preload → tuck → landing                  |
| Balasana            | Side      | Shoulder-Hip-Knee               | Head / heel proximity         | folded pose                               |
| Marjaryasana        | Side      | Spine geometry                  | Head / pelvis                 | cat / cow                                 |
| Tadasana            | Front     | Leg alignment + torso stack     | —                             | upright pose                              |
| Downward Dog        | Side      | Shoulder-Hip-Ankle              | Knee / elbow                  | inverted V                                |
| Cobra               | Side      | Shoulder-Hip-Knee               | Elbow                         | torso extension                           |
| Bridge              | Side      | Shoulder-Hip-Knee               | Knee                          | hip extension                             |
| Chair (Utkatasana)  | Side      | Hip-Knee-Ankle + arms           | —                             | squat + arms elevated                     |
| Warrior I           | Front/3/4 | Front knee                      | Rear knee / arms              | static pose                               |
| Warrior II          | Front/3/4 | Front knee                      | Rear knee / arms              | static pose                               |
| Triangle            | 3/4       | Leg + torso                     | Arm orientation               | static pose                               |
| Tree                | Front     | Support knee + ankle proximity  | —                             | balance pose                              |
| Eagle               | Front     | Knees / arms + cross distances  | —                             | crossed pose                              |
| Dancer              | Side/3/4  | Support knee + wrist-ankle      | —                             | balance / extension                       |
| Camel               | Side      | Torso / knee + wrist-ankle      | —                             | backbend pose                             |

---

## 11. Reusable Detector Primitives

Do **not** implement 51 independent detectors. Build a small set of primitives and compose them via configuration:

- `AngleFeature`
- `DistanceFeature`
- `VerticalPositionFeature`
- `HorizontalPositionFeature`
- `LineAngleFeature`
- `LandmarkVisibilityFeature`
- `RelativeDistanceFeature`
- `StateMachine`
- `HoldTimer`
- `AlternatingSideDetector`
- `LandingDetector`
- `PoseMatcher`

Example compositions:
- **Squat** = KneeAngle + HipAngle + StateMachine
- **Jumping Jack** = AnkleSeparation + WristHeight + StateMachine
- **High Knee** = HipAngle + KneeHeight + AlternatingSideDetector
- **Tree Pose** = SupportLegStraight + LiftedKneeLateral + AnkleProximity + PoseMatcher

---

## 12. Final Implementation Notes

1. All degree values in this document are **starting thresholds**. Replay real traces and calibrate enter/exit values.
2. Prefer configuration files + generic engine over hard-coded per-exercise logic.
3. Keep form validation completely separate from the counting / timing logic.
4. Always apply side locking and hysteresis.
5. For ballistic movements, confirm landing before incrementing the counter.
6. For holds, prefer pausing the timer during the 500 ms grace period rather than resetting accumulated time.
7. Normalize all distances by body height so thresholds work across different body sizes.
8. The repository’s existing One-Euro filter, per-exercise JSON configs, and trace-replay infrastructure should be leveraged rather than replaced.

---

**End of Source of Truth**
