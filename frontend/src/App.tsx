import { useEffect, useState } from 'react';
import { fetchInfo, type AppInfo } from './api/client';

type State =
  | { kind: 'loading' }
  | { kind: 'ok'; info: AppInfo }
  | { kind: 'error'; message: string };

export function App() {
  const [state, setState] = useState<State>({ kind: 'loading' });

  useEffect(() => {
    fetchInfo()
      .then((info) => setState({ kind: 'ok', info }))
      .catch((e: Error) => setState({ kind: 'error', message: e.message }));
  }, []);

  return (
    <main className="page">
      <h1 className="title">KidsVocabulary</h1>
      {state.kind === 'loading' && <p className="status">Connecting…</p>}
      {state.kind === 'error' && (
        <p className="status status--error" role="alert">
          Server unavailable ({state.message})
        </p>
      )}
      {state.kind === 'ok' && (
        <>
          <p className="status status--ok">Server is up · version {state.info.version}</p>
          <ul className="langs">
            {state.info.languages.map((l) => (
              <li key={l.code} className="lang">
                <span className="lang__code">{l.code}</span> {l.name}
              </li>
            ))}
          </ul>
        </>
      )}
    </main>
  );
}
