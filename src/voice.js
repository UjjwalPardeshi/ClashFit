// Offline voice commands.
//
// Not an accessibility extra: the player is two metres from a propped phone and physically
// cannot reach it mid-set. Voice is the only mid-set input that works at all.
// docs/21-SENSOR-PLAYBOOK.md §5

const COMMANDS = {
  stop:  ['stop', 'stop it', 'end set', 'finish', 'done'],
  next:  ['next', 'next set', 'continue', 'go again', 'again'],
  rest:  ['rest', 'break', 'pause'],
  start: ['start', 'go', 'begin', 'fight'],
};

export class Voice {
  constructor({ onCommand } = {}) {
    const SR = typeof window !== 'undefined'
      ? (window.SpeechRecognition ?? window.webkitSpeechRecognition) : null;
    this.available = !!SR;
    this.onCommand = onCommand ?? (() => {});
    this.listening = false;
    this.lastFiredMs = 0;
    if (!SR) return;

    this.rec = new SR();
    this.rec.continuous = true;
    this.rec.interimResults = true;
    this.rec.lang = 'en-IN';
    // Prefer on-device recognition where the browser exposes the choice — it is faster, it works
    // offline, and it keeps the no-upload claim true for audio as well as video.
    try { this.rec.processLocally = true; } catch { /* not supported everywhere */ }

    this.rec.onresult = (e) => {
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const text = (e.results[i][0]?.transcript ?? '').trim().toLowerCase();
        if (text) this.#match(text);
      }
    };
    // Continuous recognition stops itself on silence in some browsers; restart while we want it.
    this.rec.onend = () => { if (this.listening) { try { this.rec.start(); } catch { /* racing */ } } };
    this.rec.onerror = (e) => { if (e.error === 'not-allowed') this.stop(); };
  }

  #match(text) {
    const now = Date.now();
    if (now - this.lastFiredMs < 900) return;          // debounce interim results
    for (const [cmd, words] of Object.entries(COMMANDS)) {
      if (words.some((w) => text.endsWith(w) || text === w || text.includes(` ${w}`))) {
        this.lastFiredMs = now;
        this.onCommand(cmd, text);
        return;
      }
    }
  }

  start() {
    if (!this.available || this.listening) return false;
    this.listening = true;
    try { this.rec.start(); return true; } catch { this.listening = false; return false; }
  }

  stop() {
    this.listening = false;
    try { this.rec?.stop(); } catch { /* already stopped */ }
  }

  toggle() { return this.listening ? (this.stop(), false) : this.start(); }
}

export { COMMANDS };
