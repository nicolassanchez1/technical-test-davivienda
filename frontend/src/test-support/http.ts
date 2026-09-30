import { vi } from 'vitest';

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

export function textResponse(body: string, contentType = 'text/markdown'): Response {
  return new Response(body, { status: 200, headers: { 'content-type': contentType } });
}

export function problemResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/problem+json' },
  });
}

export type FetchMock = ReturnType<
  typeof vi.fn<(...args: Parameters<typeof fetch>) => Promise<Response>>
>;

export function installFetchMock(): FetchMock {
  const mock = vi.fn<(...args: Parameters<typeof fetch>) => Promise<Response>>();
  globalThis.fetch = mock as unknown as typeof fetch;
  return mock;
}

export function requestedUrl(mock: FetchMock, callIndex = 0): string {
  const call = mock.mock.calls[callIndex];
  if (!call) {
    throw new Error(`No fetch call at index ${callIndex}`);
  }
  return String(call[0]);
}
