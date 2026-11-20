// DTOs of the backend REST API (/api/v1). Decimal values (points, percent) arrive as JSON numbers.

export type AuthMode = 'PARENT' | 'CHILD';
export type Level = 'A0' | 'A1' | 'A2';
export type WeekStatus = 'DRAFT' | 'SCHEDULED' | 'ACTIVE' | 'COMPLETED' | 'EXPIRED';
export type WordSource = 'MANUAL' | 'AI' | 'DICTIONARY' | 'CARRY_OVER';
export type Outcome = 'LEARNED' | 'HARD' | 'UNLEARNED';
export type MonthStatus = 'ACTIVE' | 'COMPLETED';
export type MonthResult = 'REWARD' | 'GOOD' | 'REPEAT';

export interface Language {
  code: string;
  name: string;
}

export interface AppInfo {
  name: string;
  version: string;
  languages: Language[];
}

export interface TokenResponse {
  accessToken: string;
  expiresIn: number;
  mode: AuthMode;
  childId: number | null;
}

export interface Me {
  email: string;
  timezone: string;
  mode: AuthMode;
  childId: number | null;
}

export interface ChildData {
  name: string;
  birthDate: string | null;
  level: Level;
  nativeLang: string;
  targetLang: string;
  defaultWeekSize: number;
}

export interface Child extends ChildData {
  id: number;
}

export interface WeekSummary {
  id: number;
  startDate: string;
  endDate: string;
  status: WeekStatus;
  weekIndex: number | null;
  wordCount: number;
  points: number | null;
  maxPoints: number | null;
}

export interface WordView {
  id: number;
  conceptId: number;
  source: WordSource;
  prompt: string;
  answer: string;
  extraAnswers: string[];
  points: number | null;
  outcome: Outcome | null;
}

export interface Week extends WeekSummary {
  childId: number;
  missing: number;
  words: WordView[];
}

export interface WordStat {
  weekWordId: number;
  prompt: string;
  answer: string;
  wrongRounds: number;
  solvedRound: number | null;
  hintUsed: boolean;
  problematic: boolean;
  points: number | null;
  outcome: Outcome | null;
}

export interface HomeWeek {
  id: number;
  status: WeekStatus;
  startDate: string;
  endDate: string;
  weekIndex: number | null;
  wordCount: number;
  points: number | null;
  maxPoints: number | null;
  firstPassOpen: boolean;
}

export interface HomeMonth {
  id: number;
  seqNo: number;
  status: MonthStatus;
  weeksClosed: number;
  points: number | null;
  maxPoints: number | null;
  reward: string | null;
}

export interface Home {
  child: { id: number; name: string; nativeLang: string; targetLang: string };
  week: HomeWeek | null;
  month: HomeMonth | null;
}

export interface MonthSummary {
  id: number;
  seqNo: number;
  status: MonthStatus;
  reward: string | null;
  percent: number | null;
  result: MonthResult | null;
}

export interface MonthWeekRow {
  id: number;
  weekIndex: number | null;
  startDate: string;
  status: WeekStatus;
  wordCount: number;
  points: number | null;
  maxPoints: number | null;
}

export interface MonthProgress {
  id: number;
  seqNo: number;
  status: MonthStatus;
  reward: string | null;
  points: number | null;
  maxPoints: number | null;
  percent: number | null;
  result: MonthResult | null;
  weeks: MonthWeekRow[];
}

export interface WeekStats {
  index: number;
  startDate: string;
  points: number;
  maxPoints: number;
  percent: number;
  learned: number;
  hard: number;
  unlearned: number;
}

export interface ReportWord {
  conceptId: number;
  prompt: string;
  answer: string;
}

export interface MonthReport {
  monthId: number;
  seqNo: number;
  totalPoints: number;
  maxPoints: number;
  percent: number;
  result: MonthResult;
  reward: string | null;
  weeks: WeekStats[];
  hardWords: ReportWord[];
  unlearnedWords: ReportWord[];
}

// --- training ---------------------------------------------------------------------------------------

export type SessionKind = 'FIRST_PASS' | 'PRACTICE';
export type SessionStatus = 'IN_PROGRESS' | 'COMPLETED' | 'CLOSED';
export type Verdict = 'CORRECT' | 'WRONG' | 'IGNORED';

/** The word to spell now. `hintMask` is the primary answer with hidden letters as `_`. */
export interface CurrentItem {
  wordId: number;
  round: number;
  position: number;
  prompt: string;
  answerLength: number;
  hintMask: string;
  hintAvailable: boolean;
  roundSize: number;
  remainingInRound: number;
}

export interface WordState {
  weekWordId: number;
  solvedRound: number | null;
  hintUsed: boolean;
  problematic: boolean;
  points: number;
}

export interface TrainingSession {
  id: number;
  weekId: number;
  kind: SessionKind;
  status: SessionStatus;
  round: number;
  current: CurrentItem | null;
  points: number;
  maxPoints: number;
  counts: boolean;
  words: WordState[];
}

export interface AnswerResponse {
  verdict: Verdict;
  correctAnswer: string | null;
  showCorrectAnswerFor: number;
  becameProblematic: boolean;
  roundFinished: boolean;
  sessionFinished: boolean;
  next: CurrentItem | null;
  points: number;
}

export interface HintResponse {
  hintMask: string;
  hintAvailable: boolean;
}
