import { acceptTokens, clearSession, request, type Session } from './http';
import type {
  AnswerResponse,
  AppInfo,
  Child,
  ChildData,
  Game,
  GameMenu,
  GameMode,
  GameTask,
  GameTaskSummary,
  Home,
  Me,
  MonthProgress,
  MonthReport,
  MonthSummary,
  HintResponse,
  TokenResponse,
  TrainingSession,
  Week,
  WeekSummary,
  WordStat,
} from './types';

export type { AppInfo, Language } from './types';

const get = <T>(path: string) => request<T>('GET', path);
const post = <T>(path: string, body?: unknown) => request<T>('POST', path, { body });
const put = <T>(path: string, body?: unknown) => request<T>('PUT', path, { body });
const del = (path: string) => request<void>('DELETE', path);

export const fetchInfo = () => request<AppInfo>('GET', '/info', { auth: false });

// --- auth ------------------------------------------------------------------------------------------

export async function login(email: string, password: string): Promise<Session> {
  return acceptTokens(
    await request<TokenResponse>('POST', '/auth/login', { body: { email, password }, auth: false }),
  );
}

export async function register(email: string, password: string, pin: string, timezone: string): Promise<Session> {
  return acceptTokens(
    await request<TokenResponse>('POST', '/auth/register', {
      body: { email, password, pin, timezone },
      auth: false,
    }),
  );
}

/** Local development only (the server's dev mode): signs in to the dev account without a password. */
export async function devLogin(): Promise<Session> {
  return acceptTokens(await request<TokenResponse>('POST', '/auth/dev-login', { auth: false }));
}

export async function logout(): Promise<void> {
  try {
    await request<void>('POST', '/auth/logout', { auth: false });
  } finally {
    clearSession();
  }
}

export async function switchToChild(childId: number): Promise<Session> {
  return acceptTokens(await post<TokenResponse>(`/auth/switch-to-child/${childId}`));
}

export async function switchToParent(pin: string): Promise<Session> {
  return acceptTokens(await post<TokenResponse>('/auth/switch-to-parent', { pin }));
}

export const fetchMe = () => get<Me>('/account/me');
export const changePin = (currentPassword: string, newPin: string) =>
  put<void>('/account/pin', { currentPassword, newPin });

// --- children --------------------------------------------------------------------------------------

export const listChildren = () => get<Child[]>('/children');
export const getChild = (id: number) => get<Child>(`/children/${id}`);
export const createChild = (data: ChildData) => post<Child>('/children', data);
export const updateChild = (id: number, data: ChildData) => put<Child>(`/children/${id}`, data);
export const archiveChild = (id: number) => del(`/children/${id}`);
export const childHome = (id: number) => get<Home>(`/children/${id}/home`);
export const myHome = () => get<Home>('/me/home');

// --- weeks -----------------------------------------------------------------------------------------

export const listWeeks = (childId: number) => get<WeekSummary[]>(`/children/${childId}/weeks`);
export const createWeek = (childId: number, startDate?: string, wordCount?: number) =>
  post<Week>(`/children/${childId}/weeks`, { startDate, wordCount });
export const getWeek = (weekId: number) => get<Week>(`/weeks/${weekId}`);
export const updateWeek = (weekId: number, wordCount: number, startDate?: string) =>
  put<Week>(`/weeks/${weekId}`, { wordCount, startDate });
export const deleteWeek = (weekId: number) => del(`/weeks/${weekId}`);
export const addWord = (weekId: number, source: string, target: string, extraAnswers: string[], emoji?: string) =>
  post<Week>(`/weeks/${weekId}/words`, { source, target, extraAnswers, emoji: emoji || null });
/** Sets the word's picture; an empty value removes it. */
export const setWordEmoji = (weekId: number, wordId: number, emoji: string) =>
  put<Week>(`/weeks/${weekId}/words/${wordId}/emoji`, { emoji: emoji || null });
export const removeWord = (weekId: number, wordId: number) =>
  request<Week>('DELETE', `/weeks/${weekId}/words/${wordId}`);
export const replaceWord = (weekId: number, wordId: number, topic?: string) =>
  post<Week>(`/weeks/${weekId}/words/${wordId}/replace`, { topic: topic || null });
export const fillUp = (weekId: number, topic?: string) =>
  post<Week>(`/weeks/${weekId}/suggest`, { topic: topic || null });
export const launchWeek = (weekId: number) => post<Week>(`/weeks/${weekId}/launch`);
export const unscheduleWeek = (weekId: number) => post<Week>(`/weeks/${weekId}/unschedule`);
export const weekStats = (weekId: number) => get<WordStat[]>(`/weeks/${weekId}/stats`);
export const listTopics = () => get<string[]>('/dictionary/topics');

// --- months ----------------------------------------------------------------------------------------

export const listMonths = (childId: number) => get<MonthSummary[]>(`/children/${childId}/months`);
export const setReward = (childId: number, reward: string) =>
  put<MonthSummary>(`/children/${childId}/months/current/reward`, { reward });
export const getMonth = (monthId: number) => get<MonthProgress>(`/months/${monthId}`);
export const getMonthReport = (monthId: number) => get<MonthReport>(`/months/${monthId}/report`);

// --- training --------------------------------------------------------------------------------------

/** Starts the first pass (or resumes it); after it, a practice session that does not count. */
export const startSession = (weekId: number) => post<TrainingSession>(`/weeks/${weekId}/sessions`);
export const getSession = (sessionId: number) => get<TrainingSession>(`/sessions/${sessionId}`);
export const submitAnswer = (sessionId: number, weekWordId: number, round: number, answer: string) =>
  post<AnswerResponse>(`/sessions/${sessionId}/answers`, { weekWordId, round, answer });
export const requestHint = (sessionId: number, weekWordId: number, round: number) =>
  post<HintResponse>(`/sessions/${sessionId}/hint`, { weekWordId, round });

// --- games (unscored) ------------------------------------------------------------------------------

export const gameMenu = () => get<GameMenu>('/me/games');
export const weekGame = (mode: GameMode) => get<Game>(`/me/games/week/${mode}`);
export const taskGame = (taskId: number) => get<Game>(`/me/games/tasks/${taskId}`);
export const taskPlayed = (taskId: number) => post<void>(`/me/games/tasks/${taskId}/played`);

export const listGameTasks = (childId: number) => get<GameTaskSummary[]>(`/children/${childId}/game-tasks`);
export const createGameTask = (childId: number, mode: GameMode) =>
  post<GameTask>(`/children/${childId}/game-tasks`, { mode });
export const getGameTask = (taskId: number) => get<GameTask>(`/game-tasks/${taskId}`);
export const deleteGameTask = (taskId: number) => del(`/game-tasks/${taskId}`);
export const addTaskWord = (taskId: number, source: string, target: string) =>
  post<GameTask>(`/game-tasks/${taskId}/words`, { source, target });
export const removeTaskWord = (taskId: number, wordId: number) =>
  request<GameTask>('DELETE', `/game-tasks/${taskId}/words/${wordId}`);
export const fillGameTask = (taskId: number, topic?: string, count?: number) =>
  post<GameTask>(`/game-tasks/${taskId}/suggest`, { topic: topic || null, count: count ?? null });
export const giveGameTask = (taskId: number) => post<GameTask>(`/game-tasks/${taskId}/give`);
