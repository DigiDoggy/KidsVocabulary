export interface Language {
  code: string;
  name: string;
}

export interface AppInfo {
  name: string;
  version: string;
  languages: Language[];
}

const BASE = '/api/v1';

export async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`, { headers: { Accept: 'application/json' } });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`);
  }
  return (await res.json()) as T;
}

export const fetchInfo = () => getJson<AppInfo>('/info');
