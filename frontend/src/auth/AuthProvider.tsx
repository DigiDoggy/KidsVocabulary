import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { devLogin, fetchInfo } from '../api/client';
import { onSessionChange, refreshSession, type Session } from '../api/http';

export type AuthState = { status: 'loading' } | { status: 'anonymous' } | ({ status: 'signed-in' } & Session);

const AuthContext = createContext<AuthState>({ status: 'loading' });

function toState(session: Session | null): AuthState {
  return session ? { status: 'signed-in', ...session } : { status: 'anonymous' };
}

/**
 * Without a session, a server in dev mode signs in to its dev account (local development only), unless the
 * login or registration page was opened on purpose: they stay usable for other accounts.
 */
async function devSession(): Promise<Session | null> {
  if (['/login', '/register'].includes(window.location.pathname)) return null;
  try {
    return (await fetchInfo()).devMode ? await devLogin() : null;
  } catch {
    return null;
  }
}

/**
 * Restores the session from the refresh cookie on start (or, in dev mode, signs in without a password) and
 * follows every later token change. After signing out the login page stays, also in dev mode.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });

  useEffect(() => {
    const unsubscribe = onSessionChange((session) => setState(toState(session)));
    let cancelled = false;
    refreshSession().then(async (restored) => {
      const session = restored ?? (await devSession());
      if (!cancelled) {
        setState(toState(session));
      }
    });
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, []);

  return <AuthContext.Provider value={state}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  return useContext(AuthContext);
}
