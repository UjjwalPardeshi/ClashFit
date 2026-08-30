# 31 · Wake-up alarm — feature spec

Status: **specified, not in the current build.** Decided 29 Aug 2026: the page
presents it as a product feature rather than a badged roadmap item, because the
page is now a pre-launch waitlist page — every call to action is "join the
waitlist", the whole product is dated 2026, and nothing invites a reader to try
it today. The section kicker carries the one thing that is not obvious: it needs
the native app. See [30-RULES-DELTA.md](30-RULES-DELTA.md) for the claims policy
this sits under, and [32-FEATURE-BEYOND-THE-GAME.md](32-FEATURE-BEYOND-THE-GAME.md)
for the four tools built on the same idea.

## What it is

A normal alarm clock. The difference is what turns it off: you set a price in
movement — five push-ups, fifteen squats, a thirty-second plank — and the alarm
keeps ringing until the camera has counted them.

The point is not novelty. It is that the hardest part of a morning session is
being upright, and an alarm that cannot be dismissed from bed solves exactly
that. Nothing else in ClashFit reaches the user before they have decided to
train; this does.

## Rules

1. **Any exercise, any count.** The price is fully user-set. Three chair squats
   is a legitimate setting and the copy says so — a price nobody can pay at
   06:30 gets disabled, not respected.
2. **The same scoring as a boss fight.** `SessionEngine` grades these reps the
   way it grades every other rep: depth, range, tempo, alignment. A shallow rep
   does not count, and a wiggle under a blanket counts as nothing.
3. **An emergency dismiss, always.** Held for five seconds, logged in the
   session history, never hidden. An alarm that can trap somebody who is ill,
   injured, or holding a child is a safety defect, not a strict feature.
4. **No network.** Alarm times, prices and the log stay on the device, like
   everything else.

## What it needs that we do not have

| Need | Why it is not free |
| --- | --- |
| Fire while the app is closed | A web build cannot schedule a reliable alarm. This is the feature that forces a native shell (Android `AlarmManager`). |
| Camera at wake time | Permission and a warm-up path from a locked screen. |
| Low-light pose | 06:30 in a bedroom is far darker than any trace we have recorded. The pose landmarker's confidence floor has not been tuned for it. |
| Audio that survives silent mode | Platform-specific; an alarm that a silent switch defeats is not an alarm. |

The first row is the reason this is roadmap and not build: it is the only
feature in the product that cannot exist inside the prototype's delivery
mechanism. Everything else on the page runs in the browser today.

## Screen

On the landing page, `#alarm`. The mockup shows the ring state: time, the price
("5 push-ups"), the live count, and the emergency dismiss as a visible, quiet
control rather than a hidden gesture.

## Open questions for the team

- Does the alarm re-arm if the user fails and falls back asleep, or does it
  escalate the sound and hold?
- Does a completed alarm feed the day's streak, or is it counted separately so
  the streak stays a measure of training rather than of waking up?
