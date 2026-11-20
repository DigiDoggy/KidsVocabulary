import { Link } from 'react-router-dom';
import { listChildren } from '../../api/client';
import { ErrorMessage, Spinner } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { ageOn } from '../../lib/dates';
import { useLoad } from '../../lib/useLoad';

export function ChildrenPage() {
  const { t } = useI18n();
  const children = useLoad(listChildren, []);
  const today = new Date().toISOString().slice(0, 10);

  return (
    <>
      <div className="page-head">
        <h1>{t('children.title')}</h1>
        <div className="page-head__actions">
          {children.data && children.data.length > 0 && (
            <Link className="btn btn--accent" to="/pick">
              {t('children.childMode')}
            </Link>
          )}
          <Link className="btn btn--primary" to="/children/new">
            {t('children.add')}
          </Link>
        </div>
      </div>
      <ErrorMessage error={children.error} />
      {children.loading && !children.data && <Spinner />}
      {children.data && children.data.length === 0 && (
        <div className="empty">
          <p>{t('children.empty')}</p>
          <Link className="btn btn--primary" to="/children/new">
            {t('children.addFirst')}
          </Link>
        </div>
      )}
      <ul className="tiles">
        {children.data?.map((c) => (
          <li key={c.id}>
            <Link className="tile" to={`/children/${c.id}`}>
              <span className="tile__avatar" aria-hidden="true">
                {c.name.slice(0, 1).toUpperCase()}
              </span>
              <span className="tile__name">{c.name}</span>
              <span className="tile__meta">
                {c.birthDate && `${t('children.age', { count: ageOn(c.birthDate, today) })} · `}
                {c.level} · {t(`languages.short.${c.nativeLang}`)} → {t(`languages.short.${c.targetLang}`)}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
