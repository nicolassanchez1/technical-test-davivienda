import { apiErrorFrom } from './problem';

/** The SPA and the API share one origin: Vite proxies this prefix in dev, nginx in production. */
export const API_BASE_PATH = '/api';

export type QueryValue = string | number | boolean | null | undefined;

export function buildQuery(params: Readonly<Record<string, QueryValue>>): string {
  const search = new URLSearchParams();
  for (const [name, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === '') {
      continue;
    }
    search.set(name, String(value));
  }
  const query = search.toString();
  return query.length > 0 ? `?${query}` : '';
}

export function apiUrl(path: string): string {
  return `${API_BASE_PATH}${path}`;
}

/**
 * The single place a response is turned into either data or an {@link ApiError}. The signal comes
 * from TanStack Query, so a search that is superseded is cancelled instead of resolving late.
 */
export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(apiUrl(path), init);
  if (!response.ok) {
    throw await apiErrorFrom(response);
  }
  return (await response.json()) as T;
}
