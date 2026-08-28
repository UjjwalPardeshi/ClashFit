// Zero-dependency static server. getUserMedia needs a secure context, and http://localhost
// counts as one — which is why this exists instead of opening index.html from the filesystem.
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('.', import.meta.url));
const PORT = Number(process.env.PORT ?? 8080);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.jsonl': 'application/x-ndjson',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.webp': 'image/webp',
};

createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    let p = decodeURIComponent(url.pathname);
    if (p === '/') p = '/index.html';
    let full = join(ROOT, normalize(p).replace(/^(\.\.[/\\])+/, ''));
    if (!full.startsWith(ROOT)) { res.writeHead(403).end('forbidden'); return; }

    // Match Vercel's cleanUrls: /app serves app.html, so local and deployed agree.
    if (!extname(full)) {
      try { await stat(`${full}.html`); full += '.html'; } catch { /* fall through to 404 */ }
    }

    const s = await stat(full);
    if (s.isDirectory()) { res.writeHead(404).end('not found'); return; }

    const body = await readFile(full);
    res.writeHead(200, {
      'Content-Type': MIME[extname(full)] ?? 'application/octet-stream',
      'Cache-Control': 'no-store',
    });
    res.end(body);
  } catch {
    res.writeHead(404, { 'Content-Type': 'text/plain' }).end('not found');
  }
}).listen(PORT, () => {
  console.log(`\n  Site       ->  http://localhost:${PORT}`);
  console.log(`  Prototype  ->  http://localhost:${PORT}/app\n`);
  console.log('  Stand side-on, 2–2.5m back, whole body in frame.');
  console.log('  Hit Debug to watch the angle, the FSM state and the sub-scores live.\n');
});
