/**
 * ============================================================================
 * 🐾 CatAudio — Zero-Dependency Mystery Cat BGM & Interactive SFX Engine
 * Uses pure Web Audio API (AudioContext) for procedural BGM and cartoon SFX.
 * Conforms to .antigravity/rules.md (Section 6).
 * ============================================================================
 */
(function(window) {
  'use strict';

  // --- Audio Context & Master State ---
  let audioCtx = null;
  let masterGain = null;
  let bgmGain = null;
  let sfxGain = null;

  // Settings with localStorage persistence
  const STORAGE_KEYS = {
    BGM_MUTED: 'cat_bgm_muted',
    BGM_VOLUME: 'cat_bgm_volume',
    SFX_MUTED: 'cat_sfx_muted'
  };

  let bgmMuted = localStorage.getItem(STORAGE_KEYS.BGM_MUTED) === 'true';
  let sfxMuted = localStorage.getItem(STORAGE_KEYS.SFX_MUTED) === 'true';
  let bgmVolume = parseFloat(localStorage.getItem(STORAGE_KEYS.BGM_VOLUME) || '0.18');
  if (isNaN(bgmVolume) || bgmVolume < 0 || bgmVolume > 1) bgmVolume = 0.18;

  let isBgmRunning = false;
  let currentMood = 'NORMAL'; // 'NORMAL' (75 BPM) | 'VOTING' (105 BPM) | 'LAST_GUESS' (130 BPM)
  let schedulerTimer = null;
  let nextNoteTime = 0;
  let stepIndex = 0;

  // --- Ensure AudioContext is Initialized (Handles Autoplay Policy) ---
  function getContext() {
    if (!audioCtx) {
      const AudioCtxClass = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtxClass) {
        console.warn('[CatAudio] Web Audio API is not supported in this browser.');
        return null;
      }
      audioCtx = new AudioCtxClass();

      // Master Gain
      masterGain = audioCtx.createGain();
      masterGain.gain.setValueAtTime(1.0, audioCtx.currentTime);
      masterGain.connect(audioCtx.destination);

      // BGM Gain
      bgmGain = audioCtx.createGain();
      bgmGain.gain.setValueAtTime(bgmMuted ? 0.0 : bgmVolume, audioCtx.currentTime);
      bgmGain.connect(masterGain);

      // SFX Gain
      sfxGain = audioCtx.createGain();
      sfxGain.gain.setValueAtTime(sfxMuted ? 0.0 : 0.45, audioCtx.currentTime);
      sfxGain.connect(masterGain);
    }

    if (audioCtx.state === 'suspended') {
      audioCtx.resume();
    }
    return audioCtx;
  }

  // ==========================================================================
  // 1. Procedural Mystery Cat BGM Engine (Walking Bass + Pizzicato Noir)
  // ==========================================================================
  // Scale: D Minor / Blues / Dorian (D, F, G, G#, A, C)
  // Walking bass lines and mysterious sneaky melody notes
  const NOTE_FREQS = {
    D2: 73.42,  F2: 87.31,  G2: 98.00,  Ab2: 103.83, A2: 110.00, C3: 130.81,
    D3: 146.83, F3: 174.61, G3: 196.00, Ab3: 207.65, A3: 220.00, C4: 261.63,
    D4: 293.66, F4: 349.23, G4: 392.00, Ab4: 415.30, A4: 440.00, C5: 523.25
  };

  // 16-step walking bass pattern (D minor noir blues progression)
  const BASS_SEQUENCE = [
    NOTE_FREQS.D2,  NOTE_FREQS.F2,  NOTE_FREQS.G2,  NOTE_FREQS.Ab2,
    NOTE_FREQS.A2,  NOTE_FREQS.G2,  NOTE_FREQS.F2,  NOTE_FREQS.D2,
    NOTE_FREQS.G2,  NOTE_FREQS.A2,  NOTE_FREQS.C3,  NOTE_FREQS.A2,
    NOTE_FREQS.D2,  NOTE_FREQS.F2,  NOTE_FREQS.A2,  NOTE_FREQS.C3
  ];

  // 16-step pizzicato quirky melody pattern (null means rest)
  const MELODY_SEQUENCE = [
    NOTE_FREQS.D4,  null,           NOTE_FREQS.F4,  null,
    NOTE_FREQS.Ab4, NOTE_FREQS.G4,  null,           NOTE_FREQS.D4,
    null,           NOTE_FREQS.C5,  NOTE_FREQS.A4,  null,
    NOTE_FREQS.F4,  null,           NOTE_FREQS.D4,  null
  ];

  function getBpm() {
    switch (currentMood) {
      case 'LAST_GUESS': return 130;
      case 'VOTING':     return 105;
      case 'NORMAL':
      default:           return 75;
    }
  }

  function playBassNote(freq, time, duration) {
    if (!audioCtx || bgmMuted) return;

    const osc = audioCtx.createOscillator();
    const noteGain = audioCtx.createGain();
    const filter = audioCtx.createBiquadFilter();

    osc.type = 'triangle';
    osc.frequency.setValueAtTime(freq, time);

    // Warm low-pass filter
    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(320, time);
    filter.Q.setValueAtTime(1.5, time);

    // Envelope: soft pluck, steady decay
    noteGain.gain.setValueAtTime(0.001, time);
    noteGain.gain.linearRampToValueAtTime(0.55, time + 0.03);
    noteGain.gain.exponentialRampToValueAtTime(0.001, time + duration * 0.95);

    osc.connect(filter);
    filter.connect(noteGain);
    noteGain.connect(bgmGain);

    osc.start(time);
    osc.stop(time + duration);
  }

  function playPizzicatoNote(freq, time, duration) {
    if (!audioCtx || bgmMuted || !freq) return;

    const osc = audioCtx.createOscillator();
    const noteGain = audioCtx.createGain();
    const filter = audioCtx.createBiquadFilter();

    // Sine with quick percussive envelope for pizzicato cat paw pluck
    osc.type = 'sine';
    osc.frequency.setValueAtTime(freq, time);

    filter.type = 'bandpass';
    filter.frequency.setValueAtTime(freq * 1.2, time);
    filter.Q.setValueAtTime(2.0, time);

    noteGain.gain.setValueAtTime(0.001, time);
    noteGain.gain.linearRampToValueAtTime(0.35, time + 0.015);
    noteGain.gain.exponentialRampToValueAtTime(0.001, time + duration * 0.4);

    osc.connect(filter);
    filter.connect(noteGain);
    noteGain.connect(bgmGain);

    osc.start(time);
    osc.stop(time + duration * 0.45);
  }

  function scheduleNotes() {
    if (!isBgmRunning || !audioCtx) return;

    const lookahead = 0.15; // Schedule 150ms ahead
    const secondsPerBeat = 60.0 / getBpm();
    const stepDuration = secondsPerBeat * 0.5; // Eighth-notes

    while (nextNoteTime < audioCtx.currentTime + lookahead) {
      const bassFreq = BASS_SEQUENCE[stepIndex % BASS_SEQUENCE.length];
      const melodyFreq = MELODY_SEQUENCE[stepIndex % MELODY_SEQUENCE.length];

      playBassNote(bassFreq, nextNoteTime, stepDuration);
      playPizzicatoNote(melodyFreq, nextNoteTime, stepDuration);

      // In LAST_GUESS mode, tick clock on quarter beats
      if (currentMood === 'LAST_GUESS' && stepIndex % 2 === 0 && !sfxMuted) {
        CatAudio.playTick(nextNoteTime);
      }

      nextNoteTime += stepDuration;
      stepIndex++;
    }

    schedulerTimer = setTimeout(scheduleNotes, 50);
  }

  function startBgm() {
    const ctx = getContext();
    if (!ctx) return;

    if (isBgmRunning) return;
    isBgmRunning = true;
    nextNoteTime = ctx.currentTime + 0.05;
    stepIndex = 0;

    // Smooth fade-in
    if (!bgmMuted) {
      bgmGain.gain.cancelScheduledValues(ctx.currentTime);
      bgmGain.gain.setValueAtTime(0.001, ctx.currentTime);
      bgmGain.gain.linearRampToValueAtTime(bgmVolume, ctx.currentTime + 1.2);
    }

    scheduleNotes();
    updateUI();
  }

  function stopBgm(immediate = false) {
    if (!isBgmRunning) return;

    if (schedulerTimer) {
      clearTimeout(schedulerTimer);
      schedulerTimer = null;
    }

    if (immediate || !audioCtx) {
      isBgmRunning = false;
      updateUI();
      return;
    }

    // Smooth fade-out before stopping
    bgmGain.gain.cancelScheduledValues(audioCtx.currentTime);
    bgmGain.gain.setValueAtTime(bgmGain.gain.value, audioCtx.currentTime);
    bgmGain.gain.linearRampToValueAtTime(0.0001, audioCtx.currentTime + 0.5);

    setTimeout(() => {
      isBgmRunning = false;
      updateUI();
    }, 550);
  }

  // ==========================================================================
  // 2. Procedural Cartoon SFX Engine
  // ==========================================================================

  /**
   * playMeow() — Synthesizes a cute, playful cartoon cat meow sound.
   * Modulates frequencies (380Hz -> 760Hz -> 520Hz) through a vowel filter.
   */
  function playMeow() {
    if (sfxMuted) return;
    const ctx = getContext();
    if (!ctx) return;

    const t = ctx.currentTime;
    const osc1 = ctx.createOscillator();
    const osc2 = ctx.createOscillator();
    const filter = ctx.createBiquadFilter();
    const gainNode = ctx.createGain();

    osc1.type = 'triangle';
    osc2.type = 'sine';

    // Pitch envelope: slides up then down (me-o-w)
    osc1.frequency.setValueAtTime(380, t);
    osc1.frequency.exponentialRampToValueAtTime(740, t + 0.14);
    osc1.frequency.exponentialRampToValueAtTime(510, t + 0.42);

    osc2.frequency.setValueAtTime(760, t);
    osc2.frequency.exponentialRampToValueAtTime(1480, t + 0.14);
    osc2.frequency.exponentialRampToValueAtTime(1020, t + 0.42);

    // Formant/vowel filter for cute "meow" timbre
    filter.type = 'bandpass';
    filter.frequency.setValueAtTime(800, t);
    filter.frequency.linearRampToValueAtTime(1600, t + 0.15);
    filter.frequency.linearRampToValueAtTime(700, t + 0.42);
    filter.Q.setValueAtTime(3.0, t);

    // Volume envelope
    gainNode.gain.setValueAtTime(0.001, t);
    gainNode.gain.linearRampToValueAtTime(0.35, t + 0.06);
    gainNode.gain.exponentialRampToValueAtTime(0.001, t + 0.44);

    osc1.connect(filter);
    osc2.connect(filter);
    filter.connect(gainNode);
    gainNode.connect(sfxGain);

    osc1.start(t);
    osc2.start(t);
    osc1.stop(t + 0.45);
    osc2.stop(t + 0.45);
  }

  /**
   * playHiss() — Synthesizes an angry cat hiss for validation errors & voting.
   * Generated via white noise buffer + high-pass filter (1800Hz).
   */
  function playHiss() {
    if (sfxMuted) return;
    const ctx = getContext();
    if (!ctx) return;

    const duration = 0.38;
    const bufferSize = ctx.sampleRate * duration;
    const buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
    const data = buffer.getChannelData(0);

    for (let i = 0; i < bufferSize; i++) {
      data[i] = (Math.random() * 2 - 1) * 0.8;
    }

    const noise = ctx.createBufferSource();
    noise.buffer = buffer;

    const filter = ctx.createBiquadFilter();
    filter.type = 'highpass';
    filter.frequency.setValueAtTime(1800, ctx.currentTime);
    filter.Q.setValueAtTime(1.8, ctx.currentTime);

    const gainNode = ctx.createGain();
    const t = ctx.currentTime;
    gainNode.gain.setValueAtTime(0.001, t);
    gainNode.gain.linearRampToValueAtTime(0.4, t + 0.04);
    gainNode.gain.exponentialRampToValueAtTime(0.001, t + duration);

    noise.connect(filter);
    filter.connect(gainNode);
    gainNode.connect(sfxGain);

    noise.start(t);
    noise.stop(t + duration);
  }

  /**
   * playPurrFlip() — Deep vibrato purr (50Hz) + whoosh sound for 3D card flipping.
   */
  function playPurrFlip() {
    if (sfxMuted) return;
    const ctx = getContext();
    if (!ctx) return;

    const t = ctx.currentTime;

    // 1. Purr component (Low frequency amplitude modulated carrier)
    const carrier = ctx.createOscillator();
    const modulator = ctx.createOscillator();
    const modGain = ctx.createGain();
    const purrGain = ctx.createGain();

    carrier.type = 'sine';
    carrier.frequency.setValueAtTime(55, t); // Low chest purr

    modulator.type = 'sine';
    modulator.frequency.setValueAtTime(26, t); // Rapid purring rumble rate

    modGain.gain.setValueAtTime(0.5, t);
    modulator.connect(modGain);
    modGain.connect(carrier.frequency);

    purrGain.gain.setValueAtTime(0.001, t);
    purrGain.gain.linearRampToValueAtTime(0.3, t + 0.06);
    purrGain.gain.exponentialRampToValueAtTime(0.001, t + 0.5);

    carrier.connect(purrGain);
    purrGain.connect(sfxGain);

    carrier.start(t);
    modulator.start(t);
    carrier.stop(t + 0.52);
    modulator.stop(t + 0.52);

    // 2. Card Whoosh component (sweep filter)
    const bufferSize = ctx.sampleRate * 0.25;
    const buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = Math.random() * 2 - 1;
    }
    const whoosh = ctx.createBufferSource();
    whoosh.buffer = buffer;

    const whooshFilter = ctx.createBiquadFilter();
    whooshFilter.type = 'bandpass';
    whooshFilter.frequency.setValueAtTime(400, t);
    whooshFilter.frequency.exponentialRampToValueAtTime(1400, t + 0.12);
    whooshFilter.frequency.exponentialRampToValueAtTime(300, t + 0.24);
    whooshFilter.Q.setValueAtTime(2.0, t);

    const whooshGain = ctx.createGain();
    whooshGain.gain.setValueAtTime(0.001, t);
    whooshGain.gain.linearRampToValueAtTime(0.25, t + 0.08);
    whooshGain.gain.exponentialRampToValueAtTime(0.001, t + 0.25);

    whoosh.connect(whooshFilter);
    whooshFilter.connect(whooshGain);
    whooshGain.connect(sfxGain);

    whoosh.start(t);
    whoosh.stop(t + 0.25);
  }

  /**
   * playPenaltySpin() — Cartoon spinning wheel effect:
   * Accelerating clicks followed by decelerating clicks, ending in a bell chime.
   */
  function playPenaltySpin() {
    if (sfxMuted) return;
    const ctx = getContext();
    if (!ctx) return;

    const totalTicks = 18;
    let delay = 0;

    for (let i = 0; i < totalTicks; i++) {
      // Curve: starts fast (0.04s) then slows down (up to 0.18s)
      const progress = i / totalTicks;
      const interval = 0.04 + Math.pow(progress, 2.5) * 0.18;
      delay += interval;

      const clickTime = ctx.currentTime + delay;
      setTimeout(() => {
        playSingleWoodClick(clickTime, 800 + (i % 3) * 120);
      }, delay * 1000);
    }

    // Final celebration ding
    setTimeout(() => {
      playChime();
    }, (delay + 0.15) * 1000);
  }

  function playSingleWoodClick(time, freq = 900) {
    if (!audioCtx || sfxMuted) return;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();

    osc.type = 'triangle';
    osc.frequency.setValueAtTime(freq, time);

    gain.gain.setValueAtTime(0.001, time);
    gain.gain.linearRampToValueAtTime(0.25, time + 0.005);
    gain.gain.exponentialRampToValueAtTime(0.001, time + 0.035);

    osc.connect(gain);
    gain.connect(sfxGain);

    osc.start(time);
    osc.stop(time + 0.04);
  }

  function playChime() {
    if (!audioCtx || sfxMuted) return;
    const t = audioCtx.currentTime;
    [1046.5, 1318.5, 1567.98].forEach((freq, idx) => { // C6, E6, G6 major chord
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, t + idx * 0.04);

      gain.gain.setValueAtTime(0.001, t + idx * 0.04);
      gain.gain.linearRampToValueAtTime(0.2, t + idx * 0.04 + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.001, t + idx * 0.04 + 0.6);

      osc.connect(gain);
      gain.connect(sfxGain);

      osc.start(t + idx * 0.04);
      osc.stop(t + idx * 0.04 + 0.65);
    });
  }

  /**
   * playTick() — Crisp wood block pulse for countdown timers.
   */
  function playTick(targetTime) {
    if (sfxMuted) return;
    const ctx = getContext();
    if (!ctx) return;

    const t = targetTime || ctx.currentTime;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(1100, t);

    gain.gain.setValueAtTime(0.001, t);
    gain.gain.linearRampToValueAtTime(0.2, t + 0.003);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.028);

    osc.connect(gain);
    gain.connect(sfxGain);

    osc.start(t);
    osc.stop(t + 0.03);
  }

  // ==========================================================================
  // 3. UI Synchronization & Controls
  // ==========================================================================

  function updateUI() {
    // BGM Button
    const bgmBtn = document.getElementById('cat-bgm-toggle');
    if (bgmBtn) {
      if (isBgmRunning && !bgmMuted) {
        bgmBtn.classList.add('active');
        bgmBtn.setAttribute('aria-pressed', 'true');
        bgmBtn.title = 'Tắt Nhạc Nền 🎵';
      } else {
        bgmBtn.classList.remove('active');
        bgmBtn.setAttribute('aria-pressed', 'false');
        bgmBtn.title = 'Bật Nhạc Nền 🎵';
      }
    }

    // SFX Button
    const sfxBtn = document.getElementById('cat-sfx-toggle');
    if (sfxBtn) {
      if (!sfxMuted) {
        sfxBtn.classList.add('active');
        sfxBtn.setAttribute('aria-pressed', 'true');
        sfxBtn.title = 'Tắt Hiệu Ứng 🐾';
      } else {
        sfxBtn.classList.remove('active');
        sfxBtn.setAttribute('aria-pressed', 'false');
        sfxBtn.title = 'Bật Hiệu Ứng 🐾';
      }
    }

    // Volume Slider
    const volSlider = document.getElementById('cat-volume-slider');
    if (volSlider) {
      volSlider.value = Math.round(bgmVolume * 100);
    }
  }

  function toggleBgm() {
    getContext();
    if (isBgmRunning && !bgmMuted) {
      // Mute BGM
      bgmMuted = true;
      localStorage.setItem(STORAGE_KEYS.BGM_MUTED, 'true');
      stopBgm();
    } else {
      // Unmute & start BGM
      bgmMuted = false;
      localStorage.setItem(STORAGE_KEYS.BGM_MUTED, 'false');
      startBgm();
      playMeow();
    }
    updateUI();
  }

  function setBgmVolume(val) {
    const num = Math.max(0, Math.min(1, parseFloat(val)));
    bgmVolume = num;
    localStorage.setItem(STORAGE_KEYS.BGM_VOLUME, num.toString());

    if (bgmGain && audioCtx) {
      bgmGain.gain.setValueAtTime(bgmMuted ? 0 : bgmVolume, audioCtx.currentTime);
    }
    updateUI();
  }

  function toggleSfx() {
    sfxMuted = !sfxMuted;
    localStorage.setItem(STORAGE_KEYS.SFX_MUTED, sfxMuted ? 'true' : 'false');

    if (sfxGain && audioCtx) {
      sfxGain.gain.setValueAtTime(sfxMuted ? 0 : 0.45, audioCtx.currentTime);
    }

    if (!sfxMuted) {
      playMeow();
    }
    updateUI();
  }

  function setMood(mood) {
    const validMoods = ['NORMAL', 'VOTING', 'LAST_GUESS'];
    if (!validMoods.includes(mood)) return;
    currentMood = mood;
    console.log(`[CatAudio] Mood switched to: ${mood} (~${getBpm()} BPM)`);
  }

  /**
   * Smart Room Audio Setup:
   * Host machine: Autoplays BGM after first interaction.
   * Member machines: Keeps BGM muted by default to prevent audio clutter on shared tables.
   */
  function setupSmartAudio(options = {}) {
    const isHost = Boolean(options.isHost);
    console.log(`[CatAudio] Initializing Smart Room Audio (isHost = ${isHost})`);

    const unlockAudio = () => {
      getContext();
      if (isHost && !bgmMuted && !isBgmRunning) {
        startBgm();
      }
      updateUI();
      // Remove listeners after initial unlock
      ['click', 'touchstart', 'keydown'].forEach(evt => {
        window.removeEventListener(evt, unlockAudio);
      });
    };

    // If host, auto-start BGM on first interaction
    if (isHost) {
      ['click', 'touchstart', 'keydown'].forEach(evt => {
        window.addEventListener(evt, unlockAudio, { once: true, passive: true });
      });
    }

    // Connect header buttons automatically if in DOM
    document.addEventListener('DOMContentLoaded', () => {
      bindHeaderControls();
      updateUI();
    });
  }

  function bindHeaderControls() {
    const bgmBtn = document.getElementById('cat-bgm-toggle');
    if (bgmBtn && !bgmBtn.dataset.bound) {
      bgmBtn.dataset.bound = 'true';
      bgmBtn.addEventListener('click', (e) => {
        e.preventDefault();
        toggleBgm();
      });
    }

    const sfxBtn = document.getElementById('cat-sfx-toggle');
    if (sfxBtn && !sfxBtn.dataset.bound) {
      sfxBtn.dataset.bound = 'true';
      sfxBtn.addEventListener('click', (e) => {
        e.preventDefault();
        toggleSfx();
      });
    }

    const volSlider = document.getElementById('cat-volume-slider');
    if (volSlider && !volSlider.dataset.bound) {
      volSlider.dataset.bound = 'true';
      volSlider.addEventListener('input', (e) => {
        setBgmVolume(parseFloat(e.target.value) / 100);
      });
    }

    const volToggle = document.getElementById('cat-volume-toggle');
    const popover = document.getElementById('cat-volume-popover');
    if (volToggle && popover && !volToggle.dataset.bound) {
      volToggle.dataset.bound = 'true';
      volToggle.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        popover.classList.toggle('d-none');
      });
      document.addEventListener('click', (e) => {
        if (!popover.contains(e.target) && e.target !== volToggle) {
          popover.classList.add('d-none');
        }
      });
    }
  }

  // Auto-bind when script loads if DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bindHeaderControls);
  } else {
    bindHeaderControls();
  }

  // --- Export CatAudio to Global Scope ---
  window.CatAudio = {
    init: getContext,
    startBgm,
    stopBgm,
    toggleBgm,
    setBgmVolume,
    toggleSfx,
    setMood,
    playMeow,
    playHiss,
    playPurrFlip,
    playPenaltySpin,
    playTick,
    setupSmartAudio,
    get isBgmRunning() { return isBgmRunning; },
    get isBgmMuted() { return bgmMuted; },
    get isSfxMuted() { return sfxMuted; },
    get currentMood() { return currentMood; }
  };

})(window);
