import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  addWord,
  deleteWeek,
  fillUp,
  getChild,
  getWeek,
  launchWeek,
  listTopics,
  removeWord,
  replaceWord,
  unscheduleWeek,
  updateWeek,
  weekStats,
} from '../../api/client';
import type { Child, Week, WordView } from '../../api/types';
import { Card, ErrorMessage, Field, Spinner, useAction } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { weekStarts } from '../../lib/dates';
import { usePageTitle } from '../../lib/a11y';
import { useLoad } from '../../lib/useLoad';
import { useParentContext } from './ParentLayout';
import { OutcomeBadge, PointsBar, SourceBadge, WeekStatusBadge } from './shared';

export function WeekPage() {
  const weekId = Number(useParams().weekId);
  const { t, formatDate } = useI18n();
  const loaded = useLoad(async () => {
    const week = await getWeek(weekId);
    return { week, child: await getChild(week.childId) };
  }, [weekId]);
  const shown = loaded.data?.week;
  usePageTitle(shown && t('week.title', { from: formatDate(shown.startDate), to: formatDate(shown.endDate) }));

  if (loaded.error) return <ErrorMessage error={loaded.error} onRetry={loaded.reload} />;
  if (!loaded.data) return <Spinner />;
  const { week, child } = loaded.data;
  const setWeek = (next: Week) => loaded.setData({ week: next, child });

  return (
    <>
      <div className="page-head">
        <div>
          <p className="crumbs">
            <Link to="/">{t('nav.children')}</Link> / <Link to={`/children/${child.id}`}>{child.name}</Link>
          </p>
          <h1>
            {t('week.title', { from: formatDate(week.startDate), to: formatDate(week.endDate) })}{' '}
            <WeekStatusBadge status={week.status} />
          </h1>
          {week.weekIndex && <p className="muted">{t('week.indexOfMonth', { n: week.weekIndex })}</p>}
        </div>
      </div>
      {week.status === 'DRAFT' ? (
        <DraftEditor week={week} child={child} onChange={setWeek} />
      ) : week.status === 'SCHEDULED' ? (
        <ScheduledView week={week} onChange={setWeek} />
      ) : (
        <RunningView week={week} />
      )}
    </>
  );
}

// --- draft -----------------------------------------------------------------------------------------

function DraftEditor({ week, child, onChange }: { week: Week; child: Child; onChange: (w: Week) => void }) {
  const { t, formatDate } = useI18n();
  const navigate = useNavigate();
  const { account } = useParentContext();
  const { thisMonday, nextMonday } = weekStarts(account.timezone);
  const [topic, setTopic] = useState('');
  const [run, busy, error] = useAction();
  const [busyWordId, setBusyWordId] = useState<number | null>(null);
  const [filling, setFilling] = useState(false);
  const full = week.missing <= 0;
  const outdated = week.startDate < thisMonday;

  async function mutate(action: () => Promise<Week>) {
    const next = await run(action);
    if (next) onChange(next);
  }

  async function onReplace(word: WordView) {
    setBusyWordId(word.id);
    await mutate(() => replaceWord(week.id, word.id, topic));
    setBusyWordId(null);
  }

  async function onRemove(word: WordView) {
    setBusyWordId(word.id);
    await mutate(() => removeWord(week.id, word.id));
    setBusyWordId(null);
  }

  async function onFill() {
    setFilling(true);
    await mutate(() => fillUp(week.id, topic));
    setFilling(false);
  }

  async function onDelete() {
    if (!window.confirm(t('week.deleteConfirm'))) return;
    const done = await run(() => deleteWeek(week.id).then(() => true));
    if (done) navigate(`/children/${child.id}`, { replace: true });
  }

  return (
    <div className="stack">
      <WeekSettings week={week} thisMonday={thisMonday} nextMonday={nextMonday} onChange={onChange} />
      {outdated && (
        <div className="alert alert--warn" role="alert">
          {t('week.outdated')}
        </div>
      )}
      <Card
        title={t('week.words')}
        actions={
          <span className={full ? 'ok-text' : 'muted'}>
            {t('week.filled', { n: week.words.length, total: week.wordCount })}
          </span>
        }
      >
        {week.words.length === 0 && <p className="muted">{t('week.noWords')}</p>}
        <ul className="words">
          {week.words.map((w) => (
            <li key={w.id} className="word">
              <div className="word__text">
                <span className="word__prompt">{w.prompt}</span>
                <span className="word__arrow" aria-hidden="true">
                  →
                </span>
                <span className="word__answer">{w.answer}</span>
                {w.extraAnswers.length > 0 && (
                  <span className="word__extra">{t('week.alsoAccepted', { list: w.extraAnswers.join(', ') })}</span>
                )}
              </div>
              <SourceBadge source={w.source} />
              {w.source === 'CARRY_OVER' ? (
                <span className="word__note">{t('week.carryOverLocked')}</span>
              ) : (
                <div className="word__actions">
                  <button
                    type="button"
                    className="btn btn--small btn--ghost"
                    disabled={busy}
                    aria-label={t('week.replaceWord', { word: w.prompt })}
                    onClick={() => onReplace(w)}
                  >
                    {busyWordId === w.id ? '…' : t('week.replace')}
                  </button>
                  <button
                    type="button"
                    className="btn btn--small btn--danger-ghost"
                    disabled={busy}
                    aria-label={t('week.removeWord', { word: w.prompt })}
                    onClick={() => onRemove(w)}
                  >
                    {t('week.remove')}
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
        <ErrorMessage error={error} />
      </Card>
      <div className="grid">
        <AddWordCard week={week} child={child} disabled={busy || full} onAdded={onChange} />
        <Card title={t('week.fillTitle')}>
          <p className="muted">{t('week.fillHint')}</p>
          <TopicSelect value={topic} onChange={setTopic} />
          <button type="button" className="btn btn--primary" disabled={busy || full} onClick={onFill}>
            {filling ? t('week.filling') : full ? t('week.setFull') : t('week.fill', { count: week.missing })}
          </button>
        </Card>
      </div>
      <div className="action-bar">
        <button
          type="button"
          className="btn btn--primary btn--large"
          disabled={busy || !full || outdated}
          onClick={() => mutate(() => launchWeek(week.id))}
        >
          {week.startDate === thisMonday ? t('week.launchNow') : t('week.schedule', { date: formatDate(week.startDate) })}
        </button>
        {!full && <span className="muted">{t('week.launchNeedsWords', { count: week.missing })}</span>}
        <button type="button" className="btn btn--danger-ghost action-bar__end" disabled={busy} onClick={onDelete}>
          {t('week.deleteDraft')}
        </button>
      </div>
    </div>
  );
}

function WeekSettings({
  week,
  thisMonday,
  nextMonday,
  onChange,
}: {
  week: Week;
  thisMonday: string;
  nextMonday: string;
  onChange: (w: Week) => void;
}) {
  const { t, formatDate } = useI18n();
  const [startDate, setStartDate] = useState(week.startDate);
  const [wordCount, setWordCount] = useState(week.wordCount);
  const [run, busy, error] = useAction();

  useEffect(() => {
    setStartDate(week.startDate);
    setWordCount(week.wordCount);
  }, [week.startDate, week.wordCount]);

  const dirty = startDate !== week.startDate || wordCount !== week.wordCount;
  const tooSmall = wordCount < week.words.length;

  async function submit(e: FormEvent) {
    e.preventDefault();
    const next = await run(() =>
      updateWeek(week.id, wordCount, startDate !== week.startDate ? startDate : undefined),
    );
    if (next) onChange(next);
  }

  return (
    <Card title={t('week.settings')}>
      <form className="inline-form" onSubmit={submit}>
        <fieldset className="field">
          <legend className="field__label">{t('week.startsOn')}</legend>
          <div className="segmented">
            {[thisMonday, nextMonday].map((d) => (
              <label key={d} className={`segmented__item${startDate === d ? ' is-active' : ''}`}>
                <input type="radio" name="start" checked={startDate === d} onChange={() => setStartDate(d)} />
                <span>
                  {d === thisMonday ? t('week.thisWeek') : t('week.nextWeek')} <small>{formatDate(d)}</small>
                </span>
              </label>
            ))}
          </div>
        </fieldset>
        <Field
          label={t('week.wordCount')}
          error={tooSmall ? t('week.wordCountTooSmall', { count: week.words.length }) : undefined}
        >
          <input
            type="number"
            min={1}
            max={50}
            value={wordCount}
            onChange={(e) => setWordCount(Number(e.target.value))}
          />
        </Field>
        <button className="btn btn--primary" disabled={busy || !dirty || tooSmall || wordCount < 1 || wordCount > 50}>
          {t('common.save')}
        </button>
      </form>
      <ErrorMessage error={error} />
    </Card>
  );
}

function AddWordCard({
  week,
  child,
  disabled,
  onAdded,
}: {
  week: Week;
  child: Child;
  disabled: boolean;
  onAdded: (w: Week) => void;
}) {
  const { t } = useI18n();
  const [source, setSource] = useState('');
  const [target, setTarget] = useState('');
  const [extra, setExtra] = useState('');
  const [run, busy, error] = useAction();

  async function submit(e: FormEvent) {
    e.preventDefault();
    const extraAnswers = extra
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    const next = await run(() => addWord(week.id, source.trim(), target.trim(), extraAnswers));
    if (next) {
      onAdded(next);
      setSource('');
      setTarget('');
      setExtra('');
    }
  }

  return (
    <Card title={t('week.addTitle')}>
      <form className="form" onSubmit={submit}>
        <Field label={t('week.addSource', { lang: t(`languages.name.${child.nativeLang}`) })}>
          <input required maxLength={60} value={source} onChange={(e) => setSource(e.target.value)} />
        </Field>
        <Field label={t('week.addTarget', { lang: t(`languages.name.${child.targetLang}`) })}>
          <input
            required
            maxLength={60}
            lang={child.targetLang}
            autoCapitalize="none"
            value={target}
            onChange={(e) => setTarget(e.target.value)}
          />
        </Field>
        <Field label={t('week.addExtra')} hint={t('week.addExtraHint')}>
          <input lang={child.targetLang} autoCapitalize="none" value={extra} onChange={(e) => setExtra(e.target.value)} />
        </Field>
        <ErrorMessage error={error} />
        <button className="btn btn--primary" disabled={disabled || busy}>
          {t('week.addButton')}
        </button>
      </form>
    </Card>
  );
}

function TopicSelect({ value, onChange }: { value: string; onChange: (topic: string) => void }) {
  const { t, has } = useI18n();
  const topics = useLoad(listTopics, []);
  return (
    <Field label={t('week.topic')}>
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">{t('week.anyTopic')}</option>
        {topics.data?.map((topic) => (
          <option key={topic} value={topic}>
            {has(`topics.${topic}`) ? t(`topics.${topic}`) : topic}
          </option>
        ))}
      </select>
    </Field>
  );
}

// --- scheduled and running weeks -------------------------------------------------------------------

function ScheduledView({ week, onChange }: { week: Week; onChange: (w: Week) => void }) {
  const { t, formatDate } = useI18n();
  const [run, busy, error] = useAction();
  async function unschedule() {
    const next = await run(() => unscheduleWeek(week.id));
    if (next) onChange(next);
  }
  return (
    <div className="stack">
      <div className="alert alert--info">{t('week.scheduledInfo', { date: formatDate(week.startDate) })}</div>
      <WordTable week={week} />
      <div className="action-bar">
        <button type="button" className="btn btn--ghost" disabled={busy} onClick={unschedule}>
          {t('week.unschedule')}
        </button>
      </div>
      <ErrorMessage error={error} />
    </div>
  );
}

function RunningView({ week }: { week: Week }) {
  const { t, formatNumber } = useI18n();
  const stats = useLoad(() => weekStats(week.id), [week.id]);
  const closed = week.status === 'COMPLETED' || week.status === 'EXPIRED';
  return (
    <div className="stack">
      {closed && (
        <Card title={t('week.result')}>
          <PointsBar points={week.points} max={week.maxPoints} />
        </Card>
      )}
      <Card title={t('week.stats')}>
        {stats.error ? (
          <ErrorMessage error={stats.error} onRetry={stats.reload} />
        ) : !stats.data ? (
          <Spinner />
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('week.col.word')}</th>
                  <th>{t('week.col.answer')}</th>
                  <th className="num">{t('week.col.wrongRounds')}</th>
                  <th className="num">{t('week.col.solvedRound')}</th>
                  <th>{t('week.col.hint')}</th>
                  <th className="num">{t('week.col.points')}</th>
                  <th>{t('week.col.outcome')}</th>
                </tr>
              </thead>
              <tbody>
                {stats.data.map((s) => (
                  <tr key={s.weekWordId}>
                    <td>
                      {s.prompt}
                      {s.problematic && (
                        <span className="tag" title={t('week.problematicHint')}>
                          {' '}
                          {t('week.problematic')}
                        </span>
                      )}
                    </td>
                    <td>{s.answer}</td>
                    <td className="num">{s.wrongRounds}</td>
                    <td className="num">{s.solvedRound ?? '—'}</td>
                    <td>{s.hintUsed ? t('common.yes') : ''}</td>
                    <td className="num">{formatNumber(s.points)}</td>
                    <td>
                      <OutcomeBadge outcome={s.outcome} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}

function WordTable({ week }: { week: Week }) {
  const { t } = useI18n();
  return (
    <Card title={t('week.words')}>
      <ul className="words">
        {week.words.map((w) => (
          <li key={w.id} className="word">
            <div className="word__text">
              <span className="word__prompt">{w.prompt}</span>
              <span className="word__arrow" aria-hidden="true">
                →
              </span>
              <span className="word__answer">{w.answer}</span>
              {w.extraAnswers.length > 0 && (
                <span className="word__extra">{t('week.alsoAccepted', { list: w.extraAnswers.join(', ') })}</span>
              )}
            </div>
            <SourceBadge source={w.source} />
          </li>
        ))}
      </ul>
    </Card>
  );
}
