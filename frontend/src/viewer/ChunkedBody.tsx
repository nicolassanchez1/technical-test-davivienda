import { useMemo } from 'react';
import { Virtuoso } from 'react-virtuoso';
import type { DocumentChunkResponse } from '../api/types';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';
import { HighlightedText } from '../search/HighlightedText';
import { containsTerm, markTerms } from '../search/queryTerms';
import { BodyError, BodyLoading, BodyNotice } from './BodyState';
import { useDocumentChunks } from './useDocumentChunks';

/** Tall enough to read in, and fixed so the virtualiser knows how much it has to fill. */
const VIEWPORT_HEIGHT = '70vh';

export type ChunkedBodyProps = {
  readonly documentId: string;
  readonly terms: readonly string[];
  /** PDFs carry a page per chunk, and a page number is the only way back to the original. */
  readonly showPages: boolean;
};

/**
 * The indexed body, virtualised. Only the chunks on screen are mounted, so a document with ten
 * thousand of them costs the same to render as one with ten.
 */
export function ChunkedBody({ documentId, terms, showPages }: ChunkedBodyProps) {
  const content = useDocumentChunks(documentId, documentId.length > 0);
  const chunks = content.chunks;

  const firstMatch = useMemo(() => firstMatchingChunkIndex(chunks, terms), [chunks, terms]);

  if (content.isPending) {
    return <BodyLoading />;
  }
  if (content.isError) {
    return <BodyError error={content.error} onRetry={content.retry} />;
  }
  if (chunks.length === 0) {
    return <BodyNotice text={copy.viewer.body.empty} />;
  }

  return (
    <div className="flex flex-col gap-2">
      <Virtuoso
        style={{ height: VIEWPORT_HEIGHT }}
        className="rounded-lg ring-1 ring-slate-200"
        data={chunks}
        // The reader arrived from a search, so the body opens on the chunk that matched.
        initialTopMostItemIndex={firstMatch}
        computeItemKey={(index, chunk) => chunk.chunkIndex ?? index}
        endReached={content.loadMore}
        itemContent={(_, chunk) => <ChunkBlock chunk={chunk} terms={terms} showPages={showPages} />}
      />
      {content.isFetchingNextPage ? (
        <p role="status" className="text-sm text-slate-600">
          {copy.viewer.body.loadingMore}
        </p>
      ) : null}
    </div>
  );
}

/** Where a body opened from a search should start reading; the top when nothing matched. */
export function firstMatchingChunkIndex(
  chunks: readonly DocumentChunkResponse[],
  terms: readonly string[],
): number {
  const match = chunks.findIndex((chunk) => containsTerm(chunk.content ?? '', terms));
  return Math.max(match, 0);
}

function ChunkBlock({
  chunk,
  terms,
  showPages,
}: {
  readonly chunk: DocumentChunkResponse;
  readonly terms: readonly string[];
  readonly showPages: boolean;
}) {
  return (
    <article className="flex flex-col gap-1 border-b border-slate-100 px-4 py-3 last:border-b-0">
      {showPages && chunk.page !== undefined ? (
        <p className="text-xs font-semibold tracking-wide text-slate-500 uppercase">
          {interpolate(copy.viewer.body.pageLabel, { page: chunk.page })}
        </p>
      ) : null}
      {chunk.heading ? (
        <h4 className="text-base font-semibold text-slate-900">{chunk.heading}</h4>
      ) : null}
      <p className="text-slate-800 whitespace-pre-wrap [&_mark]:bg-amber-200">
        <HighlightedText text={markTerms(chunk.content ?? '', terms)} />
      </p>
    </article>
  );
}
