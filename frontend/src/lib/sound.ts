import { useCallback, useEffect, useState } from 'react';

/*
 * Sounds for the child area. Effects are synthesised with the Web Audio API (no audio files to ship or
 * license); words are spoken with the browser's speech synthesis in the target language.
 */

const STORAGE_KEY = 'kv.sound';

type AudioContextClass = typeof AudioContext;

let context: AudioContext | null = null;

function audio(): AudioContext | null {
  if (context) return context;
  const Ctor: AudioContextClass | undefined =
    globalThis.AudioContext ?? (globalThis as { webkitAudioContext?: AudioContextClass }).webkitAudioContext;
  if (!Ctor) return null;
  try {
    context = new Ctor();
  } catch {
    return null;
  }
  return context;
}

/**
 * Mobile browsers only start audio from a user gesture: call this in a tap or submit handler, before any
 * await, so the effects that follow an answer can play.
 */
export function unlockAudio() {
  const ctx = audio();
  if (ctx && ctx.state === 'suspended') void ctx.resume();
}

interface Note {
  /** start in seconds from now */
  at: number;
  /** length in seconds */
  length: number;
  /** frequency at the start and (optionally) at the end, in Hz */
  from: number;
  to?: number;
  /** vibrato depth in Hz */
  wobble?: number;
}

function play(notes: Note[], type: OscillatorType, volume: number) {
  const ctx = audio();
  if (!ctx) return;
  const now = ctx.currentTime + 0.02;
  const out = ctx.createGain();
  out.gain.value = volume;
  const filter = ctx.createBiquadFilter();
  filter.type = 'lowpass';
  filter.frequency.value = 1800;
  filter.connect(out).connect(ctx.destination);

  for (const n of notes) {
    const osc = ctx.createOscillator();
    osc.type = type;
    const start = now + n.at;
    const end = start + n.length;
    osc.frequency.setValueAtTime(n.from, start);
    if (n.to) osc.frequency.linearRampToValueAtTime(n.to, end);
    if (n.wobble) {
      const lfo = ctx.createOscillator();
      const depth = ctx.createGain();
      lfo.frequency.value = 6;
      depth.gain.value = n.wobble;
      lfo.connect(depth).connect(osc.frequency);
      lfo.start(start);
      lfo.stop(end);
    }
    const env = ctx.createGain();
    env.gain.setValueAtTime(0.0001, start);
    env.gain.exponentialRampToValueAtTime(1, start + 0.03);
    env.gain.setValueAtTime(1, end - 0.08);
    env.gain.exponentialRampToValueAtTime(0.0001, end);
    osc.connect(env).connect(filter);
    osc.start(start);
    osc.stop(end + 0.02);
  }
}

/** A short rising "ta-da". */
export function playCorrect() {
  play(
    [
      { at: 0, length: 0.12, from: 784 },
      { at: 0.11, length: 0.12, from: 988 },
      { at: 0.22, length: 0.28, from: 1319 },
    ],
    'triangle',
    0.35,
  );
}

/** The "sad trombone": three falling notes and a long wobbling slide down. */
export function playWrong() {
  play(
    [
      { at: 0, length: 0.32, from: 294 },
      { at: 0.34, length: 0.32, from: 277 },
      { at: 0.68, length: 0.32, from: 262 },
      { at: 1.02, length: 0.9, from: 247, to: 220, wobble: 5 },
    ],
    'sawtooth',
    0.18,
  );
}

/** How long {@link playWrong} lasts, so speech can wait for it. */
export const WRONG_SOUND_MS = 1950;

// --- speech -------------------------------------------------------------------------------------------

function synth(): SpeechSynthesis | null {
  return typeof globalThis.speechSynthesis === 'undefined' ? null : globalThis.speechSynthesis;
}

function voiceFor(lang: string): SpeechSynthesisVoice | null {
  const voices = synth()?.getVoices() ?? [];
  const prefix = lang.toLowerCase();
  const matching = voices.filter((v) => v.lang.toLowerCase().replace('_', '-').split('-')[0] === prefix);
  return matching.find((v) => v.localService) ?? matching[0] ?? null;
}

/** Whether the device can speak this language; voices load asynchronously, so this updates. */
export function useCanSpeak(lang: string): boolean {
  const [can, setCan] = useState(() => voiceFor(lang) !== null);
  useEffect(() => {
    const s = synth();
    if (!s) return;
    const update = () => setCan(voiceFor(lang) !== null);
    update();
    s.addEventListener?.('voiceschanged', update);
    return () => s.removeEventListener?.('voiceschanged', update);
  }, [lang]);
  return can;
}

/** Speaks a word in the given language; does nothing if the device has no voice for it. */
export function speak(text: string, lang: string) {
  const s = synth();
  const voice = voiceFor(lang);
  if (!s || !voice || typeof SpeechSynthesisUtterance === 'undefined') return;
  s.cancel();
  const u = new SpeechSynthesisUtterance(text);
  u.voice = voice;
  u.lang = voice.lang;
  u.rate = 0.85;
  s.speak(u);
}

export function stopSpeaking() {
  synth()?.cancel();
}

// --- the child's sound switch -------------------------------------------------------------------------

function readSetting(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) !== 'off';
  } catch {
    return true;
  }
}

/** Sound on or off, chosen by the child and remembered on this device. */
export function useSoundSetting(): [boolean, () => void] {
  const [on, setOn] = useState(readSetting);
  const toggle = useCallback(() => {
    setOn((was) => {
      const next = !was;
      try {
        localStorage.setItem(STORAGE_KEY, next ? 'on' : 'off');
      } catch {
        // private mode: the choice lasts until the page is closed
      }
      if (!next) stopSpeaking();
      return next;
    });
  }, []);
  return [on, toggle];
}
