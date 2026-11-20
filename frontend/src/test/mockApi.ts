import { vi } from 'vitest';

export interface Call {
  method: string;
  path: string;
  body: unknown;
  auth: string | null;
}

export interface Reply {
  status?: number;
  body?: unknown;
}

type Route = Reply | ((call: Call) => Reply | Promise<Reply>);

/**
 * Replaces fetch with a table of routes keyed by "METHOD /path" (path without the /api/v1 prefix).
 * A plain value is answered with 200 and that value as JSON; unknown routes get 404.
 */
export function mockApi(routes: Record<string, Route | object>) {
  const calls: Call[] = [];
  const spy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const path = url.replace(/^.*\/api\/v1/, '');
    const method = init?.method ?? 'GET';
    const headers = (init?.headers ?? {}) as Record<string, string>;
    const call: Call = {
      method,
      path,
      body: init?.body ? JSON.parse(String(init.body)) : undefined,
      auth: headers.Authorization ?? null,
    };
    calls.push(call);
    const route = routes[`${method} ${path}`];
    if (route === undefined) {
      return json(404, { code: 'NOT_FOUND', detail: `No mock for ${method} ${path}` });
    }
    const reply: Reply =
      typeof route === 'function'
        ? await (route as (c: Call) => Reply | Promise<Reply>)(call)
        : isReply(route)
          ? route
          : { body: route };
    return reply.body === undefined ? new Response(null, { status: reply.status ?? 204 }) : json(reply.status ?? 200, reply.body);
  });
  return { calls, spy, called: (method: string, path: string) => calls.filter((c) => c.method === method && c.path === path) };
}

function isReply(value: unknown): value is Reply {
  return typeof value === 'object' && value !== null && typeof (value as Reply).status === 'number';
}

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

export const parentTokens = { accessToken: 'parent-token', expiresIn: 900, mode: 'PARENT', childId: null };
export const childTokens = (childId: number) => ({ accessToken: 'child-token', expiresIn: 900, mode: 'CHILD', childId });
export const account = { email: 'parent@example.com', timezone: 'Europe/Tallinn', mode: 'PARENT', childId: null };
