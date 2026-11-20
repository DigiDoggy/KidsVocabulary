import { parseIsoDate, translate } from './I18nProvider';
import en from './locales/en.json';
import et from './locales/et.json';
import ru from './locales/ru.json';

type Tree = { [key: string]: string | Tree };

function keys(tree: Tree, prefix = ''): string[] {
  return Object.entries(tree).flatMap(([k, v]) =>
    typeof v === 'string' ? [prefix + k.replace(/_(zero|one|two|few|many|other)$/, '')] : keys(v, `${prefix}${k}.`),
  );
}

describe('translate', () => {
  it('fills placeholders', () => {
    expect(translate('en', 'week.title', { from: '1 Dec', to: '7 Dec' })).toBe('Set 1 Dec – 7 Dec');
  });

  it('selects plural forms by count', () => {
    expect(translate('en', 'week.wordsCount', { count: 1 })).toBe('1 word');
    expect(translate('en', 'week.wordsCount', { count: 5 })).toBe('5 words');
    // Russian has one/few/many forms; the forms differ, so the results must differ too
    const forms = [1, 2, 5].map((n) => translate('ru', 'week.wordsCount', { count: n }));
    expect(new Set(forms).size).toBe(3);
  });

  it('falls back to English and then to the key', () => {
    expect(translate('ru', 'no.such.key')).toBe('no.such.key');
  });

  it('has the same keys in every catalog', () => {
    expect(new Set(keys(ru))).toEqual(new Set(keys(en)));
    expect(new Set(keys(et))).toEqual(new Set(keys(en)));
  });
});

describe('parseIsoDate', () => {
  it('keeps the calendar day regardless of the time zone', () => {
    const d = parseIsoDate('2026-11-23');
    expect([d.getFullYear(), d.getMonth(), d.getDate()]).toEqual([2026, 10, 23]);
  });
});
