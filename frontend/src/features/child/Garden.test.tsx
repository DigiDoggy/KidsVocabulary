import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { resetHttpState } from '../../api/http';
import type { Garden, GardenPlant, TrainingSession } from '../../api/types';
import { App } from '../../App';
import { childTokens, mockApi } from '../../test/mockApi';

const child = { id: 7, name: 'Mari', nativeLang: 'en', targetLang: 'en' };
const home = { child, week: null, month: null };

function plant(overrides: Partial<GardenPlant> = {}): GardenPlant {
  return {
    conceptId: 1,
    kind: 0,
    stage: 'SPROUT',
    due: false,
    thirsty: false,
    prompt: 'koer',
    answer: 'dog',
    emoji: '🐕',
    ...overrides,
  };
}

const garden: Garden = {
  dueCount: 1,
  reviewSize: 10,
  openReviewId: null,
  plantCount: 3,
  beds: [
    {
      month: 1,
      plants: [
        plant(),
        plant({ conceptId: 2, kind: 5, stage: 'FRUIT', answer: 'cat', prompt: 'kass', emoji: null }),
      ],
    },
    { month: 2, plants: [plant({ conceptId: 3, kind: 3, stage: 'SEED', due: true, thirsty: true, answer: 'cow' })] },
  ],
};

const review: TrainingSession = {
  id: 40,
  weekId: null,
  kind: 'REVIEW',
  status: 'IN_PROGRESS',
  round: 1,
  current: {
    wordId: 3,
    round: 1,
    position: 1,
    prompt: 'lehm',
    emoji: null,
    hintMask: null,
    hintAvailable: true,
    roundSize: 1,
    remainingInRound: 1,
  },
  points: 0,
  maxPoints: 2,
  counts: false,
  words: [],
};

function renderAt(path: string, routes: Record<string, unknown>) {
  const api = mockApi({
    'POST /auth/refresh': childTokens(7),
    'GET /me/home': home,
    'GET /me/games': { week: null, tasks: [] },
    'GET /me/garden': garden,
    ...(routes as Record<string, object>),
  });
  window.history.pushState({}, '', path);
  render(<App initialLang="en" />);
  return api;
}

describe('word garden', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    resetHttpState();
  });

  it('shows the plants in beds by month, thirsty ones drooping', async () => {
    renderAt('/child/garden', {});

    expect(await screen.findByRole('heading', { name: /My garden/ })).toBeInTheDocument();
    const month1 = screen.getByRole('region', { name: 'Month 1' });
    expect(within(month1).getByRole('button', { name: 'dog: Sprout' })).toBeInTheDocument();
    expect(within(month1).getByRole('button', { name: 'cat: Fully grown' })).toBeInTheDocument();
    const thirsty = within(screen.getByRole('region', { name: 'Month 2' })).getByRole('button', {
      name: 'cow: Seed, thirsty',
    });
    expect(thirsty.querySelector('svg')).toHaveClass('plant--thirsty');
    expect(screen.getByText('3 words grow here.')).toBeInTheDocument();
    expect(screen.getByText('1 plant wants water.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Water the garden/ })).toHaveAttribute('href', '/child/garden/water');

    const result = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } });
    expect(result.violations.map((v) => v.id)).toEqual([]);
  });

  it('shows the word and its picture when a plant is tapped', async () => {
    renderAt('/child/garden', {});

    await userEvent.click(await screen.findByRole('button', { name: 'dog: Sprout' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText('koer')).toBeInTheDocument();
    expect(within(dialog).getByText('🐕')).toBeInTheDocument();
    expect(within(dialog).getByText('Sprout')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'OK' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('says when every plant has water, and when the garden is still empty', async () => {
    renderAt('/child/garden', { 'GET /me/garden': { ...garden, dueCount: 0 } });
    expect(await screen.findByText(/All plants have water/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Water the garden/ })).not.toBeInTheDocument();
  });

  it('explains an empty garden', async () => {
    renderAt('/child/garden', { 'GET /me/garden': { ...garden, plantCount: 0, dueCount: 0, beds: [] } });
    expect(await screen.findByText(/Your garden is empty/)).toBeInTheDocument();
  });

  it('offers to keep watering an unfinished review', async () => {
    renderAt('/child/garden', { 'GET /me/garden': { ...garden, dueCount: 0, openReviewId: 40 } });
    expect(await screen.findByRole('link', { name: /Keep watering/ })).toBeInTheDocument();
  });

  it('home shows the garden card once something grows', async () => {
    renderAt('/child', {});
    expect(await screen.findByRole('heading', { name: /My garden/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Water the garden' })).toHaveAttribute('href', '/child/garden/water');
  });

  it('waters the garden like a training and tells how many plants grew', async () => {
    const api = renderAt('/child/garden/water', {
      'POST /me/garden/reviews': review,
      'POST /sessions/40/answers': {
        verdict: 'CORRECT',
        correctAnswer: 'cow',
        showCorrectAnswerFor: 5,
        becameProblematic: false,
        roundFinished: true,
        sessionFinished: true,
        next: null,
        points: 2,
      },
      'GET /sessions/40': {
        ...review,
        status: 'COMPLETED',
        current: null,
        words: [{ weekWordId: 3, solvedRound: 1, hintUsed: false, problematic: false, points: 2 }],
      },
    });

    expect(await screen.findByText('lehm')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'To the garden' })).toHaveAttribute('href', '/child/garden');
    expect(screen.queryByText('Stars:')).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Your answer'), 'cow');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));
    await userEvent.click(await screen.findByRole('button', { name: /Correct|Great job|Well done/ }));

    expect(await screen.findByRole('heading', { name: 'The garden is watered!' })).toBeInTheDocument();
    expect(screen.getByText('1 plant has grown.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /To the garden/ })).toHaveAttribute('href', '/child/garden');
    expect(api.called('POST', '/weeks/5/sessions')).toHaveLength(0);
  });

  it('goes back to the garden when there is nothing to water', async () => {
    renderAt('/child/garden/water', {
      'POST /me/garden/reviews': { status: 409, body: { code: 'CONFLICT', detail: 'No plant needs water today' } },
    });
    expect(await screen.findByRole('heading', { name: /My garden/ })).toBeInTheDocument();
    await waitFor(() => expect(window.location.pathname).toBe('/child/garden'));
  });
});
