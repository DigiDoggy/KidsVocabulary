import { Link } from 'react-router-dom';
import type { HomeMonth, HomeWeek } from '../../api/types';
import { useI18n } from '../../i18n/I18nProvider';
import { usePageTitle } from '../../lib/a11y';
import { useChildContext } from './ChildLayout';
import { Stars } from './Stars';

export function ChildHome() {
  const { t } = useI18n();
  const { home } = useChildContext();
  usePageTitle(home.child.name);
  return (
    <div className="kid__page">
      <h1 className="kid__hello">{t('kid.hello', { name: home.child.name })}</h1>
      {home.week ? <WeekCard week={home.week} /> : <p className="kid__card kid__text">{t('kid.noWeek')}</p>}
      {home.month && <MonthCard month={home.month} />}
    </div>
  );
}

function WeekCard({ week }: { week: HomeWeek }) {
  const { t } = useI18n();
  const active = week.status === 'ACTIVE';
  return (
    <section className="kid__card">
      <p className="kid__text">
        {active
          ? week.firstPassOpen
            ? t('kid.weekInProgress', { count: week.wordCount })
            : t('kid.weekReady', { count: week.wordCount })
          : week.status === 'COMPLETED'
            ? t('kid.weekDone')
            : t('kid.weekOver')}
      </p>
      <Stars points={week.points} max={week.maxPoints} />
      <Link className="btn btn--accent btn--huge" to="/child/train">
        {active ? (week.firstPassOpen ? t('kid.continue') : t('kid.start')) : t('kid.practice')}
      </Link>
      {!active && <p className="muted">{t('kid.practiceNote')}</p>}
    </section>
  );
}

function MonthCard({ month }: { month: HomeMonth }) {
  const { t } = useI18n();
  return (
    <section className="kid__card kid__month">
      <div className="kid__weeks" aria-label={t('kid.weeksDone', { n: month.weeksClosed })}>
        {[1, 2, 3, 4].map((i) => (
          <span key={i} className={`kid__week-dot${i <= month.weeksClosed ? ' is-done' : ''}`} aria-hidden="true">
            {i}
          </span>
        ))}
      </div>
      <p>{t('kid.weeksDone', { n: month.weeksClosed })}</p>
      {month.reward && (
        <p className="kid__reward">
          <span aria-hidden="true">🎁</span> {t('kid.reward', { reward: month.reward })}
        </p>
      )}
    </section>
  );
}
