import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  addTaskWord,
  deleteGameTask,
  fillGameTask,
  getChild,
  getGameTask,
  giveGameTask,
  removeTaskWord,
} from '../../api/client';
import type { Child, GameTask, GameTaskStatus } from '../../api/types';
import { Badge, Card, ErrorMessage, Field, Spinner, useAction } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { usePageTitle } from '../../lib/a11y';
import { useLoad } from '../../lib/useLoad';
import { TOPIC_PATTERN, TopicSelect } from './WeekPage';

export function GameTaskStatusBadge({ status }: { status: GameTaskStatus }) {
  const { t } = useI18n();
  return <Badge tone={status === 'GIVEN' ? 'ok' : 'neutral'}>{t(`games.status.${status}`)}</Badge>;
}

/** A game task: the parent collects words (typed in or suggested) and gives the game to the child. */
export function GameTaskPage() {
  const taskId = Number(useParams().taskId);
  const { t } = useI18n();
  const loaded = useLoad(async () => {
    const task = await getGameTask(taskId);
    return { task, child: await getChild(task.childId) };
  }, [taskId]);
  const shown = loaded.data?.task;
  usePageTitle(shown && t('games.taskTitle', { mode: t(`games.modes.${shown.mode}`) }));

  if (loaded.error) return <ErrorMessage error={loaded.error} onRetry={loaded.reload} />;
  if (!loaded.data) return <Spinner />;
  const { task, child } = loaded.data;
  const setTask = (next: GameTask) => loaded.setData({ task: next, child });

  return (
    <>
      <div className="page-head">
        <div>
          <p className="crumbs">
            <Link to="/">{t('nav.children')}</Link> / <Link to={`/children/${child.id}`}>{child.name}</Link>
          </p>
          <h1>
            {t('games.taskTitle', { mode: t(`games.modes.${task.mode}`) })} <GameTaskStatusBadge status={task.status} />
          </h1>
          <p className="muted">{t(`games.modeHint.${task.mode}`)}</p>
        </div>
      </div>
      <TaskEditor task={task} child={child} onChange={setTask} />
    </>
  );
}

function TaskEditor({ task, child, onChange }: { task: GameTask; child: Child; onChange: (t: GameTask) => void }) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [run, busy, error] = useAction();
  const [busyWordId, setBusyWordId] = useState<number | null>(null);
  const draft = task.status === 'DRAFT';
  const full = task.words.length >= task.maxWords;
  const missing = Math.max(0, task.minWords - task.words.length);

  async function mutate(action: () => Promise<GameTask>) {
    const next = await run(action);
    if (next) onChange(next);
    return next;
  }

  async function onRemove(wordId: number) {
    setBusyWordId(wordId);
    await mutate(() => removeTaskWord(task.id, wordId));
    setBusyWordId(null);
  }

  async function onDelete() {
    if (!window.confirm(t('games.deleteConfirm'))) return;
    const done = await run(() => deleteGameTask(task.id).then(() => true));
    if (done) navigate(`/children/${child.id}`, { replace: true });
  }

  return (
    <div className="stack">
      {!draft && <p className="alert alert--info">{t('games.givenInfo', { count: task.timesPlayed })}</p>}
      <Card
        title={t('week.words')}
        actions={
          <span className={full ? 'ok-text' : 'muted'}>
            {t('games.filled', { n: task.words.length, max: task.maxWords })}
          </span>
        }
      >
        {task.words.length === 0 && <p className="muted">{t('games.noWords')}</p>}
        <ul className="words">
          {task.words.map((w) => (
            <li key={w.id} className="word">
              <div className="word__text">
                <span className="word__prompt">{w.prompt}</span>
                <span className="word__arrow" aria-hidden="true">
                  →
                </span>
                <span className="word__answer">{w.answer}</span>
              </div>
              {draft && (
                <div className="word__actions">
                  <button
                    type="button"
                    className="btn btn--small btn--danger-ghost"
                    disabled={busy}
                    aria-label={t('week.removeWord', { word: w.prompt })}
                    onClick={() => onRemove(w.id)}
                  >
                    {busyWordId === w.id ? '…' : t('week.remove')}
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
        <ErrorMessage error={error} />
      </Card>
      {draft && (
        <div className="grid">
          <AddTaskWordCard task={task} child={child} disabled={busy || full} onAdded={onChange} />
          <FillCard task={task} disabled={busy || full} mutate={mutate} />
        </div>
      )}
      <div className="action-bar">
        {draft && (
          <>
            <button
              type="button"
              className="btn btn--primary btn--large"
              disabled={busy || missing > 0}
              onClick={() => mutate(() => giveGameTask(task.id))}
            >
              {t('games.give', { name: child.name })}
            </button>
            <span className="muted">
              {missing > 0 ? t('games.needWords', { count: missing }) : t('games.giveHint')}
            </span>
          </>
        )}
        <button type="button" className="btn btn--danger-ghost action-bar__end" disabled={busy} onClick={onDelete}>
          {t('games.delete')}
        </button>
      </div>
    </div>
  );
}

function AddTaskWordCard({
  task,
  child,
  disabled,
  onAdded,
}: {
  task: GameTask;
  child: Child;
  disabled: boolean;
  onAdded: (t: GameTask) => void;
}) {
  const { t } = useI18n();
  const [source, setSource] = useState('');
  const [target, setTarget] = useState('');
  const [run, busy, error] = useAction();

  async function submit(e: FormEvent) {
    e.preventDefault();
    const next = await run(() => addTaskWord(task.id, source.trim(), target.trim()));
    if (next) {
      onAdded(next);
      setSource('');
      setTarget('');
    }
  }

  return (
    <Card title={t('week.addTitle')}>
      <form className="form" onSubmit={submit}>
        <Field label={t('week.addSource', { lang: t(`languages.name.${child.nativeLang}`) })}>
          <input required maxLength={60} value={source} onChange={(e) => setSource(e.target.value)} />
        </Field>
        <Field
          label={t('week.addTarget', { lang: t(`languages.name.${child.targetLang}`) })}
          hint={task.mode === 'WORD_SEARCH' ? t('games.wordSearchLimit') : undefined}
        >
          <input
            required
            maxLength={60}
            lang={child.targetLang}
            autoCapitalize="none"
            value={target}
            onChange={(e) => setTarget(e.target.value)}
          />
        </Field>
        <ErrorMessage error={error} />
        <button className="btn btn--primary" disabled={disabled || busy}>
          {t('week.addButton')}
        </button>
      </form>
    </Card>
  );
}

function FillCard({
  task,
  disabled,
  mutate,
}: {
  task: GameTask;
  disabled: boolean;
  mutate: (action: () => Promise<GameTask>) => Promise<GameTask | undefined>;
}) {
  const { t } = useI18n();
  const room = Math.max(0, task.maxWords - task.words.length);
  const [topic, setTopic] = useState('');
  const [count, setCount] = useState(Math.min(room, 6));
  const [filling, setFilling] = useState(false);
  const topicOk = TOPIC_PATTERN.test(topic);
  const wanted = Math.max(1, Math.min(count || 1, room));

  async function onFill() {
    setFilling(true);
    await mutate(() => fillGameTask(task.id, topic.trim(), wanted));
    setFilling(false);
  }

  return (
    <Card title={t('games.fillTitle')}>
      <p className="muted">{t('games.fillHint')}</p>
      <TopicSelect value={topic} onChange={setTopic} />
      <Field label={t('games.fillCount')}>
        <input
          type="number"
          min={1}
          max={Math.max(1, room)}
          value={count}
          onChange={(e) => setCount(Number(e.target.value))}
        />
      </Field>
      <button type="button" className="btn btn--primary" disabled={disabled || !topicOk || room === 0} onClick={onFill}>
        {filling ? t('week.filling') : room === 0 ? t('games.full') : t('week.fill', { count: wanted })}
      </button>
    </Card>
  );
}
