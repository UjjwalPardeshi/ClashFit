# Deck

Ten slides, written from [`../docs/26-DECK-COPY.md`](../docs/26-DECK-COPY.md). Slide 10 is optional —
use it if the pitch is running short or the jury leans clinical.

## Export to PDF

Open `index.html` in Chrome → **Print** → **Landscape** → **Margins: None** →
**Background graphics: ON** → Save as PDF.

Each slide is exactly one page at 297×167mm, which is 16:9. Check the result is under 25 MB — the
submission platform caps it there.

## Before you export

| | |
|---|---|
| `[SURNAME]` | Ujjwal's surname, slides 1 and 9 |
| `[ONE LINE …]` | One countable credential each, slide 9 |
| `[WHY THIS PROBLEM]` | One honest sentence, slide 9 |
| `[MODES ACTUALLY BUILT]` | Fill on the Sunday with what actually shipped, slide 8 |
| Slide 3 | Three real screenshots — calibration, fight HUD mid-rep, boss down |
| Slide 4 | The fatigue curve PNG. Run a set, open **Summary**, **Download PNG**. |

Drop images in `deck/img/` and replace the `.shot` divs with `<img src="img/….png" alt="">`.

**Use real screenshots.** A mocked-up UI sitting next to a live demo reads as dishonest, and a
jury that has just watched the real thing will notice immediately.

## The two slides that win

**4 — the fatigue curve.** It is the one thing on screen no other team has.

**8 — what shipped versus what didn't.** "Idea and scope fit for 30 hours" is an explicit scoring
criterion. Teams who blur the two get caught; teams who separate them cleanly read as senior.
