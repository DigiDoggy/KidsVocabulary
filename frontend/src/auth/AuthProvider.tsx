import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { onSessionChange, refreshSession, type Session } from '../api/http';

export type AuthState = { status: 'loading' } | { status: 'anonymous' } | ({ status: 'signed-in' } & Session);

const AuthContext = createContext<AuthState>({ status: 'loading' });

function toState(session: Session | null): AuthState {
  return session ? { status: 'signed-in', ...session } : { status: 'anonymous' };
}

/** Restores the session from the refresh cookie on start and follows every later token change. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });

  useEffect(() => {
    const unsubscribe = onSessionChange((session) => setState(toState(session)));
    let cancelled = false;
    refreshSession().then((session) => {
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
