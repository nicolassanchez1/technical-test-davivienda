import { QueryClient } from '@tanstack/react-query';

/**
 * No interval and no retry anywhere: a document's status arrives over the event stream, and a
 * retry loop would be the poll this design exists to avoid. A failed query surfaces its error
 * state with an explicit retry the reader triggers.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        refetchInterval: false,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        retry: false,
        staleTime: 30_000,
      },
      mutations: { retry: false },
    },
  });
}
