# Phase-1 pitch deck

The deck for the **1 Sep 2026** submission. Separate from [`../deck/`](../deck), which is the
event-day pitch for 5–6 Sep and talks about what shipped in 30 hours.

| File | What it is |
| --- | --- |
| `index.html` | The deck itself. Animated, keyboard-driven, 15 slides. |
| `ClashFit-Phase1-Deck.pdf` | 15 pages, 1920×1080. |
| `ClashFit-Phase1-Deck.pptx` | 15 slides, 13.333 × 7.5 in (16:9). |
| `slides/` | The 15 frames both exports are built from. |

## Presenting

Open `index.html` and press **F** for full screen.

`←` `→` or space to move · `Home` / `End` to jump · click the left third to go back,
anywhere else to advance · swipe on touch · the dots on the right jump to any slide.
The URL hash tracks the slide, so `#8` opens on the fatigue curve.

## Rebuilding the exports

The exports are screenshots of the real deck, captured at 2× after the entrance animations
finish, so the PDF and the PPTX look exactly like the screen. Chrome's own print-to-PDF was
tried first and rejected — it reflowed four slides onto extra pages.

```bash
python3 -m http.server 8080          # from the repo root
# capture 15 frames at 3840×2160, downscale to 1920×1080 JPEGs
# then Pillow for the PDF and python-pptx for the PPTX
```

Every number on every chart is read from `config/` or from the engine's own replay output.
If a config value changes, regenerate rather than editing a slide by hand.
