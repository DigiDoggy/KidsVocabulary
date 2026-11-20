import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { login, register } from '../../api/client';
import { ApiError } from '../../api/http';
import { ErrorMessage, Field, useAction } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { LanguageSwitcher } from '../../components/LanguageSwitcher';

const PIN_PATTERN = /^\d{4,6}$/;

function AuthLayout({ title, children }: { title: string; children: React.ReactNode }) {
  const { t } = useI18n();
  return (
    <main className="auth">
      <div className="auth__top">
        <LanguageSwitcher />
      </div>
      <div className="auth__box">
        <p className="auth__brand">{t('app.name')}</p>
        <h1 className="auth__title">{title}</h1>
        {children}
      </div>
    </main>
  );
}

export function LoginPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [run, busy, error] = useAction();

  async function submit(e: FormEvent) {
    e.preventDefault();
    const session = await run(() => login(email, password));
    if (session) {
      navigate('/', { replace: true });
    }
  }

  return (
    <AuthLayout title={t('auth.loginTitle')}>
      <form className="form" onSubmit={submit}>
        <Field label={t('auth.email')}>
          <input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label={t('auth.password')}>
          <input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        <ErrorMessage error={error} />
        <button className="btn btn--primary btn--wide" disabled={busy}>
          {t('auth.loginButton')}
        </button>
      </form>
      <p className="auth__switch">
        {t('auth.noAccount')} <Link to="/register">{t('auth.registerLink')}</Link>
      </p>
    </AuthLayout>
  );
}

export function RegisterPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [pin, setPin] = useState('');
  const [pinRepeat, setPinRepeat] = useState('');
  const [localErrors, setLocalErrors] = useState<Record<string, string>>({});
  const [run, busy, error] = useAction();

  const serverErrors = error instanceof ApiError ? error.fieldErrors : {};
  const fieldError = (name: string) => localErrors[name] ?? (serverErrors[name] ? t(`auth.invalid.${name}`) : undefined);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const errors: Record<string, string> = {};
    if (password.length < 8) errors.password = t('auth.invalid.password');
    if (!PIN_PATTERN.test(pin)) errors.pin = t('auth.invalid.pin');
    else if (pin !== pinRepeat) errors.pinRepeat = t('auth.invalid.pinRepeat');
    setLocalErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const session = await run(() => register(email, password, pin, timezone));
    if (session) {
      navigate('/', { replace: true });
    }
  }

  return (
    <AuthLayout title={t('auth.registerTitle')}>
      <form className="form" onSubmit={submit} noValidate>
        <Field label={t('auth.email')} error={fieldError('email')}>
          <input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label={t('auth.password')} error={fieldError('password')} hint={t('auth.passwordHint')}>
          <input
            type="password"
            autoComplete="new-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        <Field label={t('auth.pin')} error={fieldError('pin')} hint={t('auth.pinHint')}>
          <input
            type="password"
            inputMode="numeric"
            autoComplete="off"
            maxLength={6}
            value={pin}
            onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
          />
        </Field>
        <Field label={t('auth.pinRepeat')} error={fieldError('pinRepeat')}>
          <input
            type="password"
            inputMode="numeric"
            autoComplete="off"
            maxLength={6}
            value={pinRepeat}
            onChange={(e) => setPinRepeat(e.target.value.replace(/\D/g, ''))}
          />
        </Field>
        {Object.keys(serverErrors).length === 0 && <ErrorMessage error={error} />}
        <button className="btn btn--primary btn--wide" disabled={busy}>
          {t('auth.registerButton')}
        </button>
      </form>
      <p className="auth__switch">
        {t('auth.haveAccount')} <Link to="/login">{t('auth.loginLink')}</Link>
      </p>
    </AuthLayout>
  );
}
