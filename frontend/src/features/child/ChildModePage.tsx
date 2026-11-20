import { useState, type FormEvent } from 'react';
import { myHome, switchToParent } from '../../api/client';
import { ApiError } from '../../api/http';
import { ErrorMessage, Modal, Spinner, useAction } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { useLoad } from '../../lib/useLoad';

/** Child mode shell. The training screens arrive with the child UI; for now it shows the week and the way back. */
export function ChildModePage() {
  const { t } = useI18n();
  const home = useLoad(myHome, []);
  const [pinOpen, setPinOpen] = useState(false);

  return (
    <main className="child-shell">
      <header className="child-shell__top">
        <button type="button" className="btn btn--ghost btn--small" onClick={() => setPinOpen(true)}>
          {t('childMode.parentMode')}
        </button>
      </header>
      {home.error ? (
        <ErrorMessage error={home.error} />
      ) : !home.data ? (
        <Spinner />
      ) : (
        <div className="child-shell__body">
          <h1 className="child-shell__hello">{t('childMode.hello', { name: home.data.child.name })}</h1>
          <p className="child-shell__text">
            {home.data.week ? t('childMode.weekReady', { count: home.data.week.wordCount }) : t('childMode.noWeek')}
          </p>
          <p className="muted">{t('childMode.trainingSoon')}</p>
        </div>
      )}
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
    <Modal title={t('childMode.pinTitle')} onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <input
          className="pin-input"
          type="password"
          inputMode="numeric"
          autoComplete="off"
          autoFocus
          maxLength={6}
          aria-label={t('auth.pin')}
          value={pin}
          onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
        />
        <ErrorMessage error={error} />
        <div className="form__actions">
          <button className="btn btn--primary" disabled={busy || locked || pin.length < 4}>
            {t('childMode.unlock')}
          </button>
          <button type="button" className="btn btn--ghost" onClick={onClose}>
            {t('common.cancel')}
          </button>
        </div>
      </form>
    </Modal>
  );
}
