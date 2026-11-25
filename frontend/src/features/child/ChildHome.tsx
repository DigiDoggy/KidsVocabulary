import { Link } from 'react-router-dom';
import { gameMenu, myGarden } from '../../api/client';
import type { HomeMonth, HomeWeek } from '../../api/types';
import { ErrorMessage } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { usePageTitle } from '../../lib/a11y';
import { useLoad } from '../../lib/useLoad';
import { useChildContext } from './ChildLayout';
import { GAME_MODES, MODE_ICONS } from './GamePage';
import { Stars } from './Stars';

export function ChildHome() {
  const { t } = useI18n();
  const { home } = useChildContext();
  usePageTitle(home.child.name);
  return (
    <div className="kid__page">
      <h1 className="kid__hello">{t('kid.hello', { name: home.child.name })}</h1>
      {home.week ? <WeekCard week={home.week} /> : <p className="kid__card kid__text">{t('kid.noWeek')}</p>}
      <GardenCard />
      <GamesCard />
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

/** A peek at the word garden, once there is something growing in it. */
function GardenCard() {
  const { t } = useI18n();
  const garden = useLoad(myGarden, []);
  const data = garden.data;
  if (!data || data.plantCount === 0) return null;
  return (
    <section className="kid__card garden-card" aria-labelledby="garden-title">
      <h2 id="garden-title" className="games__title">
        <span aria-hidden="true">🌱</span> {t('kid.garden')}
      </h2>
      <p className="kid__text">{t('kid.gardenCount', { count: data.plantCount })}</p>
      {data.dueCount > 0 && <p className="kid__text">{t('kid.dueCount', { count: data.dueCount })}</p>}
      <Link
        className={`btn btn--huge ${data.dueCount > 0 ? 'btn--accent' : 'btn--ghost'}`}
        to={data.dueCount > 0 ? '/child/garden/water' : '/child/garden'}
      >
        {data.dueCount > 0 ? t('kid.water') : t('kid.openGarden')}
      </Link>
      {data.dueCount > 0 && (
        <Link className="btn btn--ghost" to="/child/garden">
          {t('kid.openGarden')}
        </Link>
      )}
    </section>
  );
}

/** The game modes: this week's words in any mode, and the tasks the parent gave. */
function GamesCard() {
  const { t } = useI18n();
  const menu = useLoad(gameMenu, []);
  if (menu.error) {
    return (
      <section className="kid__card">
        <ErrorMessage error={menu.error} onRetry={menu.reload} />
      </section>
    );
  }
  const data = menu.data;
  if (!data || (!data.week && data.tasks.length === 0)) return null;
  return (
    <section className="kid__card games" aria-labelledby="games-title">
      <h2 id="games-title" className="games__title">
        <span aria-hidden="true">🎲</span> {t('kid.games')}
      </h2>
      <p className="muted games__note">{t('kid.gamesNote')}</p>
      {data.week && (
        <>
          <h3 className="games__subtitle">{t('kid.weekWords')}</h3>
          <div className="games__modes">
            {GAME_MODES.map((mode) => (
              <Link key={mode} className="btn btn--ghost games__mode" to={`/child/play/week/${mode}`}>
                <span className="games__icon" aria-hidden="true">
                  {MODE_ICONS[mode]}
                </span>
                {t(`kid.mode${mode}`)}
              </Link>
            ))}
          </div>
        </>
      )}
      {data.tasks.length > 0 && (
        <>
          <h3 className="games__subtitle">{t('kid.fromParents')}</h3>
          <ul className="games__tasks">
            {data.tasks.map((task) => (
              <li key={task.id}>
                <Link className="btn btn--ghost games__task" to={`/child/play/task/${task.id}`}>
                  <span className="games__icon" aria-hidden="true">
                    {MODE_ICONS[task.mode]}
                  </span>
                  <span>{t(`kid.mode${task.mode}`)}</span>
                  <span className="games__count">{t('kid.taskWords', { count: task.wordCount })}</span>
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}
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
