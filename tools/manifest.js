// Regenerates config/exercises/index.json. A browser cannot list a directory, so the exercise
// library needs a manifest — and a hand-maintained one would drift the moment someone adds a
// config file, which is exactly the kind of silent breakage that surfaces at hour 20.
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'config', 'exercises');
const ORDER = ['REP_CYCLE', 'ISOMETRIC_HOLD', 'CADENCE', 'BALLISTIC', 'POSE_MATCH'];

const recs = readdirSync(DIR)
  .filter((f) => f.endsWith('.json') && f !== 'index.json')
  .map((f) => JSON.parse(readFileSync(join(DIR, f), 'utf8')))
  .map((r) => ({ id: r.id, name: r.name, family: r.family,
                 difficulty: r.difficulty ?? 2, games: r.games ?? [], tags: r.tags ?? [] }))
  .sort((a, b) => ORDER.indexOf(a.family) - ORDER.indexOf(b.family)
                || a.difficulty - b.difficulty
                || a.name.localeCompare(b.name));

writeFileSync(join(DIR, 'index.json'),
  JSON.stringify({ generated: true, count: recs.length, exercises: recs }, null, 2) + '\n');
console.log(`manifest: ${recs.length} exercises across ${new Set(recs.map(r => r.family)).size} families`);
