// Config loading. Everything tunable lives in JSON on disk — no compiled constants.
// This mirrors the ConfigStore contract the Android build uses, so tuning done here
// transfers as literal files. docs/adr/ADR-005-hot-reload-config.md

// The exercise list comes from a generated manifest, because a browser cannot list a directory.
// Regenerate with: npm run manifest

async function json(path) {
  const r = await fetch(path, { cache: 'no-store' });
  if (!r.ok) throw new Error(`${path}: ${r.status}`);
  return r.json();
}

const CLINIC = ['sit_to_stand_30s'];

export async function loadConfig() {
  const [pose, combat, manifest] = await Promise.all([
    json('config/pose.json'),
    json('config/combat.json'),
    json('config/exercises/index.json'),
  ]);
  const ids = manifest.exercises.map((e) => e.id);
  const [ex, cl] = await Promise.all([
    Promise.all(ids.map((id) => json(`config/exercises/${id}.json`))),
    Promise.all(CLINIC.map((id) => json(`config/clinic/${id}.json`))),
  ]);
  return {
    pose, combat, manifest,
    exercises: Object.fromEntries(ex.map((e) => [e.id, e])),
    clinic: Object.fromEntries(cl.map((c) => [c.id, c])),
    version: Date.now(),
  };
}

/** Re-read from disk. On the phone this is the onResume hook; here it is a button.
 *  Malformed JSON must never take the app down — keep the last good config. */
export async function reloadInto(store) {
  try {
    const next = await loadConfig();
    Object.assign(store, next);
    return { ok: true };
  } catch (e) {
    return { ok: false, error: String(e.message ?? e) };
  }
}
