import { useState, type FormEvent } from 'react';
import { Outlet, useOutletContext } from 'react-router-dom';
import { myHome, switchToParent } from '../../api/client';
import { ApiError } from '../../api/http';
import type { Home } from '../../api/types';
import { ErrorMessage, Modal, Spinner, useAction } from '../../components/ui';
import { I18nProvider, useI18n, type UiLanguage } from '../../i18n/I18nProvider';
import { useLoad } from '../../lib/useLoad';

export interface ChildContext {
  home: Home;
  reloadHome: () => void;
}

export function useChildContext(): ChildContext {
  return useOutletContext<ChildContext>();
}

const CHILD_LANGUAGES: UiLanguage[] = ['ru', 'et', 'en'];

function childLanguage(nativeLang: string): UiLanguage {
  return CHILD_LANGUAGES.includes(nativeLang as UiLanguage) ? (nativeLang as UiLanguage) : 'en';
}

/** Child mode: everything is shown in the child's native language; leaving it needs the parent PIN. */
export function ChildLayout() {
  const home = useLoad(myHome, []);

  if (!home.data) {
    return <main className="kid">{home.error ? <ErrorMessage error={home.error} /> : <Spinner />}</main>;
  }
  return (
    <I18nProvider fixedLang={childLanguage(home.data.child.nativeLang)}>
      <ChildShell home={home.data} reloadHome={home.reload} />
    </I18nProvider>
  );
}

function ChildShell({ home, reloadHome }: ChildContext) {
  const { t } = useI18n();
  const [pinOpen, setPinOpen] = useState(false);
  return (
    <main className="kid">
      <header className="kid__top">
        <button type="button" className="btn btn--ghost btn--small kid__parent" onClick={() => setPinOpen(true)}>
          <span aria-hidden="true">🔒</span> {t('kid.parentMode')}
        </button>
      </header>
      <Outlet context={{ home, reloadHome } satisfies ChildContext} />
      {pinOpen && <PinDialog onClose={() => setPinOpen(false)} />}
    </main>
  );
}

export function PinDialog({ onClose }: { onClose: () => void }) {
  const { t } = useI18n();
  const [pin, setPin] = useState('');
  const [run, busy, error] = useAction();

  async function submit(e: FormEvent) {
    e.preventDefault();
    // on success the route guard opens this child's page in parent mode
    const session = await run(() => switchToParent(pin));
    if (!session) {
      setPin('');
    }
  }

  const locked = error instanceof ApiError && error.code === 'PIN_LOCKED';
  return (
    <Modal title={t('kid.pinTitle')} onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <input
          className="pin-input"
          type="password"
          inputMode="numeric"
          autoComplete="off"
          autoFocus
          maxLength={6}
          aria-label={t('kid.pinLabel')}
          value={pin}
          onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
        />
        <ErrorMessage error={error} />
        <div className="form__actions">
          <button className="btn btn--primary" disabled={busy || locked || pin.length < 4}>
            {t('kid.unlock')}
          </button>
          <button type="button" className="btn btn--ghost" onClick={onClose}>
            {t('common.cancel')}
          </button>
        </div>
      </form>
    </Modal>
  );
}
