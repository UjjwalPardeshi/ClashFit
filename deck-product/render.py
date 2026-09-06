#!/usr/bin/env python3
"""Render an HTML deck to PNG frames at 2x, then assemble a PDF. Frames are one .slide each."""
import sys, os, glob
from playwright.sync_api import sync_playwright
from PIL import Image

html, outdir, pdf = sys.argv[1], sys.argv[2], sys.argv[3]
W, H, SCALE = 1920, 1080, 2
os.makedirs(outdir, exist_ok=True)
for f in glob.glob(os.path.join(outdir, '*.png')):
    os.remove(f)

with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={'width': W, 'height': H}, device_scale_factor=SCALE)
    pg.goto('file://' + os.path.abspath(html))
    pg.wait_for_timeout(2500)
    n = pg.evaluate("document.querySelectorAll('.slide').length")
    print(f"{n} slides")
    for i in range(n):
        pg.evaluate(f"""() => {{
            const s = document.querySelectorAll('.slide');
            s.forEach((el, j) => el.style.display = (j === {i} ? 'flex' : 'none'));
            window.scrollTo(0, 0);
        }}""")
        pg.wait_for_timeout(450)
        pg.screenshot(path=os.path.join(outdir, f'slide-{i:02d}.png'))
        print(f"  captured {i+1}/{n}", flush=True)
    b.close()

frames = sorted(glob.glob(os.path.join(outdir, 'slide-*.png')))
imgs = []
for f in frames:
    im = Image.open(f).convert('RGB')
    if im.size != (W, H):
        im = im.resize((W, H), Image.LANCZOS)
    imgs.append(im)
imgs[0].save(pdf, save_all=True, append_images=imgs[1:], resolution=96.0, quality=94)
print(f"\nPDF: {pdf}  ({len(imgs)} pages, {os.path.getsize(pdf)/1e6:.1f} MB)")
