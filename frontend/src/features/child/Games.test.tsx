import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { resetHttpState } from '../../api/http';
import type { Game, GameMenu } from '../../api/types';
import { App } from '../../App';
import { translate } from '../../i18n/I18nProvider';
import { childTokens, mockApi } from '../../test/mockApi';
import { extendSelection, findInGrid, letterKey, straightLine, WRONG_SCRAMBLE_MS, WRONG_WORD_MS } from './GamePage';

const child = { id: 7, name: 'Mari', nativeLang: 'et', targetLang: 'en' };
const week = {
  id: 5,
  status: 'ACTIVE',
  startDate: '2026-11-16',
  endDate: '2026-11-22',
  weekIndex: 1,
  wordCount: 2,
  points: 0,
  maxPoints: 4,
  firstPassOpen: false,
};
const home = { child, week, month: null };
const menu: GameMenu = { week: { id: 5, wordCount: 2 }, tasks: [{ id: 9, mode: 'WORD_SEARCH', wordCount: 2 }] };

const dog = { id: 1, prompt: 'koer', answer: 'dog', options: null };
const cat = { id: 2, prompt: 'kass', answer: 'cat', options: null };

function renderAt(path: string, routes: Record<string, unknown>) {
  const api = mockApi({
    'POST /auth/refresh': childTokens(7),
    'GET /me/home': home,
    'GET /me/games': menu,
    ...(routes as Record<string, object>),
  });
  window.history.pushState({}, '', path);
  const view = render(<App initialLang="en" />);
  return { api, ...view };
}

afterEach(() => {
  vi.restoreAllMocks();
  resetHttpState();
  localStorage.clear();
});

describe('game helpers', () => {
  it('reads grid letters the way the server stores them', () => {
    expect(letterKey('Teddy bear')).toBe('teddybear');
    expect(letterKey('T-shirt')).toBe('tshirt');
    expect(letterKey('Straße')).toBe('straße');
  });

  it('accepts straight lines only', () => {
    expect(straightLine(0, 2, 6)).toEqual([0, 1, 2]);
    expect(straightLine(2, 0, 6)).toEqual([2, 1, 0]);
    expect(straightLine(0, 12, 6)).toEqual([0, 6, 12]);
    expect(straightLine(0, 14, 6)).toEqual([0, 7, 14]);
    expect(straightLine(14, 0, 6)).toEqual([14, 7, 0]);
    expect(straightLine(0, 8, 6)).toBeNull();
    expect(straightLine(3, 3, 6)).toEqual([3]);
  });
});

describe('game menu', () => {
  it('offers every mode for this week and the tasks from the parents', async () => {
    renderAt('/child', {});
    const card = (await screen.findByRole('heading', { name: /Mängi sõnadega/ })).closest('section')!;
    expect(within(card).getByRole('link', { name: /Vali/ })).toHaveAttribute('href', '/child/play/week/CHOICE');
    expect(within(card).getByRole('link', { name: 'Tähed' })).toHaveAttribute('href', '/child/play/week/SCRAMBLE');
    expect(within(card).getByRole('link', { name: /Sõnarägastik\s*2 sõna/ })).toHaveAttribute('href', '/child/play/task/9');
    expect(within(card).getByText('Mängud on lihtsalt lõbuks: tähed ei muutu.')).toBeInTheDocument();
  });

  it('talks to a girl in the feminine form', async () => {
    const girl = { ...home, child: { ...child, nativeLang: 'ru', gender: 'GIRL' }, week: { ...week, firstPassOpen: true } };
    renderAt('/child', { 'GET /me/home': girl });
    expect(await screen.findByText(translate('ru', 'kid.weekInProgress', { count: 2 }, 'girl'))).toBeInTheDocument();
  });

  it('is hidden when there is nothing to play', async () => {
    renderAt('/child', { 'GET /me/games': { week: null, tasks: [] } });
    await screen.findByRole('heading', { name: /Mari/ });
    await waitFor(() => expect(screen.queryByText('Mängi sõnadega')).not.toBeInTheDocument());
  });
});

describe('choose from 4', () => {
  const game: Game = {
    mode: 'CHOICE',
    taskId: null,
    grid: null,
    words: [
      { ...dog, options: ['dog', 'cat', 'cow', 'pig'] },
      { ...cat, options: ['cat', 'dog', 'cow', 'pig'] },
    ],
  };

  it('wags a finger at a wrong pick, cheers the right one and finishes', async () => {
    renderAt('/child/play/week/CHOICE', { 'GET /me/games/week/CHOICE': game });
    for (let i = 0; i < 2; i++) {
      // the same element shows the next word later, so keep its text now
      const shown = (await screen.findByText(/^(koer|kass)$/)).textContent!;
      const right = shown === 'koer' ? 'dog' : 'cat';
      const options = within(screen.getByRole('group', { name: 'Vali õige tõlge' }));
      await userEvent.click(options.getByRole('button', { name: 'cow' }));
      expect(options.getByRole('button', { name: 'cow' })).toBeDisabled();
      expect(screen.getByTestId('reaction')).toHaveAttribute('data-kind', 'finger');
      await userEvent.click(options.getByRole('button', { name: right }));
      expect(screen.getByTestId('reaction').dataset.kind).toMatch(/thumb|wink/);
      expect(screen.getByRole('img', { name: `Tehtud: ${i + 1} / 2` })).toBeInTheDocument();
      if (i === 0) await waitFor(() => expect(screen.queryByText(shown)).not.toBeInTheDocument(), { timeout: 2000 });
    }
    expect(await screen.findByRole('heading', { name: 'Hästi mängitud!' }, { timeout: 2000 })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Mängi uuesti/ })).toBeInTheDocument();
  });
});

describe('scrambled letters', () => {
  const task = (answer: string) => ({
    'GET /me/games/tasks/4': { mode: 'SCRAMBLE', taskId: 4, grid: null, words: [{ ...dog, answer }] },
    'POST /me/games/tasks/4/played': { status: 204 },
  });

  it('checks the word only when every box is filled, and lets letters be taken back', async () => {
    const { api } = renderAt('/child/play/task/4', task('Dog'));
    const tiles = within(await screen.findByRole('group', { name: 'Pane tähtedest sõna kokku' }));
    const boxes = within(screen.getByRole('group', { name: 'Sinu sõna' }));

    await userEvent.click(tiles.getByRole('button', { name: 'o' }));
    await userEvent.click(tiles.getByRole('button', { name: 'd' }));
    expect(screen.queryByTestId('reaction')).not.toBeInTheDocument();
    // a wrong start is fine while the word is not complete: take the letters back
    await userEvent.click(boxes.getByRole('button', { name: 'Võta tagasi o' }));
    await userEvent.click(boxes.getByRole('button', { name: 'Võta tagasi d' }));
    expect(tiles.getByRole('button', { name: 'o' })).toBeEnabled();

    for (const letter of ['d', 'o', 'g']) {
      await userEvent.click(tiles.getByRole('button', { name: letter }));
    }
    expect(screen.getByTestId('reaction').dataset.kind).toMatch(/thumb|wink/);
    expect(boxes.getByRole('button', { name: 'Võta tagasi D' })).toBeDisabled();
    expect(await screen.findByRole('heading', { name: 'Hästi mängitud!' }, { timeout: 2000 })).toBeInTheDocument();
    await waitFor(() => expect(api.called('POST', '/me/games/tasks/4/played')).toHaveLength(1));
  });

  it('says a wrong word is wrong and gives the letters back', async () => {
    renderAt('/child/play/task/4', task('dog'));
    const tiles = within(await screen.findByRole('group', { name: 'Pane tähtedest sõna kokku' }));
    for (const letter of ['g', 'o', 'd']) {
      await userEvent.click(tiles.getByRole('button', { name: letter }));
    }
    expect(screen.getByTestId('reaction')).toHaveAttribute('data-kind', 'finger');
    expect(screen.getByRole('group', { name: 'Sinu sõna' })).toHaveClass('is-wrong');
    await waitFor(() => expect(tiles.getByRole('button', { name: 'g' })).toBeEnabled(), {
      timeout: WRONG_SCRAMBLE_MS + 1000,
    });
    expect(within(screen.getByRole('group', { name: 'Sinu sõna' })).queryAllByRole('button')).toHaveLength(0);
  });
});

describe('word search', () => {
  // dog in the first row, cat down the last column
  const rows = ['dogxyc', 'zzzzza', 'qqqqqt', 'wwwwww', 'eeeeee', 'rrrrrr'];
  const game: Game = { mode: 'WORD_SEARCH', taskId: null, grid: { size: 6, rows }, words: [dog, cat] };

  it('finds a word letter by letter, or by its first and last letter, backwards too', async () => {
    const { container } = renderAt('/child/play/week/WORD_SEARCH', { 'GET /me/games/week/WORD_SEARCH': game });
    await screen.findByRole('group', { name: 'Tähtede ruudustik' });
    const cell = (n: number) => container.querySelector<HTMLButtonElement>(`[data-cell="${n}"]`)!;
    expect(cell(0)).toHaveTextContent('D');

    await userEvent.click(cell(0));
    await userEvent.click(cell(1));
    expect(cell(0)).toHaveAttribute('aria-pressed', 'true');
    expect(cell(1)).toHaveClass('is-selected');
    expect(screen.queryByTestId('reaction')).not.toBeInTheDocument();
    await userEvent.click(cell(2));
    expect(cell(1)).toHaveClass('is-found');
    expect(screen.getByTestId('reaction').dataset.kind).toMatch(/thumb|wink/);
    expect(screen.getByText('dog')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Tehtud: 1 / 2' })).toBeInTheDocument();

    await userEvent.click(cell(17));
    await userEvent.click(cell(5));
    expect(cell(11)).toHaveClass('is-found');
    expect(screen.getByText('cat')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Hästi mängitud!' }, { timeout: 2000 })).toBeInTheDocument();
  });

  it('gives hints for a chosen word: its length, its first letter, then the whole word', async () => {
    const { container } = renderAt('/child/play/week/WORD_SEARCH', { 'GET /me/games/week/WORD_SEARCH': game });
    await screen.findByRole('group', { name: 'Tähtede ruudustik' });
    const cell = (n: number) => container.querySelector<HTMLButtonElement>(`[data-cell="${n}"]`)!;
    const hint = screen.getByRole('button', { name: /Vihje/ });

    await userEvent.click(hint);
    expect(hint).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('Nüüd puuduta sõna, mille kohta vihjet vajad')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'kass' }));
    expect(hint).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByText(/3 tähte/)).toBeInTheDocument();
    expect(container.querySelectorAll('.is-hinted')).toHaveLength(0);

    await userEvent.click(hint);
    await userEvent.click(screen.getByRole('button', { name: 'kass' }));
    expect([...container.querySelectorAll('.is-hinted')]).toEqual([cell(5)]);

    await userEvent.click(hint);
    await userEvent.click(screen.getByRole('button', { name: 'kass' }));
    expect([...container.querySelectorAll('.is-hinted')]).toEqual([cell(5), cell(11), cell(17)]);

    // no more hints for that word
    await userEvent.click(hint);
    expect(screen.queryByRole('button', { name: 'kass' })).not.toBeInTheDocument();
    await userEvent.click(hint);

    await userEvent.click(cell(5));
    await userEvent.click(cell(17));
    expect(container.querySelectorAll('.is-hinted')).toHaveLength(0);
    expect(screen.getByText('cat')).toBeInTheDocument();
  });

  it('calls a selection wrong only after a pause, then starts over', async () => {
    const { container } = renderAt('/child/play/week/WORD_SEARCH', { 'GET /me/games/week/WORD_SEARCH': game });
    await screen.findByRole('group', { name: 'Tähtede ruudustik' });
    const cell = (n: number) => container.querySelector<HTMLButtonElement>(`[data-cell="${n}"]`)!;
    await userEvent.click(cell(6));
    await userEvent.click(cell(7));
    expect(cell(7)).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByTestId('reaction')).not.toBeInTheDocument();

    await waitFor(() => expect(screen.getByTestId('reaction')).toHaveAttribute('data-kind', 'finger'), {
      timeout: WRONG_WORD_MS + 1000,
    });
    expect(cell(6)).toHaveAttribute('aria-pressed', 'false');
    expect(cell(7)).toHaveAttribute('aria-pressed', 'false');
  });
});

describe('word search selection', () => {
  it('locates a hidden word, also written backwards', () => {
    const letters = Array.from('dogxyc' + 'zzzzza' + 'qqqqqt' + 'wwwwww' + 'eeeeee' + 'rrrrrr');
    expect(findInGrid(letters, 6, 'dog')).toEqual([0, 1, 2]);
    expect(findInGrid(letters, 6, 'cat')).toEqual([5, 11, 17]);
    expect(findInGrid(letters, 6, 'tac')).toEqual([17, 11, 5]);
    expect(findInGrid(letters, 6, 'cow')).toBeNull();
  });


  it('grows along a straight line, fills gaps, takes back and restarts', () => {
    expect(extendSelection([], 7, 6)).toEqual([7]);
    expect(extendSelection([0], 1, 6)).toEqual([0, 1]);
    expect(extendSelection([0], 3, 6)).toEqual([0, 1, 2, 3]);
    expect(extendSelection([0, 1], 3, 6)).toEqual([0, 1, 2, 3]);
    expect(extendSelection([0, 1, 2], 2, 6)).toEqual([0, 1]);
    expect(extendSelection([0, 1, 2], 1, 6)).toEqual([0, 1, 2]);
    expect(extendSelection([0, 1], 6, 6)).toEqual([6]);
    expect(extendSelection([0], 8, 6)).toEqual([8]);
    expect(extendSelection([0, 7], 21, 6)).toEqual([0, 7, 14, 21]);
  });
});

describe('game errors', () => {
  it('sends an unknown mode home and offers a retry when loading fails', async () => {
    renderAt('/child/play/week/TYPING', {});
    expect(await screen.findByRole('heading', { name: /Mari/ })).toBeInTheDocument();
  });
});
