# Product pitch deck

`ClashFit-Product-Deck.pdf` — 15 slides, 1920×1080, 16:9.

Opens on the promise: **your personal fitness coach, gamified**. Then the problem
(every app counts what you tell it), the insight (the camera is already there), the
mechanism, the fatigue model, breadth, privacy, multiplayer, proof and the close.

Every screenshot is a real capture from `android/app/screenshots/`. Every number on
slide 13 is checked against the repo — see the build script for the sources.

## Rebuilding

```bash
python3 build.py     # writes index.html with fonts and screenshots inlined
python3 render.py index.html frames ClashFit-Product-Deck.pdf
```

`build.py` embeds the screenshots as data URIs sized by height (the captures are
1078×2399, so width-sizing overflows a 1080-tall slide) and inlines Anton, Archivo
and Barlow Condensed as base64, so the HTML and the PDF are both self-contained.

`render.py` shows one `.slide` at a time in headless Chromium at 2× and screenshots
each, then assembles the frames with Pillow. Chrome's own print-to-PDF was not used:
it reflows the slides onto extra pages.
