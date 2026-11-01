const MINIMUM_LOADING_MS = 5000;
const loadingStarts = new Map<number, number>();

export function rememberApplicationLoadingStart(applicationId: number, startedAt: number) {
  loadingStarts.set(applicationId, startedAt);
}

export function remainingApplicationLoadingMs(applicationId: number, fallbackStartedAt: number) {
  return Math.max(0, MINIMUM_LOADING_MS - (Date.now() - (loadingStarts.get(applicationId) ?? fallbackStartedAt)));
}

export function clearApplicationLoadingStart(applicationId: number) {
  loadingStarts.delete(applicationId);
}
