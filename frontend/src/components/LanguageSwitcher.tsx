import { LANGUAGES, useI18n, type UiLanguage } from '../i18n/I18nProvider';

export function LanguageSwitcher() {
  const { lang, setLang, t } = useI18n();
  return (
    <select
      className="lang-switch"
      aria-label={t('common.language')}
      value={lang}
      onChange={(e) => setLang(e.target.value as UiLanguage)}
    >
      {LANGUAGES.map((l) => (
        <option key={l} value={l}>
          {t(`languages.ui.${l}`)}
        </option>
      ))}
    </select>
  );
}
