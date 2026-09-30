import { useInfiniteQuery } from '@tanstack/react-query';
import { getDocumentContent } from '../api/documents';
import { documentKeys } from '../api/queryKeys';
import type { DocumentChunkResponse } from '../api/types';

/** The widest window the API serves; a larger one is clamped there anyway. */
const CHUNK_WINDOW = 50;

export type DocumentChunks = {
  readonly chunks: readonly DocumentChunkResponse[];
  readonly isPending: boolean;
  readonly isError: boolean;
  readonly error: unknown;
  readonly isFetchingNextPage: boolean;
  readonly hasNextPage: boolean;
  readonly loadMore: () => void;
  readonly retry: () => void;
};

/**
 * A document's body, one window at a time. `nextChunkIndex` is a cursor, so a window is asked for
 * only when the reader reaches the end of what is already rendered: a ten thousand chunk document
 * costs one request, not ten thousand rendered nodes.
 */
export function useDocumentChunks(id: string, enabled: boolean): DocumentChunks {
  const content = useInfiniteQuery({
    queryKey: documentKeys.content(id),
    queryFn: ({ pageParam, signal }) => getDocumentContent(id, pageParam, CHUNK_WINDOW, signal),
    initialPageParam: 0,
    getNextPageParam: (lastWindow) => lastWindow.nextChunkIndex ?? null,
    enabled,
  });

  return {
    chunks: (content.data?.pages ?? []).flatMap((window) => window.chunks ?? []),
    isPending: content.isPending,
    isError: content.isError,
    error: content.error,
    isFetchingNextPage: content.isFetchingNextPage,
    hasNextPage: content.hasNextPage,
    loadMore: () => {
      if (content.hasNextPage && !content.isFetchingNextPage) {
        void content.fetchNextPage();
      }
    },
    retry: () => void content.refetch(),
  };
}
