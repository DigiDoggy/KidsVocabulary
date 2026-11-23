import css from '../styles.css?raw';

// Text/background pairs used by the stylesheet, checked against WCAG 2.1 AA (4.5:1 for normal text).
const PAIRS: [string, string][] = [
  ['fg', 'bg'],
  ['muted', 'bg'],
  ['muted', 'surface'],
  ['muted', 'neutral-bg'],
  ['primary', 'surface'],
  ['primary', 'info-bg'],
  ['primary-fg', 'primary'],
  ['ok', 'ok-bg'],
  ['ok', 'surface'],
  ['warn', 'warn-bg'],
  ['error', 'error-bg'],
  ['error', 'surface'],
  ['info', 'info-bg'],
  ['accent-fg', 'accent'],
  ['accent-text', 'warn-bg'],
];

// Child-area pairs (tokens declared on .kid).
const KID_PAIRS: [string, string][] = [
  ['kid-ink', 'kid-bg'],
  ['kid-ink', 'kid-surface'],
  ['kid-muted', 'kid-bg'],
  ['kid-muted', 'kid-surface'],
  ['kid-go-fg', 'kid-go'],
  ['kid-ink', 'kid-star'],
  ['kid-star-text', 'kid-bg'],
  ['kid-star-text', 'kid-surface'],
  ['kid-ok', 'kid-ok-bg'],
  ['kid-almost', 'kid-almost-bg'],
  ['kid-almost', 'kid-surface'],
];

function tokens(selector = ':root'): Record<string, string> {
  const start = css.indexOf(`${selector} {`);
  const block = css.slice(start, css.indexOf('}', start));
  return Object.fromEntries([...block.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{6})/g)].map((m) => [m[1], m[2]]));
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

describe('colour contrast', () => {
  const t = tokens();
  it.each(PAIRS)('%s on %s is at least 4.5:1', (fg, bg) => {
    expect(t[fg], fg).toBeDefined();
    expect(t[bg], bg).toBeDefined();
    expect(contrast(t[fg], t[bg])).toBeGreaterThanOrEqual(4.5);
  });

  const kid = tokens('.kid');
  it.each(KID_PAIRS)('child area: %s on %s is at least 4.5:1', (fg, bg) => {
    expect(kid[fg], fg).toBeDefined();
    expect(kid[bg], bg).toBeDefined();
    expect(contrast(kid[fg], kid[bg])).toBeGreaterThanOrEqual(4.5);
  });
});
