import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { getSession, requestHint, startSession, submitAnswer } from '../../api/client';
import { ApiError } from '../../api/http';
import type { AnswerResponse, CurrentItem, TrainingSession } from '../../api/types';
import { ErrorMessage, Spinner } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { usePageTitle } from '../../lib/a11y';
import { useLoad } from '../../lib/useLoad';
import { useChildContext } from './ChildLayout';
import { Stars } from './Stars';

/** How long the "correct" feedback stays before the next word (the child may tap on sooner). */
export const CORRECT_PAUSE_MS = 1200;

const PRAISE_KEYS = ['kid.praise1', 'kid.praise2', 'kid.praise3'];

type Phase =
  | { kind: 'question' }
  | { kind: 'correct'; result: AnswerResponse }
  | { kind: 'wrong'; result: AnswerResponse; given: string }
  | { kind: 'round'; next: CurrentItem; finishedRound: number }
  | { kind: 'finished'; session: TrainingSession }
  | { kind: 'closed' };

export function TrainingPage() {
  const { home } = useChildContext();
  const weekId = home.week?.id;
  const session = useLoad(() => (weekId ? startSession(weekId) : Promise.resolve(null)), [weekId]);

  if (!weekId) return <Navigate to="/child" replace />;
  if (session.error) {
    return (
      <div className="kid__page">
        <ErrorMessage error={session.error} onRetry={session.reload} />
        <BackHome />
      </div>
    );
  }
  if (!session.data) return <Spinner />;
  return <Trainer key={session.data.id} initial={session.data} />;
}

function Trainer({ initial }: { initial: TrainingSession }) {
  const { t, formatNumber } = useI18n();
  const { reloadHome, home } = useChildContext();
  usePageTitle(t('kid.trainingTitle'));
  const [item, setItem] = useState<CurrentItem | null>(initial.current);
  const [points, setPoints] = useState(initial.points);
  const [phase, setPhase] = useState<Phase>(
    initial.status === 'IN_PROGRESS' && initial.current
      ? { kind: 'question' }
      : initial.status === 'CLOSED'
        ? { kind: 'closed' }
        : { kind: 'finished', session: initial },
  );
  const [answer, setAnswer] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const input = useRef<HTMLInputElement>(null);
  const praise = useRef(PRAISE_KEYS[0]);

  const sessionOver = phase.kind === 'finished' || phase.kind === 'closed';
  useEffect(() => {
    if (sessionOver) reloadHome();
  }, [sessionOver, reloadHome]);

  // keep the keyboard up on tablets: focus the input whenever a question is shown
  useEffect(() => {
    if (phase.kind === 'question') input.current?.focus();
  }, [phase, item]);

  async function moveOn(result: AnswerResponse) {
    setAnswer('');
    if (result.sessionFinished) {
      try {
        setPhase({ kind: 'finished', session: await getSession(initial.id) });
      } catch {
        setPhase({ kind: 'finished', session: { ...initial, points: result.points, words: [] } });
      }
    } else if (result.roundFinished && result.next) {
      setPhase({ kind: 'round', next: result.next, finishedRound: item?.round ?? 1 });
    } else {
      setItem(result.next);
      setPhase({ kind: 'question' });
    }
  }

  // feedback timers: a short pause after a correct answer, the configured time after a mistake (A4)
  useEffect(() => {
    if (phase.kind !== 'correct' && phase.kind !== 'wrong') return;
    const ms = phase.kind === 'correct' ? CORRECT_PAUSE_MS : phase.result.showCorrectAnswerFor * 1000;
    const timer = setTimeout(() => moveOn(phase.result), ms);
    return () => clearTimeout(timer);
    // moveOn only reads state that is stable while this phase lasts
  }, [phase]);

  async function handleFailure(e: unknown) {
    if (e instanceof ApiError && e.code === 'SESSION_CLOSED') {
      setPhase({ kind: 'closed' });
    } else if (e instanceof ApiError && e.code === 'CONFLICT') {
      // another tab moved on: continue from the server's state
      const fresh = await getSession(initial.id);
      setItem(fresh.current);
      setPoints(fresh.points);
      setPhase(fresh.current ? { kind: 'question' } : { kind: 'finished', session: fresh });
    } else {
      setError(e);
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!item || busy || phase.kind !== 'question' || answer.trim() === '') return; // A5: blank is not an attempt
    setBusy(true);
    setError(null);
    try {
      const result = await submitAnswer(initial.id, item.wordId, item.round, answer);
      if (result.verdict === 'IGNORED') return;
      setPoints(result.points);
      if (result.verdict === 'CORRECT') {
        praise.current = PRAISE_KEYS[Math.floor(Math.random() * PRAISE_KEYS.length)];
        setPhase({ kind: 'correct', result });
      } else {
        setPhase({ kind: 'wrong', result, given: answer });
      }
    } catch (err) {
      await handleFailure(err);
    } finally {
      setBusy(false);
    }
  }

  async function hint() {
    if (!item || busy) return;
    setBusy(true);
    setError(null);
    try {
      const h = await requestHint(initial.id, item.wordId, item.round);
      setItem({ ...item, hintMask: h.hintMask, hintAvailable: h.hintAvailable });
    } catch (err) {
      await handleFailure(err);
    } finally {
      setBusy(false);
      input.current?.focus();
    }
  }

  if (phase.kind === 'closed') {
    return (
      <div className="kid__page kid__center">
        <p className="kid__big-icon" aria-hidden="true">⏰</p>
        <h1>{t('kid.closedTitle')}</h1>
        <p className="kid__text">{t('kid.closedText')}</p>
        <BackHome />
      </div>
    );
  }

  if (phase.kind === 'finished') {
    return <Summary session={phase.session} />;
  }

  if (phase.kind === 'round') {
    return (
      <div className="kid__page kid__center">
        <p className="kid__big-icon" aria-hidden="true">🎉</p>
        <h1>{t('kid.roundDone', { n: phase.finishedRound })}</h1>
        <p className="kid__text">{t('kid.roundNext', { count: phase.next.roundSize })}</p>
        <button
          type="button"
          className="btn btn--accent btn--huge"
          autoFocus
          onClick={() => {
            setItem(phase.next);
            setPhase({ kind: 'question' });
          }}
        >
          {t('kid.go')}
        </button>
      </div>
    );
  }

  if (!item) return <Spinner />;
  const feedback = phase.kind === 'correct' || phase.kind === 'wrong' ? phase.kind : null;
  const done = item.position - 1;

  return (
    <div className="kid__page train">
      <div className="train__status">
        <span>{t('kid.roundLabel', { n: item.round })}</span>
        <span className="train__dots" role="img" aria-label={t('kid.wordOf', { n: item.position, total: item.roundSize })}>
          {Array.from({ length: item.roundSize }, (_, i) => (
            <span key={i} className={`train__dot${i < done ? ' is-done' : i === done ? ' is-current' : ''}`} />
          ))}
        </span>
        <span className="train__points">
          <span aria-hidden="true">⭐</span>
          <span className="visually-hidden">{t('kid.starsLabel')}</span> {formatNumber(points)}
        </span>
      </div>

      <p className="train__prompt" lang={home.child.nativeLang}>
        {item.prompt}
      </p>

      {feedback === 'wrong' && phase.kind === 'wrong' ? (
        <div className="train__reveal" role="status">
          <p className="train__reveal-label">{t('kid.correctIs')}</p>
          <Letters word={phase.result.correctAnswer ?? ''} variant="correct" />
          <p className="train__given">
            {t('kid.youWrote')} <s>{phase.given}</s>
          </p>
          <div className="train__timer">
            <div
              className="train__timer-fill"
              style={{ animationDuration: `${phase.result.showCorrectAnswerFor}s` }}
            />
          </div>
        </div>
      ) : (
        <Letters word={item.hintMask} variant="mask" />
      )}

      <form className="train__form" onSubmit={submit}>
        <input
          ref={input}
          className={`train__input${feedback ? ` is-${feedback}` : ''}`}
          aria-label={t('kid.answerLabel')}
          lang={home.child.targetLang}
          value={answer}
          readOnly={phase.kind !== 'question'}
          onChange={(e) => setAnswer(e.target.value)}
          autoComplete="off"
          autoCorrect="off"
          autoCapitalize="none"
          spellCheck={false}
          enterKeyHint="done"
          maxLength={100}
        />
        {feedback === 'correct' ? (
          <button
            type="button"
            className="btn btn--ok btn--huge train__praise"
            aria-live="polite"
            onClick={() => phase.kind === 'correct' && moveOn(phase.result)}
          >
            <span aria-hidden="true">✓</span> {t(praise.current)}
          </button>
        ) : (
          <div className="train__buttons">
            <button className="btn btn--accent btn--huge" disabled={busy || feedback !== null || answer.trim() === ''}>
              {t('kid.check')}
            </button>
            <button
              type="button"
              className="btn btn--ghost btn--huge"
              disabled={busy || feedback !== null || !item.hintAvailable}
              onClick={hint}
            >
              <span aria-hidden="true">💡</span> {t('kid.hint')}
            </button>
          </div>
        )}
      </form>
      {error !== null && (
        <div className="train__error">
          <ErrorMessage error={error} />
          <p className="muted">{t('kid.tryAgain')}</p>
        </div>
      )}
    </div>
  );
}

/** The answer as letter boxes: hidden letters (`_`) are empty boxes, spaces stay gaps. */
function Letters({ word, variant }: { word: string; variant: 'mask' | 'correct' }) {
  const { t } = useI18n();
  const chars = Array.from(word);
  const label =
    variant === 'mask' ? t('kid.lettersLabel', { count: chars.filter((c) => c !== ' ').length }) : word;
  return (
    <div className={`letters letters--${variant}`} role="img" aria-label={label}>
      {chars.map((c, i) =>
        c === ' ' ? (
          <span key={i} className="letters__gap" />
        ) : (
          <span key={i} className={`letters__box${c === '_' ? ' is-empty' : ''}`}>
            {c === '_' ? '' : c}
          </span>
        ),
      )}
    </div>
  );
}

function Summary({ session }: { session: TrainingSession }) {
  const { t } = useI18n();
  const firstTry = session.words.filter((w) => w.solvedRound === 1 && !w.hintUsed).length;
  return (
    <div className="kid__page kid__center">
      <p className="kid__big-icon" aria-hidden="true">🏆</p>
      <h1>{t('kid.finishedTitle')}</h1>
      {session.words.length > 0 && (
        <p className="kid__text">{t('kid.firstTry', { count: firstTry, total: session.words.length })}</p>
      )}
      <Stars points={session.points} max={session.maxPoints} />
      <p className="muted">{session.counts ? t('kid.counts') : t('kid.practiceNote')}</p>
      <BackHome />
    </div>
  );
}

function BackHome() {
  const { t } = useI18n();
  return (
    <Link className="btn btn--accent btn--huge" to="/child">
      {t('kid.home')}
    </Link>
  );
}
