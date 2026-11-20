import { cloneElement, useEffect, useId, useRef, useState, type ReactElement, type ReactNode } from 'react';
import { ApiError } from '../api/http';
import { useI18n } from '../i18n/I18nProvider';

export function Spinner({ label }: { label?: string }) {
  const { t } = useI18n();
  return (
    <p className="spinner" role="status">
      <span className="spinner__dot" aria-hidden="true" />
      {label ?? t('common.loading')}
    </p>
  );
}

/** Message for a failed request: a localised text per error code, plus the server's detail. */
export function errorText(error: unknown, t: (key: string) => string, has: (key: string) => boolean): string {
  if (error instanceof ApiError) {
    const key = `errors.${error.code}`;
    return has(key) ? t(key) : t('errors.generic');
  }
  return t('errors.generic');
}

export function ErrorMessage({ error }: { error: unknown }) {
  const { t, has } = useI18n();
  if (!error) {
    return null;
  }
  const detail = error instanceof ApiError && error.status !== 0 ? error.message : null;
  return (
    <div className="alert alert--error" role="alert">
      <div>{errorText(error, t, has)}</div>
      {detail && <div className="alert__detail">{detail}</div>}
    </div>
  );
}

export function Badge({ tone, children }: { tone: string; children: ReactNode }) {
  return <span className={`badge badge--${tone}`}>{children}</span>;
}

export function Card({ title, actions, children }: { title?: ReactNode; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && (
        <header className="card__header">
          {title && <h2 className="card__title">{title}</h2>}
          {actions && <div className="card__actions">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  );
}

/**
 * A labelled form control with an optional hint or validation message. The control gets an id for
 * the label and is described by the hint/error, so screen readers announce them separately.
 */
export function Field({
  label,
  error,
  hint,
  children,
}: {
  label: string;
  error?: string;
  hint?: string;
  children: ReactElement<{ id?: string; 'aria-describedby'?: string; 'aria-invalid'?: boolean }>;
}) {
  const id = useId();
  const noteId = `${id}-note`;
  const note = error ?? hint;
  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>
        {label}
      </label>
      {cloneElement(children, {
        id,
        'aria-describedby': note ? noteId : undefined,
        'aria-invalid': error ? true : undefined,
      })}
      {note && (
        <span id={noteId} className={error ? 'field__error' : 'field__hint'}>
          {note}
        </span>
      )}
    </div>
  );
}

/**
 * Runs an async action and tracks its state, so buttons can show progress and errors once.
 * Returns [run, busy, error, clearError].
 */
export function useAction(): [
  <T>(action: () => Promise<T>) => Promise<T | undefined>,
  boolean,
  unknown,
  () => void,
] {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  async function run<T>(action: () => Promise<T>): Promise<T | undefined> {
    setBusy(true);
    setError(null);
    try {
      return await action();
    } catch (e) {
      if (mounted.current) {
        setError(e);
      }
      return undefined;
    } finally {
      if (mounted.current) {
        setBusy(false);
      }
    }
  }
  return [run, busy, error, () => setError(null)];
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="modal" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal__box" role="dialog" aria-modal="true" aria-label={title}>
        <h2 className="modal__title">{title}</h2>
        {children}
      </div>
    </div>
  );
}
