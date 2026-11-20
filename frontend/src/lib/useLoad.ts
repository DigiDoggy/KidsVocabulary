import { useCallback, useEffect, useState, type DependencyList } from 'react';

export interface Loaded<T> {
  data: T | undefined;
  error: unknown;
  loading: boolean;
  reload: () => void;
  /** Replaces the data with a value returned by a mutation (no extra request). */
  setData: (data: T) => void;
}

/** Runs an async loader when the dependencies change; ignores results of outdated runs. */
export function useLoad<T>(loader: () => Promise<T>, deps: DependencyList): Loaded<T> {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [generation, setGeneration] = useState(0);

  // the caller's deps decide when the loader is considered new
  const load = useCallback(loader, deps);

  useEffect(() => {
    let current = true;
    setLoading(true);
    load()
      .then((value) => {
        if (current) {
          setData(value);
          setError(null);
        }
      })
      .catch((e: unknown) => {
        if (current) {
          setError(e);
        }
      })
      .finally(() => {
        if (current) {
          setLoading(false);
        }
      });
    return () => {
      current = false;
    };
  }, [load, generation]);

  const reload = useCallback(() => setGeneration((g) => g + 1), []);
  return { data, error, loading, reload, setData };
}
