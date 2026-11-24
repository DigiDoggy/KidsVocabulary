import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { resetHttpState } from '../api/http';
import { App } from '../App';
import { weekStarts } from '../lib/dates';
import { account, childTokens, mockApi, parentTokens } from '../test/mockApi';

// jsdom has no layout engine, so colour contrast is covered by contrast.test.ts instead.
const OPTIONS: axe.RunOptions = { rules: { 'color-contrast': { enabled: false } } };

async function expectNoViolations() {
  const result = await axe.run(document.body, OPTIONS);
  const problems = result.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
  expect(problems).toEqual([]);
}

const child = { id: 7, name: 'Mari', birthDate: '2018-05-04', level: 'A1', nativeLang: 'et', targetLang: 'en', defaultWeekSize: 2 };
const { thisMonday } = weekStarts(account.timezone);
const words = [
  { id: 1, conceptId: 11, source: 'CARRY_OVER', prompt: 'koer', answer: 'dog', extraAnswers: [], points: null, outcome: null },
  { id: 2, conceptId: 12, source: 'MANUAL', prompt: 'värv', answer: 'colour', extraAnswers: ['color'], points: null, outcome: null },
];
const parent = {
  'POST /auth/refresh': parentTokens,
  'GET /account/me': account,
  'GET /children': [child],
  'GET /children/7': child,
  'GET /children/7/home': {
    child,
    week: { id: 5, status: 'ACTIVE', startDate: thisMonday, endDate: thisMonday, weekIndex: 1, wordCount: 2, points: 1, maxPoints: 4, firstPassOpen: true },
    month: { id: 9, seqNo: 1, status: 'ACTIVE', weeksClosed: 0, points: 0, maxPoints: 0, reward: 'Zoo' },
  },
  'GET /children/7/weeks': [],
  'GET /children/7/months': [],
  'GET /children/7/game-tasks': [
    { id: 4, mode: 'CHOICE', status: 'GIVEN', wordCount: 3, timesPlayed: 1, createdAt: '2026-11-20T10:00:00Z', givenAt: '2026-11-20T10:00:00Z' },
  ],
  'GET /game-tasks/4': {
    id: 4, childId: 7, mode: 'WORD_SEARCH', status: 'DRAFT', timesPlayed: 0, minWords: 3, maxWords: 8,
    words: [{ id: 1, conceptId: 11, prompt: 'koer', answer: 'dog' }],
  },
  'GET /dictionary/topics': ['animals'],
  'GET /weeks/6': { id: 6, childId: 7, startDate: thisMonday, endDate: thisMonday, status: 'DRAFT', weekIndex: null, wordCount: 3, missing: 1, points: null, maxPoints: null, words },
  'GET /months/9': { id: 9, seqNo: 1, status: 'COMPLETED', reward: 'Zoo', points: 3, maxPoints: 4, percent: 75, result: 'GOOD', weeks: [] },
  'GET /months/9/report': {
    monthId: 9, seqNo: 1, totalPoints: 3, maxPoints: 4, percent: 75, result: 'GOOD', reward: null,
    weeks: [{ index: 1, startDate: thisMonday, points: 3, maxPoints: 4, percent: 75, learned: 1, hard: 1, unlearned: 0 }],
    hardWords: [{ conceptId: 11, prompt: 'koer', answer: 'dog' }], unlearnedWords: [],
  },
};

function renderAt(path: string) {
  window.history.pushState({}, '', path);
  render(<App initialLang="en" />);
}

describe('accessibility (axe)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    resetHttpState();
  });

  it('login page', async () => {
    mockApi({ 'POST /auth/refresh': { status: 401, body: {} } });
    renderAt('/login');
    await screen.findByLabelText('E-mail');
    await expectNoViolations();
  });

  it('registration page with validation messages', async () => {
    mockApi({ 'POST /auth/refresh': { status: 401, body: {} } });
    renderAt('/register');
    await userEvent.click(await screen.findByRole('button', { name: 'Create account' }));
    await screen.findByText('The PIN must be 4 to 6 digits.');
    await expectNoViolations();
  });

  it.each([
    ['children list', '/', 'Mari'],
    ['child page', '/children/7', 'Weeks finished: 0 of 4'],
    ['child form', '/children/7/edit', 'Edit profile'],
    ['week builder', '/weeks/6', 'koer'],
    ['game task', '/game-tasks/4', 'koer'],
    ['month report', '/months/9', 'Month report'],
    ['profile picker', '/pick', 'Who is training?'],
  ])('%s', async (_name, path, text) => {
    mockApi(parent);
    renderAt(path);
    await screen.findByText(text);
    await expectNoViolations();
  });

  describe('child mode', () => {
    const childApi = {
      'POST /auth/refresh': childTokens(7),
      'GET /me/home': { child, week: { id: 5, status: 'ACTIVE', startDate: thisMonday, endDate: thisMonday, weekIndex: 1, wordCount: 2, points: 0, maxPoints: 4, firstPassOpen: false }, month: null },
      'POST /weeks/5/sessions': {
        id: 33, weekId: 5, kind: 'FIRST_PASS', status: 'IN_PROGRESS', round: 1, points: 0, maxPoints: 4, counts: true, words: [],
        current: { wordId: 1, round: 1, position: 1, prompt: 'koer', hintMask: '_o_', hintAvailable: true, roundSize: 2, remainingInRound: 2 },
      },
      'GET /me/games': { week: { id: 5, wordCount: 2 }, tasks: [{ id: 4, mode: 'SCRAMBLE', wordCount: 3 }] },
      'GET /me/games/week/CHOICE': {
        mode: 'CHOICE', taskId: null, grid: null,
        words: [{ id: 1, prompt: 'koer', answer: 'dog', options: ['dog', 'cat', 'cow', 'pig'] }],
      },
      'GET /me/games/tasks/4': {
        mode: 'SCRAMBLE', taskId: 4, grid: null, words: [{ id: 1, prompt: 'koer', answer: 'dog', options: null }],
      },
      'GET /me/games/week/WORD_SEARCH': {
        mode: 'WORD_SEARCH', taskId: null, words: [{ id: 1, prompt: 'koer', answer: 'dog', options: null }],
        grid: { size: 6, rows: ['dogabc', 'aaaaaa', 'bbbbbb', 'cccccc', 'dddddd', 'eeeeee'] },
      },
      'POST /sessions/33/answers': { verdict: 'WRONG', correctAnswer: 'dog', showCorrectAnswerFor: 5, becameProblematic: false, roundFinished: false, sessionFinished: false, next: null, points: 0 },
    };

    it('home with the PIN dialog open', async () => {
      mockApi(childApi);
      renderAt('/child');
      await userEvent.click(await screen.findByRole('button', { name: /Lapsevanem/ }));
      await expectNoViolations();
    });

    it('home with the game menu', async () => {
      mockApi(childApi);
      renderAt('/child');
      await screen.findByRole('heading', { name: /Mängi sõnadega/ });
      await expectNoViolations();
    });

    it.each([
      ['choose from 4', '/child/play/week/CHOICE', 'koer'],
      ['scrambled letters', '/child/play/task/4', 'koer'],
      ['word search', '/child/play/week/WORD_SEARCH', 'koer'],
    ])('%s', async (_name, path, text) => {
      mockApi(childApi);
      renderAt(path);
      await screen.findByText(text);
      await expectNoViolations();
    });

    it('training, also while showing the right spelling', async () => {
      mockApi(childApi);
      renderAt('/child/train');
      await userEvent.type(await screen.findByLabelText('Sinu vastus'), 'dgo');
      await expectNoViolations();
      await userEvent.click(screen.getByRole('button', { name: 'Kontrolli' }));
      await screen.findByText('Õigesti on nii:');
      await expectNoViolations();
    });
  });
});
