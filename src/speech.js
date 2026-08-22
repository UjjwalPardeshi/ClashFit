// Text to speech for the coach and the boss.
//
// The coach line is ALWAYS spoken. During a push-up the player is face-down and will not read
// anything; during a squat they are two metres from the screen. Speech is the primary delivery
// channel for coaching, not an accessibility extra.
// docs/06-AI-COACH-SPEC.md §7

export class Speech {
  constructor(cfg = {}) {
    this.cfg = { locale: 'en-IN', fallbackLocale: 'en-US', coachPitch: 1.0,
                 bossPitch: 0.75, bossRate: 0.9, ...cfg };
    this.enabled = typeof speechSynthesis !== 'undefined';
    this.voice = null;
    if (this.enabled) {
      const pick = () => { this.voice = this.#pickVoice(); };
      pick();
      speechSynthesis.addEventListener?.('voiceschanged', pick);
    }
  }

  #pickVoice() {
    const vs = speechSynthesis.getVoices?.() ?? [];
    if (!vs.length) return null;
    return vs.find((v) => v.lang === this.cfg.locale)
        ?? vs.find((v) => v.lang?.startsWith('en-IN'))
        ?? vs.find((v) => v.lang === this.cfg.fallbackLocale)
        ?? vs.find((v) => v.lang?.startsWith('en'))
        ?? vs[0];
  }

  /** A coach line arriving mid-set is worse than no coach line, so a new set flushes the queue. */
  flush() { if (this.enabled) speechSynthesis.cancel(); }

  say(text, { persona = 'coach', onEnd } = {}) {
    if (!this.enabled || !text) { onEnd?.(); return; }
    const u = new SpeechSynthesisUtterance(text);
    if (this.voice) u.voice = this.voice;
    u.lang = this.voice?.lang ?? this.cfg.locale;
    u.pitch = persona === 'boss' ? this.cfg.bossPitch : this.cfg.coachPitch;
    u.rate = persona === 'boss' ? this.cfg.bossRate : 1.0;
    u.onend = () => onEnd?.();
    u.onerror = () => onEnd?.();
    speechSynthesis.speak(u);
  }

  /** Coach first, then the boss answers — the two personas in sequence, which is the moment
   *  the rest screen exists for. */
  sayPair(coachLine, bossLine, onDone) {
    this.flush();
    this.say(coachLine, { persona: 'coach', onEnd: () => this.say(bossLine, { persona: 'boss', onEnd: onDone }) });
  }
}
