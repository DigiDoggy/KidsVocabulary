import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { taskGame, taskPlayed, weekGame } from '../../api/client';
import type { Game, GameMode, GameWord } from '../../api/types';
import { ErrorMessage, Spinner } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { usePageTitle } from '../../lib/a11y';
import { pick, shuffled } from '../../lib/shuffle';
import { playCorrect, playWrong, unlockAudio, useSoundSetting } from '../../lib/sound';
import { useLoad } from '../../lib/useLoad';
import { useChildContext } from './ChildLayout';
import { Reaction, type ReactionKind } from './Reaction';

/*
 * Game modes: unscored ways to play with words. No points, no pictures and no pronunciation here (those
 * belong to the training); right answers and mistakes still get the funny reactions.
 */

export const GAME_MODES: GameMode[] = ['CHOICE', 'SCRAMBLE', 'WORD_SEARCH'];
export const MODE_ICONS: Record<GameMode, string> = { CHOICE: '🎯', SCRAMBLE: '🔤', WORD_SEARCH: '🔎' };

/** Pause after a solved word before the next one. */
export const NEXT_WORD_MS = 1000;

/** A word's letters as they appear in the grid: lower case, without spaces, hyphens or apostrophes. */
export function letterKey(word: string): string {
  return word.toLowerCase().replace(/[^\p{L}]/gu, '');
}

function isLetter(c: string): boolean {
  return /\p{L}/u.test(c);
}

/** Upper case for the grid, except the sharp s, which has no single-letter capital in common use. */
function gridLetter(c: string, lang: string): string {
  return c === 'ß' ? c : c.toLocaleUpperCase(lang);
}

export function GamePage({ source }: { source: 'week' | 'task' }) {
  const params = useParams();
  const mode = params.mode as GameMode;
  const taskId = Number(params.taskId);
  const valid = source === 'week' ? GAME_MODES.includes(mode) : Number.isInteger(taskId) && taskId > 0;
  const game = useLoad<Game | null>(
    () => (!valid ? Promise.resolve(null) : source === 'week' ? weekGame(mode) : taskGame(taskId)),
    [source, mode, taskId],
  );
  const [round, setRound] = useState(0);

  if (!valid) return <Navigate to="/child" replace />;
  if (game.error) {
    return (
      <div className="kid__page">
        <ErrorMessage error={game.error} onRetry={game.reload} />
        <BackHome />
      </div>
    );
  }
  if (!game.data) return <Spinner />;
  return <Player key={round} game={game.data} onAgain={() => setRound((r) => r + 1)} />;
}

/** Reactions and sounds shared by all games. */
export interface Feedback {
  /** a thumbs-up or a wink and the cheerful sound */
  cheer: () => void;
  /** the wagging finger; with {@code loud} also the sad trombone */
  oops: (loud: boolean) => void;
  /** the current reaction, to be placed inside the game's stage */
  reaction: ReactNode;
}

function useFeedback(soundOn: boolean): Feedback {
  const [shown, setShown] = useState<{ kind: ReactionKind; n: number } | null>(null);
  const soundRef = useRef(soundOn);
  soundRef.current = soundOn;
  return {
    cheer: () => {
      setShown((s) => ({ kind: pick<ReactionKind>(['thumb', 'wink']), n: (s?.n ?? 0) + 1 }));
      if (soundRef.current) playCorrect();
    },
    oops: (loud) => {
      setShown((s) => ({ kind: 'finger', n: (s?.n ?? 0) + 1 }));
      if (loud && soundRef.current) playWrong();
    },
    reaction: shown && <Reaction key={shown.n} kind={shown.kind} />,
  };
}

interface GameProps {
  words: GameWord[];
  feedback: Feedback;
  /** how many words are done so far */
  onProgress: (done: number) => void;
  onDone: () => void;
}

function Player({ game, onAgain }: { game: Game; onAgain: () => void }) {
  const { t } = useI18n();
  usePageTitle(t(`kid.mode${game.mode}`));
  const [soundOn, toggleSound] = useSoundSetting();
  const feedback = useFeedback(soundOn);
  const [done, setDone] = useState(0);
  const [over, setOver] = useState(false);
  const finish = useCallback(() => setOver(true), []);
  const total = game.words.length;

  useEffect(() => {
    // the parent sees how often a task was played; a lost request is not worth bothering the child
    if (over && game.taskId !== null) taskPlayed(game.taskId).catch(() => undefined);
  }, [over, game.taskId]);

  if (over || total === 0) {
    return (
      <div className="kid__page kid__center">
        <p className="kid__big-icon" aria-hidden="true">🎉</p>
        <h1>{t('kid.gameOver')}</h1>
        {total > 0 && <p className="kid__text">{t('kid.gameOverText', { count: total })}</p>}
        <p className="muted">{t('kid.gamesNote')}</p>
        <div className="game__end-buttons">
          {total > 0 && (
            <button type="button" className="btn btn--ghost btn--huge" onClick={onAgain}>
              <span aria-hidden="true">🔁</span> {t('kid.again')}
            </button>
          )}
          <BackHome />
        </div>
      </div>
    );
  }

  const props: GameProps = { words: game.words, feedback, onProgress: setDone, onDone: finish };
  return (
    <div className="kid__page train game">
      <div className="train__status">
        <span className="train__start">
          <Link className="train__home" to="/child" aria-label={t('kid.home')}>
            <span aria-hidden="true">🏠</span>
          </Link>
          <button
            type="button"
            className="train__sound"
            aria-label={soundOn ? t('kid.soundOff') : t('kid.soundOn')}
            title={soundOn ? t('kid.soundOff') : t('kid.soundOn')}
            onClick={toggleSound}
          >
            <span aria-hidden="true">{soundOn ? '🔇' : '🔊'}</span>
          </button>
          <span className="train__round">
            <span aria-hidden="true">{MODE_ICONS[game.mode]}</span> {t(`kid.mode${game.mode}`)}
          </span>
        </span>
        <span
          className="train__dots"
          role="img"
          aria-label={t('kid.doneOf', { n: done, total })}
        >
          {game.words.map((w, i) => (
            <span key={w.id} className={`train__dot${i < done ? ' is-done' : ''}`} />
          ))}
        </span>
      </div>
      {game.mode === 'CHOICE' && <ChoiceGame {...props} />}
      {game.mode === 'SCRAMBLE' && <ScrambleGame {...props} />}
      {game.mode === 'WORD_SEARCH' && game.grid && <WordSearchGame {...props} grid={game.grid} />}
    </div>
  );
}

// --- choose from 4 -----------------------------------------------------------------------------------

function ChoiceGame({ words, feedback, onProgress, onDone }: GameProps) {
  const { t } = useI18n();
  const { home } = useChildContext();
  const order = useMemo(
    () => shuffled(words).map((w) => ({ ...w, options: shuffled(w.options ?? [w.answer]) })),
    [words],
  );
  const [index, setIndex] = useState(0);
  const [wrong, setWrong] = useState<string[]>([]);
  const [right, setRight] = useState<string | null>(null);
  const word = order[index];

  useEffect(() => {
    if (right === null) return;
    const timer = setTimeout(() => {
      if (index + 1 >= order.length) {
        onDone();
      } else {
        setIndex(index + 1);
        setWrong([]);
        setRight(null);
      }
    }, NEXT_WORD_MS);
    return () => clearTimeout(timer);
  }, [right, index, order.length, onDone]);

  function choose(option: string) {
    unlockAudio();
    if (right !== null || wrong.includes(option)) return;
    if (option === word.answer) {
      setRight(option);
      feedback.cheer();
      onProgress(index + 1);
    } else {
      setWrong([...wrong, option]);
      feedback.oops(true);
    }
  }

  return (
    <>
      <div className="train__stage game__stage">
        <p className="train__prompt" lang={home.child.nativeLang}>
          {word.prompt}
        </p>
        {feedback.reaction}
      </div>
      <p className="muted game__hint">{t('kid.chooseHint')}</p>
      <div className="choice" role="group" aria-label={t('kid.chooseHint')}>
        {word.options.map((option) => (
          <button
            key={option}
            type="button"
            lang={home.child.targetLang}
            className={`btn btn--ghost btn--huge choice__option${option === right ? ' is-right' : ''}${
              wrong.includes(option) ? ' is-wrong' : ''
            }`}
            disabled={wrong.includes(option)}
            onClick={() => choose(option)}
          >
            {option}
          </button>
        ))}
      </div>
    </>
  );
}

// --- scrambled letters -------------------------------------------------------------------------------

function ScrambleGame({ words, feedback, onProgress, onDone }: GameProps) {
  const order = useMemo(() => shuffled(words), [words]);
  const [index, setIndex] = useState(0);
  const word = order[index];
  return (
    <ScrambleWord
      key={word.id}
      word={word}
      feedback={feedback}
      onSolved={() => {
        onProgress(index + 1);
        if (index + 1 >= order.length) onDone();
        else setIndex(index + 1);
      }}
    />
  );
}

/** How long a wrong word stays on screen before its letters go back to the tiles. */
export const WRONG_SCRAMBLE_MS = 1400;

/**
 * The child puts all the letters into the boxes in any order (a tap on a filled box takes its letter back);
 * the word is checked only when every box is filled.
 */
function ScrambleWord({ word, feedback, onSolved }: { word: GameWord; feedback: Feedback; onSolved: () => void }) {
  const { t } = useI18n();
  const { home } = useChildContext();
  const lang = home.child.targetLang;
  const chars = useMemo(() => Array.from(word.answer), [word]);
  // positions of the letters in the answer; spaces, hyphens and apostrophes stay fixed
  const letterAt = useMemo(() => chars.flatMap((c, i) => (isLetter(c) ? [i] : [])), [chars]);
  const tiles = useMemo(() => shuffled(letterAt.map((i) => ({ id: i, c: chars[i] }))), [letterAt, chars]);
  /** tile id in each letter box, or null while the box is empty */
  const [boxes, setBoxes] = useState<(number | null)[]>(() => letterAt.map(() => null));
  const [verdict, setVerdict] = useState<'right' | 'wrong' | null>(null);
  const letterOf = (id: number) => chars[id];

  useEffect(() => {
    if (verdict === null) return;
    const timer = setTimeout(() => {
      if (verdict === 'right') {
        onSolved();
      } else {
        setBoxes(letterAt.map(() => null));
        setVerdict(null);
      }
    }, verdict === 'right' ? NEXT_WORD_MS : WRONG_SCRAMBLE_MS);
    return () => clearTimeout(timer);
    // onSolved changes on every render of the parent; the timer must only start once per verdict
  }, [verdict]);

  function put(tileId: number) {
    unlockAudio();
    if (verdict !== null || boxes.includes(tileId)) return;
    const next = [...boxes];
    next[next.indexOf(null)] = tileId;
    setBoxes(next);
    if (next.includes(null)) return;
    const right = next.every(
      (id, n) => letterOf(id!).toLocaleLowerCase(lang) === chars[letterAt[n]].toLocaleLowerCase(lang),
    );
    setVerdict(right ? 'right' : 'wrong');
    if (right) feedback.cheer();
    else feedback.oops(true);
  }

  function takeBack(box: number) {
    if (verdict !== null) return;
    const next = [...boxes];
    next[box] = null;
    setBoxes(next);
  }

  const shown = (i: number) => {
    const n = letterAt.indexOf(i);
    if (n < 0) return chars[i];
    const id = boxes[n];
    // the right word is shown as written (a German noun keeps its capital); while placing, lower case
    return id === null ? null : verdict === 'right' ? chars[i] : letterOf(id).toLocaleLowerCase(lang);
  };

  return (
    <>
      <div className="train__stage game__stage">
        <p className="train__prompt" lang={home.child.nativeLang}>
          {word.prompt}
        </p>
        {feedback.reaction}
      </div>
      <div
        className={`letters scramble__slots${verdict === 'right' ? ' is-solved' : ''}${verdict === 'wrong' ? ' is-wrong' : ''}`}
        role="group"
        aria-label={t('kid.scrambleBoxes')}
      >
        {chars.map((c, i) => {
          if (c === ' ') return <span key={i} className="letters__gap" />;
          const n = letterAt.indexOf(i);
          const letter = shown(i);
          if (n < 0 || letter === null) {
            return (
              <span key={i} className={`letters__box${letter === null ? ' is-empty' : ''}`}>
                {letter ?? ''}
              </span>
            );
          }
          return (
            <button
              key={i}
              type="button"
              lang={lang}
              className="letters__box scramble__placed"
              aria-label={t('kid.takeBack', { letter })}
              disabled={verdict !== null}
              onClick={() => takeBack(n)}
            >
              {letter}
            </button>
          );
        })}
      </div>
      <p className="muted game__hint">{t('kid.scrambleHint')}</p>
      <div className="scramble__tiles" role="group" aria-label={t('kid.scrambleHint')}>
        {tiles.map((tile) => (
          <button
            key={tile.id}
            type="button"
            lang={lang}
            className="scramble__tile"
            disabled={verdict !== null || boxes.includes(tile.id)}
            onClick={() => put(tile.id)}
          >
            {tile.c.toLocaleLowerCase(lang)}
          </button>
        ))}
      </div>
    </>
  );
}

// --- word search -------------------------------------------------------------------------------------

/** Cells from a to b if they form a straight line (row, column or diagonal), else null. */
export function straightLine(a: number, b: number, size: number): number[] | null {
  const [r1, c1, r2, c2] = [Math.floor(a / size), a % size, Math.floor(b / size), b % size];
  const dr = Math.sign(r2 - r1);
  const dc = Math.sign(c2 - c1);
  const steps = Math.max(Math.abs(r2 - r1), Math.abs(c2 - c1));
  if (r1 !== r2 && c1 !== c2 && Math.abs(r2 - r1) !== Math.abs(c2 - c1)) return null;
  return Array.from({ length: steps + 1 }, (_, i) => (r1 + i * dr) * size + c1 + i * dc);
}

const FOUND_COLOURS = 6;

/** Cells of a word hidden in the grid: right, down or diagonally down-right, read either way; null if absent. */
export function findInGrid(letters: string[], size: number, key: string): number[] | null {
  const backwards = Array.from(key).reverse().join('');
  for (const [dr, dc] of [[0, 1], [1, 0], [1, 1]]) {
    for (let r = 0; r < size; r++) {
      for (let c = 0; c < size; c++) {
        const cells: number[] = [];
        for (let i = 0; i < key.length && r + i * dr < size && c + i * dc < size; i++) {
          cells.push((r + i * dr) * size + c + i * dc);
        }
        if (cells.length !== key.length) continue;
        const text = cells.map((n) => letters[n]).join('');
        if (text === key) return cells;
        if (text === backwards) return cells.reverse();
      }
    }
  }
  return null;
}

/** Hint steps for a word: 1 the number of letters, 2 its first letter in the grid, 3 the whole word. */
export const MAX_HINT = 3;

/** How long a selection that is not a word may wait for the next letter before it counts as a miss. */
export const WRONG_WORD_MS = 2000;

/**
 * The selection after tapping a cell. The child picks letters one by one along a straight line; a tap
 * further along the line fills the letters in between (so tapping the first and the last letter works too).
 * Tapping the last selected letter again takes it back; a tap off the line starts a new selection there.
 */
export function extendSelection(selection: number[], cell: number, size: number): number[] {
  if (selection.length === 0) return [cell];
  if (cell === selection[selection.length - 1]) return selection.slice(0, -1);
  if (selection.includes(cell)) return selection;
  const line = straightLine(selection[0], cell, size);
  if (!line) return [cell];
  if (selection.length === 1) return line;
  const sameDirection = line[1] - line[0] === selection[1] - selection[0];
  return sameDirection && line.length > selection.length ? line : [cell];
}

function WordSearchGame({ words, feedback, onProgress, onDone, grid }: GameProps & { grid: { size: number; rows: string[] } }) {
  const { t } = useI18n();
  const { home } = useChildContext();
  const lang = home.child.targetLang;
  const size = grid.size;
  const letters = useMemo(() => grid.rows.flatMap((r) => Array.from(r)), [grid]);
  const keys = useMemo(() => words.map((w) => letterKey(w.answer)), [words]);
  const [found, setFound] = useState<Record<number, number[]>>({});
  const [selection, setSelection] = useState<number[]>([]);
  /** hint step reached per word id */
  const [hints, setHints] = useState<Record<number, number>>({});
  /** the hint button was pressed: the next tap on a word gives it a hint */
  const [choosingHint, setChoosingHint] = useState(false);
  const drag = useRef<{ start: number; moved: boolean } | null>(null);
  const skipClick = useRef(false);
  const foundCount = Object.keys(found).length;
  const feedbackRef = useRef(feedback);
  feedbackRef.current = feedback;

  useEffect(() => {
    if (foundCount < words.length) return;
    const timer = setTimeout(onDone, NEXT_WORD_MS);
    return () => clearTimeout(timer);
  }, [foundCount, words.length, onDone]);

  // a selection that is not a word and gets no new letter for a while is a miss: wag the finger, start over
  useEffect(() => {
    if (selection.length === 0) return;
    const timer = setTimeout(() => {
      if (drag.current) return;
      feedbackRef.current.oops(false);
      setSelection([]);
    }, WRONG_WORD_MS);
    return () => clearTimeout(timer);
  }, [selection]);

  /** Takes the selection; a hidden word (read either way) is found at once. */
  function select(cells: number[]) {
    const text = cells.map((c) => letters[c]).join('');
    const backwards = Array.from(text).reverse().join('');
    const i = cells.length < 2 ? -1 : words.findIndex((w, n) => !found[w.id] && (keys[n] === text || keys[n] === backwards));
    if (i < 0) {
      setSelection(cells);
      return;
    }
    const next = { ...found, [words[i].id]: cells };
    setFound(next);
    setSelection([]);
    feedback.cheer();
    onProgress(Object.keys(next).length);
  }

  function cellOf(target: EventTarget | null): number | null {
    const el = target instanceof Element ? target.closest<HTMLElement>('[data-cell]') : null;
    return el ? Number(el.dataset.cell) : null;
  }

  function tap(cell: number) {
    unlockAudio();
    select(extendSelection(selection, cell, size));
  }

  function giveHint(wordId: number) {
    setHints({ ...hints, [wordId]: Math.min(MAX_HINT, (hints[wordId] ?? 0) + 1) });
    setChoosingHint(false);
  }

  // cells to point at: the first letter (step 2) or the whole word (step 3) of words not found yet
  const hinted = new Set<number>();
  words.forEach((w, n) => {
    const step = hints[w.id] ?? 0;
    if (found[w.id] || step < 2) return;
    const cells = findInGrid(letters, size, keys[n]) ?? [];
    (step >= MAX_HINT ? cells : cells.slice(0, 1)).forEach((c) => hinted.add(c));
  });
  const allHinted = words.every((w) => found[w.id] || (hints[w.id] ?? 0) >= MAX_HINT);

  return (
    <>
      <p className="muted game__hint" aria-live="polite">
        {choosingHint ? t('kid.hintPickWord') : t('kid.findHint')}
      </p>
      <div className="search__tools">
        <button
          type="button"
          className={`btn btn--ghost search__hint${choosingHint ? ' is-active' : ''}`}
          aria-pressed={choosingHint}
          disabled={allHinted && !choosingHint}
          onClick={() => setChoosingHint(!choosingHint)}
        >
          <span aria-hidden="true">💡</span> {t('kid.hint')}
        </button>
      </div>
      <ul className={`search__words${choosingHint ? ' is-choosing' : ''}`}>
        {words.map((w, i) => (
          <li key={w.id} className={found[w.id] ? `is-found colour-${i % FOUND_COLOURS}` : ''}>
            {choosingHint && !found[w.id] && (hints[w.id] ?? 0) < MAX_HINT ? (
              <button type="button" className="search__pick" lang={home.child.nativeLang} onClick={() => giveHint(w.id)}>
                {w.prompt}
              </button>
            ) : (
              <span lang={home.child.nativeLang}>{w.prompt}</span>
            )}
            {!found[w.id] && (hints[w.id] ?? 0) >= 1 && (
              <span className="search__letters">
                {' · '}
                {t('kid.lettersLabel', { count: keys[i].length })}
              </span>
            )}
            {found[w.id] && (
              <>
                {' '}
                <span aria-hidden="true">→</span>{' '}
                <span className="search__answer" lang={lang}>
                  {w.answer}
                </span>
              </>
            )}
          </li>
        ))}
      </ul>
      <div className="game__stage search__stage">
        <div
          className="search__grid"
          role="group"
          aria-label={t('kid.gridLabel')}
          style={{ gridTemplateColumns: `repeat(${size}, 1fr)` }}
          onPointerDown={(e) => {
            const cell = cellOf(e.target);
            if (cell === null) return;
            unlockAudio();
            drag.current = { start: cell, moved: false };
            // let the move events reach the grid while the finger slides over other cells
            (e.target as Element).releasePointerCapture?.(e.pointerId);
          }}
          onPointerMove={(e) => {
            const d = drag.current;
            if (!d) return;
            const cell = cellOf(document.elementFromPoint?.(e.clientX, e.clientY) ?? null);
            if (cell === null || (cell === d.start && !d.moved)) return;
            d.moved = true;
            setSelection(straightLine(d.start, cell, size) ?? [d.start]);
          }}
          onPointerUp={() => {
            const d = drag.current;
            drag.current = null;
            if (d?.moved) {
              // a swipe ends like a tap sequence: a word counts at once, anything else waits for more letters
              skipClick.current = true;
              select([...selection]);
            }
          }}
          onPointerCancel={() => {
            drag.current = null;
            setSelection([]);
          }}
        >
          {letters.map((c, cell) => {
            const owner = words.findIndex((w) => found[w.id]?.includes(cell));
            return (
              <button
                key={cell}
                type="button"
                data-cell={cell}
                lang={lang}
                aria-pressed={selection.includes(cell)}
                className={`search__cell${selection.includes(cell) ? ' is-selected' : ''}${
                  owner >= 0 ? ` is-found colour-${owner % FOUND_COLOURS}` : ''
                }${hinted.has(cell) && owner < 0 ? ' is-hinted' : ''}`}
                onClick={() => {
                  if (skipClick.current) {
                    skipClick.current = false;
                    return;
                  }
                  tap(cell);
                }}
              >
                {gridLetter(c, lang)}
              </button>
            );
          })}
        </div>
        {feedback.reaction}
      </div>
    </>
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
