# 34 · Accounts, Leaderboards and the Social Layer

**Why this exists.** Everything ClashFit measures happens on one phone, and for most of the build
that was the whole story. But a fitness game with no one else in it is a stopwatch. This document
describes the layer that puts other people on the screen — accounts, friends, boards, levels,
badges and a weekly target — and, just as importantly, the line it will not cross: **nothing that
identifies your body ever leaves the phone.**

> Every number in this document is read from the shipping code. Where the code and this file
> disagree, the code is right and this file is a bug.

---

## 1. What an account is for

You can play the entire game without one. The camera, the referee, the coach, every mode, the
history and the streak all work on a phone that has never been signed in. An account buys exactly
three things:

1. **A name on a board**, so a week of training is comparable to somebody else's.
2. **Friends**, so the comparison is with people you chose.
3. **A place your progress survives**, so a lost phone is not a lost year.

It buys nothing else. There is no account-gated exercise, no premium mode and no daily reward for
opening the app. Signing in never makes the game easier.

---

## 2. Sign-in

**Email and password, through Firebase Auth.** No Google sign-in, no phone number, no social
provider. One field the user already knows and one they choose.

| Screen | What it asks | What it refuses |
|---|---|---|
| Create account | Display name, email, password (8+ characters) | A password it cannot confirm twice |
| Sign in | Email, password | — |
| Reset password | Email | — sends a link, says so, and never reveals whether the address exists |
| Profile setup | A colour, why you are here, a favourite exercise | Nothing — every field has a default |

**The display name is the only name anyone sees.** It is chosen at sign-up, changeable in Account,
and it is what goes on every board. Your email address is never written to the database — the
security rules reject a profile document that contains an `email` field at all, so the app cannot
leak it even by accident.

**When Firebase keys are absent**, the app falls back to a local account service. Sign-up and
sign-in still work, the profile is still yours, and the leaderboard says *"The board is not
reachable"* rather than pretending to be empty. This is the path a fresh clone takes, and it is
tested.

---

## 3. Friends, without an address book

Adding a friend needs **a six-character code**, and nothing else. No contact permission, no email
lookup, no phone number, no "find people you may know".

The code is derived, not stored: the first six bytes of `SHA-256(uid)`, encoded in **Crockford
base32**. That alphabet leaves out `I`, `L`, `O` and `U`, so a code cannot be misread across a
table or misheard down a phone, and it is case-insensitive on entry.

Because it is derived, the code is stable for the life of the account, needs no allocation table,
and cannot collide with a code someone else has already been given.

A friendship is a pair of edges under `users/{uid}/friends/{friendId}`. The rules allow either
side to write the edge, so adding someone is one action rather than an invitation both people have
to remember to accept.

---

## 4. Boards

Four boards, all read-only to everyone and writable only by the person the row belongs to.

| Board | Ranked by | Window |
|---|---|---|
| This week · damage | `weeklyDamage` | ISO week, resets Monday |
| This week · clean reps | `weeklyCleanReps` | ISO week, resets Monday |
| All time · XP | `xp` | Forever |
| Best streak | `bestStreak` | Forever |

Each board has an **Everyone** and a **Friends** view over the same data.

Weekly scores live at `scores/{weekKey}/entries/{uid}`, keyed by the ISO week (`2026-W36`). That
shape matters: a week is a small collection that can be read directly and ordered by the server,
so the weekly board costs one query no matter how many weeks of history exist behind it.

**What a row contains:** display name, level, and the score for that board. Nothing else. No rep
data, no form scores, no fatigue, no session times, no video, no landmarks.

---

## 5. Levels

Cumulative XP for level *L* is **100 × (L−1)^1.5**. Level 2 costs 100, level 5 costs 800, level 10
costs 2,700, level 20 costs 8,300. The curve is deliberately gentle early and steep late, so a
first week feels like progress and a fiftieth does not feel automatic.

Nine titles ride on top of it:

| Level | Title | | Level | Title |
|---|---|---|---|---|
| 1 | Recruit | | 16 | Gladiator |
| 3 | Contender | | 20 | Champion |
| 5 | Brawler | | 30 | Titan |
| 8 | Fighter | | 40 | Legend |
| 12 | Warrior | | | |

### What earns XP

**Reps, weighted by how clean they were.** A rep is worth `4 + 8 × form` XP, so a perfect rep pays
12 and a barely-counted one pays 4. Three times the reward for form the camera can actually see.

| Bonus | XP |
|---|---|
| Boss defeated | 25 |
| New personal best | 30 |
| Streak day *N* | 5 × min(*N*, 7) |
| Weekly challenge complete | 100 |

**Casual mode halves the rep XP and earns no win bonus.** Casual sessions still count toward the
streak and still bank their reps — they just cannot be used to farm a level, which is the whole
reason casual mode is safe to offer.

Nothing awards XP for opening the app, watching anything, or waiting.

---

## 6. Badges

**Eighteen achievements: six bronze, seven silver, five gold.** They come from showing up and from
clean reps. None can be bought and none change the game's difficulty.

Badges use **crossing semantics**, not "is it true now". A badge fires on the session where a
counter goes from below its threshold to at or above it, which means:

- It cannot fire twice.
- It cannot fire retroactively for a threshold you were already past when the badge was added.
- The unlock is attributable to a specific session, so the summary screen can say which rep did it.

There is one arithmetic path for counters (`advance`) and one for unlocks (`newlyUnlocked`), so a
badge and the number behind it can never disagree.

---

## 7. The weekly challenge

**One target a week, the same for everyone.** Eleven challenges rotate deterministically by a hash
of the ISO week key, so every player in the world gets the same one and nobody has to be told what
it is. It resets Monday and pays 100 XP once.

Three metrics: total damage, clean reps, and sessions. Targets range from 3,000 damage or 150 clean
reps at the easy end to a seven-day streak at the hard end.

**Casual sessions count toward the challenge's reps and sessions, but not toward damage.** Rest
days never break it — the challenge is a week-long total, not a chain.

---

## 8. What goes to the cloud, and what never does

This is the part that has to be exactly right.

| Written to Firestore | Never leaves the phone |
|---|---|
| Display name | Camera frames — no video, ever |
| Level and total XP | Pose landmarks |
| Best streak | Per-rep depth, range, tempo, alignment |
| Friend code | Fatigue values and bands |
| Weekly damage and clean reps | Session timings and coach lines |
| A server timestamp | GPS traces from run tracking |
| Friend edges (two user ids) | Your email address |

The security rules enforce the last row rather than trusting the client: a profile document
containing an `email` field is rejected outright. Reads require a signed-in user, writes require
being the owner of the document, and a catch-all denies every path not explicitly listed, so a
collection added later is closed until someone opens it on purpose.

The Privacy screen in the app states all of this in the same words, and the About screen tells you
whether the build you are holding is cloud-backed or local-only.

---

## 9. Deployment

The rules live in `firebase/firestore.rules` and are **not** deployed by building the app. Until
someone runs the deploy, every read fails closed and the leaderboard honestly reports that the
board is unreachable.

```bash
cd firebase
npx -y firebase-tools login
npx -y firebase-tools deploy --only firestore
```

Sign-up deliberately does not fail if the profile write is rejected. An account that exists in Auth
but has no profile document yet is a recoverable state — the profile is rewritten on the next score
sync — whereas telling someone their sign-up failed while their account quietly exists is not.

---

## 10. Related

- [23 · Meta Progression](23-META-PROGRESSION.md) — the streak, ladders and personal bests these levels sit on
- [22 · Health Domains](22-HEALTH-DOMAINS.md) — the character sheet the XP does *not* feed
- [08 · Data Model](08-DATA-MODEL.md) — the local database, which remains the source of truth
- [29 · Build Status](29-BUILD-STATUS.md) — what of this is shipping today
