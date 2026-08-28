#!/usr/bin/env python3
"""Finalise site photography from the judged picks.

    python3 tools/photos.py picks.json [--html index.html] [--max 1800]

`picks.json` is either the judge object
  {"picks":[{role,path,title,page,artist,license,focal,grayscale,reason}],
   "missing":[{role,fallback_role,why}], "note":""}
or the whole workflow return ({"found":[...], "judged":{...}}).

For every pick: resize the candidate to <= MAX px on the long side (hero gets
2200), save as img/<role>.jpg, write img/CREDITS.md, fill the footer credits
list in the HTML, and replace each `var(--f-<role>, ...)` default with the
judge's focal point so `object-position` crops toward the subject.
"""
import html as H
import json
import os
import re
import sys

from PIL import Image, ImageOps

args = sys.argv[1:]
if not args:
    sys.exit(__doc__)
SRC = args[0]
HTML = args[args.index('--html') + 1] if '--html' in args else 'index.html'
MAX = int(args[args.index('--max') + 1]) if '--max' in args else 1800

data = json.load(open(SRC))
judge = data.get('judged', data)
ROLES = {'hero', 'squat', 'pushup', 'plank', 'yoga', 'cardio', 'jump', 'run', 'group', 'pair', 'gym', 'clinic', 'portrait'}


def norm(pick):
    """Some finders put a persona in `role`; the candidate filename is the truth."""
    if pick.get('role') not in ROLES:
        stem = os.path.basename(pick['path']).rsplit('-', 1)[0]
        pick['role'] = stem.replace('-extra', '')
    return pick


picks = {p['role']: p for p in (norm(p) for p in judge['picks'])}
FOCAL = re.compile(r'^(?:\d{1,3}%|center|left|right|top|bottom)(?:\s+(?:\d{1,3}%|center|left|right|top|bottom))?$')

os.makedirs('img', exist_ok=True)


def emit(role, pick):
    im = ImageOps.exif_transpose(Image.open(pick['path'])).convert('RGB')
    lim = 2200 if role == 'hero' else MAX
    im.thumbnail((lim, lim), Image.LANCZOS)
    out = f'img/{role}.jpg'
    im.save(out, 'JPEG', quality=82, optimize=True, progressive=True)
    print(f"{role:<9} {im.width:>4}x{im.height:<4} {os.path.getsize(out) // 1024:>4} KB  "
          f"focal={pick['focal']:<14} bw={str(pick['grayscale']):<5} <- {os.path.basename(pick['path'])}")


for role, pick in picks.items():
    emit(role, pick)

resolved = dict(picks)
for m in judge.get('missing', []):
    fb = picks.get(m['fallback_role'])
    if fb:
        emit(m['role'], fb)
        resolved[m['role']] = fb
        print(f"  {m['role']}: fallback -> {m['fallback_role']} ({m['why']})")
    else:
        print(f"  !! {m['role']}: no usable fallback ({m['why']})")

# ---- credits, de-duplicated by source page ----
seen, credits = set(), []
for pick in picks.values():
    if pick['page'] in seen:
        continue
    seen.add(pick['page'])
    title = re.sub(r'^File:', '', pick['title'])
    title = re.sub(r'\.(jpe?g|png)$', '', title, flags=re.I)
    credits.append((title, pick['artist'] or 'Unknown', pick['license'], pick['page']))

with open('img/CREDITS.md', 'w') as f:
    f.write("# Photography\n\nAll photographs via Wikimedia Commons, used under the licence shown.\n\n"
            "| Photo | Photographer | Licence |\n|---|---|---|\n")
    f.write("\n".join(f"| [{t}]({u}) | {a} | {l} |" for t, a, l, u in credits) + "\n")

# ---- patch the page ----
s = open(HTML).read()
items = "\n".join(
    f'        <li><a href="{H.escape(u)}" rel="noopener">{H.escape(t)}</a> — {H.escape(a)} · {H.escape(l)}</li>'
    for t, a, l, u in credits)
# Replace the whole credits list each run, so the script can be re-run as picks change.
s, n_ul = re.subn(r'(<ul id="credits">).*?(</ul>)', lambda m: f'{m.group(1)}\n{items}\n      {m.group(2)}', s, count=1, flags=re.S)
if not n_ul:
    sys.exit('no <ul id="credits"> in the page')
subs = 0
for role, pick in resolved.items():
    focal = pick['focal'].strip().lower()
    focal = focal if FOCAL.match(focal) else 'center'
    s, n = re.subn(r'var\(--f-%s,[^)]*\)' % re.escape(role), f'var(--f-{role},{focal})', s)
    subs += n
open(HTML, 'w').write(s)
print(f"\ncredits: {len(credits)} · focal substitutions: {subs} · patched {HTML}")
