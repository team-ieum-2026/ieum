"use client";

import { useCallback, useEffect, useState } from "react";

export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "일시적인 오류가 생겼어요. 다시 시도해 주세요.";
}

/** loader는 useCallback으로 고정한다. 경로 변경 후 늦게 도착한 응답은 버린다. */
export function useApi<T>(loader: () => Promise<T>, { refreshIntervalMs = 0 }: { refreshIntervalMs?: number } = {}) {
  const [state, setState] = useState<{ data: T | null; error: string | null; loading: boolean; loader: () => Promise<T> }>({ data: null, error: null, loading: true, loader });
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    async function fetchData() {
      try {
        const data = await loader();
        if (active) setState({ data, error: null, loading: false, loader });
      } catch (error: unknown) {
        if (active) setState((previous) => ({ data: previous.loader === loader ? previous.data : null, error: errorMessage(error), loading: false, loader }));
      } finally {
        if (active && refreshIntervalMs > 0) timer = setTimeout(refresh, refreshIntervalMs);
      }
    }
    function refresh() {
      if (document.visibilityState === "hidden") timer = setTimeout(refresh, refreshIntervalMs);
      else void fetchData();
    }
    void fetchData();
    return () => { active = false; clearTimeout(timer); };
  }, [loader, revision, refreshIntervalMs]);
  const reload = useCallback(() => {
    setState((previous) => previous.loader === loader
      ? { ...previous, error: null, loading: previous.data === null }
      : { data: null, error: null, loading: true, loader });
    setRevision((value) => value + 1);
  }, [loader]);
  const setData = useCallback((data: T) => setState({ data, error: null, loading: false, loader }), [loader]);
  const current = state.loader === loader ? state : { data: null, error: null, loading: true };
  return { ...current, reload, setData };
}
