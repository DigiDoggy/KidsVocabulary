import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  childHome,
  createGameTask,
  createWeek,
  getChild,
  listGameTasks,
  listMonths,
  listWeeks,
  setReward,
  switchToChild,
} from '../../api/client';
import type { GameMode, Home, HomeWeek, MonthSummary, WeekSummary } from '../../api/types';
import { Card, ErrorMessage, Spinner, useAction } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { weekStarts } from '../../lib/dates';
import { usePageTitle } from '../../lib/a11y';
import { useLoad } from '../../lib/useLoad';
import { useParentContext } from './ParentLayout';
import { GameTaskStatusBadge } from './GameTaskPage';
import { MonthResultBadge, PointsBar, WeekStatusBadge } from './shared';

const PENDING = ['DRAFT', 'SCHEDULED'];

export function ChildPage() {
  const childId = Number(useParams().id);
  const { t } = useI18n();
  const navigate = useNavigate();
  const overview = useLoad(
    () => Promise.all([getChild(childId), childHome(childId), listWeeks(childId), listMonths(childId)]),
    [childId],
  );
  const [openChildMode, switching, switchError] = useAction();
  usePageTitle(overview.data?.[0].name);

  if (overview.error) return <ErrorMessage error={overview.error} onRetry={overview.reload} />;
  if (!overview.data) return <Spinner />;
  const [child, home, weeks, months] = overview.data;

  async function toChildMode() {
    const session = await openChildMode(() => switchToChild(childId));
    if (session) navigate('/child', { replace: true });
  }

  return (
    <>
      <div className="page-head">
        <div>
          <p className="crumbs">
            <Link to="/">{t('nav.children')}</Link>
          </p>
          <h1>{child.name}</h1>
          <p className="muted">
            {child.level} · {t(`languages.name.${child.nativeLang}`)} → {t(`languages.name.${child.targetLang}`)} ·{' '}
            {t('child.weekSize', { count: child.defaultWeekSize })}
          </p>
        </div>
        <div className="page-head__actions">
          <Link className="btn btn--ghost" to={`/children/${childId}/edit`}>
            {t('common.edit')}
          </Link>
          <button type="button" className="btn btn--accent" disabled={switching} onClick={toChildMode}>
            {t('child.openChildMode')}
          </button>
        </div>
      </div>
      <ErrorMessage error={switchError} />
      <div className="grid">
        <CurrentWeekCard home={home} />
        <PreparedWeekCard childId={childId} weeks={weeks} />
        <MonthCard childId={childId} home={home} onSaved={overview.reload} />
        <GameTasksCard childId={childId} />
        <HistoryCard weeks={weeks} months={months} />
      </div>
    </>
  );
}

function CurrentWeekCard({ home }: { home: Home }) {
  const { t, formatDate } = useI18n();
  const week = home.week;
  return (
    <Card title={t('child.currentWeek')}>
      {!week ? (
        <p className="muted">{t('child.noCurrentWeek')}</p>
      ) : (
        <>
          <p>
            <Link to={`/weeks/${week.id}`}>
              {formatDate(week.startDate)} – {formatDate(week.endDate)}
            </Link>{' '}
            <WeekStatusBadge status={week.status} />
          </p>
          <PointsBar points={week.points} max={week.maxPoints} />
          <p className="muted">
            {t(`child.firstPass.${firstPassState(week)}`)} ·{' '}
            {t('week.wordsCount', { count: week.wordCount })}
          </p>
        </>
      )}
    </Card>
  );
}

/** The first pass is open once the child has started it; an active week without it is not started yet. */
function firstPassState(week: HomeWeek): 'notStarted' | 'inProgress' | 'finished' | 'expired' {
  if (week.status === 'EXPIRED') return 'expired';
  if (week.status === 'COMPLETED') return 'finished';
  return week.firstPassOpen ? 'inProgress' : 'notStarted';
}

function PreparedWeekCard({ childId, weeks }: { childId: number; weeks: WeekSummary[] }) {
  const { t, formatDate } = useI18n();
  const { account } = useParentContext();
  const navigate = useNavigate();
  const [run, busy, error] = useAction();
  const prepared = weeks.find((w) => PENDING.includes(w.status));
  const { thisMonday, nextMonday } = weekStarts(account.timezone);
  const thisWeekTaken = weeks.some((w) => w.startDate === thisMonday && w.status !== 'DRAFT');

  async function prepare(startDate: string) {
    const week = await run(() => createWeek(childId, startDate));
    if (week) navigate(`/weeks/${week.id}`);
  }

  return (
    <Card title={t('child.preparedWeek')}>
      {prepared ? (
        <>
          <p>
            {formatDate(prepared.startDate)} – {formatDate(prepared.endDate)} <WeekStatusBadge status={prepared.status} />
          </p>
          <Link className="btn btn--primary" to={`/weeks/${prepared.id}`}>
            {prepared.status === 'DRAFT' ? t('child.continueEditing') : t('child.viewSet')}
          </Link>
        </>
      ) : (
        <>
          <p className="muted">{t('child.noPreparedWeek')}</p>
          <div className="button-row">
            <button
              type="button"
              className="btn btn--primary"
              disabled={busy || thisWeekTaken}
              onClick={() => prepare(thisMonday)}
            >
              {t('child.prepareThisWeek')}
            </button>
            <button type="button" className="btn btn--ghost" disabled={busy} onClick={() => prepare(nextMonday)}>
              {t('child.prepareNextWeek', { date: formatDate(nextMonday) })}
            </button>
          </div>
          <ErrorMessage error={error} />
        </>
      )}
    </Card>
  );
}

function MonthCard({ childId, home, onSaved }: { childId: number; home: Home; onSaved: () => void }) {
  const { t } = useI18n();
  const month = home.month;
  const [reward, setRewardText] = useState(month?.reward ?? '');
  const [saved, setSaved] = useState(false);
  const [run, busy, error] = useAction();

  useEffect(() => setRewardText(month?.reward ?? ''), [month?.reward]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const result = await run(() => setReward(childId, reward.trim()));
    if (result) {
      setSaved(true);
      onSaved();
    }
  }

  return (
    <Card
      title={month ? t('child.monthTitle', { n: month.seqNo }) : t('child.monthNotStarted')}
      actions={month && <Link to={`/months/${month.id}`}>{t('child.monthDetails')}</Link>}
    >
      {month && (
        <>
          <p className="muted">{t('child.weeksClosed', { n: month.weeksClosed })}</p>
          <PointsBar points={month.points} max={month.maxPoints} />
        </>
      )}
      <form className="inline-form" onSubmit={submit}>
        <label className="field">
          <span className="field__label">{t('child.reward')}</span>
          <input
            maxLength={255}
            placeholder={t('child.rewardPlaceholder')}
            value={reward}
            onChange={(e) => {
              setRewardText(e.target.value);
              setSaved(false);
            }}
          />
        </label>
        <button className="btn btn--primary" disabled={busy || reward.trim() === (month?.reward ?? '')}>
          {t('common.save')}
        </button>
      </form>
      {saved && <p className="ok-text">{t('child.rewardSaved')}</p>}
      <p className="field__hint">{t('child.rewardHint')}</p>
      <ErrorMessage error={error} />
    </Card>
  );
}

const GAME_MODES: GameMode[] = ['CHOICE', 'SCRAMBLE', 'WORD_SEARCH'];

/** Extra, unscored games with their own words, given to the child as tasks. */
function GameTasksCard({ childId }: { childId: number }) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const tasks = useLoad(() => listGameTasks(childId), [childId]);
  const [mode, setMode] = useState<GameMode>('CHOICE');
  const [run, busy, error] = useAction();

  async function create(e: FormEvent) {
    e.preventDefault();
    const task = await run(() => createGameTask(childId, mode));
    if (task) navigate(`/game-tasks/${task.id}`);
  }

  return (
    <Card title={t('games.title')}>
      <p className="muted">{t('games.hint')}</p>
      {tasks.error ? (
        <ErrorMessage error={tasks.error} onRetry={tasks.reload} />
      ) : !tasks.data ? (
        <Spinner />
      ) : tasks.data.length === 0 ? (
        <p className="muted">{t('games.none')}</p>
      ) : (
        <ul className="rows">
          {tasks.data.map((task) => (
            <li key={task.id}>
              <Link to={`/game-tasks/${task.id}`}>
                {t(`games.modes.${task.mode}`)} · {t('week.wordsCount', { count: task.wordCount })}
              </Link>
              <span>
                {task.status === 'GIVEN' && (
                  <span className="muted">
                    {task.timesPlayed > 0 ? t('games.played', { count: task.timesPlayed }) : t('games.notPlayed')}{' '}
                  </span>
                )}
                <GameTaskStatusBadge status={task.status} />
              </span>
            </li>
          ))}
        </ul>
      )}
      <form className="inline-form" onSubmit={create}>
        <label className="field">
          <span className="field__label">{t('games.mode')}</span>
          <select value={mode} onChange={(e) => setMode(e.target.value as GameMode)}>
            {GAME_MODES.map((m) => (
              <option key={m} value={m}>
                {t(`games.modes.${m}`)}
              </option>
            ))}
          </select>
        </label>
        <button className="btn btn--primary" disabled={busy}>
          {t('games.create')}
        </button>
      </form>
      <ErrorMessage error={error} />
    </Card>
  );
}

function HistoryCard({ weeks, months }: { weeks: WeekSummary[]; months: MonthSummary[] }) {
  const { t, formatDate, formatNumber } = useI18n();
  const closed = weeks.filter((w) => !PENDING.includes(w.status));
  return (
    <Card title={t('child.history')}>
      {closed.length === 0 && months.length === 0 && <p className="muted">{t('child.noHistory')}</p>}
      {months.length > 0 && (
        <>
          <h3 className="subhead">{t('child.months')}</h3>
          <ul className="rows">
            {months.map((m) => (
              <li key={m.id}>
                <Link to={`/months/${m.id}`}>{t('child.monthTitle', { n: m.seqNo })}</Link>
                <span>
                  {m.percent !== null && `${formatNumber(m.percent)} % `}
                  {m.result ? <MonthResultBadge result={m.result} /> : <span className="muted">{t('month.inProgress')}</span>}
                </span>
              </li>
            ))}
          </ul>
        </>
      )}
      {closed.length > 0 && (
        <>
          <h3 className="subhead">{t('child.weeks')}</h3>
          <ul className="rows">
            {closed.map((w) => (
              <li key={w.id}>
                <Link to={`/weeks/${w.id}`}>
                  {w.weekIndex && `${t('week.indexShort', { n: w.weekIndex })} · `}
                  {formatDate(w.startDate)} – {formatDate(w.endDate)}
                </Link>
                <span>
                  {w.points !== null && `${formatNumber(w.points)} / ${formatNumber(w.maxPoints)} `}
                  <WeekStatusBadge status={w.status} />
                </span>
              </li>
            ))}
          </ul>
        </>
      )}
    </Card>
  );
}
