import { useState, type FormEvent } from 'react';
import { changePin } from '../../api/client';
import { Card, ErrorMessage, Field, useAction } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { usePageTitle } from '../../lib/a11y';
import { useParentContext } from './ParentLayout';

export function SettingsPage() {
  const { t } = useI18n();
  const { account } = useParentContext();
  const [password, setPassword] = useState('');
  const [pin, setPin] = useState('');
  const [done, setDone] = useState(false);
  const [run, busy, error] = useAction();
  usePageTitle(t('settings.title'));
  const pinValid = /^\d{4,6}$/.test(pin);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setDone(false);
    const ok = await run(() => changePin(password, pin).then(() => true));
    if (ok) {
      setDone(true);
      setPassword('');
      setPin('');
    }
  }

  return (
    <>
      <div className="page-head">
        <h1>{t('settings.title')}</h1>
      </div>
      <div className="stack">
        <Card title={t('settings.account')}>
          <p>{account.email}</p>
          <p className="muted">{t('settings.timezone', { tz: account.timezone })}</p>
        </Card>
        <Card title={t('settings.changePin')}>
          <form className="form" onSubmit={submit}>
            <Field label={t('settings.currentPassword')}>
              <input
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </Field>
            <Field label={t('settings.newPin')} hint={t('auth.pinHint')}>
              <input
                type="password"
                inputMode="numeric"
                autoComplete="off"
                maxLength={6}
                value={pin}
                onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
              />
            </Field>
            <ErrorMessage error={error} />
            {done && <p className="ok-text">{t('settings.pinChanged')}</p>}
            <button className="btn btn--primary" disabled={busy || !pinValid || !password}>
              {t('common.save')}
            </button>
          </form>
        </Card>
      </div>
    </>
  );
}
