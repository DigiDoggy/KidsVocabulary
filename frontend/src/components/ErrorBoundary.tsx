import { Component, type ErrorInfo, type ReactNode } from 'react';
import { useI18n } from '../i18n/I18nProvider';

/** Last line of defence: an unexpected rendering error shows a friendly page instead of a blank screen. */
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unexpected UI error', error, info.componentStack);
  }

  render() {
    return this.state.failed ? <CrashPage /> : this.props.children;
  }
}

function CrashPage() {
  const { t } = useI18n();
  return (
    <main className="auth">
      <div className="auth__box" role="alert">
        <h1 className="auth__title">{t('crash.title')}</h1>
        <p>{t('crash.text')}</p>
        <button type="button" className="btn btn--primary btn--wide" onClick={() => window.location.reload()}>
          {t('crash.reload')}
        </button>
      </div>
    </main>
  );
}
