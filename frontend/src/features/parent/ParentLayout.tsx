import { NavLink, Outlet, useNavigate, useOutletContext } from 'react-router-dom';
import { fetchMe, logout } from '../../api/client';
import type { Me } from '../../api/types';
import { LanguageSwitcher } from '../../components/LanguageSwitcher';
import { ErrorMessage, Spinner } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { useLoad } from '../../lib/useLoad';

export interface ParentContext {
  account: Me;
}

export function useParentContext(): ParentContext {
  return useOutletContext<ParentContext>();
}

export function ParentLayout() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const me = useLoad(fetchMe, []);

  async function signOut() {
    try {
      await logout();
    } finally {
      navigate('/login', { replace: true });
    }
  }

  return (
    <div className="shell">
      <header className="topbar">
        <NavLink to="/" className="topbar__brand">
          {t('app.name')}
        </NavLink>
        <nav className="topbar__nav">
          <NavLink to="/" end>
            {t('nav.children')}
          </NavLink>
          <NavLink to="/settings">{t('nav.settings')}</NavLink>
        </nav>
        <div className="topbar__end">
          <LanguageSwitcher />
          <button type="button" className="btn btn--ghost" onClick={signOut}>
            {t('nav.logout')}
          </button>
        </div>
      </header>
      <main className="content">
        {me.data ? (
          <Outlet context={{ account: me.data } satisfies ParentContext} />
        ) : me.error ? (
          <ErrorMessage error={me.error} />
        ) : (
          <Spinner />
        )}
      </main>
    </div>
  );
}
