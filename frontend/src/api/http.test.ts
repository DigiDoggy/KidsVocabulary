import { mockApi, parentTokens } from '../test/mockApi';
import { acceptTokens, ApiError, onSessionChange, refreshSession, request, resetHttpState } from './http';

describe('http client', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    resetHttpState();
  });

  it('sends the access token and parses JSON', async () => {
    const api = mockApi({ 'GET /children': [{ id: 1 }] });
    acceptTokens({ ...parentTokens, mode: 'PARENT' });

    await expect(request('GET', '/children')).resolves.toEqual([{ id: 1 }]);
    expect(api.calls[0].auth).toBe('Bearer parent-token');
  });

  it('refreshes once for concurrent 401s and retries each request with the new token', async () => {
    let refreshed = false;
    const api = mockApi({
      'POST /auth/refresh': async () => {
        await new Promise((r) => setTimeout(r, 5));
        refreshed = true;
        return { body: { ...parentTokens, accessToken: 'fresh' } };
      },
      'GET /children': (c) => (refreshed && c.auth === 'Bearer fresh' ? { body: [] } : { status: 401, body: {} }),
      'GET /account/me': (c) => (refreshed && c.auth === 'Bearer fresh' ? { body: { email: 'x' } } : { status: 401, body: {} }),
    });
    acceptTokens({ ...parentTokens, mode: 'PARENT', accessToken: 'stale' });

    const [children, me] = await Promise.all([request('GET', '/children'), request('GET', '/account/me')]);

    expect(children).toEqual([]);
    expect(me).toEqual({ email: 'x' });
    expect(api.called('POST', '/auth/refresh')).toHaveLength(1);
  });

  it('signs out when the refresh cookie is rejected', async () => {
    mockApi({
      'GET /children': { status: 401, body: { code: 'UNAUTHORIZED' } },
      'POST /auth/refresh': { status: 401, body: { code: 'INVALID_REFRESH_TOKEN' } },
    });
    const sessions: unknown[] = [];
    onSessionChange((s) => sessions.push(s));

    await expect(request('GET', '/children')).rejects.toMatchObject({ status: 401 });
    expect(sessions).toEqual([null]);
  });

  it('shares one refresh between simultaneous callers', async () => {
    const api = mockApi({ 'POST /auth/refresh': parentTokens });
    const [a, b] = await Promise.all([refreshSession(), refreshSession()]);
    expect(a).toEqual({ mode: 'PARENT', childId: null });
    expect(b).toBe(a);
    expect(api.calls).toHaveLength(1);
  });

  it('turns a problem response into an ApiError with code and field errors', async () => {
    mockApi({
      'POST /children': {
        status: 400,
        body: { code: 'VALIDATION', detail: 'Request is not valid', errors: { name: 'must not be blank' } },
      },
    });

    const error = await request('POST', '/children', { body: {} }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, code: 'VALIDATION', fieldErrors: { name: 'must not be blank' } });
  });

  it('reports a network failure as code NETWORK after one retry of a GET', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(request('GET', '/children', { auth: false })).rejects.toMatchObject({ status: 0, code: 'NETWORK' });
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('repeats a GET that hit a network failure', async () => {
    const fetch = vi
      .spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(new Response('[]', { status: 200 }));
    await expect(request('GET', '/children', { auth: false })).resolves.toEqual([]);
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('never repeats a write by itself', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(request('POST', '/children', { body: {}, auth: false })).rejects.toMatchObject({ code: 'NETWORK' });
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it('returns undefined for 204 No Content', async () => {
    mockApi({ 'DELETE /weeks/3': { status: 204 } });
    await expect(request('DELETE', '/weeks/3')).resolves.toBeUndefined();
  });
});
