import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { resetHttpState } from '../api/http';
import { App } from '../App';
import { account, mockApi, parentTokens } from '../test/mockApi';

const info = (devMode: boolean) => ({ name: 'KidsVocabulary', version: 'dev', languages: [], devMode });

afterEach(() => {
  vi.restoreAllMocks();
  resetHttpState();
});

describe('dev mode', () => {
  it('signs in without a password when the server is in dev mode', async () => {
    const api = mockApi({
      'POST /auth/refresh': { status: 401, body: { code: 'INVALID_REFRESH_TOKEN' } },
      'GET /info': info(true),
      'POST /auth/dev-login': parentTokens,
      'GET /account/me': { ...account, email: 'admin' },
      'GET /children': [],
    });
    window.history.pushState({}, '', '/');
    render(<App initialLang="en" />);
    expect(await screen.findByText(/No children yet/)).toBeInTheDocument();
    expect(api.called('POST', '/auth/dev-login')).toHaveLength(1);
  });

  it('shows nothing of it without dev mode', async () => {
    const api = mockApi({
      'POST /auth/refresh': { status: 401, body: { code: 'INVALID_REFRESH_TOKEN' } },
      'GET /info': info(false),
    });
    window.history.pushState({}, '', '/');
    render(<App initialLang="en" />);
    expect(await screen.findByLabelText('E-mail')).toHaveAttribute('type', 'email');
    expect(screen.queryByRole('button', { name: /Sign in as/ })).not.toBeInTheDocument();
    expect(api.called('POST', '/auth/dev-login')).toHaveLength(0);
  });

  it('opened on purpose, the login page stays and offers the dev account', async () => {
    let infoCalls = 0;
    const api = mockApi({
      'POST /auth/refresh': { status: 401, body: { code: 'INVALID_REFRESH_TOKEN' } },
      'GET /info': () => {
        infoCalls++;
        return { status: 200, body: info(true) };
      },
      'POST /auth/dev-login': parentTokens,
      'GET /account/me': account,
      'GET /children': [],
    });
    window.history.pushState({}, '', '/login');
    render(<App initialLang="en" />);
    expect(await screen.findByText('Parent PIN: 1234')).toBeInTheDocument();
    expect(screen.getByLabelText('E-mail')).toHaveAttribute('type', 'text');
    expect(api.called('POST', '/auth/dev-login')).toHaveLength(0);
    expect(infoCalls).toBe(1);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in as admin' }));
    expect(await screen.findByText(/No children yet/)).toBeInTheDocument();
    expect(api.called('POST', '/auth/dev-login')).toHaveLength(1);
  });
});
