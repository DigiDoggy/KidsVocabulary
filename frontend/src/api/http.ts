import type { TokenResponse } from './types';

export const API_BASE = '/api/v1';

/** An RFC 7807 problem returned by the backend, or a network failure (status 0). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export interface Session {
  mode: TokenResponse['mode'];
  childId: number | null;
}

type SessionListener = (session: Session | null) => void;

// The access token lives in memory only; the refresh token is an httpOnly cookie.
let accessToken: string | null = null;
let refreshInFlight: Promise<Session | null> | null = null;
const listeners = new Set<SessionListener>();

export function onSessionChange(listener: SessionListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function emit(session: Session | null) {
  listeners.forEach((l) => l(session));
}

/** Stores a freshly issued token pair and notifies listeners about the new mode. */
export function acceptTokens(tokens: TokenResponse): Session {
  accessToken = tokens.accessToken;
  const session = { mode: tokens.mode, childId: tokens.childId };
  emit(session);
  return session;
}

export function clearSession() {
  accessToken = null;
  emit(null);
}

/** For tests: forget the in-memory state without notifying anyone. */
export function resetHttpState() {
  accessToken = null;
  refreshInFlight = null;
  listeners.clear();
}

/**
 * Exchanges the refresh cookie for a new access token. Refresh tokens are single-use and reusing one
 * revokes the whole account's sessions, so concurrent callers share one request, and other tabs wait
 * for it through a Web Lock (they then send the already rotated cookie).
 */
export function refreshSession(): Promise<Session | null> {
  if (!refreshInFlight) {
    refreshInFlight = withRefreshLock(async () => {
      const res = await fetch(`${API_BASE}/auth/refresh`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { Accept: 'application/json' },
      });
      if (!res.ok) {
        clearSession();
        return null;
      }
      return acceptTokens((await res.json()) as TokenResponse);
    }).finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
  return locks ? locks.request('kidsvocab-refresh', task) : task();
}

interface RequestOptions {
  body?: unknown;
  /** Send the access token and refresh it on 401 (default true). */
  auth?: boolean;
}

export async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const { body, auth = true } = options;
  let res = await send(method, path, body, auth);
  if (res.status === 401 && auth) {
    const session = await refreshSession();
    if (!session) {
      throw await toError(res);
    }
    res = await send(method, path, body, auth);
    if (res.status === 401) {
      clearSession();
    }
  }
  if (!res.ok) {
    throw await toError(res);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Reads are safe to repeat, so a GET that hit a network failure is tried once more after this pause. */
export const GET_RETRY_DELAY_MS = 800;

async function send(method: string, path: string, body: unknown, auth: boolean): Promise<Response> {
  try {
    return await sendOnce(method, path, body, auth);
  } catch (e) {
    if (method !== 'GET' || !(e instanceof ApiError) || e.code !== 'NETWORK') throw e;
    await new Promise((resolve) => setTimeout(resolve, GET_RETRY_DELAY_MS));
    return sendOnce(method, path, body, auth);
  }
}

async function sendOnce(method: string, path: string, body: unknown, auth: boolean): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (auth && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  try {
    return await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (e) {
    throw new ApiError(0, 'NETWORK', e instanceof Error ? e.message : String(e));
  }
}

async function toError(res: Response): Promise<ApiError> {
  let problem: { code?: string; detail?: string; errors?: Record<string, string> } = {};
  try {
    problem = (await res.json()) as typeof problem;
  } catch {
    // not a problem+json body (e.g. a proxy error page)
  }
  const code = problem.code ?? (res.status === 401 ? 'UNAUTHORIZED' : res.status === 403 ? 'FORBIDDEN' : `HTTP_${res.status}`);
  return new ApiError(res.status, code, problem.detail ?? `HTTP ${res.status}`, problem.errors ?? {});
}
