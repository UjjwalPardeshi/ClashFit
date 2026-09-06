#!/usr/bin/env python3
"""Build the ClashFit deck HTML: screenshots embedded as data URIs, fonts inlined."""
import base64, io, os, sys
from PIL import Image

ROOT = '/home/palkia/code/stuff/hacks/iqoo/ClashFit'
SHOTS = os.path.join(ROOT, 'android/app/screenshots')
SP = '/tmp/claude-1000/-home-palkia-code-stuff-hacks-iqoo-ClashFit/71b0d431-fcb2-45f3-be33-f50e6e4a0231/scratchpad'
FONTS = open(os.path.join(SP, 'fonts.css')).read()

_cache = {}
def shot(name, h=700):
    """Screenshot as a data URI, rendered at 2x the on-slide height."""
    key = (name, h)
    if key in _cache: return _cache[key]
    im = Image.open(os.path.join(SHOTS, name)).convert('RGB')
    th = h * 2
    tw = max(1, round(im.width * th / im.height))
    im = im.resize((tw, th), Image.LANCZOS)
    b = io.BytesIO(); im.save(b, 'JPEG', quality=86, optimize=True)
    uri = 'data:image/jpeg;base64,' + base64.b64encode(b.getvalue()).decode()
    _cache[key] = uri
    return uri

def cap(name, h, label):
    """A phone with a caption under it, so a wall of screens reads as a labelled set."""
    return (f'<div class="wi"><div class="phone" style="height:{h}px">'
            f'<img src="{shot(name, h)}" alt=""></div><div class="cp">{label}</div></div>')

def phone(name, h=700, cls=''):
    """A screenshot in a phone bezel, sized by HEIGHT so tall shots cannot overflow."""
    return (f'<div class="phone {cls}" style="height:{h}px">'
            f'<img src="{shot(name, h)}" alt=""></div>')

CSS = """
*{box-sizing:border-box;margin:0;padding:0}
:root{
  --ink:#08080A; --ink2:#0F1014; --line:#232733;
  --hot:#FF5A1F; --hot2:#FF8A3D; --ember:#7A2D10;
  --paper:#F4F5F7; --mute:#8B90A0; --dim:#5A6070;
}
body{background:#000;font-family:'Archivo',system-ui,sans-serif;-webkit-font-smoothing:antialiased}
.slide{
  width:1920px;height:1080px;display:flex;flex-direction:column;position:relative;
  background:var(--ink);color:var(--paper);overflow:hidden;padding:92px 110px;
}
/* a faint diagonal grain so flat black does not band on a projector */
.slide::after{
  content:'';position:absolute;inset:0;pointer-events:none;opacity:.5;
  background:repeating-linear-gradient(45deg,rgba(255,255,255,.014) 0 2px,transparent 2px 5px);
}
.eyebrow{font-family:'Barlow Condensed';font-weight:700;font-size:26px;letter-spacing:.30em;
  text-transform:uppercase;color:var(--hot)}
.eyebrow.dim{color:var(--dim)}
h1{font-family:'Anton';font-weight:400;font-size:150px;line-height:.92;letter-spacing:-.018em;
  text-transform:uppercase}
h2{font-family:'Anton';font-weight:400;font-size:104px;line-height:.94;letter-spacing:-.014em;
  text-transform:uppercase}
h3{font-family:'Anton';font-weight:400;font-size:62px;line-height:1;text-transform:uppercase}
.sub{font-size:34px;line-height:1.34;color:var(--mute);font-weight:600;max-width:1080px}
.hot{color:var(--hot)}
.rule{height:7px;width:150px;background:var(--hot);margin:38px 0}
.rule.sm{height:5px;width:96px;margin:26px 0}
.pageno{position:absolute;right:110px;bottom:64px;font-family:'Barlow Condensed';font-weight:600;
  font-size:24px;color:var(--dim);letter-spacing:.22em}
.tag{position:absolute;left:110px;bottom:64px;font-family:'Barlow Condensed';font-weight:600;
  font-size:24px;color:var(--dim);letter-spacing:.22em;text-transform:uppercase}
.grow{flex:1}
.row{display:flex;gap:64px;align-items:center}
.col{display:flex;flex-direction:column}
/* phone bezel */
.phone{border-radius:30px;overflow:hidden;border:3px solid #2A2E3A;background:#000;
  box-shadow:0 44px 90px rgba(0,0,0,.75), 0 0 0 1px rgba(255,255,255,.05);flex:none;line-height:0}
.phone img{display:block;height:100%;width:auto}
.phone.tilt{transform:rotate(-3deg)}
.phone.tilt2{transform:rotate(2.4deg)}
/* stat blocks */
.stats{display:flex;gap:0;border-top:2px solid var(--line);border-bottom:2px solid var(--line)}
.stat{flex:1;padding:30px 30px;border-right:2px solid var(--line)}
.stat:last-child{border-right:0}
.stat .n{font-family:'Anton';font-size:72px;line-height:1;color:var(--hot)}
.stat .l{font-family:'Barlow Condensed';font-weight:600;font-size:25px;letter-spacing:.16em;
  text-transform:uppercase;color:var(--mute);margin-top:12px}
/* pipeline */
.pipe{display:flex;align-items:stretch;gap:20px;margin-top:14px}
.step{flex:1;border:2px solid var(--line);border-radius:16px;padding:32px 26px;background:var(--ink2)}
.step .k{font-family:'Barlow Condensed';font-weight:700;font-size:23px;letter-spacing:.20em;
  color:var(--hot);text-transform:uppercase}
.step .v{font-size:29px;font-weight:600;margin-top:12px;line-height:1.24}
.arr{align-self:center;color:var(--ember);font-size:40px;font-weight:900}
/* two-column compare */
.cmp{display:flex;gap:0;border:2px solid var(--line);border-radius:20px;overflow:hidden}
.cmp>div{flex:1;padding:52px 46px}
.cmp>div:first-child{border-right:2px solid var(--line);background:#0C0D11}
.cmp h4{font-family:'Barlow Condensed';font-weight:700;font-size:30px;letter-spacing:.20em;
  text-transform:uppercase;color:var(--dim);margin-bottom:26px}
.cmp.win>div:last-child{background:linear-gradient(180deg,rgba(255,90,31,.12),rgba(255,90,31,.03))}
.cmp.win h4:last-of-type{color:var(--hot)}
.li{font-size:31px;line-height:1.62;color:var(--paper);font-weight:600}
.li.m{color:var(--mute);font-weight:600}
/* wall of screenshots */
.wall{display:flex;gap:34px;align-items:flex-end;justify-content:center}
.wi{display:flex;flex-direction:column;align-items:center;gap:20px}
.cp{font-family:'Barlow Condensed';font-weight:700;font-size:24px;letter-spacing:.16em;
  text-transform:uppercase;color:var(--mute);text-align:center;white-space:nowrap}
.badge{display:inline-block;font-family:'Barlow Condensed';font-weight:700;font-size:24px;
  letter-spacing:.18em;text-transform:uppercase;color:var(--ink);background:var(--hot);
  padding:9px 20px;border-radius:6px}
.badge.ghost{background:transparent;color:var(--hot);border:2px solid var(--hot)}
"""

def slide(inner, tag='ClashFit', no=None, cls=''):
    p = f'<div class="pageno">{no:02d}</div>' if no else ''
    t = f'<div class="tag">{tag}</div>' if tag else ''
    return f'<section class="slide {cls}">{inner}{t}{p}</section>'

S = []

# 01 — TITLE
S.append(slide(f"""
<div class="row" style="gap:80px;height:100%">
  <div class="col grow" style="justify-content:center">
    <div class="eyebrow">iQOO Hackathon 2026 · Pune · HealthTech</div>
    <h1 style="margin-top:30px;font-size:118px">Your personal<br>fitness coach.<br><span class="hot">Gamified.</span></h1>
    <div class="rule"></div>
    <div class="sub" style="font-size:33px;color:#C9CDD8;max-width:820px">
      Your body is the controller. Your camera is the referee.<br>
      Every rep is <b style="color:#fff">graded before it counts</b> — and the grade
      becomes damage against a boss that fights back.
    </div>
    <div style="margin-top:38px;display:flex;gap:16px">
      <span class="badge">ClashFit</span>
      <span class="badge ghost">Team Da Goats</span>
    </div>
  </div>
  <div style="display:flex;gap:30px;flex:none">
    {phone('01-train.png', 800, 'tilt')}
    {phone('50-fight-opening.png', 800, 'tilt2')}
  </div>
</div>""", tag='', no=None))

# 02 — PROBLEM
S.append(slide(f"""
<div class="eyebrow dim">The problem</div>
<h2 style="margin-top:30px">Every fitness app<br>counts what you <span class="hot">tell it</span>.</h2>
<div class="rule"></div>
<div class="sub" style="max-width:1240px">Tap ten, it logs ten. Nothing checks whether the rep
actually happened — so the numbers climb while the movement quietly falls apart.</div>
<div class="grow"></div>
<div class="cmp">
  <div>
    <h4>What the app records</h4>
    <div class="li">10 reps<br>Set complete<br>Streak +1</div>
  </div>
  <div>
    <h4>What your body did</h4>
    <div class="li m">4 half reps<br>Range collapsing by rep 6<br>Left side compensating</div>
  </div>
</div>""", no=2))

# 03 — INSIGHT
S.append(slide(f"""
<div class="row" style="height:100%;gap:90px">
  <div class="col grow" style="justify-content:center">
    <div class="eyebrow dim">The insight</div>
    <h2 style="margin-top:30px">The sensor was<br>already in<br>your pocket.</h2>
    <div class="rule"></div>
    <div class="sub">The front camera can see depth, range, tempo and alignment
    at 30&nbsp;frames a second. No strap. No console. No subscription hardware.</div>
  </div>
  {phone('15-preflight.png', 860)}
</div>""", no=3))

# 04 — MECHANISM
S.append(slide(f"""
<div class="eyebrow dim">How it works</div>
<h2 style="margin-top:26px">The camera becomes<br>the <span class="hot">referee</span>.</h2>
<div class="rule sm"></div>
<div class="pipe">
  <div class="step"><div class="k">See</div><div class="v">33 world landmarks, on the GPU, 30 fps</div></div>
  <div class="arr">›</div>
  <div class="step"><div class="k">Measure</div><div class="v">Joint angles, smoothed, in metres</div></div>
  <div class="arr">›</div>
  <div class="step"><div class="k">Judge</div><div class="v">Depth · range · tempo · alignment</div></div>
  <div class="arr">›</div>
  <div class="step"><div class="k">Score</div><div class="v">Form grade becomes boss damage</div></div>
</div>
<div class="grow"></div>
<div class="row" style="gap:40px;justify-content:center">
  {phone('50-fight-opening.png', 396)}
  {phone('51-fight-landing.png', 396)}
  {phone('57-fight-gesture.png', 396)}
</div>""", no=4))

# 05 — FORM GRADING
S.append(slide(f"""
<div class="row" style="height:100%;gap:80px">
  {phone('52-fight-gassed.png', 860)}
  <div class="col grow" style="justify-content:center">
    <div class="eyebrow dim">Why it changes behaviour</div>
    <h2 style="margin-top:26px">A shallow rep<br>buys almost<br><span class="hot">nothing</span>.</h2>
    <div class="rule"></div>
    <div class="sub">Damage is the form score, not the rep count. Cheating the movement
    cheats only you — the boss simply doesn't take the hit.</div>
    <div style="margin-top:44px" class="stats">
      <div class="stat"><div class="n">4</div><div class="l">Graded parts</div></div>
      <div class="stat"><div class="n">≤4<span style="font-size:44px">ms</span></div><div class="l">Landmark → rep</div></div>
      <div class="stat"><div class="n">≤100<span style="font-size:44px">ms</span></div><div class="l">Rep → feedback</div></div>
    </div>
  </div>
</div>""", no=5))

# 06 — FATIGUE
S.append(slide(f"""
<div class="eyebrow dim">The differentiator</div>
<h2 style="margin-top:26px">It reads <span class="hot">fatigue</span> from<br>how you actually move.</h2>
<div class="rule sm"></div>
<div class="sub" style="max-width:1300px">Not a difficulty slider. Velocity decay, range collapse and
growing pauses — measured against your own opening reps, on the phone, every rep.</div>
<div class="grow"></div>
<div class="row" style="gap:56px;align-items:center">
  {phone('36-seeded-summary.png', 470)}
  <div class="col grow" style="gap:22px">
    <div class="step"><div class="k">Fresh</div><div class="v">The boss fights at full strength</div></div>
    <div class="step"><div class="k">Fading</div><div class="v">It presses the advantage</div></div>
    <div class="step" style="border-color:var(--hot)"><div class="k">Gassed</div><div class="v">It staggers — the fight bends to you, and mercy fires when you are done</div></div>
  </div>
</div>""", no=6))

# 07 — BREADTH
S.append(slide(f"""
<div class="eyebrow dim">Not a demo — a platform</div>
<h2 style="margin-top:26px">One engine.<br>Five ways to <span class="hot">tire</span>.</h2>
<div class="rule sm"></div>
<div class="row" style="gap:22px;margin-top:14px">
  <div class="step"><div class="k">Reps</div><div class="v">Velocity</div></div>
  <div class="step"><div class="k">Holds</div><div class="v">Tremor</div></div>
  <div class="step"><div class="k">Poses</div><div class="v">Accuracy</div></div>
  <div class="step"><div class="k">Cardio</div><div class="v">Cadence</div></div>
  <div class="step"><div class="k">Jumps</div><div class="v">Height</div></div>
</div>
<div class="grow"></div>
<div class="row" style="gap:34px;justify-content:center">
  {cap('13-modes.png', 382, 'Sixteen modes')}
  {cap('08-picker.png', 382, 'Fifty-eight movements')}
  {cap('02-library.png', 382, 'Exercise library')}
  {cap('29-breathing.png', 382, 'Breathing &amp; holds')}
</div>""", no=7))

# 08 — ON DEVICE
S.append(slide(f"""
<div class="row" style="height:100%;gap:84px">
  <div class="col grow" style="justify-content:center">
    <div class="eyebrow dim">Architecture</div>
    <h2 style="margin-top:26px">The camera loop<br>never leaves<br>the <span class="hot">phone</span>.</h2>
    <div class="rule"></div>
    <div class="sub">Pose, form scoring and fatigue all run on-device. Frames and landmarks
    are never uploaded. Training works with the radio off — leaderboards sync later.</div>
    <div style="margin-top:40px;display:flex;gap:14px;flex-wrap:wrap">
      <span class="badge">Offline first</span>
      <span class="badge ghost">No video leaves the device</span>
      <span class="badge ghost">No wearable</span>
    </div>
  </div>
  {phone('16-privacy.png', 860)}
</div>""", no=8))

# 09 — MULTIPLAYER
S.append(slide(f"""
<div class="eyebrow dim">Social</div>
<h2 style="margin-top:26px">Two phones. One boss.<br><span class="hot">No server.</span></h2>
<div class="rule sm"></div>
<div class="sub" style="max-width:1280px">Peer-to-peer over local radio. Each phone reports the damage
it earned; the boss takes the sum. It works in airplane mode, in a hall with no signal.</div>
<div class="grow"></div>
<div class="wall">
  {cap('55-duel-rope.png', 448, 'Live duel')}
  {cap('27-compete.png', 448, 'Compete hub')}
  {cap('05a-leaderboard.png', 448, 'Leaderboard')}
  {cap('70-rank-ladder.png', 448, 'Rank ladder')}
</div>""", no=9))

# 10 — PROGRESSION
S.append(slide(f"""
<div class="eyebrow dim">Why people come back</div>
<h2 style="margin-top:26px">Progress you can<br>actually <span class="hot">see</span>.</h2>
<div class="rule sm"></div>
<div class="grow"></div>
<div class="wall">
  {cap('23-charts.png', 358, 'Form over time')}
  {cap('31-seeded-streaks.png', 358, 'Streaks')}
  {cap('33-seeded-character.png', 358, 'Your fighter')}
  {cap('32-seeded-history.png', 358, 'Every rep, kept')}
</div>
<div class="grow"></div>
<div class="stats">
  <div class="stat"><div class="n">58</div><div class="l">Exercises</div></div>
  <div class="stat"><div class="n">16</div><div class="l">Game modes</div></div>
  <div class="stat"><div class="n">22</div><div class="l">Achievements</div></div>
  <div class="stat"><div class="n">3</div><div class="l">Boss phases</div></div>
</div>""", no=10))

# 11 — BEYOND THE GYM
S.append(slide(f"""
<div class="eyebrow dim">Beyond the workout</div>
<h2 style="margin-top:26px">It follows you<br>out of the <span class="hot">room</span>.</h2>
<div class="rule sm"></div>
<div class="grow"></div>
<div class="wall">
  {cap('2e-run-summary.png', 470, 'Outdoor run')}
  {cap('37-seeded-activity.png', 470, 'Activity')}
  {cap('2d-alarm-edit.png', 470, 'Wake-up reps')}
  {cap('14-clinic.png', 470, 'Sit-to-stand clinic')}
</div>
<div class="grow"></div>
<div class="row" style="gap:20px;justify-content:center">
  <span class="badge ghost">Outdoor runs</span>
  <span class="badge ghost">Zombie chase</span>
  <span class="badge ghost">Reps to silence the alarm</span>
  <span class="badge ghost">Sit-to-stand clinic</span>
</div>""", no=11))

# 12 — COMPETITION
S.append(slide(f"""
<div class="eyebrow dim">The field</div>
<h2 style="margin-top:26px">Everyone else needs<br>hardware or a <span class="hot">server</span>.</h2>
<div class="rule sm"></div>
<div class="grow"></div>
<div class="cmp win">
  <div>
    <h4>Ring Fit · Kinect · Peloton Guide · Tempo · Kemtai</h4>
    <div class="li m">A console, a strap or a camera you have to buy<br><br>
    A difficulty curve set in advance<br><br>
    Or your video leaving the room to be scored</div>
  </div>
  <div>
    <h4>ClashFit</h4>
    <div class="li">The phone already in your pocket<br><br>
    A fight that adapts to the fatigue it measures<br><br>
    Nothing about your body leaves the device</div>
  </div>
</div>""", no=12))

# 13 — PROOF
S.append(slide(f"""
<div class="eyebrow dim">Built, not mocked</div>
<h2 style="margin-top:26px">It ships, and it is<br><span class="hot">under test</span>.</h2>
<div class="rule sm"></div>
<div class="grow"></div>
<div class="stats" style="border-bottom:0">
  <div class="stat"><div class="n">1,055</div><div class="l">Tests passing</div></div>
  <div class="stat"><div class="n">58</div><div class="l">Exercises tuned</div></div>
  <div class="stat"><div class="n">56k</div><div class="l">Lines of Kotlin</div></div>
</div>
<div class="stats">
  <div class="stat"><div class="n">33</div><div class="l">Landmarks / frame</div></div>
  <div class="stat"><div class="n">30</div><div class="l">Frames per second</div></div>
  <div class="stat"><div class="n">0</div><div class="l">Frames uploaded</div></div>
</div>
<div class="sub" style="margin-top:40px;font-size:29px">Thresholds are measured on real bodies from
recorded sessions, then pinned by tests — not taken from a diagram.</div>""", no=13))

# 14 — THE WHOLE APP
S.append(slide(f"""
<div class="eyebrow dim">The product today</div>
<h2 style="margin-top:22px">A finished app,<br>not a <span class="hot">prototype</span>.</h2>
<div class="grow"></div>
<div class="wall" style="gap:20px">
  {cap('60-whole-train.png', 500, 'Train')}
  {cap('61-whole-progress.png', 500, 'Progress')}
  {cap('62-whole-compete.png', 500, 'Compete')}
  {cap('63-whole-you.png', 500, 'You')}
  {cap('36-seeded-summary.png', 500, 'Set summary')}
  {cap('34-seeded-you.png', 500, 'Profile')}
</div>
<div class="grow"></div>""", no=14))

# 15 — CLOSE
S.append(slide(f"""
<div class="row" style="height:100%;gap:80px">
  <div class="col grow" style="justify-content:center">
    <div class="eyebrow">Team Da Goats</div>
    <h1 style="margin-top:30px;font-size:126px">Make them<br>earn <span class="hot">every</span><br>rep.</h1>
    <div class="rule"></div>
    <div class="sub" style="font-size:34px">Omkar Kadam &nbsp;·&nbsp; Ujjwal Pardeshi<br>
    <span style="color:var(--hot)">clash-fit.vercel.app</span></div>
  </div>
  <div style="display:flex;gap:30px;flex:none">
    {phone('26-boss-preview.png', 760, 'tilt2')}
    {phone('19-boss-states.png', 760, 'tilt')}
  </div>
</div>""", tag='', no=None))

html = f"""<!doctype html><html><head><meta charset="utf-8"><title>ClashFit</title>
<style>{FONTS}</style><style>{CSS}</style></head><body>{''.join(S)}</body></html>"""

out = os.path.join(SP, 'deck.html')
open(out, 'w').write(html)
print(f"{len(S)} slides -> {out}  ({len(html)/1e6:.1f} MB)")
