import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { resetHttpState } from '../../api/http';
import type { AnswerResponse, CurrentItem, TrainingSession } from '../../api/types';
import { App } from '../../App';
import { childTokens, mockApi, type Call } from '../../test/mockApi';

const child = { id: 7, name: 'Mari', nativeLang: 'et', targetLang: 'en' };
const activeWeek = {
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
const home = { child, week: activeWeek, month: null };

function item(overrides: Partial<CurrentItem> = {}): CurrentItem {
  return {
    wordId: 1,
    round: 1,
    position: 1,
    prompt: 'koer',
    hintMask: null,
    hintAvailable: true,
    roundSize: 2,
    remainingInRound: 2,
    ...overrides,
  };
}

function session(overrides: Partial<TrainingSession> = {}): TrainingSession {
  return {
    id: 33,
    weekId: 5,
    kind: 'FIRST_PASS',
    status: 'IN_PROGRESS',
    round: 1,
    current: item(),
    points: 0,
    maxPoints: 4,
    counts: true,
    words: [],
    ...overrides,
  };
}

function answer(overrides: Partial<AnswerResponse>): AnswerResponse {
  return {
    verdict: 'CORRECT',
    correctAnswer: null,
    showCorrectAnswerFor: 0,
    becameProblematic: false,
    roundFinished: false,
    sessionFinished: false,
    next: null,
    points: 0,
    ...overrides,
  };
}

const cat = item({ wordId: 2, position: 2, prompt: 'kass', remainingInRound: 1 });

function renderTraining(routes: Record<string, unknown>) {
  const api = mockApi({
    'POST /auth/refresh': childTokens(7),
    'GET /me/home': home,
    'POST /weeks/5/sessions': session(),
    ...(routes as Record<string, object>),
  });
  window.history.pushState({}, '', '/child/train');
  render(<App initialLang="en" />);
  return api;
}

async function type(text: string) {
  const input = await screen.findByLabelText('Sinu vastus');
  await userEvent.type(input, text);
  await userEvent.click(screen.getByRole('button', { name: 'Kontrolli' }));
}

describe('training screen', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    resetHttpState();
  });

  it('praises a correct answer and moves on to the next word', async () => {
    const api = renderTraining({
      'POST /sessions/33/answers': answer({ verdict: 'CORRECT', next: cat, points: 2 }),
    });

    expect(await screen.findByText('koer')).toBeInTheDocument();
    await type('dog');

    const praise = await screen.findByRole('button', { name: /Õige|Väga hea|Tubli/ });
    expect(screen.getByLabelText('Sinu vastus')).toHaveClass('is-correct');
    await userEvent.click(praise);

    expect(await screen.findByText('kass')).toBeInTheDocument();
    expect(api.called('POST', '/sessions/33/answers')[0].body).toEqual({ weekWordId: 1, round: 1, answer: 'dog' });
  });

  it('shows the right spelling after a mistake, then the next word', async () => {
    renderTraining({
      'POST /sessions/33/answers': answer({ verdict: 'WRONG', correctAnswer: 'dog', next: cat, showCorrectAnswerFor: 1 }),
    });

    await type('dgo');

    expect(await screen.findByText('Õigesti on nii:')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'dog' })).toBeInTheDocument();
    expect(screen.getByText('dgo').tagName).toBe('S');
    expect(screen.queryByText('kass')).toBeNull(); // the right spelling stays for the configured time
    expect(await screen.findByText('kass', {}, { timeout: 2500 })).toBeInTheDocument();
  });

  it('does not send a blank answer', async () => {
    const api = renderTraining({});
    await userEvent.type(await screen.findByLabelText('Sinu vastus'), '   ');
    expect(screen.getByRole('button', { name: 'Kontrolli' })).toBeDisabled();
    await userEvent.keyboard('{Enter}');
    expect(api.called('POST', '/sessions/33/answers')).toHaveLength(0);
  });

  it('keeps the length secret until the first hint, then reveals letters', async () => {
    const masks = [
      { hintMask: '___', hintAvailable: true },
      { hintMask: '_o_', hintAvailable: true },
      { hintMask: '_og', hintAvailable: false },
    ];
    const api = renderTraining({ 'POST /sessions/33/hint': () => ({ body: masks.shift() }) });

    await screen.findByText('koer');
    expect(screen.queryByRole('img', { name: /täh/ })).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: /Vihje/ }));
    expect(await screen.findByRole('img', { name: '3 tähte' })).toBeInTheDocument();
    expect(screen.queryByText('o')).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: /Vihje/ }));
    expect(await screen.findByText('o')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Vihje/ }));
    expect(await screen.findByText('g')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Vihje/ })).toBeDisabled();
    expect(api.called('POST', '/sessions/33/hint')).toHaveLength(3);
    expect(api.called('POST', '/sessions/33/hint')[0].body).toEqual({ weekWordId: 1, round: 1 });
  });

  it('announces a new round for the words still to learn', async () => {
    renderTraining({
      'POST /weeks/5/sessions': session({ current: cat }),
      'POST /sessions/33/answers': answer({
        verdict: 'CORRECT',
        roundFinished: true,
        next: item({ round: 2, roundSize: 1, remainingInRound: 1 }),
      }),
    });

    await type('cat');
    await userEvent.click(await screen.findByRole('button', { name: /Õige|Väga hea|Tubli/ }));

    expect(await screen.findByText('1. voor tehtud!')).toBeInTheDocument();
    expect(screen.getByText('Jäi veel 1 sõna. Proovime veel kord!')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Lähme' }));
    expect(await screen.findByText('2. voor')).toBeInTheDocument();
    expect(screen.getByText('koer')).toBeInTheDocument();
  });

  it('shows a summary when the last word is done', async () => {
    const homeCalls: Call[] = [];
    const api = renderTraining({
      'GET /me/home': (c: Call) => {
        homeCalls.push(c);
        return { body: home };
      },
      'POST /weeks/5/sessions': session({ current: cat }),
      'POST /sessions/33/answers': answer({ verdict: 'CORRECT', roundFinished: true, sessionFinished: true, points: 3 }),
      'GET /sessions/33': session({
        status: 'COMPLETED',
        current: null,
        points: 3,
        words: [
          { weekWordId: 1, solvedRound: 1, hintUsed: false, problematic: false, points: 2 },
          { weekWordId: 2, solvedRound: 2, hintUsed: false, problematic: false, points: 1 },
        ],
      }),
    });

    await type('cat');
    await userEvent.click(await screen.findByRole('button', { name: /Õige|Väga hea|Tubli/ }));

    expect(await screen.findByText('Kõik sõnad on tehtud!')).toBeInTheDocument();
    expect(screen.getByText('Esimesel korral õigesti: 1 / 2.')).toBeInTheDocument();
    expect(screen.getByText('3 / 4')).toBeInTheDocument();
    expect(screen.getByText('Need tähed lähevad kuu arvestusse.')).toBeInTheDocument();
    await waitFor(() => expect(homeCalls.length).toBeGreaterThan(1)); // the home screen gets fresh points
    expect(api.called('GET', '/sessions/33')).toHaveLength(1);
  });

  it('tells the child when the week ended during training', async () => {
    renderTraining({
      'POST /sessions/33/answers': { status: 409, body: { code: 'SESSION_CLOSED', detail: 'closed' } },
    });

    await type('dog');

    expect(await screen.findByText('Nädal on läbi')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Avalehele' })).toHaveAttribute('href', '/child');
  });

  it('keeps the answer and asks to retry after a network failure', async () => {
    let attempts = 0;
    renderTraining({
      'POST /sessions/33/answers': () => {
        attempts += 1;
        if (attempts === 1) throw new TypeError('Failed to fetch');
        return { body: answer({ verdict: 'CORRECT', next: cat }) };
      },
    });

    await type('dog');
    expect(await screen.findByText('Vajuta uuesti „Kontrolli“.')).toBeInTheDocument();
    expect(screen.getByLabelText('Sinu vastus')).toHaveValue('dog');

    await userEvent.click(screen.getByRole('button', { name: 'Kontrolli' }));
    expect(await screen.findByRole('button', { name: /Õige|Väga hea|Tubli/ })).toBeInTheDocument();
  });
});

describe('child home', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    resetHttpState();
  });

  it('offers practice after the week is completed and shows the month goal', async () => {
    mockApi({
      'POST /auth/refresh': childTokens(7),
      'GET /me/home': {
        child,
        week: { ...activeWeek, status: 'COMPLETED', points: 3 },
        month: { id: 1, seqNo: 1, status: 'ACTIVE', weeksClosed: 1, points: 3, maxPoints: 4, reward: 'loomaaed' },
      },
    });
    window.history.pushState({}, '', '/child');
    render(<App initialLang="en" />);

    expect(await screen.findByText('See nädal on tehtud. Tubli!')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Harjuta veel' })).toHaveAttribute('href', '/child/train');
    expect(screen.getByText('Eesmärk: loomaaed')).toBeInTheDocument();
    expect(screen.getByText('Tehtud nädalaid: 1 / 4')).toBeInTheDocument();
  });
});
