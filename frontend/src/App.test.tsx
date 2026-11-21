import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { Week } from './api/types';
import { resetHttpState } from './api/http';
import { App } from './App';
import { weekStarts } from './lib/dates';
import { account, childTokens, mockApi, parentTokens } from './test/mockApi';

const child = {
  id: 7,
  name: 'Mari',
  birthDate: '2018-05-04',
  level: 'A1',
  nativeLang: 'et',
  targetLang: 'en',
  defaultWeekSize: 3,
};

function renderAt(path: string) {
  window.history.pushState({}, '', path);
  return render(<App initialLang="en" />);
}

function draft(overrides: Partial<Week> = {}): Week {
  const { thisMonday } = weekStarts(account.timezone);
  return {
    id: 5,
    childId: 7,
    startDate: thisMonday,
    endDate: thisMonday,
    status: 'DRAFT',
    weekIndex: null,
    wordCount: 3,
    missing: 1,
    points: null,
    maxPoints: null,
    words: [
      { id: 1, conceptId: 11, source: 'CARRY_OVER', prompt: 'koer', answer: 'dog', extraAnswers: [], points: null, outcome: null },
      { id: 2, conceptId: 12, source: 'MANUAL', prompt: 'värv', answer: 'colour', extraAnswers: ['color'], points: null, outcome: null },
    ],
    ...overrides,
  };
}

const signedInParent = {
  'POST /auth/refresh': parentTokens,
  'GET /account/me': account,
  'GET /children/7': child,
  'GET /dictionary/topics': ['animals', 'food'],
};

describe('App', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    resetHttpState();
  });

  it('sends an anonymous visitor to the login page and signs in', async () => {
    const api = mockApi({
      'POST /auth/refresh': { status: 401, body: { code: 'INVALID_REFRESH_TOKEN' } },
      'POST /auth/login': parentTokens,
      'GET /account/me': account,
      'GET /children': [child],
    });
    renderAt('/');

    await userEvent.type(await screen.findByLabelText('E-mail'), 'parent@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'secret-password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Mari')).toBeInTheDocument();
    expect(api.called('POST', '/auth/login')[0].body).toEqual({ email: 'parent@example.com', password: 'secret-password' });
    expect(api.called('GET', '/children')[0].auth).toBe('Bearer parent-token');
  });

  it('shows a localised message for wrong credentials', async () => {
    mockApi({
      'POST /auth/refresh': { status: 401, body: {} },
      'POST /auth/login': { status: 401, body: { code: 'INVALID_CREDENTIALS', detail: 'Wrong e-mail or password' } },
    });
    renderAt('/login');

    await userEvent.type(await screen.findByLabelText('E-mail'), 'a@b.c');
    await userEvent.type(screen.getByLabelText('Password'), 'nope-nope');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong e-mail or password.');
  });

  it('checks the PIN repeat before registering', async () => {
    const api = mockApi({ 'POST /auth/refresh': { status: 401, body: {} } });
    renderAt('/register');

    await userEvent.type(await screen.findByLabelText('E-mail'), 'a@b.c');
    await userEvent.type(screen.getByLabelText('Password'), 'long-enough');
    await userEvent.type(screen.getByLabelText('Parent PIN'), '1234');
    await userEvent.type(screen.getByLabelText('Repeat the PIN'), '1235');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('The PINs do not match.')).toBeInTheDocument();
    expect(api.called('POST', '/auth/register')).toHaveLength(0);
  });

  it('creates a child profile', async () => {
    const api = mockApi({
      ...signedInParent,
      'POST /children': (c) => ({ status: 201, body: { ...(c.body as object), id: 7 } }),
      'GET /children/7/home': { child, week: null, month: null },
      'GET /children/7/weeks': [],
      'GET /children/7/months': [],
    });
    renderAt('/children/new');

    await userEvent.type(await screen.findByLabelText('Name'), 'Mari');
    await userEvent.click(screen.getByLabelText(/A1/));
    await userEvent.selectOptions(screen.getByLabelText('Native language'), 'et');
    expect(screen.getByRole('option', { name: 'German' })).toBeInTheDocument();
    const size = screen.getByLabelText(/Words per week/);
    await userEvent.clear(size);
    expect(size).toHaveValue(null);
    await userEvent.type(size, '8');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('heading', { name: 'Mari' })).toBeInTheDocument();
    expect(api.called('POST', '/children')[0].body).toEqual({
      name: 'Mari',
      birthDate: null,
      level: 'A1',
      nativeLang: 'et',
      targetLang: 'en',
      defaultWeekSize: 8,
    });
  });

  it('prepares a draft for next week from the child page', async () => {
    const { nextMonday } = weekStarts(account.timezone);
    const api = mockApi({
      ...signedInParent,
      'GET /children/7/home': { child, week: null, month: null },
      'GET /children/7/weeks': [],
      'GET /children/7/months': [],
      'POST /children/7/weeks': { status: 201, body: draft({ startDate: nextMonday }) },
      'GET /weeks/5': draft({ startDate: nextMonday }),
    });
    renderAt('/children/7');

    await userEvent.click(await screen.findByRole('button', { name: /Prepare from/ }));

    expect(await screen.findByText('koer')).toBeInTheDocument();
    expect(api.called('POST', '/children/7/weeks')[0].body).toEqual({ startDate: nextMonday });
    expect(screen.getByRole('button', { name: /Schedule from/ })).toBeDisabled();
  });

  describe('week builder', () => {
    it('locks carried-over words and blocks launch until the set is full', async () => {
      mockApi({ ...signedInParent, 'GET /weeks/5': draft() });
      renderAt('/weeks/5');

      const carried = (await screen.findByText('koer')).closest('li')!;
      expect(within(carried).queryByRole('button', { name: /Remove/ })).toBeNull();
      expect(within(carried).getByText('from last month, cannot be removed')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Remove värv' })).toBeEnabled();
      expect(screen.getByText('also: color')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Start the week now' })).toBeDisabled();
      expect(screen.getByText('1 more word needed')).toBeInTheDocument();
    });

    it('adds a word with extra spellings and then allows launching', async () => {
      const full = draft({ missing: 0 });
      full.words = [
        ...full.words,
        { id: 3, conceptId: 13, source: 'MANUAL', prompt: 'hall', answer: 'grey', extraAnswers: ['gray'], points: null, outcome: null },
      ];
      const api = mockApi({
        ...signedInParent,
        'GET /weeks/5': draft(),
        'POST /weeks/5/words': full,
        'POST /weeks/5/launch': { ...full, status: 'ACTIVE', weekIndex: 1 },
        'GET /weeks/5/stats': [],
      });
      renderAt('/weeks/5');

      await userEvent.type(await screen.findByLabelText('Word (Estonian)'), 'hall');
      await userEvent.type(screen.getByLabelText('Translation (English)'), 'grey');
      await userEvent.type(screen.getByLabelText('Other accepted spellings'), 'gray, ');
      await userEvent.click(screen.getByRole('button', { name: 'Add' }));

      expect(await screen.findByText('hall')).toBeInTheDocument();
      expect(api.called('POST', '/weeks/5/words')[0].body).toEqual({ source: 'hall', target: 'grey', extraAnswers: ['gray'] });

      await userEvent.click(screen.getByRole('button', { name: 'Start the week now' }));
      expect(await screen.findByText('Running')).toBeInTheDocument();
    });

    it('fills up with the chosen topic', async () => {
      const api = mockApi({ ...signedInParent, 'GET /weeks/5': draft(), 'POST /weeks/5/suggest': draft({ missing: 0 }) });
      renderAt('/weeks/5');

      await screen.findByText('koer');
      await userEvent.selectOptions(await screen.findByLabelText('Topic'), 'animals');
      await userEvent.click(screen.getByRole('button', { name: 'Add 1 word' }));

      await waitFor(() => expect(api.called('POST', '/weeks/5/suggest')).toHaveLength(1));
      expect(api.called('POST', '/weeks/5/suggest')[0].body).toEqual({ topic: 'animals' });
    });

    it('fills up on the parent\'s own topic', async () => {
      const api = mockApi({ ...signedInParent, 'GET /weeks/5': draft(), 'POST /weeks/5/suggest': draft({ missing: 0 }) });
      renderAt('/weeks/5');

      await screen.findByText('koer');
      await userEvent.selectOptions(await screen.findByLabelText('Topic'), 'Own topic…');
      const own = screen.getByLabelText('Your topic');
      await userEvent.type(own, 'space!');
      expect(screen.getByText('Use letters, spaces and hyphens only.')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Add 1 word' })).toBeDisabled();

      await userEvent.clear(own);
      await userEvent.type(own, ' winter sports ');
      await userEvent.click(screen.getByRole('button', { name: 'Add 1 word' }));

      await waitFor(() => expect(api.called('POST', '/weeks/5/suggest')).toHaveLength(1));
      expect(api.called('POST', '/weeks/5/suggest')[0].body).toEqual({ topic: 'winter sports' });
    });

    it('lets the parent clear the number of words before typing a new one', async () => {
      const api = mockApi({
        ...signedInParent,
        'GET /weeks/5': draft(),
        'PUT /weeks/5': draft({ wordCount: 12, missing: 10 }),
      });
      renderAt('/weeks/5');

      const count = await screen.findByLabelText('Number of words');
      await userEvent.clear(count);
      expect(count).toHaveValue(null);
      expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

      await userEvent.type(count, '12');
      expect(count).toHaveValue(12);
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));
      await waitFor(() => expect(api.called('PUT', '/weeks/5')).toHaveLength(1));
      expect(api.called('PUT', '/weeks/5')[0].body).toEqual({ wordCount: 12 });
    });

    it('shows the server error when replacing is impossible', async () => {
      mockApi({
        ...signedInParent,
        'GET /weeks/5': draft(),
        'POST /weeks/5/words/2/replace': { status: 409, body: { code: 'CONFLICT', detail: 'No other words are available' } },
      });
      renderAt('/weeks/5');

      await userEvent.click(await screen.findByRole('button', { name: 'Replace värv' }));

      const alert = await screen.findByRole('alert');
      expect(alert).toHaveTextContent('This action is not possible right now.');
      expect(alert).toHaveTextContent('No other words are available');
    });

    it('warns about a draft for a week that is over', async () => {
      mockApi({ ...signedInParent, 'GET /weeks/5': draft({ startDate: '2020-01-06', missing: 0 }) });
      renderAt('/weeks/5');

      expect(await screen.findByText(/already over/)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Schedule from/ })).toBeDisabled();
    });
  });

  it('shows the monthly report with hard and unlearned words', async () => {
    mockApi({
      ...signedInParent,
      'GET /months/9': { id: 9, seqNo: 1, status: 'COMPLETED', reward: 'Zoo', points: 50, maxPoints: 60, percent: 83.33, result: 'REWARD', weeks: [] },
      'GET /months/9/report': {
        monthId: 9,
        seqNo: 1,
        totalPoints: 50,
        maxPoints: 60,
        percent: 83.33,
        result: 'REWARD',
        reward: 'Zoo',
        weeks: [{ index: 1, startDate: '2026-11-02', points: 14, maxPoints: 16, percent: 87.5, learned: 7, hard: 1, unlearned: 0 }],
        hardWords: [{ conceptId: 1, prompt: 'hobune', answer: 'horse' }],
        unlearnedWords: [],
      },
    });
    renderAt('/months/9');

    expect(await screen.findByText('83.3 %')).toBeInTheDocument();
    expect(screen.getByText('Zoo')).toBeInTheDocument();
    expect(screen.getByText('horse')).toBeInTheDocument();
    expect(screen.getByText('Not learned (0)')).toBeInTheDocument();
  });

  it('lets the parent pick who is training', async () => {
    const api = mockApi({
      ...signedInParent,
      'GET /children': [child],
      'POST /auth/switch-to-child/7': childTokens(7),
      'GET /me/home': { child, week: null, month: null },
    });
    renderAt('/pick');

    await userEvent.click(await screen.findByRole('button', { name: /Mari/ }));

    expect(await screen.findByText('Tere, Mari!')).toBeInTheDocument();
    expect(api.called('POST', '/auth/switch-to-child/7')).toHaveLength(1);
  });

  describe('child mode', () => {
    it('hands over to the child and returns with the PIN', async () => {
      const api = mockApi({
        ...signedInParent,
        'GET /children/7/home': { child, week: null, month: null },
        'GET /children/7/weeks': [],
        'GET /children/7/months': [],
        'POST /auth/switch-to-child/7': childTokens(7),
        'GET /me/home': { child, week: null, month: null },
        'POST /auth/switch-to-parent': (c) =>
          (c.body as { pin: string }).pin === '2468'
            ? { body: parentTokens }
            : { status: 403, body: { code: 'INVALID_PIN', detail: 'Wrong PIN' } },
      });
      renderAt('/children/7');

      await userEvent.click(await screen.findByRole('button', { name: 'Hand over to the child' }));
      // child mode speaks the child's native language (Estonian here)
      expect(await screen.findByText('Tere, Mari!')).toBeInTheDocument();
      expect(document.documentElement.lang).toBe('et');

      await userEvent.click(screen.getByRole('button', { name: /Lapsevanem/ }));
      await userEvent.type(screen.getByLabelText('PIN'), '1111');
      await userEvent.click(screen.getByRole('button', { name: 'Jätka' }));
      expect(await screen.findByText('Vale PIN.')).toBeInTheDocument();

      await userEvent.type(screen.getByLabelText('PIN'), '2468');
      await userEvent.click(screen.getByRole('button', { name: 'Jätka' }));

      expect(await screen.findByRole('heading', { name: 'Mari' })).toBeInTheDocument();
      expect(api.called('POST', '/auth/switch-to-parent')).toHaveLength(2);
    });

    it('keeps a child session out of the parent screens', async () => {
      mockApi({ 'POST /auth/refresh': childTokens(7), 'GET /me/home': { child, week: null, month: null } });
      renderAt('/children/7');

      expect(await screen.findByText('Tere, Mari!')).toBeInTheDocument();
      expect(window.location.pathname).toBe('/child');
    });
  });
});
