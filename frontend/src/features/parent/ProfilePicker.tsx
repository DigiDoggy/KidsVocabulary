import { Link } from 'react-router-dom';
import { listChildren, switchToChild } from '../../api/client';
import { ErrorMessage, Spinner, useAction } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { useLoad } from '../../lib/useLoad';

/** "Who is training?": hands the tablet over to one of the children (the route guard opens child mode). */
export function ProfilePicker() {
  const { t } = useI18n();
  const children = useLoad(listChildren, []);
  const [run, busy, error] = useAction();

  return (
    <div className="picker">
      <h1 className="picker__title">{t('picker.title')}</h1>
      <ErrorMessage error={children.error ?? error} />
      {!children.data && !children.error && <Spinner />}
      {children.data?.length === 0 && (
        <p>
          {t('children.empty')} <Link to="/children/new">{t('children.addFirst')}</Link>
        </p>
      )}
      <div className="picker__grid">
        {children.data?.map((c) => (
          <button
            key={c.id}
            type="button"
            className="picker__child"
            disabled={busy}
            onClick={() => run(() => switchToChild(c.id))}
          >
            <span className="picker__avatar" aria-hidden="true">
              {c.name.slice(0, 1).toUpperCase()}
            </span>
            <span className="picker__name">{c.name}</span>
          </button>
        ))}
      </div>
      <p className="muted">{t('picker.hint')}</p>
    </div>
  );
}
