import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import en from './locales/en.json';
import et from './locales/et.json';
import ru from './locales/ru.json';

type Messages = { [key: string]: string | Messages };

/** Languages of the UI, in the order shown by the language switcher. */
export const LANGUAGES = ['ru', 'et', 'en'] as const;
/** Every UI language has a complete catalog; English is the fallback for missing keys. */
export type UiLanguage = (typeof LANGUAGES)[number];

const CATALOGS: Record<UiLanguage, Messages> = { en, ru, et };
const FALLBACK: UiLanguage = 'en';
const DEFAULT_LANG: UiLanguage = 'ru';
const STORAGE_KEY = 'kidsvocab.lang';

export type Params = Record<string, string | number>;

export interface I18n {
  lang: UiLanguage;
  setLang: (lang: UiLanguage) => void;
  /** Translates a dotted key; `{name}` placeholders are filled from params; `count` selects a plural form. */
  t: (key: string, params?: Params) => string;
  /** True when the key exists (in the current language or the fallback). */
  has: (key: string) => boolean;
  formatDate: (isoDate: string, options?: Intl.DateTimeFormatOptions) => string;
  formatNumber: (value: number | null | undefined) => string;
}

function lookup(catalog: Messages, key: string): string | undefined {
  let node: string | Messages | undefined = catalog;
  for (const part of key.split('.')) {
    if (node === undefined || typeof node === 'string') {
      return undefined;
    }
    node = node[part];
  }
  return typeof node === 'string' ? node : undefined;
}

/**
 * @param variant a grammatical variant tried first, e.g. `girl` picks `key_girl` (or `key_girl_one`) when the
 *                catalog has it; Russian needs it for verbs in the past tense ("ty nachal" / "ty nachala")
 */
export function translate(lang: UiLanguage, key: string, params?: Params, variant?: string): string {
  const count = params?.count;
  const keys = variant ? [`${key}_${variant}`, key] : [key];
  let template: string | undefined;
  search: for (const l of [lang, FALLBACK]) {
    for (const k of keys) {
      if (typeof count === 'number') {
        const form = new Intl.PluralRules(l).select(count);
        template = lookup(CATALOGS[l], `${k}_${form}`) ?? lookup(CATALOGS[l], `${k}_other`);
      }
      template ??= lookup(CATALOGS[l], k);
      if (template !== undefined) {
        break search;
      }
    }
  }
  if (template === undefined) {
    return key;
  }
  return template.replace(/\{(\w+)\}/g, (match, name: string) =>
    params && name in params ? String(params[name]) : match,
  );
}

/** Parses `YYYY-MM-DD` as a local calendar date (no time zone shift). */
export function parseIsoDate(isoDate: string): Date {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function readStoredLang(): UiLanguage | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return (LANGUAGES as readonly string[]).includes(stored ?? '') ? (stored as UiLanguage) : null;
  } catch {
    return null;
  }
}

const I18nContext = createContext<I18n | null>(null);

/**
 * @param fixedLang a language that cannot be changed and is not remembered (the child area uses the
 *                  child's native language)
 * @param gender    the person the texts talk to (the child area), for grammatical gender
 */
export function I18nProvider({
  children,
  initialLang,
  fixedLang,
  gender,
}: {
  children: ReactNode;
  initialLang?: UiLanguage;
  fixedLang?: UiLanguage;
  gender?: 'BOY' | 'GIRL' | null;
}) {
  const [chosen, setLangState] = useState<UiLanguage>(() => initialLang ?? readStoredLang() ?? DEFAULT_LANG);
  const lang = fixedLang ?? chosen;

  const setLang = useCallback((next: UiLanguage) => {
    setLangState(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // storage may be unavailable (private mode); the choice then lasts for this page only
    }
  }, []);

  useEffect(() => {
    const previous = document.documentElement.lang;
    document.documentElement.lang = lang;
    return () => {
      document.documentElement.lang = previous;
    };
  }, [lang]);

  const value = useMemo<I18n>(() => {
    const locale = lang === 'en' ? 'en-GB' : lang;
    const numbers = new Intl.NumberFormat(locale, { maximumFractionDigits: 2 });
    return {
      lang,
      setLang,
      t: (key, params) => translate(lang, key, params, gender?.toLowerCase()),
      has: (key) => lookup(CATALOGS[lang], key) !== undefined || lookup(CATALOGS[FALLBACK], key) !== undefined,
      formatDate: (isoDate, options = { day: 'numeric', month: 'short' }) =>
        new Intl.DateTimeFormat(locale, options).format(parseIsoDate(isoDate)),
      formatNumber: (value) => (value === null || value === undefined ? '—' : numbers.format(value)),
    };
  }, [lang, setLang, gender]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18n {
  const ctx = useContext(I18nContext);
  if (!ctx) {
    throw new Error('useI18n must be used inside I18nProvider');
  }
  return ctx;
}
