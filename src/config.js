// Config loading. Everything tunable lives in JSON on disk — no compiled constants.
// This mirrors the ConfigStore contract the Android build uses, so tuning done here
// transfers as literal files. docs/adr/ADR-005-hot-reload-config.md

const EXERCISES = [
  'squat', 'chair_squat', 'lunge', 'calf_raise',
  'glute_bridge', 'push_up', 'knee_push_up', 'sit_up',
];

async function json(path) {
  const r = await fetch(path, { cache: 'no-store' });
  if (!r.ok) throw new Error(`${path}: ${r.status}`);
  return r.json();
}

export async function loadConfig() {
  const [pose, combat, ...ex] = await Promise.all([
    json('config/pose.json'),
    json('config/combat.json'),
    ...EXERCISES.map((id) => json(`config/exercises/${id}.json`)),
  ]);
  const exercises = Object.fromEntries(ex.map((e) => [e.id, e]));
  return { pose, combat, exercises, version: Date.now() };
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
