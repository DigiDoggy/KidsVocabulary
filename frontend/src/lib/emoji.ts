const segmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' });
const PICTURE = /\p{Emoji_Presentation}|\p{Emoji}️|⃣/u;

/**
 * Mirrors the server rule: exactly one emoji (one grapheme, so ZWJ sequences, flags and keycaps count as
 * one), drawn as a picture. The server has the final word; this only gives early feedback.
 */
export function isSingleEmoji(value: string): boolean {
  const s = value.trim();
  if (s === '' || s.length > 16) return false;
  const graphemes = Array.from(segmenter.segment(s));
  return graphemes.length === 1 && PICTURE.test(s);
}
