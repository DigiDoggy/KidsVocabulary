import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { resetHttpState } from '../../api/http';
import type { GameTask } from '../../api/types';
import { App } from '../../App';
import { account, mockApi, parentTokens } from '../../test/mockApi';

const child = {
  id: 7,
  name: 'Mari',
  birthDate: '2018-05-04',
  level: 'A1',
  nativeLang: 'et',
  targetLang: 'en',
  defaultWeekSize: 3,
};

const signedIn = {
  'POST /auth/refresh': parentTokens,
  'GET /account/me': account,
  'GET /children/7': child,
  'GET /dictionary/topics': ['animals', 'food'],
};

function task(overrides: Partial<GameTask> = {}): GameTask {
  return {
    id: 9,
    childId: 7,
    mode: 'WORD_SEARCH',
    status: 'DRAFT',
    timesPlayed: 0,
    minWords: 3,
    maxWords: 8,
    words: [
      { id: 1, conceptId: 11, prompt: 'koer', answer: 'dog' },
      { id: 2, conceptId: 12, prompt: 'kass', answer: 'cat' },
    ],
    ...overrides,
  };
}

function renderAt(path: string) {
  window.history.pushState({}, '', path);
  return render(<App initialLang="en" />);
}

afterEach(() => {
  vi.restoreAllMocks();
  resetHttpState();
});

describe('game tasks (parent)', () => {
  it('lists the tasks on the child page and creates a new one', async () => {
    const api = mockApi({
      ...signedIn,
      'GET /children/7/home': { child, week: null, month: null },
      'GET /children/7/weeks': [],
      'GET /children/7/months': [],
      'GET /children/7/game-tasks': [
        { id: 3, mode: 'CHOICE', status: 'GIVEN', wordCount: 5, timesPlayed: 2, createdAt: '2026-11-20T10:00:00Z', givenAt: '2026-11-20T10:05:00Z' },
      ],
      'POST /children/7/game-tasks': { status: 201, body: task({ words: [] }) },
      'GET /game-tasks/9': task({ words: [] }),
    });
    renderAt('/children/7');

    const card = (await screen.findByRole('heading', { name: 'Game tasks' })).closest('section')!;
    expect(await within(card).findByRole('link', { name: 'Choose from 4 · 5 words' })).toHaveAttribute('href', '/game-tasks/3');
    expect(within(card).getByText(/played 2 times/)).toBeInTheDocument();
    expect(within(card).getByText('Given')).toBeInTheDocument();

    await userEvent.selectOptions(within(card).getByLabelText('Game'), 'WORD_SEARCH');
    await userEvent.click(within(card).getByRole('button', { name: 'Create a task' }));
    expect(api.called('POST', '/children/7/game-tasks')[0].body).toEqual({ mode: 'WORD_SEARCH' });
    expect(await screen.findByRole('heading', { name: /Game: Word search/ })).toBeInTheDocument();
    expect(screen.getByText('No words yet. Add words yourself or let them be suggested.')).toBeInTheDocument();
  });

  it('collects words, suggests more and gives the task to the child', async () => {
    const three = task({ words: [...task().words, { id: 3, conceptId: 13, prompt: 'lehm', answer: 'cow' }] });
    const api = mockApi({
      ...signedIn,
      'GET /game-tasks/9': task(),
      'POST /game-tasks/9/words': three,
      'DELETE /game-tasks/9/words/3': task(),
      'POST /game-tasks/9/suggest': three,
      'POST /game-tasks/9/give': task({ ...three, status: 'GIVEN' }),
    });
    renderAt('/game-tasks/9');

    expect(await screen.findByText('Up to 10 letters, so that the word fits into the grid.')).toBeInTheDocument();
    expect(screen.getByText('1 more word needed')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Give to Mari' })).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Word (Estonian)'), 'lehm');
    await userEvent.type(screen.getByLabelText(/Translation \(English\)/), 'cow');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    expect(api.called('POST', '/game-tasks/9/words')[0].body).toEqual({ source: 'lehm', target: 'cow' });
    expect(await screen.findByText('cow')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Remove lehm' }));
    await waitFor(() => expect(screen.queryByText('cow')).not.toBeInTheDocument());

    await userEvent.selectOptions(screen.getByLabelText('Topic'), 'animals');
    const count = screen.getByLabelText('How many words');
    await userEvent.clear(count);
    await userEvent.type(count, '1');
    await userEvent.click(screen.getByRole('button', { name: 'Add 1 word' }));
    expect(api.called('POST', '/game-tasks/9/suggest')[0].body).toEqual({ topic: 'animals', count: 1 });
    expect(await screen.findByText('cow')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Give to Mari' }));
    expect(await screen.findByText(/in the child's game menu/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument();
  });

  it('deletes a task after asking', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const api = mockApi({
      ...signedIn,
      'GET /game-tasks/9': task({ status: 'GIVEN', timesPlayed: 1 }),
      'DELETE /game-tasks/9': { status: 204 },
      'GET /children/7/home': { child, week: null, month: null },
      'GET /children/7/weeks': [],
      'GET /children/7/months': [],
      'GET /children/7/game-tasks': [],
    });
    renderAt('/game-tasks/9');
    expect(await screen.findByText(/Played 1 time so far/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Delete task' }));
    expect(api.called('DELETE', '/game-tasks/9')).toHaveLength(1);
    expect(await screen.findByText('No game tasks yet.')).toBeInTheDocument();
  });
});
