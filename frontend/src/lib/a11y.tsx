import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { useI18n } from '../i18n/I18nProvider';

/** Sets the browser tab title, so screen reader users hear where they are after navigation. */
export function usePageTitle(title: string | undefined) {
  const { t } = useI18n();
  const appName = t('app.name');
  useEffect(() => {
    if (title) document.title = `${title} · ${appName}`;
  }, [title, appName]);
}

/** After a client-side navigation, moves focus to the main region (as a full page load would). */
export function FocusOnNavigate({ targetId }: { targetId: string }) {
  const { pathname } = useLocation();
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    document.getElementById(targetId)?.focus({ preventScroll: false });
  }, [pathname, targetId]);
  return null;
}
